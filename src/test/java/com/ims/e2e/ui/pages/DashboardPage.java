package com.ims.e2e.ui.pages;

import com.ims.e2e.ui.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class DashboardPage extends BasePage {
  private final Locator welcomeBanner;

  public DashboardPage(Page page) {
    super(page);
    this.welcomeBanner = page.getByTestId("welcome-banner");
  }

  @Override
  protected String getUrl() {
    return "/dashboard";
  }

  public String getWelcomeMessage() {
    return welcomeBanner.textContent();
  }
}
