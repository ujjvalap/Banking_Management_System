package com.sprint.bank.security.jwt;

import com.sprint.bank.security.principal.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final long accessExpiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer:northstar-bank}") String issuer,
            @Value("${app.jwt.access-expiration:900000}") long accessExpiration) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment property must be configured.");
        }
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 256 bits (32 bytes).");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = issuer;
        this.accessExpiration = accessExpiration;
    }

    public String generateAccessToken(UserDetails userDetails) {
        CustomUserDetails principal = requireCustomUserDetails(userDetails);
        Instant now = Instant.now();

        List<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String role = authorities.isEmpty() ? "" : authorities.get(0).replace("ROLE_", "");

        return Jwts.builder()
                .subject(principal.getUsername())
                .issuer(issuer)
                .claim("userId", principal.getId())
                .claim("role", role)
                .claim("authorities", authorities)
                .claim("tokenVersion", principal.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessExpiration)))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Instant extractExpiration(String token) {
        return extractAllClaims(token).getExpiration().toInstant();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        CustomUserDetails principal = requireCustomUserDetails(userDetails);
        Claims claims = extractAllClaims(token);
        Long tokenVersion = claims.get("tokenVersion", Long.class);
        Number userId = claims.get("userId", Number.class);
        String subject = claims.getSubject();

        return subject != null
                && subject.equals(principal.getUsername())
                && issuer.equals(claims.getIssuer())
                && claims.getExpiration() != null
                && claims.getExpiration().after(new Date())
                && userId != null
                && userId.longValue() == principal.getId()
                && tokenVersion != null
                && tokenVersion.longValue() == principal.getTokenVersion();
    }

    public long getAccessExpiration() {
        return accessExpiration;
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private CustomUserDetails requireCustomUserDetails(UserDetails userDetails) {
        if (userDetails instanceof CustomUserDetails customUserDetails) {
            return customUserDetails;
        }
        throw new IllegalArgumentException("Unsupported user principal.");
    }
}
