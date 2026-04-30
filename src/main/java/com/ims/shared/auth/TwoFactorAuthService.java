package com.ims.shared.auth;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TwoFactorAuthService {

    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();

    /**
     * Generates a new TOTP secret key for a user.
     * 
     * @return The secret key details including the shared secret and QR code URL.
     */
    public TwoFactorSecret generateNewSecret(String email) {
        final GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        String otpAuthUrl = GoogleAuthenticatorQRGenerator.getOtpAuthURL("IMS-Inventory", email, key);

        return new TwoFactorSecret(key.getKey(), otpAuthUrl);
    }

    /**
     * Verifies a TOTP code against a secret.
     * 
     * @param secret The shared secret
     * @param code   The 6-digit code provided by the user
     * @return true if valid, false otherwise
     */
    public boolean verifyCode(String secret, int code) {
        return googleAuthenticator.authorize(secret, code);
    }

    /**
     * Generates a set of backup codes for emergency recovery.
     * 
     * @return List of random backup codes
     */
    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            codes.add(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        return codes;
    }

    public record TwoFactorSecret(String secret, String qrCodeUrl) {
    }
}
