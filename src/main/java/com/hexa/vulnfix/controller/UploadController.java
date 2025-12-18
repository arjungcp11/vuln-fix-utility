package com.hexa.vulnfix.controller;

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
import com.hexa.vulnfix.service.RezipService;
import com.hexa.vulnfix.service.ZipService;
import com.hexa.vulnfix.serviceImpl.OwaspScanService;

@RestController
public class UploadController {

	private static final Logger log = LoggerFactory.getLogger(UploadController.class);

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

	@Value("${scan.pom.only}")
	private boolean scanPomOnly;

	@Value("${scan.output.path}")
	private String baseOutputPath;

	@Autowired
	ProjectScannerService projectScannerService;
	private final AtomicBoolean scanCompleted = new AtomicBoolean(false);


	@Scheduled(cron = "${scan.cron}")
	public void scheduledScan() {

	    if (scanCompleted.get()) {
	        return; 
	    }

	    try {
	        Path path = Paths.get(projectPath);
	        log.info("Scheduled scan started...");

	        if (scanPomOnly) {
	            pom.updateProject(path);
	            log.info("Scanning POM done only...");
	        } else {
	            Path targetProjectDir = Paths.get(baseOutputPath);
	            projectScannerService.scanAndUpdateProject(path, targetProjectDir);
	            log.info("Scanning full project done ...");
	        }

	        scanCompleted.set(true);

	    } catch (Exception e) {
	        log.error("Scheduled scan failed", e);
	    }
	}


}