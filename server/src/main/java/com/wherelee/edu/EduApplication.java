package com.wherelee.edu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan({"com.wherelee.edu.modules.learning.playback", "com.wherelee.edu.modules.operations.admin", "com.wherelee.edu.modules.identity.invitation", "com.wherelee.edu.modules.course.governance"})
@ConfigurationPropertiesScan
@EnableScheduling
public class EduApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduApplication.class, args);
    }
}
