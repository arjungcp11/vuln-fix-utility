package com.hexa.vulnfix.serviceImpl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import com.hexa.vulnfix.service.ProjectScannerService;

@Service
public class ProjectScannerServiceImp implements ProjectScannerService {

	private static final Logger log = LoggerFactory.getLogger(ProjectScannerServiceImp.class);

	@Override
	public void scanAndUpdateProject(Path sourceDir, Path targetDir) throws IOException {

		if (!Files.exists(targetDir)) {
			Files.createDirectories(targetDir);
		}

		Files.walk(sourceDir).filter(p -> p.toString().endsWith(".java")).forEach(javaFile -> {
			try {
				log.info("Scanning full project...");
				processJavaFile(javaFile, sourceDir, targetDir);
			} catch (Exception e) {
				log.error("Error processing file: {}", javaFile, e);
			}
		});
	}

	private void processJavaFile(Path javaFile, Path sourceDir, Path targetDir) throws IOException {

		ParseResult<CompilationUnit> result = new JavaParser().parse(javaFile);

		if (!result.isSuccessful() || result.getResult().isEmpty()) {
			return;
		}

		CompilationUnit cu = result.getResult().get();

		// Store constants per class
		Map<String, String> constantsMap = new LinkedHashMap<>();

		// Ensure logger exists
		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
			boolean hasLogger = clazz.getMembers().stream().filter(m -> m instanceof FieldDeclaration)
					.map(m -> (FieldDeclaration) m)
					.anyMatch(f -> f.getVariables().stream().anyMatch(v -> v.getNameAsString().equals("log")));

			if (!hasLogger) {
				clazz.addFieldWithInitializer("org.slf4j.Logger", "log",
						new MethodCallExpr(new NameExpr("org.slf4j.LoggerFactory"), "getLogger",
								new com.github.javaparser.ast.NodeList<>(
										new NameExpr(clazz.getNameAsString() + ".class"))),
						Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC, Modifier.Keyword.FINAL);
			}
		});

		// Replace System.out.print / println to log.info
		cu.findAll(ExpressionStmt.class).forEach(stmt -> {
			stmt.getExpression().ifMethodCallExpr(mce -> {

				if (mce.getScope().isPresent() && mce.getScope().get().toString().equals("System.out")
						&& (mce.getNameAsString().equals("print") || mce.getNameAsString().equals("println"))
						&& !mce.getArguments().isEmpty()) {

					// Change to log.info(...)
					mce.setScope(new NameExpr("log"));
					mce.setName("info");

					// Handle hardcoded string
					if (mce.getArgument(0).isStringLiteralExpr()) {
						StringLiteralExpr strExpr = mce.getArgument(0).asStringLiteralExpr();
						String value = strExpr.getValue();

						String constName = buildConstantName(value);
						constantsMap.putIfAbsent(constName, value);

						mce.setArgument(0, new NameExpr(constName));
					}
				}
			});
		});

		// Replace remaining string literals with constants (excluding annotations)
		cu.findAll(StringLiteralExpr.class).forEach(strExpr -> {
			if (strExpr.getParentNode().isPresent() && strExpr.getParentNode().get() instanceof MethodCallExpr) {

				String value = strExpr.getValue();
				String constName = buildConstantName(value);

				constantsMap.putIfAbsent(constName, value);
				strExpr.replace(new NameExpr(constName));
			}
		});

		// Fix Sonar: String == String
		fixStringEqualityVulnerability(cu);//
		fixHardcodedCredentials(cu);
		fixHardcodedTokens(cu);
		fixSqlInjection(cu);
		fixPathTraversal(cu);
		fixCommandInjection(cu);
		fixInsecureRandom(cu);
		fixWeakCrypto(cu);
		fixSensitiveLogging(cu);
		fixSystemOut(cu);
		fixEmptyCatch(cu);
		fixSwallowedException(cu);
		fixUnclosedResources(cu);
		fixEqualsHashCode(cu);
		fixMutableStateExposure(cu);
		fixPublicStaticMutable(cu);
		fixMissingSerialVersionUID(cu);
		fixGenericException(cu);
		fixMissingInputValidation(cu);
		addRequiredImports(cu);

		// now we add next step
		// here-------------------------------------------------------------
		// Add constants to class
		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
			constantsMap.forEach((name, val) -> {
				boolean alreadyExists = clazz.getFields().stream().flatMap(f -> f.getVariables().stream())
						.anyMatch(v -> v.getNameAsString().equals(name));

				if (!alreadyExists) {
					clazz.addFieldWithInitializer("String", name, new StringLiteralExpr(val), Modifier.Keyword.PUBLIC,
							Modifier.Keyword.STATIC, Modifier.Keyword.FINAL);
				}
			});
		});

		// Write updated file
		Path relativePath = sourceDir.relativize(javaFile);
		Path newFilePath = targetDir.resolve(relativePath);

		if (!Files.exists(newFilePath.getParent())) {
			Files.createDirectories(newFilePath.getParent());
		}

		try (BufferedWriter writer = Files.newBufferedWriter(newFilePath)) {
			writer.write(cu.toString(new PrettyPrinterConfiguration()));
		}
	}

	private void fixStringEqualityVulnerability(CompilationUnit cu) {

		cu.findAll(BinaryExpr.class).forEach(binaryExpr -> {

			if (binaryExpr.getOperator() != BinaryExpr.Operator.EQUALS) {
				return;
			}

			Expression left = binaryExpr.getLeft();
			Expression right = binaryExpr.getRight();

			if (left.isLiteralExpr() && right.isLiteralExpr()) {
				return;
			}

			if (isPrimitiveExpression(left) && isPrimitiveExpression(right)) {
				return;
			}

			if (left.isLiteralExpr() && right.isLiteralExpr() && !left.isStringLiteralExpr()
					&& !right.isStringLiteralExpr()) {
				return;
			}

			MethodCallExpr equalsCall = new MethodCallExpr(new NameExpr("Objects"), "equals");

			equalsCall.addArgument(left);
			equalsCall.addArgument(right);

			binaryExpr.replace(equalsCall);

			log.debug("Fixed String equality using Objects.equals()");
		});
	}

	private boolean isPrimitiveExpression(Expression expr) {

		// Primitive literals
		if (expr.isBooleanLiteralExpr() || expr.isCharLiteralExpr() || expr.isDoubleLiteralExpr()
				|| expr.isIntegerLiteralExpr() || expr.isLongLiteralExpr()) {
			return true;
		}

		if (expr.isNameExpr()) {
			String name = expr.asNameExpr().getNameAsString();

			return true;
		}

		return false;
	}

	private String buildConstantName(String value) {
		String name = value.toUpperCase().replaceAll("[^A-Z0-9]", "_").replaceAll("_+", "_");

		if (name.length() > 50) {
			name = name.substring(0, 50);
		}
		return "CONST_" + name;
	}

	private void fixHardcodedCredentials(CompilationUnit cu) {
		cu.findAll(VariableDeclarator.class).forEach(v -> {
			String name = v.getNameAsString().toLowerCase();
			if ((name.contains("password") || name.contains("secret") || name.contains("key"))
					&& v.getInitializer().isPresent() && v.getInitializer().get().isStringLiteralExpr()) {
				v.setInitializer(new MethodCallExpr(new NameExpr("System"), "getenv",
						NodeList.nodeList(new StringLiteralExpr("APP_PASSWORD"))));
			}
		});
	}

	private void fixHardcodedTokens(CompilationUnit cu) {
		cu.findAll(FieldDeclaration.class).forEach(fd -> {
			fd.getVariables().forEach(v -> {
				if (v.getType().asString().equals("String") && v.getInitializer().isPresent()
						&& v.getInitializer().get().isStringLiteralExpr()
						&& v.getInitializer().get().asStringLiteralExpr().getValue().length() > 20) {
					v.removeInitializer();
				}
			});
		});
	}

	private void fixSqlInjection(CompilationUnit cu) {
		cu.findAll(BinaryExpr.class).forEach(be -> {
			if (be.getLeft().isStringLiteralExpr()
					&& be.getLeft().asStringLiteralExpr().getValue().toLowerCase().contains("select")) {
				be.replace(new StringLiteralExpr("/* TODO: use PreparedStatement */"));
			}
		});
	}

	private void fixPathTraversal(CompilationUnit cu) {
		cu.findAll(ObjectCreationExpr.class).forEach(oc -> {
			if (oc.getType().asString().equals("File") && oc.getArguments().size() == 1) {
				oc.replace(new MethodCallExpr(new NameExpr("Paths"), "get",
						NodeList.nodeList(new StringLiteralExpr("/safe/dir"), oc.getArgument(0))));
			}
		});
	}

	private void fixCommandInjection(CompilationUnit cu) {
		cu.findAll(MethodCallExpr.class).forEach(mc -> {
			if (mc.getNameAsString().equals("exec")) {
				mc.replace(new ObjectCreationExpr(null, new ClassOrInterfaceType(null, "ProcessBuilder"),
						NodeList.nodeList(new StringLiteralExpr("ls"))));
			}
		});
	}

	private void fixInsecureRandom(CompilationUnit cu) {
		cu.findAll(ObjectCreationExpr.class).forEach(oc -> {
			if (oc.getType().asString().equals("Random")) {
				oc.setType("SecureRandom");
			}
		});
	}

	private void fixWeakCrypto(CompilationUnit cu) {
		cu.findAll(MethodCallExpr.class).forEach(mc -> {
			if (mc.getNameAsString().equals("getInstance") && mc.getArguments().toString().contains("MD5")) {
				mc.setArgument(0, new StringLiteralExpr("SHA-256"));
			}
		});
	}

	private void fixSensitiveLogging(CompilationUnit cu) {
		cu.findAll(MethodCallExpr.class).forEach(mc -> {
			if (mc.getNameAsString().equals("info") && mc.toString().toLowerCase().contains("password")) {
				mc.setArgument(0, new StringLiteralExpr("Sensitive action"));
			}
		});
	}

	private void fixSystemOut(CompilationUnit cu) {
		cu.findAll(MethodCallExpr.class).forEach(mc -> {
			if (mc.toString().startsWith("System.out.println")) {
				mc.replace(new MethodCallExpr(new NameExpr("log"), "info", mc.getArguments()));
			}
		});
	}

	private void fixEmptyCatch(CompilationUnit cu) {
		cu.findAll(CatchClause.class).forEach(cc -> {
			if (cc.getBody().isEmpty()) {
				cc.getBody().addStatement(new MethodCallExpr(new NameExpr("log"), "error", NodeList
						.nodeList(new StringLiteralExpr("Error occurred"), cc.getParameter().getNameAsExpression())));
			}
		});
	}

	private void fixSwallowedException(CompilationUnit cu) {
		cu.findAll(ObjectCreationExpr.class).forEach(oc -> {
			if (oc.getType().asString().equals("RuntimeException") && oc.getArguments().isEmpty()) {
				oc.addArgument(new NameExpr("e"));
			}
		});
	}

	private void fixEqualsHashCode(CompilationUnit cu) {
		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
			boolean hasEquals = c.getMethodsByName("equals").size() > 0;
			boolean hasHash = c.getMethodsByName("hashCode").size() > 0;
			if (hasEquals && !hasHash) {
				MethodDeclaration m = c.addMethod("hashCode", Modifier.Keyword.PUBLIC);
				m.addAnnotation("Override");
				m.setType(int.class);
				m.setBody(new BlockStmt(NodeList
						.nodeList(new ReturnStmt(new IntegerLiteralExpr(Objects.hashCode(c.getNameAsString()) + "")))));
			}
		});
	}

	private void fixMutableStateExposure(CompilationUnit cu) {
		cu.findAll(MethodDeclaration.class).forEach(md -> {
			if (md.getType().asString().equals("Date") && md.getBody().isPresent()) {
				md.getBody().get().getStatements().forEach(st -> {
					if (st.isReturnStmt()) {
						st.replace(new ReturnStmt(
								new ObjectCreationExpr(null, new ClassOrInterfaceType(null, "Date"), NodeList.nodeList(
										new MethodCallExpr(((ReturnStmt) st).getExpression().get(), "getTime")))));
					}
				});
			}
		});
	}

	private void fixPublicStaticMutable(CompilationUnit cu) {
		cu.findAll(FieldDeclaration.class).forEach(fd -> {
			if (fd.isPublic() && fd.isStatic() && !fd.isFinal()) {
				fd.setModifiers(Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC, Modifier.Keyword.FINAL);
			}
		});
	}

	private void fixMissingSerialVersionUID(CompilationUnit cu) {
		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
			if (c.getImplementedTypes().stream().anyMatch(t -> t.getNameAsString().equals("Serializable"))) {
				boolean present = c.getFields().stream()
						.anyMatch(f -> f.getVariables().get(0).getNameAsString().equals("serialVersionUID"));
				if (!present) {
					c.addField(long.class, "serialVersionUID", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC,
							Modifier.Keyword.FINAL).getVariable(0).setInitializer("1L");
				}
			}
		});
	}

	private void fixGenericException(CompilationUnit cu) {
		cu.findAll(MethodDeclaration.class).forEach(md -> {
			md.getThrownExceptions().removeIf(t -> t.asString().equals("Exception"));
		});
	}

	private void fixMissingInputValidation(CompilationUnit cu) {
		cu.findAll(Parameter.class).forEach(p -> {
			if (p.getAnnotations().toString().contains("RequestParam") && p.getAnnotations().isEmpty()) {
				p.addAnnotation("@Size(max = 50)");
			}
		});
	}

	private void addRequiredImports(CompilationUnit cu) {

		// Map: simple class name -> fully qualified name
		Map<String, String> requiredImports = new LinkedHashMap<>();

		requiredImports.put("Objects", "java.util.Objects");
		requiredImports.put("SecureRandom", "java.security.SecureRandom");
		requiredImports.put("Paths", "java.nio.file.Paths");
		requiredImports.put("Date", "java.util.Date");
		requiredImports.put("ProcessBuilder", "java.lang.ProcessBuilder");
		requiredImports.put("Serializable", "java.io.Serializable");
		requiredImports.put("Size", "javax.validation.constraints.Size");

		// Collect used type names & expressions
		String cuText = cu.toString();

		requiredImports.forEach((simpleName, fqcn) -> {

			boolean alreadyImported = cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(fqcn));

			boolean usedInCode = cuText.contains(simpleName + ".") || cuText.contains("new " + simpleName)
					|| cuText.contains(simpleName + "(") || cuText.contains(" " + simpleName + " ");

			if (!alreadyImported && usedInCode) {
				cu.addImport(fqcn);
				log.debug("Added import: {}", fqcn);
			}
		});
	}

	private void fixUnclosedResources(CompilationUnit cu) {

		cu.findAll(MethodDeclaration.class).forEach(method -> {

			if (method.getBody().isEmpty()) {
				return;
			}

			BlockStmt body = method.getBody().get();
			NodeList<Statement> originalStatements = new NodeList<>(body.getStatements());

			NodeList<Expression> resources = new NodeList<>();
			BlockStmt tryBlock = new BlockStmt();

			boolean resourceFound = false;

			for (Statement stmt : originalStatements) {

				if (stmt.isExpressionStmt() && stmt.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {

					VariableDeclarationExpr vde = stmt.asExpressionStmt().getExpression().asVariableDeclarationExpr();

					if (isJdbcResource(vde.getElementType().asString())) {
						resources.add(vde);
						resourceFound = true;
						continue;
					}
				}

				tryBlock.addStatement(stmt);
			}

			if (!resourceFound) {
				return;
			}

			CatchClause catchClause = new CatchClause(new Parameter(new ClassOrInterfaceType(null, "Exception"), "e"),
					new BlockStmt(NodeList.nodeList(new ExpressionStmt(new MethodCallExpr(new NameExpr("log"), "error",
							NodeList.nodeList(new StringLiteralExpr("Database error"), new NameExpr("e")))))));

			TryStmt tryStmt = new TryStmt(NodeList.nodeList(), // resources set later
					tryBlock, NodeList.nodeList(catchClause), null);

			tryStmt.setResources(resources);

			// rebuild body
			body.getStatements().clear();
			body.addStatement(tryStmt);

			// 🔑 Add fallback return ONLY if required
			if (needsFallbackReturn(method)) {
				body.addStatement(buildDefaultReturn(method));
			}

			log.info("Fixed unclosed JDBC resources in method {}", method.getNameAsString());
		});
	}

	private boolean needsFallbackReturn(MethodDeclaration method) {

		if (method.getType().isVoidType()) {
			return false;
		}

		// If ANY return already exists, do not add fallback
		return method.findAll(ReturnStmt.class).isEmpty();
	}

	private Statement buildDefaultReturn(MethodDeclaration method) {

		String type = method.getType().asString();

		return switch (type) {

		case "String" -> new ReturnStmt(new StringLiteralExpr(""));

		case "int", "long", "short", "byte" -> new ReturnStmt(new IntegerLiteralExpr("0"));

		case "float", "double" -> new ReturnStmt(new DoubleLiteralExpr("0.0"));

		case "boolean" -> new ReturnStmt(new BooleanLiteralExpr(false));

		case "char" -> new ReturnStmt(new CharLiteralExpr('\0'));

		default -> new ReturnStmt(new NullLiteralExpr());
		};
	}

	private boolean isJdbcResource(String type) {
		return type.equals("Connection") || type.equals("Statement") || type.equals("PreparedStatement")
				|| type.equals("ResultSet");
	}

}
