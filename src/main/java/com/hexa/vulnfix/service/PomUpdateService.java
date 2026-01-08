
package com.hexa.vulnfix.service;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Service
public class PomUpdateService {
	private static final Logger log = LoggerFactory.getLogger(PomUpdateService.class);

	@Value("${scan.output.path}")
	private String baseOutputPath;

	@Value("${scan.pom.only}")
	private boolean scanPomOnly;

	@Autowired
	private SafeDependencyConfig safeDependencyConfig;

	public void updateProjectPomFile(Path projectDir) throws Exception {

	    if (projectDir == null || !Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
	        throw new RuntimeException("Invalid project directory: " + projectDir);
	    }

	    Path pomPath = projectDir.resolve("pom.xml");
	    if (!Files.exists(pomPath)) {
	        throw new RuntimeException("pom.xml not found in project: " + projectDir);
	    }

	    log.info("Updating pom.xml at: {}", pomPath);

	    Model model;
	    try (FileReader reader = new FileReader(pomPath.toFile())) {
	        model = new MavenXpp3Reader().read(reader);
	    }

	    // 4️⃣ Update dependencies ONLY
	    updateDependencies(model);

	    try (FileWriter writer = new FileWriter(pomPath.toFile())) {
	        new MavenXpp3Writer().write(writer, model);
	    }

	    log.info("pom.xml updated successfully (in-place)");
	}
	
	
	public void updateGradleProject(Path projectDir) throws IOException {

	    if (projectDir == null || !Files.isDirectory(projectDir)) {
	        throw new RuntimeException("Invalid project directory: " + projectDir);
	    }

	    Path buildGradle = projectDir.resolve("build.gradle");
	    if (!Files.exists(buildGradle)) {
	        throw new RuntimeException("build.gradle not found in project");
	    }

	    log.info("Updating Gradle build file: {}", buildGradle);

	    List<String> lines = Files.readAllLines(buildGradle);
	    List<String> updated = new ArrayList<>();

	    Map<String, String> safeVersions = safeDependencyConfig.getVersions();

	    for (String line : lines) {
	        String modified = line;

	        for (Map.Entry<String, String> entry : safeVersions.entrySet()) {
	            String artifact = entry.getKey();
	            String safeVersion = entry.getValue();

	            // implementation 'group:artifact:version'
	            modified = modified.replaceAll(
	                    "(implementation|api|compileOnly|runtimeOnly)\\s+['\"]([^:'\"]+):"
	                            + artifact + ":[^'\"]+['\"]",
	                    "$1 '$2:" + artifact + ":" + safeVersion + "'"
	            );

	            // implementation(\"group:artifact:version\")
	            modified = modified.replaceAll(
	                    "(implementation|api|compileOnly|runtimeOnly)\\s*\\(\\s*['\"]([^:'\"]+):"
	                            + artifact + ":[^'\"]+['\"]\\s*\\)",
	                    "$1(\"$2:" + artifact + ":" + safeVersion + "\")"
	            );
	        }

	        updated.add(modified);
	    }

	    // Backup (recommended)
	    Files.copy(buildGradle,
	            buildGradle.resolveSibling("build.gradle.bak"),
	            StandardCopyOption.REPLACE_EXISTING);

	    Files.write(buildGradle, updated);

	    log.info("build.gradle updated successfully (in-place)");
	}



	private void updateDependencies(Model model) {

		Map<String, String> safeVersions = safeDependencyConfig.getVersions();

		for (Dependency d : model.getDependencies()) {
			String safeVersion = safeVersions.get(d.getArtifactId());
			if (safeVersion != null) {
				log.info("Updating " + d.getArtifactId() + " → " + safeVersion);
				d.setVersion(safeVersion);
			}
		}
	}

	private void copyProject(Path source, Path target) throws IOException {
		Files.walk(source).forEach(path -> {
			try {
				Path dest = target.resolve(source.relativize(path));
				if (Files.isDirectory(path)) {
					Files.createDirectories(dest);
				} else {
					Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}
	
	public void getOldPomFileTextAsReferance(Path sourceProjectDir) throws Exception {

	    if (!Files.exists(sourceProjectDir)) {
	        throw new RuntimeException("Project folder not found: " + sourceProjectDir);
	    }

	    Path pomPath = sourceProjectDir.resolve("pom.xml");

	    if (!Files.exists(pomPath)) {
	        throw new RuntimeException("pom.xml not found in project");
	    }

	    // Parse pom.xml
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
	    factory.setExpandEntityReferences(false);

	    DocumentBuilder builder = factory.newDocumentBuilder();
	    Document document = builder.parse(pomPath.toFile());

	    NodeList dependencyNodes = document.getElementsByTagName("dependency");

	    // Output file on D drive
	    Path outputFile = Paths.get("D:/safe-dependencies.txt");

	    try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {

	        for (int i = 0; i < dependencyNodes.getLength(); i++) {
	            Element dependency = (Element) dependencyNodes.item(i);

	            String groupId = getTagValue(dependency, "groupId");
	            String artifactId = getTagValue(dependency, "artifactId");
	            String version = getTagValue(dependency, "version");

	            // Skip dependencies without version (managed by parent/BOM)
	            if (artifactId == null || version == null) {
	                continue;
	            }

	            String key = "safe-dependencies.versions." + artifactId;
	            String line = key + "=" + version;

	            writer.write(line);
	            writer.newLine();
	        }
	    }
	}
	
	private String getTagValue(Element parent, String tagName) {
	    NodeList nodes = parent.getElementsByTagName(tagName);
	    if (nodes == null || nodes.getLength() == 0) {
	        return null;
	    }
	    return nodes.item(0).getTextContent().trim();
	}

}
