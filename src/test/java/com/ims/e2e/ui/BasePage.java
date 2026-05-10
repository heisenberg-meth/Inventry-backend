package com.ims.e2e.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class BasePage {
  protected final Page page;

  public BasePage(Page page) {
    this.page = page;
  }

  protected abstract String getUrl();

  public void navigate() {
    String baseUrl = System.getProperty("baseUrl", "http://localhost:8080");
    navigate(baseUrl);
  }

  public void navigate(String baseUrl) {
    String fullUrl = baseUrl + getUrl();
    System.out.println("Navigating to: " + fullUrl);
    page.navigate(fullUrl);
    page.waitForLoadState(LoadState.LOAD);
    page.waitForLoadState(LoadState.NETWORKIDLE);

    System.out.println("Final URL: " + page.url());
    System.out.println(
        "Page Content Preview: "
            + page.content().substring(0, Math.min(500, page.content().length())));

    Path screenshotPath =
        Paths.get("target/debug-screenshots/" + this.getClass().getSimpleName() + ".png");
    File dir = screenshotPath.getParent().toFile();
    if (!dir.exists()) dir.mkdirs();
    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath));
    System.out.println("Screenshot saved to: " + screenshotPath.toAbsolutePath());
  }

  protected void fill(Locator locator, String text) {
    System.out.println("URL => " + page.url());
    System.out.println("TITLE => " + page.title());
    System.out.println(
        "PAGE CONTENT PREVIEW: "
            + page.content().substring(0, Math.min(1000, page.content().length())));

    Path debugPath = Paths.get("playwright-debug.png");
    page.screenshot(new Page.ScreenshotOptions().setPath(debugPath));
    System.out.println("DEBUG SCREENSHOT saved to: " + debugPath.toAbsolutePath());

    System.out.println("DEBUG - Current URL: " + page.url());
    System.out.println("DEBUG - Page Content: " + page.content());
    locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    locator.fill(text);
  }

  protected void clickAndWaitForNav(Locator locator) {
    locator.click();
    page.waitForLoadState(LoadState.LOAD);
  }
}
