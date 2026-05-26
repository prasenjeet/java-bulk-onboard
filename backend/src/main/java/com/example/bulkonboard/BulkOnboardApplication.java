package com.example.bulkonboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BulkOnboardApplication {
    public static void main(String[] args) {
        SpringApplication.run(BulkOnboardApplication.class, args);
    }
}
