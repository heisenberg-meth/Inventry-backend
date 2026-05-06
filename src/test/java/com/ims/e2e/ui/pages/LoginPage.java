package com.ims.e2e.ui.pages;

import com.ims.e2e.ui.BasePage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public class LoginPage extends BasePage {

    private final Locator emailInput;
    private final Locator passwordInput;
    private final Locator companyCodeInput;
    private final Locator loginButton;

    public LoginPage(Page page) {
        super(page);
        this.emailInput = page.getByLabel("Email address");
        this.passwordInput = page.getByLabel("Password");
        this.companyCodeInput = page.getByLabel("Company Code");
        this.loginButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in"));
    }

    @Override
    protected String getUrl() {
        return "/login";
    }

    public DashboardPage loginAs(String email, String password, String companyCode) {
        fill(emailInput, email);
        fill(passwordInput, password);
        fill(companyCodeInput, companyCode);
        clickAndWaitForNav(loginButton);
        return new DashboardPage(page);
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
        return page.locator(".error-message");
    }}
