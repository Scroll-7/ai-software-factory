package com.pfe.factory.api;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Connexion locale de développement : aucun mot de passe, aucun service externe.
 * Le secret est identique à celui de `scripts/dev_token.py` et ne doit jamais être commité.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    @Value("${security.jwt.secret}")
    private String secret;

    @PostMapping("/dev-token")
    public Map<String, Object> devToken(@RequestBody(required = false) Map<String, Object> body) {
        String subject = body == null ? "requester"
                : String.valueOf(body.getOrDefault("subject", "requester"));
        if (subject.isBlank()) subject = "requester";
        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(7200)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return Map.of(
                "token", token,
                "subject", subject,
                "note", "Jeton local de développement. Ne jamais utiliser en production.");
    }
}
