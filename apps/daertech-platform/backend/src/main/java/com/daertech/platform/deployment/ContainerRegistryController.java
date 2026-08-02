package com.daertech.platform.deployment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/container-registries")
public class ContainerRegistryController {
    private final JdbcTemplate jdbc;

    public ContainerRegistryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REGISTRY_READ')")
    public List<Map<String,Object>> list() {
        return jdbc.queryForList("SELECT id,code,name,registry_url,username_secret_key,password_secret_key,insecure,active,created_at,updated_at FROM platform.container_registries ORDER BY code");
    }

    @PostMapping
    @PreAuthorize("hasAuthority('REGISTRY_WRITE')")
    public Map<String,Object> save(@RequestBody Request request, Authentication authentication) {
        if (request.code() == null || !request.code().matches("[A-Za-z0-9_-]{2,80}")) throw new IllegalArgumentException("Código de registro inválido");
        if (request.name() == null || request.name().isBlank()) throw new IllegalArgumentException("Nombre requerido");
        if (request.registryUrl() == null || request.registryUrl().isBlank()) throw new IllegalArgumentException("URL del registro requerida");
        UUID id = request.id() == null ? UUID.randomUUID() : request.id();
        jdbc.update("INSERT INTO platform.container_registries(id,code,name,registry_url,username_secret_key,password_secret_key,insecure,active) VALUES (?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(id) DO UPDATE SET code=EXCLUDED.code,name=EXCLUDED.name,registry_url=EXCLUDED.registry_url,username_secret_key=EXCLUDED.username_secret_key,password_secret_key=EXCLUDED.password_secret_key,insecure=EXCLUDED.insecure,active=EXCLUDED.active,updated_at=NOW()",
            id, request.code().toUpperCase(Locale.ROOT), request.name(), request.registryUrl(), request.usernameSecretKey(), request.passwordSecretKey(), request.insecure(), request.active());
        return jdbc.queryForMap("SELECT id,code,name,registry_url,username_secret_key,password_secret_key,insecure,active,created_at,updated_at FROM platform.container_registries WHERE id=?", id);
    }

    public record Request(UUID id, String code, String name, String registryUrl, String usernameSecretKey,
                          String passwordSecretKey, boolean insecure, boolean active) {}
}
