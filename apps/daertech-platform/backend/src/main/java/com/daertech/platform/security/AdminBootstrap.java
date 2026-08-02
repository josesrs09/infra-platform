package com.daertech.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String username;
    private final String password;

    public AdminBootstrap(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                          @Value("${app.admin.name}") String name,
                          @Value("${app.admin.email}") String email,
                          @Value("${app.admin.username}") String username,
                          @Value("${app.admin.password}") String password) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM platform.users", Integer.class);
        if (count != null && count > 0) return;
        if (password == null || password.length() < 12 || "CHANGE_ME".equals(password)) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD debe configurarse con al menos 12 caracteres antes del primer arranque");
        }
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.users(id,username,email,full_name,password_hash) VALUES (?,?,?,?,?)",
            userId, username, email, name, passwordEncoder.encode(password));
        jdbc.update("INSERT INTO platform.user_roles(user_id,role_id) SELECT ?,id FROM platform.roles WHERE code='ADMIN' ON CONFLICT DO NOTHING", userId);
    }
}
