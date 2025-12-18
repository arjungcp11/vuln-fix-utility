package com.hexa.vulnfix.serviceImpl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
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
		// now we add next step here-------------------------------------------------------------
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

			if (left.isLiteralExpr() && right.isLiteralExpr()
			        && !left.isStringLiteralExpr()
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
	    if (expr.isBooleanLiteralExpr()
	            || expr.isCharLiteralExpr()
	            || expr.isDoubleLiteralExpr()
	            || expr.isIntegerLiteralExpr()
	            || expr.isLongLiteralExpr()) {
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
}
