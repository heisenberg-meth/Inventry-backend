package com.ims.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Jasypt property encryption.
 * This allows encrypting sensitive values in application.yml like:
 * spring.datasource.password: "ENC(encrypted_password_here)"
 */
@Configuration
public class JasyptConfig {

    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        // Use an environment variable for the master password.
        // If not set, use a fallback for dev/tests (though environment variables are
        // preferred).
        String password = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
        if (password == null || password.isBlank()) {
            password = "dev-secret-key-change-me";
        }

        config.setPassword(password);
        config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");
        encryptor.setConfig(config);

        return encryptor;
    }
}
