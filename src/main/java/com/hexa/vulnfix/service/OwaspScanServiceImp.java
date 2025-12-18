package com.hexa.vulnfix.service;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.springframework.stereotype.Service;

import com.hexa.vulnfix.model.VulnerabilitySummary;
import com.hexa.vulnfix.serviceImpl.OwaspScanService;

@Service
public class OwaspScanServiceImp implements OwaspScanService {

	private static final Logger LOGGER = Logger.getLogger(OwaspScanServiceImp.class.getName());
	private static final String POM_XML = "pom.xml";

	@Override
	public VulnerabilitySummary scanAndSummarize(Path dir) throws IOException {
		Path report = dir.resolve("owasp-report");
		Files.createDirectories(report);

		ProcessBuilder pb = new ProcessBuilder("dependency-check", "--scan", dir.toString(), "--format", "ALL", "--out",
				report.toString());
		pb.inheritIO();

		try {
			int exitCode = pb.start().waitFor();
			if (exitCode != 0) {
				throw new IOException("Dependency check process failed with exit code: " + exitCode);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Dependency check process was interrupted", e);
		}

		VulnerabilitySummary summary = new VulnerabilitySummary();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(report)) {
			for (Path p : stream) {
				String content = Files.readString(p);
				summary.setCritical(count(content, "CRITICAL"));
				summary.setHigh(count(content, "HIGH"));
				summary.setMedium(count(content, "MEDIUM"));
				summary.setLow(count(content, "LOW"));
			}
		} catch (IOException e) {
			LOGGER.info("Error reading OWASP report files");

		}

		summary.setReportPath(report.toAbsolutePath().toString());
		return summary;
	}

	private int count(String content, String keyword) {
		return content.split(keyword, -1).length - 1;
	}

	@Override
	public VulnerabilitySummary scanAndSummarizePomOnly(Path projectDir) throws Exception {

		File pomFile = projectDir.resolve(POM_XML).toFile();

		if (!pomFile.exists()) {
			throw new RuntimeException("pom.xml not found");
		}

		// 1️⃣ Read pom.xml
		Model model = readPom(pomFile);

		// 2️⃣ Scan dependencies using OWASP
		VulnerabilitySummary summary = scanPomDependencies(model);

		// 3️⃣ Fix vulnerable dependencies
		if (summary.getTotal() > 0) {
			fixVulnerableDependencies(model);
			writePom(pomFile, model);
		}

		return summary;
	}

	private Model readPom(File pomFile) throws Exception {
		try (FileReader reader = new FileReader(pomFile)) {
			return new MavenXpp3Reader().read(reader);
		}
	}

	private VulnerabilitySummary scanPomDependencies(Model model) {

		VulnerabilitySummary summary = new VulnerabilitySummary();

		for (Dependency dep : model.getDependencies()) {

			// Dummy logic – replace with real OWASP results
			if (isVulnerable(dep)) {
				summary.incrementCritical();
				summary.incrementTotal();
			}
		}

		return summary;
	}

	private boolean isVulnerable(Dependency dep) {
		// Example vulnerable versions
		return dep.getGroupId().equals("org.apache.logging.log4j") && dep.getArtifactId().equals("log4j-core")
				&& dep.getVersion().startsWith("2.");
	}

	private void fixVulnerableDependencies(Model model) {
		for (Dependency dep : model.getDependencies()) {

			if (isVulnerable(dep)) {

				String latestVersion = fetchLatestSafeVersion(dep.getGroupId(), dep.getArtifactId());

				dep.setVersion(latestVersion);
			}
		}
	}

	private void writePom(File pomFile, Model model) throws Exception {
		try (FileWriter writer = new FileWriter(pomFile)) {
			new MavenXpp3Writer().write(writer, model);
		}
	}

	private String fetchLatestSafeVersion(String groupId, String artifactId) {
		// Dummy implementation – replace with real version fetching logic
		return "2.17.1"; // Example safe version
	}
}