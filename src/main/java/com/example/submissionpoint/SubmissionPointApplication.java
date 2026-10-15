package com.example.submissionpoint;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.submissionpoint.mapper")
public class SubmissionPointApplication {
    public static void main(String[] args) {
        SpringApplication.run(SubmissionPointApplication.class, args);
    }
}