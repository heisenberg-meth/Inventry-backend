package com.ims.shared.auth;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class NaasService {

    private final RestTemplate restTemplate = new RestTemplate();
    
    @Getter
    private String noMessage = "No"; // Default fallback

    @PostConstruct
    public void init() {
        try {
            String response = restTemplate.getForObject("https://naas.isalman.dev/no", String.class);
            if (response != null && !response.isBlank()) {
                this.noMessage = response.trim();
                log.info("NaaS message cached successfully: {}", noMessage);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch NaaS message, using fallback: {}", e.getMessage());
        }
    }
}
