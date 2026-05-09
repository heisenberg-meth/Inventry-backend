package com.ims.shared.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Minimal controller to serve a mock login page for E2E testing.
 * This allows Playwright tests to find the expected elements without a real frontend.
 */
@Controller
@Profile("test")
public class TestUiController {

  @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String mockLoginPage() {
    return """
        <!DOCTYPE html>
        <html>
        <head><title>Mock Login</title></head>
        <body>
          <form>
            <input data-testid="login-email" type="text" />
            <input data-testid="login-password" type="password" />
            <input data-testid="login-company" type="text" />
            <button data-testid="login-submit" type="submit">Login</button>
            <div data-testid="login-error-message" style="display:none">Invalid email or password</div>
          </form>
          <script>
            document.querySelector('form').addEventListener('submit', function(e) {
              e.preventDefault();
              // Simulate an error message appearing after a "failed" login attempt
              document.querySelector('[data-testid="login-error-message"]').style.display = 'block';
            });
          </script>
        </body>
        </html>
        """;
  }
}
