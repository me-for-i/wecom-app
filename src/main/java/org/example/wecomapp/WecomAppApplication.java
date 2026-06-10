package org.example.wecomapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class WecomAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(WecomAppApplication.class, args);
    }

}
