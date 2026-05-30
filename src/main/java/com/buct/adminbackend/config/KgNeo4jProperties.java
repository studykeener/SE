package com.buct.adminbackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kg.neo4j")
public class KgNeo4jProperties {

    private boolean enabled = true;
    private String uri = "bolt://localhost:7687";
    private String username = "neo4j";
    private String password = "neo4j";
}
