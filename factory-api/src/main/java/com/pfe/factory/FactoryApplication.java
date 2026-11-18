package com.pfe.factory;

import com.pfe.factory.config.FactoryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FactoryProperties.class)
public class FactoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(FactoryApplication.class, args);
    }
}
