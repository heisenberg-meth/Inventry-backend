package com.ims.e2e.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import com.ims.e2e.ui.pages.LoginPage;

class LoginE2ETest extends BasePlaywrightTest {

    private final LoginPage loginPage = new LoginPage(page());

    @Test
    void shouldShowErrorOnInvalidCredentials() {

        loginPage.navigate();

        loginPage.loginExpectingError(
                "bad@test.com",
                "wrongpass",
                "T1001");

        assertThat(page())
                .hasURL(Pattern.compile(".*/login"));

        assertThat(loginPage.errorMessage())
                .containsText("Invalid email or password");
    }
}