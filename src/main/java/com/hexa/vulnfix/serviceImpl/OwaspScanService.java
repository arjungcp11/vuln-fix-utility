package com.hexa.vulnfix.serviceImpl;

import java.io.IOException;
import java.nio.file.Path;

import com.hexa.vulnfix.model.VulnerabilitySummary;

public interface OwaspScanService {
    VulnerabilitySummary scanAndSummarizePomOnly(Path projectDir) throws Exception;

	VulnerabilitySummary scanAndSummarize(Path dir) throws IOException;


}
