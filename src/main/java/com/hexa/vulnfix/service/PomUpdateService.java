
package com.hexa.vulnfix.service;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

	public void updateProject(Path sourceProjectDir) throws Exception {

		if (!Files.exists(sourceProjectDir)) {
			throw new RuntimeException("Project folder not found: " + sourceProjectDir);
		}

		Path pomPath1 = sourceProjectDir.resolve("pom.xml");

		if (!Files.exists(pomPath1)) {
			throw new RuntimeException("pom.xml not found in project");
		}

		if (!Files.exists(sourceProjectDir)) {
			Files.createDirectories(sourceProjectDir); // 👈 creates full path safely
			log.info("Created base directory: " + sourceProjectDir);
		}
		// Validate project
		Path originalPom = sourceProjectDir.resolve("pom.xml");
		if (!Files.exists(originalPom)) {
			throw new RuntimeException("pom.xml not found in project");
		}

		// Create output directory
		String projectName = sourceProjectDir.getFileName().toString();
		Path targetProjectDir = Paths.get(baseOutputPath, projectName + "-fixed-" + System.currentTimeMillis());

		// Copy full project
		copyProject(sourceProjectDir, targetProjectDir);

		// Read copied pom.xml
		Path pomPath = targetProjectDir.resolve("pom.xml");

		Model model;
		try (FileReader reader = new FileReader(pomPath.toFile())) {
			model = new MavenXpp3Reader().read(reader);
		}

		// Scan & update ONLY pom.xml if flag = true
		if (scanPomOnly) {
			updateDependencies(model);
		} else {
			// later: full project scan (classes, plugins, etc.)
			updateDependencies(model);
		}

		// Write updated pom.xml
		try (FileWriter writer = new FileWriter(pomPath.toFile())) {
			new MavenXpp3Writer().write(writer, model);
		}

		log.info("Project updated at: " + targetProjectDir);
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
