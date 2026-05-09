package com.ims.e2e.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Thread-safe base class for all Playwright UI tests using ThreadLocal context. Follows the
 * playwright-java guidelines.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
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
      } catch (Exception e) {
      }

      if (!isPortOpen("localhost", frontendPort)) {
        System.out.println("Frontend not detected on " + frontendPort + ". Attempting to start...");
        startFrontend();
      } else {
        System.out.println("Frontend detected on " + frontendPort + ". Continuing...");
      }
    }
  }

  private static boolean isPortOpen(String host, int port) {
    try (java.net.Socket socket = new java.net.Socket()) {
      socket.connect(new java.net.InetSocketAddress(host, port), 1000);
      return true;
    } catch (java.io.IOException e) {
      return false;
    }
  }

  private static void startFrontend() {
    // Try to find the frontend directory relative to the current project
    java.io.File frontendDir = new java.io.File("../inventory-management-frontend");
    if (!frontendDir.exists()) {
      frontendDir = new java.io.File("inventory-management-frontend");
    }

    if (frontendDir.exists()) {
      try {
        System.out.println("Starting frontend in: " + frontendDir.getAbsolutePath());
        String os = System.getProperty("os.name").toLowerCase();
        String npmCommand = os.contains("win") ? "npm.cmd" : "npm";

        ProcessBuilder pb = new ProcessBuilder(npmCommand, "run", "dev");
        pb.directory(frontendDir);
        pb.inheritIO();
        Process process = pb.start();

        // Give it some time to start up
        System.out.println("Waiting for frontend to stabilize...");
        Thread.sleep(5000);

        Runtime.getRuntime()
            .addShutdownHook(
                new Thread(
                    () -> {
                      System.out.println("Shutting down frontend...");
                      process.destroy();
                    }));
      } catch (Exception e) {
        System.err.println("FAILED TO START FRONTEND: " + e.getMessage());
      }
    } else {
      System.err.println(
          "COULD NOT FIND FRONTEND DIRECTORY. Please start manually: cd frontend && npm run dev");
    }
  }

  protected String baseUrl() {
    // UI tests should point to the frontend server (usually Vite on 5173)
    // Backend port is available if needed for direct API checks
    String frontendUrl =
        System.getProperty(
            "frontendUrl", "https://inventory-management-frontend-r3amqhnaf.vercel.app/");
    System.out.println(
        "Using base URL for UI tests: " + frontendUrl + " (Backend is on port " + port + ")");
    return frontendUrl;
  }

  protected static ThreadLocal<Playwright> playwrightTL = new ThreadLocal<>();
  protected static ThreadLocal<Browser> browserTL = new ThreadLocal<>();
  protected static ThreadLocal<BrowserContext> contextTL = new ThreadLocal<>();
  protected static ThreadLocal<Page> pageTL = new ThreadLocal<>();

  protected Page page() {
    return pageTL.get();
  }

  @BeforeEach
  protected void setUp() {
    System.setProperty("baseUrl", baseUrl());
    Playwright playwright = Playwright.create();
    playwrightTL.set(playwright);

    // Can be configured to non-headless if needed
    boolean isHeadless = Boolean.parseBoolean(System.getProperty("headless", "true"));
    Browser browser =
        resolveBrowser(playwright).launch(new BrowserType.LaunchOptions().setHeadless(isHeadless));
    browserTL.set(browser);

    BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setRecordVideoDir(Paths.get("target/videos/"))
                .setLocale("en-US"));

    context
        .tracing()
        .start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));

    contextTL.set(context);
    Page page = context.newPage();

    // Debugging instrumentation
    page.onConsoleMessage(msg -> System.out.println("BROWSER CONSOLE: " + msg.text()));
    page.onPageError(
        err -> {
          System.err.println("BROWSER PAGE ERROR: " + err);
        });

    pageTL.set(page);
  }

  @AfterEach
  protected void tearDown(TestInfo testInfo) {
    String name = testInfo.getDisplayName().replaceAll("[^a-zA-Z0-9]", "_");
    contextTL
        .get()
        .tracing()
        .stop(new Tracing.StopOptions().setPath(Paths.get("target/traces/" + name + ".zip")));

    pageTL.get().close();
    contextTL.get().close();
    browserTL.get().close();
    playwrightTL.get().close();

    pageTL.remove();
    contextTL.remove();
    browserTL.remove();
    playwrightTL.remove();
  }

  private BrowserType resolveBrowser(Playwright pw) {
    return switch (System.getProperty("browser", "chromium").toLowerCase()) {
      case "firefox" -> pw.firefox();
      case "webkit" -> pw.webkit();
      default -> pw.chromium();
    };
  }
}
