package com.sefujo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SeFuJoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeFuJoApplication.class, args);
    }

}
