package com.supportpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for the SupportPilot AI Customer Support Platform.
 */
@SpringBootApplication
@EnableScheduling
public class SupportPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportPilotApplication.class, args);
    }
}
