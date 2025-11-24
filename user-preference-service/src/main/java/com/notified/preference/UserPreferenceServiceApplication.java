package com.notified.preference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class UserPreferenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserPreferenceServiceApplication.class, args);
    }
}
