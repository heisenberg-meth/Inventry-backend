package com.ims.shared.auth;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class NaasService {

  private final RestTemplate restTemplate;

  public NaasService() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2000); // 2 seconds
    factory.setReadTimeout(2000); // 2 seconds
    this.restTemplate = new RestTemplate(factory);
  }

  @Getter private String noMessage = "No"; // Default fallback

  @PostConstruct
  public void init() {
    CompletableFuture.runAsync(
        () -> {
          try {
            log.info("Fetching NaaS message asynchronously...");
            String response =
                restTemplate.getForObject("https://naas.isalman.dev/no", String.class);
            if (response != null && !response.isBlank()) {
              this.noMessage = response.trim();
              log.info("NaaS message cached successfully: {}", noMessage);
            }
          } catch (Exception e) {
            log.warn("Failed to fetch NaaS message, using fallback: {}", e.getMessage());
          }
        });
  }
}
