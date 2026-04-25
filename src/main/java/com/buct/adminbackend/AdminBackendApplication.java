package com.buct.adminbackend;

import com.buct.adminbackend.config.IntegrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IntegrationProperties.class)
public class AdminBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminBackendApplication.class, args);
    }
}
