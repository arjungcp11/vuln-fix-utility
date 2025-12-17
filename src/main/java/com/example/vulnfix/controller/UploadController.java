package com.example.vulnfix.controller;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.vulnfix.model.VulnerabilitySummary;
import com.example.vulnfix.service.OwaspScanService;
import com.example.vulnfix.service.PomFixService;
import com.example.vulnfix.service.RezipService;
import com.example.vulnfix.service.ZipService;

@RestController
public class UploadController {

    @Autowired
    private ZipService zip;

    @Autowired
    private PomFixService pom;

    @Autowired
    private OwaspScanService owasp;

    @Autowired
    private RezipService rezip;

    public UploadController() {
    }

    @PostMapping("/upload")
    public VulnerabilitySummary upload(@RequestParam MultipartFile file) throws Exception {

    	Path projectDir = zip.extract(file);
        pom.updatePom(projectDir);

        VulnerabilitySummary summary = owasp.scanAndSummarize(projectDir);

        if (summary.critical > 0) {
            throw new RuntimeException("CRITICAL vulnerabilities found. Build stopped.");
        }

        rezip.zip(projectDir);
        return summary;
    }
}