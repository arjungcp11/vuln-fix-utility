
package com.example.vulnfix.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

@Service
public class ZipService {
    public Path extract(MultipartFile file) throws IOException {
        Path target = Paths.get("workdir/fixed/project");
        Files.createDirectories(target);

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path p = target.resolve(e.getName());
                if (e.isDirectory()) Files.createDirectories(p);
                else {
                    Files.createDirectories(p.getParent());
                    Files.copy(zis, p, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return target;
    }
}
