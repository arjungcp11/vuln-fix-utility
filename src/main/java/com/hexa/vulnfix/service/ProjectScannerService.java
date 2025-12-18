package com.hexa.vulnfix.service;

import java.io.IOException;
import java.nio.file.Path;

public interface ProjectScannerService {

	void scanAndUpdateProject(Path sourceDir, Path targetDir) throws IOException;

}
