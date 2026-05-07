package com.ims.e2e.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Paths;

/**
 * Thread-safe base class for all Playwright UI tests using ThreadLocal context.
 * Follows the playwright-java guidelines.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BasePlaywrightTest {
    @LocalServerPort
    protected int port;

    protected String baseUrl() {
        return "http://localhost:" + port;
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
        Browser browser = resolveBrowser(playwright).launch(
                new BrowserType.LaunchOptions().setHeadless(isHeadless));
        browserTL.set(browser);

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setRecordVideoDir(Paths.get("target/videos/"))
                .setLocale("en-US"));

        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));

        contextTL.set(context);
        pageTL.set(context.newPage());
    }

    @AfterEach
    protected void tearDown(TestInfo testInfo) {
        String name = testInfo.getDisplayName().replaceAll("[^a-zA-Z0-9]", "_");
        contextTL.get().tracing().stop(new Tracing.StopOptions()
                .setPath(Paths.get("target/traces/" + name + ".zip")));

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
