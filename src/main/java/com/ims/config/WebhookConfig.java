package com.ims.config;

import com.ims.shared.logging.MdcTaskDecorator;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

@Configuration
@Profile("!test")
public class WebhookConfig {

  private static final int CONNECT_TIMEOUT_MS = 5000;
  private static final int READ_TIMEOUT_MS = 10000;
  private static final int MAX_TOTAL_CONNECTIONS = 100;
  private static final int MAX_PER_ROUTE = 20;
  private static final int CORE_POOL_SIZE = 10;
  private static final int MAX_POOL_SIZE = 20;
  private static final int QUEUE_CAPACITY = 100;

  @Bean
  public RestTemplate webhookRestTemplate() {
    ConnectionConfig connectionConfig = ConnectionConfig.custom()
        .setConnectTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
        .setSocketTimeout(Timeout.ofMilliseconds(READ_TIMEOUT_MS))
        .build();

    PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
    connManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
    connManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);
    connManager.setDefaultConnectionConfig(connectionConfig);

    RequestConfig requestConfig = RequestConfig.custom()
        .setResponseTimeout(Timeout.ofMilliseconds(READ_TIMEOUT_MS))
        .build();

    CloseableHttpClient httpClient = HttpClients.custom()
        .setConnectionManager(connManager)
        .setDefaultRequestConfig(requestConfig)
        .evictIdleConnections(TimeValue.of(30, TimeUnit.SECONDS))
        .build();

    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(
        java.util.Objects.requireNonNull(httpClient));

    return new RestTemplate(factory);
  }

  @Bean(name = "webhookExecutor")
  public Executor webhookExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(CORE_POOL_SIZE);
    exec.setMaxPoolSize(MAX_POOL_SIZE);
    exec.setQueueCapacity(QUEUE_CAPACITY);
    exec.setThreadNamePrefix("webhook-");
    exec.setTaskDecorator(new MdcTaskDecorator());
    exec.initialize();
    return exec;
  }
}
