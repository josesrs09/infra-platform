package com.daertech.platform.security;

import com.daertech.platform.user.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final UserAccountRepository users;
    private final PlatformUserDetailsService userDetailsService;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private final long accessMinutes;
    private final long refreshDays;

    public AuthService(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
                       UserAccountRepository users, PlatformUserDetailsService userDetailsService,
                       JdbcTemplate jdbc, @Value("${app.jwt.access-minutes}") long accessMinutes,
                       @Value("${app.jwt.refresh-days}") long refreshDays) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.users = users;
        this.userDetailsService = userDetailsService;
        this.jdbc = jdbc;
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public Map<String,Object> login(String username, String password, String ip) {
        Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        var user = users.findByUsernameIgnoreCase(auth.getName()).orElseThrow();
        user.setLastLoginAt(OffsetDateTime.now());
        String refresh = newRefreshToken();
        jdbc.update("INSERT INTO platform.refresh_tokens(id,user_id,token_hash,expires_at,created_ip) VALUES (?,?,?,?,?)",
            UUID.randomUUID(), user.getId(), hash(refresh), OffsetDateTime.now(ZoneOffset.UTC).plusDays(refreshDays), ip);
        return tokenResponse(auth, refresh);
    }

    @Transactional
    public Map<String,Object> refresh(String refreshToken) {
        String username = jdbc.query("SELECT u.username FROM platform.refresh_tokens t JOIN platform.users u ON u.id=t.user_id WHERE t.token_hash=? AND t.revoked_at IS NULL AND t.expires_at>NOW()",
            rs -> rs.next() ? rs.getString(1) : null, hash(refreshToken));
        if (username == null) throw new IllegalArgumentException("Refresh token inválido o expirado");
        var account = users.findByUsernameIgnoreCase(username).orElseThrow();
        var details = userDetailsService.loadUserByUsername(username);
        var auth = new UsernamePasswordAuthenticationToken(username, null, details.getAuthorities());
        jdbc.update("UPDATE platform.refresh_tokens SET revoked_at=NOW() WHERE token_hash=?", hash(refreshToken));
        String replacement = newRefreshToken();
        jdbc.update("INSERT INTO platform.refresh_tokens(id,user_id,token_hash,expires_at) VALUES (?,?,?,?)",
            UUID.randomUUID(), account.getId(), hash(replacement), OffsetDateTime.now(ZoneOffset.UTC).plusDays(refreshDays));
        return tokenResponse(auth, replacement);
    }

    @Transactional
    public void logout(String refreshToken) {
        jdbc.update("UPDATE platform.refresh_tokens SET revoked_at=NOW() WHERE token_hash=? AND revoked_at IS NULL", hash(refreshToken));
    }

    private Map<String,Object> tokenResponse(Authentication auth, String refresh) {
        Instant now = Instant.now();
        String scope = auth.getAuthorities().stream().map(a -> a.getAuthority()).sorted().collect(Collectors.joining(" "));
        var claims = JwtClaimsSet.builder().issuer("daertech-platform").issuedAt(now)
            .expiresAt(now.plusSeconds(accessMinutes * 60)).subject(auth.getName()).claim("scope", scope).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String access = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return Map.of("tokenType", "Bearer", "accessToken", access,
            "expiresIn", accessMinutes * 60, "refreshToken", refresh);
    }

    private String newRefreshToken() {
        byte[] value = new byte[48];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
