package com.ims.e2e.ui;

import com.ims.config.TestCacheConfig;
import com.ims.config.TestRedisConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Thread-safe base class for all Playwright UI tests using ThreadLocal context. Follows the
 * playwright-java guidelines.
 */
@SuppressWarnings("unused")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    })
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestRedisConfig.class, TestCacheConfig.class})
public abstract class BasePlaywrightTest {
  @LocalServerPort protected int port;

  static {
    // Option C: Attempt to ensure frontend is running before tests start
    String frontendUrl =
        System.getProperty(
            "frontendUrl", "https://inventory-management-frontend-r3amqhnaf.vercel.app/");
    if (frontendUrl != null && frontendUrl.contains("localhost")) {
      int frontendPort = 5173;
      try {
        URL url = URI.create(frontendUrl).toURL();
        frontendPort = url.getPort() != -1 ? url.getPort() : 80;
      } catch (Exception ignored) {
      }
    }
  }

  // Playwright instances
  private static Playwright playwright;
  private static Browser browser;

  // ThreadLocal storage for thread safety during parallel execution
  private final ThreadLocal<BrowserContext> browserContext = new ThreadLocal<>();
  private final ThreadLocal<Page> threadPage = new ThreadLocal<>();

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
    browser =
        resolveBrowser(playwright)
            .launch(
                new BrowserType.LaunchOptions()
                    .setExecutablePath(Paths.get("/usr/bin/chromium-browser"))
                    .setHeadless(true));
  }

  @AfterAll
  static void closeBrowser() {
    if (browser != null) browser.close();
    if (playwright != null) playwright.close();
  }

  @BeforeEach
  void createContextAndPage(TestInfo testInfo) {
    BrowserContext context = browser.newContext();
    browserContext.set(context);

    // Start tracing for each test
    context
        .tracing()
        .start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));

    Page page = context.newPage();
    threadPage.set(page);
  }

  @AfterEach
  void closeContext(TestInfo testInfo) {
    BrowserContext context = browserContext.get();
    if (context != null) {
      String testName = testInfo.getDisplayName().replaceAll("[^a-zA-Z0-9]", "_");
      context
          .tracing()
          .stop(
              new Tracing.StopOptions()
                  .setPath(Paths.get("target/playwright-traces/" + testName + ".zip")));
      context.close();
    }
    browserContext.remove();
    threadPage.remove();
  }

  protected Page page() {
    return threadPage.get();
  }

  protected String baseUrl() {
    return System.getProperty("frontendUrl", "http://localhost:" + port);
  }

  private static BrowserType resolveBrowser(Playwright pw) {
    return switch (System.getProperty("browser", "chromium").toLowerCase()) {
      case "firefox" -> pw.firefox();
      case "webkit" -> pw.webkit();
      default -> pw.chromium();
    };
  }
}
