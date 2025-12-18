package com.hexa.vulnfix.controller;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hexa.vulnfix.model.VulnerabilitySummary;
import com.hexa.vulnfix.service.PomUpdateService;
import com.hexa.vulnfix.service.RezipService;
import com.hexa.vulnfix.service.ZipService;
import com.hexa.vulnfix.serviceImpl.OwaspScanService;

@RestController
public class UploadController {

	@Autowired
	private ZipService zip;

	@Autowired
	private PomUpdateService pom;

	@Autowired
	private OwaspScanService owasp;

	@Autowired
	private RezipService rezip;

	@Value("${scan.project.path}")
	private String projectPath;

	@Value("${scan.output.path}")
	private String outputPath;

	@Value("${scan.pom.only}")
	private boolean scanPomOnly;

	// 🔁 Scheduled automatic scan
	@Scheduled(cron = "${scan.cron}")
	public void scheduledScan() {
		try {
			Path path = Paths.get(projectPath);
			System.out.println("Scheduled scan started...");
			if (scanPomOnly) {
				pom.updateProject(path);
				System.out.println("Scanning POM only...");
			} else {
				System.out.println("Scanning full project...");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

//	@PostMapping("/upload")
//	public VulnerabilitySummary upload(@RequestParam MultipartFile file, @RequestParam boolean scanPomOnly)
//			throws Exception {
//
//		Path projectDir = zip.extract(file);
//
//		if (scanPomOnly) {
//
////        	PomVulnerabilityFixer fixer = new PomVulnerabilityFixer(); No use of this class now due to direct pom update in PomFixService
////        	fixer.updatePom(projectDir);
//			pom.updatePom(projectDir);
//			VulnerabilitySummary summary = owasp.scanAndSummarizePomOnly(projectDir);
//
//			if (summary.getCritical() > 0) {
//				throw new RuntimeException("CRITICAL vulnerabilities found in POM file. Build stopped.");
//			}
//
//			return summary;
//		} else {
//			pom.updatePom(projectDir);
//			VulnerabilitySummary summary = owasp.scanAndSummarize(projectDir);
//
//			if (summary.getCritical() > 0) {
//				throw new RuntimeException("CRITICAL vulnerabilities found. Build stopped.");
//			}
//
//			rezip.zip(projectDir);
//			return summary;
//		}
//	}
}