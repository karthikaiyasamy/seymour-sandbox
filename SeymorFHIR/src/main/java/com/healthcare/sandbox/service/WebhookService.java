package com.healthcare.sandbox.service;

import com.healthcare.sandbox.model.AdtEvent;
import com.healthcare.sandbox.model.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Slf4j
public class WebhookService {

    /**
     * Sends an asynchronous ADT webhook notification to the specified URL.
     */
    @Async
    public void fireAdtWebhook(AdtEvent savedEvent, Patient patient, String webhookUrl) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            log.info("No webhook URL provided. Skipping webhook notification.");
            return;
        }

        try {
            String eventCode = mapEventCode(savedEvent.getEventType());
            String timestamp = savedEvent.getEventDatetime() != null 
                    ? savedEvent.getEventDatetime().toString() 
                    : LocalDateTime.now().toString();

            // Constructing standard FHIR/EHR JSON webhook payload
            String payload = String.format("""
                {
                  "patientId": "%d",
                  "mrn": "%s",
                  "eventType": "%s",
                  "eventCode": "%s",
                  "visitNumber": "%s",
                  "facility": "%s",
                  "timestamp": "%s",
                  "callbackBaseUrl": "http://localhost:8080"
                }
                """,
                patient.getId(),
                patient.getMrn(),
                savedEvent.getEventType(),
                eventCode,
                savedEvent.getVisitNumber(),
                escapeJson(savedEvent.getFacility()),
                timestamp
            );

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            log.info("Firing ADT Webhook to [POST {}] with payload: {}", webhookUrl, payload);
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.info("Webhook Response [Code: {}] Body: {}", response.statusCode(), response.body());
            
        } catch (Exception e) {
            log.error("Failed to execute Webhook call to [{}]. Saved event was not impacted. Exception message: {}", 
                    webhookUrl, e.getMessage(), e);
        }
    }

    private String mapEventCode(String eventType) {
        if (eventType == null) return "A08";
        return switch (eventType.toUpperCase()) {
            case "ADMIT" -> "A01";
            case "TRANSFER" -> "A02";
            case "DISCHARGE" -> "A03";
            default -> "A08";
        };
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
