package com.ims.e2e.ui.pages;

import com.ims.e2e.ui.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class LoginPage extends BasePage {

  private final Locator emailInput;
  private final Locator passwordInput;
  private final Locator companyCodeInput;
  private final Locator loginButton;

  public LoginPage(Page page) {
    super(page);
    this.emailInput = page.getByTestId("login-email");
    this.passwordInput = page.getByTestId("login-password");
    this.companyCodeInput = page.getByTestId("login-company");
    this.loginButton = page.getByTestId("login-submit");
  }

  @Override
  protected String getUrl() {
    return "/login";
  }

  public void login(String email, String password, String companyCode) {
    emailInput.fill(email);
    passwordInput.fill(password);
    companyCodeInput.fill(companyCode);
    loginButton.click();
  }

  public LoginPage loginExpectingError(String email, String password, String companyCode) {
    fill(emailInput, email);
    fill(passwordInput, password);
    fill(companyCodeInput, companyCode);
    loginButton.click();
    errorMessage().waitFor();
    return this;
  }

  public Locator errorMessage() {
    return page.getByTestId("login-error-message");
  }
}
