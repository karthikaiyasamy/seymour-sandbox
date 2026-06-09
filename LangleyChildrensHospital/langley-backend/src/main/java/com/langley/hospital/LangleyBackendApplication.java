package com.langley.hospital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LangleyBackendApplication {

    public static void main(String[] args) {
        System.out.println("""
                ╔══════════════════════════════════════════════════╗
                ║   Langley Children's Hospital Backend Server     ║
                ║   Webhook Listener on http://localhost:8081      ║
                ║   API Endpoint: GET /api/patients                ║
                ╚══════════════════════════════════════════════════╝
                """);
        SpringApplication.run(LangleyBackendApplication.class, args);
    }
}
