package com.freestyleperu.aplicacion.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_AUTHORITIES = "authorities";
    private static final String CLAIM_TENANT_ID = "tenantId";

    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validarSecreto() {
        if (properties.getSecret() == null || properties.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET no está definido o tiene menos de 32 caracteres. "
                            + "Defínelo como variable de entorno antes de iniciar la aplicación.");
        }
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username, Set<String> authorities, Long tenantId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getAccessTokenMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_AUTHORITIES, authorities)
                .claim(CLAIM_TENANT_ID, String.valueOf(tenantId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public int getAccessTokenSeconds() {
        return properties.getAccessTokenMinutes() * 60;
    }

    public int getRefreshTokenDays() {
        return properties.getRefreshTokenDays();
    }

    public String generateRawRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public AuthenticatedUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get(CLAIM_USERNAME, String.class);
            @SuppressWarnings("unchecked")
            List<String> authorities = claims.get(CLAIM_AUTHORITIES, List.class);
            Set<String> authoritySet = authorities == null
                    ? Set.of()
                    : authorities.stream().collect(Collectors.toUnmodifiableSet());
            Long tenantId = Long.valueOf(claims.get(CLAIM_TENANT_ID, String.class));
            return new AuthenticatedUser(userId, username, authoritySet, tenantId);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
