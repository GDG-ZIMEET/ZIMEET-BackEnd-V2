package com.gdg.z_meet.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.gdg.z_meet")
public class ZiMeetApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZiMeetApiApplication.class, args);
    }

}
