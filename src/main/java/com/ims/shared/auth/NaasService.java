package com.ims.shared.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class NaasService {

  private static final int HTTP_TIMEOUT_MS = 2000;
  private final RestTemplate restTemplate;
  private String cachedNoMessage = null;

  public NaasService() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(HTTP_TIMEOUT_MS);
    factory.setReadTimeout(HTTP_TIMEOUT_MS);
    this.restTemplate = new RestTemplate(factory);
  }

  public synchronized String getNoMessage() {
    if (cachedNoMessage != null) {
      return cachedNoMessage;
    }

    try {
      log.info("Fetching NaaS message lazily...");
      String response = restTemplate.getForObject("https://naas.isalman.dev/no", String.class);
      if (response != null && !response.isBlank()) {
        this.cachedNoMessage = response.trim();
        log.info("NaaS message cached successfully: {}", cachedNoMessage);
        return cachedNoMessage;
      }
    } catch (Exception e) {
      log.warn("Failed to fetch NaaS message, using fallback: {}", e.getMessage());
    }

    return "No"; // Fallback
  }
}
