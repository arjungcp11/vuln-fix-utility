package com.hexa.vulnfix.controller;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;

import com.hexa.vulnfix.service.PomUpdateService;
import com.hexa.vulnfix.service.ProjectScannerService;

@RestController
public class UploadController {

	private static final Logger log = LoggerFactory.getLogger(UploadController.class);

	@Autowired
	private PomUpdateService pom;

	@Value("${scan.project.path}")
	private String projectPath;

	@Value("${scan.pom.only}")
	private boolean scanPomOnly;

	@Value("${scan.output.path}")
	private String baseOutputPath;

	@Value("${old.pom.file.text}")
	private boolean oldPomFileText;

	@Value("${scan.classfiles.only}")
	private boolean classFileScan;

	@Autowired
	ProjectScannerService projectScannerService;
	private final AtomicBoolean scanCompleted = new AtomicBoolean(false);

	@Scheduled(cron = "${scan.cron}")
	public void scheduledScan() throws Exception {

		if (scanCompleted.get()) {
			return;
		}

		Path baseDir = Paths.get(projectPath);
		log.info("Scheduled scan started for base path: {}", baseDir);

		if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
			log.warn("Invalid base directory: {}", baseDir);
			return;
		}

		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(baseDir)) {

			boolean projectFound = false;

			for (Path projectDir : directoryStream) {

				if (classFileScan) {
					Path targetProjectDir = Paths.get(baseOutputPath);
					projectScannerService.scanAndUpdateProject(projectDir, targetProjectDir);
					log.info("Scanning full project done ...");
				}

				if (!Files.isDirectory(projectDir)) {
					continue;
				}

				projectFound = true;
				scanSingleProject(projectDir);
			}

			if (!projectFound) {
				log.warn("No projects found in directory: {}", baseDir);
			}

		} catch (IOException e) {
			log.error("Failed to scan projects in base directory: {}", baseDir, e);
		}
	}

	private void scanSingleProject(Path projectDir) throws Exception {

		log.info("--------------------------------------------------");
		log.info("Scanning project: {}", projectDir.getFileName());

		boolean isMaven = isMavenProject(projectDir);
		boolean isGradle = isGradleProject(projectDir);

		if (isMaven) {
			log.info("Detected Maven project");
			pom.updateProjectPomFile(projectDir);
			log.info("pom.xml updated successfully");
			return;
		}

		if (isGradle) {
			log.info("Detected Gradle project");
			pom.updateGradleProject(projectDir);
			log.info("build.gradle updated successfully");
			return;
		}

		log.warn("Skipping folder (not a Maven or Gradle project): {}", projectDir);
	}

	private boolean isMavenProject(Path projectDir) {
		return Files.exists(projectDir.resolve("pom.xml"));
	}

	private boolean isGradleProject(Path projectDir) {
		return Files.exists(projectDir.resolve("build.gradle")) || Files.exists(projectDir.resolve("build.gradle.kts"));
	}

}