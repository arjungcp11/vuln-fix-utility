
package com.hexa.vulnfix.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

@Service
public class RezipService {
    public void zip(Path dir) throws Exception {
        Path zip = Paths.get("workdir/fixed-project.zip");
        Files.createDirectories(zip.getParent());

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            Files.walk(dir).filter(Files::isRegularFile).forEach(p -> {
                try {
                    zos.putNextEntry(new ZipEntry(dir.relativize(p).toString()));
                    Files.copy(p, zos);
                    zos.closeEntry();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        }
    }
}
