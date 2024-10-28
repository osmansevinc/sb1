package com.streamsegmenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SegmentApp {
    public static void main(String[] args) {
        SpringApplication.run(SegmentApp.class, args);
    }
}