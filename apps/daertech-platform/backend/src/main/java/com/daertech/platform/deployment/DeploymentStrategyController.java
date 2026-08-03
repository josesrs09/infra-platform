package com.daertech.platform.deployment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/admin/deployment-operations")
public class DeploymentStrategyController {
    private static final Set<String> STRATEGIES = Set.of("RECREATE", "ROLLING", "BLUE_GREEN");
    private final JdbcTemplate jdbc;
    private final DeploymentService deployments;

    public DeploymentStrategyController(JdbcTemplate jdbc, DeploymentService deployments) {
        this.jdbc = jdbc;
        this.deployments = deployments;
    }

    @GetMapping("/strategies")
    @PreAuthorize("hasAuthority('DEPLOYMENT_READ')")
    public List<Map<String,Object>> strategies() {
        return List.of(
            Map.of("code","RECREATE","name","Recreate","description","Detiene y reemplaza la versión actual."),
            Map.of("code","ROLLING","name","Rolling update","description","Actualiza instancias progresivamente; requiere réplicas y Compose compatible."),
            Map.of("code","BLUE_GREEN","name","Blue/Green","description","Prepara un entorno paralelo y requiere conmutación controlada del proxy.")
        );
    }

    @PostMapping("/{id}/strategy")
    @PreAuthorize("hasAuthority('DEPLOYMENT_EXECUTE')")
    public Map<String,Object> setStrategy(@PathVariable UUID id, @RequestBody StrategyRequest request) {
        String strategy = request.strategy() == null ? "RECREATE" : request.strategy().toUpperCase(Locale.ROOT);
        if (!STRATEGIES.contains(strategy)) throw new IllegalArgumentException("Estrategia no soportada");
        jdbc.update("UPDATE platform.deployments SET strategy=?,registry_id=?,registry_image=? WHERE id=? AND status IN ('PENDING','FAILED')",
            strategy, request.registryId(), request.registryImage(), id);
        return deployments.detail(id);
    }

    @PostMapping("/{id}/promote")
    @PreAuthorize("hasAuthority('DEPLOYMENT_EXECUTE')")
    public Map<String,Object> promote(@PathVariable UUID id, @RequestBody PromotionRequest request, Authentication authentication) {
        Map<String,Object> source = deployments.detail(id);
        if (!"SUCCESS".equals(source.get("status"))) throw new IllegalStateException("Solo se puede promover un despliegue exitoso");
        if (request.targetEnvironment() == null || request.targetEnvironment().isBlank()) throw new IllegalArgumentException("Ambiente destino requerido");
        var created = deployments.create(new DeploymentService.Request(
            UUID.fromString(source.get("application_id").toString()),
            request.targetEnvironment(),
            source.get("version").toString(),
            source.get("git_branch").toString(),
            request.reason() == null ? "Promoción desde " + source.get("environment") : request.reason()
        ), authentication.getName());
        UUID promotedId = UUID.fromString(created.get("id").toString());
        jdbc.update("UPDATE platform.deployments SET promoted_from=?,strategy=?,registry_id=?,registry_image=? WHERE id=?",
            id, source.get("strategy"), source.get("registry_id"), source.get("registry_image"), promotedId);
        return deployments.detail(promotedId);
    }

    public record StrategyRequest(String strategy, UUID registryId, String registryImage) {}
    public record PromotionRequest(String targetEnvironment, String reason) {}
}
