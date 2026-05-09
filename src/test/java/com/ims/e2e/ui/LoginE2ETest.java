package com.ims.e2e.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import org.junit.jupiter.api.Test;
import com.ims.e2e.ui.pages.LoginPage;

class LoginE2ETest extends BasePlaywrightTest {

        @Test
        void shouldShowErrorOnInvalidCredentials() {
                LoginPage loginPage = new LoginPage(page());

                loginPage.navigate(baseUrl());

                loginPage.loginExpectingError(
                                "bad@test.com",
                                "wrongpass",
                                "T1001");

                assertThat(loginPage.errorMessage())
                                .containsText("Invalid email or password");
        }
}