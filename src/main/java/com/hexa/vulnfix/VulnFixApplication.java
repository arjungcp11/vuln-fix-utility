
package com.hexa.vulnfix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VulnFixApplication {
    public static void main(String[] args) {
        SpringApplication.run(VulnFixApplication.class, args);
        System.out.println("VulnFixApplication started.");
    }
}
