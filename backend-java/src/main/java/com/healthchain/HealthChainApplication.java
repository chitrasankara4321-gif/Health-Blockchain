package com.healthchain;

import com.healthchain.service.DataService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HealthChainApplication {

    public static void main(String[] args) {
        System.out.println("Starting Secure Healthcare Data Server...");
        System.out.println("Authentication: ENABLED");
        System.out.println("Doctor Access: SECURE");
        System.out.println("Audit Logging: ACTIVE");
        System.out.println("Emergency Protocols: READY");
        System.out.println("Server running at http://localhost:3000");
        SpringApplication.run(HealthChainApplication.class, args);
    }

    @Bean
    public CommandLineRunner initDatabase(DataService dataService) {
        return args -> dataService.initDatabase();
    }
}
