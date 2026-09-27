package com.example.booking.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "qwertyuioplkjhgfdsazxcvbnmmnbvcxzasdfghjklpoiuytrewq";
    private static final String OTHER_SECRET = "zxcvbnmlkjhgfdsaqwertyuioppoiuytrewqasdfghjklmnbvcxz";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    @Test
    void generatedTokenIsValidAndContainsUsername() {
        String token = jwtService.generateToken("alice", "USER");

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("alice", jwtService.extractUsername(token));
    }

    @Test
    void garbageTokenIsInvalid() {
        assertFalse(jwtService.isTokenValid("not.a.jwt"));
        assertFalse(jwtService.isTokenValid(""));
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwtService.generateToken("alice", "USER");
        assertFalse(jwtService.isTokenValid(token + "x"));
    }

    @Test
    void tokenSignedWithDifferentKeyIsInvalid() {
        String foreignToken = new JwtService(OTHER_SECRET, 60_000).generateToken("alice", "ADMIN");
        assertFalse(jwtService.isTokenValid(foreignToken));
    }

    @Test
    void expiredTokenIsInvalid() {
        String expired = new JwtService(SECRET, -1_000).generateToken("alice", "USER");
        assertFalse(jwtService.isTokenValid(expired));
    }

    @Test
    void secretShorterThan32BytesIsRejectedAtStartup() {
        assertThrows(Exception.class, () -> new JwtService("too-short", 60_000));
    }
}