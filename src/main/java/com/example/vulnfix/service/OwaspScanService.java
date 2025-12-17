
package com.example.vulnfix.service;

import com.example.vulnfix.model.VulnerabilitySummary;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;

@Service
public class OwaspScanService {

    public VulnerabilitySummary scanAndSummarize(Path dir) throws Exception {
        Path report = dir.resolve("owasp-report");
        Files.createDirectories(report);

        ProcessBuilder pb = new ProcessBuilder(
            "dependency-check",
            "--scan", dir.toString(),
            "--format", "ALL",
            "--out", report.toString()
        );
        pb.inheritIO();
        pb.start().waitFor();

        VulnerabilitySummary s = new VulnerabilitySummary();
        for (Path p : Files.newDirectoryStream(report)) {
            String c = Files.readString(p);
            s.critical += count(c,"CRITICAL");
            s.high += count(c,"HIGH");
            s.medium += count(c,"MEDIUM");
            s.low += count(c,"LOW");
        }
        s.reportPath = report.toAbsolutePath().toString();
        return s;
    }

    private int count(String s,String k){
        return s.split(k,-1).length-1;
    }
}
