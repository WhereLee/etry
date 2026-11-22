package com.wherelee.material;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan({"com.wherelee.material.modules.learning.playback", "com.wherelee.material.modules.operations.admin", "com.wherelee.material.modules.identity.invitation", "com.wherelee.material.modules.course.governance"})
@ConfigurationPropertiesScan
@EnableScheduling
public class MaterialApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaterialApplication.class, args);
    }
}
