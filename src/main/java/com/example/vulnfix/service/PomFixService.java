
package com.example.vulnfix.service;

import org.apache.maven.model.*;
import org.apache.maven.model.io.xpp3.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class PomFixService {

    private static final Map<String,String> SAFE = Map.of(
        "log4j-core","2.17.1",
        "jackson-databind","2.15.3"
    );

    public void updatePom(Path dir) throws Exception {
        Path pom = dir.resolve("pom.xml");
        if (!Files.exists(pom)) return;

        Model model = new MavenXpp3Reader().read(new FileReader(pom.toFile()));
        for (Dependency d : model.getDependencies()) {
            if (SAFE.containsKey(d.getArtifactId())) {
                d.setVersion(SAFE.get(d.getArtifactId()));
            }
        }
        new MavenXpp3Writer().write(new FileWriter(pom.toFile()), model);
    }
}
