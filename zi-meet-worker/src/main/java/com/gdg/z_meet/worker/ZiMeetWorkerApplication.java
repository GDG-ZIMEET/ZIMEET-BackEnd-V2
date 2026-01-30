package com.gdg.z_meet.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.gdg.z_meet")
public class ZiMeetWorkerApplication {

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "worker");
        SpringApplication.run(ZiMeetWorkerApplication.class, args);
    }
}
