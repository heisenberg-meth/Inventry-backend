package com.ims.e2e.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Base Page Object representing common operations.
 * Follows the playwright-java guidelines for POM.
 */
public abstract class BasePage {
    protected final Page page;

    public BasePage(Page page) {
        this.page = page;
    }

    protected abstract String getUrl();

    public void navigate() {
        // Assuming application runs on port 8080 locally
        String baseUrl = System.getProperty("baseUrl", "http://localhost:8080");
        page.navigate(baseUrl + getUrl());
        page.waitForLoadState(LoadState.LOAD);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    protected void fill(Locator locator, String text) {
        locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        locator.fill(text);
    }

    protected void clickAndWaitForNav(Locator locator) {
        locator.click();
        page.waitForLoadState(LoadState.LOAD);
    }
}
