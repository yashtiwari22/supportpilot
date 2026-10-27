package com.supportpilot.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BCryptTest {

    @Test
    void testBCryptMatch() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String generatedHash = encoder.encode("password123");
        System.out.println("GENERATED_HASH=" + generatedHash);
        
        String realHash = "$2a$10$XwkQbphlW.QbP9QQYlTNw.pqVXuZRgD6EdWeE5DTtlTjFWyA9NPPy";
        assertTrue(encoder.matches("password123", realHash));
    }
}
