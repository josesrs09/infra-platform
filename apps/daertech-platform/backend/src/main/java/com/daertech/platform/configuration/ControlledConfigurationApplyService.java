package com.daertech.platform.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ControlledConfigurationApplyService {
    private final JdbcTemplate jdbc;
    private final Path rootDirectory;
    private final Path backupDirectory;
    private final boolean restartEnabled;
    private final Set<String> allowedServices;

    public ControlledConfigurationApplyService(
        JdbcTemplate jdbc,
        @Value("${app.config.apply.root-directory:/opt/infra-platform/generated}") String rootDirectory,
        @Value("${app.config.apply.backup-directory:/opt/infra-platform/backups/configuration}") String backupDirectory,
        @Value("${app.config.apply.restart-enabled:false}") boolean restartEnabled,
        @Value("${app.config.apply.allowed-services:postgres,redis,rabbitmq,mqtt,minio,traefik,prometheus,grafana,loki,alertmanager,backend,frontend}") String allowedServices
    ) {
        this.jdbc = jdbc;
        this.rootDirectory = Path.of(rootDirectory).toAbsolutePath().normalize();
        this.backupDirectory = Path.of(backupDirectory).toAbsolutePath().normalize();
        this.restartEnabled = restartEnabled;
        this.allowedServices = Set.copyOf(Arrays.stream(allowedServices.split(","))
            .map(String::trim).filter(v -> !v.isBlank()).map(v -> v.toLowerCase(Locale.ROOT)).toList());
    }

    @Transactional
    public Map<String, Object> apply(ApplyRequest request, String actor) {
        validate(request);
        UUID operationId = UUID.randomUUID();
        Path target = resolveTarget(request.relativePath());
        Path backup = null;
        String beforeChecksum = null;
        int healthStatus = 0;

        insertHistory(operationId, request, actor, target, "APPLY", "STARTED", null, null, null, null);
        try {
            Files.createDirectories(target.getParent());
            Files.createDirectories(backupDirectory);
            if (Files.exists(target)) {
                beforeChecksum = checksum(Files.readAllBytes(target));
                String stamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
                backup = backupDirectory.resolve(stamp + "-" + target.getFileName() + ".bak").normalize();
                Files.copy(target, backup, StandardCopyOption.COPY_ATTRIBUTES);
            }

            byte[] content = request.content().getBytes(StandardCharsets.UTF_8);
            Path temporary = Files.createTempFile(target.getParent(), ".daertech-", ".tmp");
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }

            String afterChecksum = checksum(content);
            if (request.restartService()) restart(request.serviceName());
            if (request.healthUrl() != null && !request.healthUrl().isBlank()) {
                healthStatus = health(request.healthUrl());
                if (healthStatus < 200 || healthStatus >= 400) {
                    throw new IllegalStateException("Health check no satisfactorio: HTTP " + healthStatus);
                }
            }

            completeHistory(operationId, "SUCCESS", beforeChecksum, afterChecksum, backup, healthStatus, "Configuración aplicada correctamente");
            return response(operationId, target, backup, "SUCCESS", beforeChecksum, afterChecksum, healthStatus, false);
        } catch (Exception ex) {
            boolean rolledBack = restore(target, backup);
            completeHistory(operationId, rolledBack ? "ROLLED_BACK" : "FAILED", beforeChecksum, null, backup, healthStatus, ex.getMessage());
            throw new IllegalStateException("No fue posible aplicar la configuración. Rollback=" + rolledBack + ". " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public Map<String, Object> rollback(UUID operationId, String actor, String reason) {
        Map<String, Object> previous = jdbc.queryForMap("SELECT target_path,backup_path,service_name,health_url FROM platform.configuration_apply_history WHERE id=?", operationId);
        Path target = Path.of((String) previous.get("target_path")).toAbsolutePath().normalize();
        Path backup = previous.get("backup_path") == null ? null : Path.of((String) previous.get("backup_path")).toAbsolutePath().normalize();
        if (!target.startsWith(rootDirectory) || backup == null || !backup.startsWith(backupDirectory) || !Files.exists(backup)) {
            throw new IllegalArgumentException("No existe un backup válido para esta operación");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO platform.configuration_apply_history(id,environment,target_path,backup_path,service_name,action_name,result_status,checksum_after,requested_by,reason,details,completed_at) VALUES (?,?,?,?,?,'ROLLBACK','SUCCESS',?,?,?,?,NOW())",
                id, "PRODUCTION", target.toString(), backup.toString(), previous.get("service_name"), checksum(Files.readAllBytes(target)), actor, reason, "Rollback manual de " + operationId);
            return response(id, target, backup, "SUCCESS", null, checksum(Files.readAllBytes(target)), 0, true);
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible restaurar el backup", ex);
        }
    }

    public java.util.List<Map<String, Object>> history() {
        return jdbc.queryForList("SELECT id,template_code,environment,target_path,backup_path,service_name,action_name,result_status,requested_by,reason,health_status,details,created_at,completed_at FROM platform.configuration_apply_history ORDER BY created_at DESC LIMIT 200");
    }

    private void validate(ApplyRequest request) {
        if (request.content() == null || request.content().isBlank()) throw new IllegalArgumentException("Contenido requerido");
        if (request.content().length() > 2_000_000) throw new IllegalArgumentException("El archivo excede 2 MB");
        if (request.reason() == null || request.reason().isBlank()) throw new IllegalArgumentException("Motivo requerido");
        if (request.relativePath() == null || request.relativePath().isBlank()) throw new IllegalArgumentException("Ruta relativa requerida");
        if (request.restartService() && (request.serviceName() == null || !allowedServices.contains(request.serviceName().toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("Servicio no permitido");
        }
    }

    private Path resolveTarget(String relativePath) {
        Path relative = Path.of(relativePath);
        if (relative.isAbsolute()) throw new IllegalArgumentException("Sólo se permiten rutas relativas");
        Path target = rootDirectory.resolve(relative).normalize();
        if (!target.startsWith(rootDirectory)) throw new IllegalArgumentException("La ruta sale del directorio permitido");
        return target;
    }

    private void restart(String service) throws Exception {
        if (!restartEnabled) throw new IllegalStateException("El reinicio de servicios está deshabilitado");
        Process process = new ProcessBuilder("docker", "compose", "restart", service)
            .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("Falló el reinicio del servicio: " + output);
    }

    private int health(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        return connection.getResponseCode();
    }

    private boolean restore(Path target, Path backup) {
        if (backup == null || !Files.exists(backup)) return false;
        try {
            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String checksum(byte[] content) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private void insertHistory(UUID id, ApplyRequest request, String actor, Path target, String action, String status, String before, String after, Path backup, String details) {
        jdbc.update("INSERT INTO platform.configuration_apply_history(id,template_code,environment,target_path,backup_path,service_name,action_name,result_status,checksum_before,checksum_after,requested_by,reason,health_url,details) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id, request.templateCode(), request.environment(), target.toString(), backup == null ? null : backup.toString(), request.serviceName(), action, status, before, after, actor, request.reason(), request.healthUrl(), details);
    }

    private void completeHistory(UUID id, String status, String before, String after, Path backup, int healthStatus, String details) {
        jdbc.update("UPDATE platform.configuration_apply_history SET result_status=?,checksum_before=?,checksum_after=?,backup_path=?,health_status=?,details=?,completed_at=NOW() WHERE id=?",
            status, before, after, backup == null ? null : backup.toString(), healthStatus == 0 ? null : healthStatus, details, id);
    }

    private Map<String, Object> response(UUID id, Path target, Path backup, String status, String before, String after, int healthStatus, boolean manualRollback) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", id);
        result.put("status", status);
        result.put("targetPath", target.toString());
        result.put("backupPath", backup == null ? null : backup.toString());
        result.put("checksumBefore", before);
        result.put("checksumAfter", after);
        result.put("healthStatus", healthStatus == 0 ? null : healthStatus);
        result.put("manualRollback", manualRollback);
        return result;
    }

    public record ApplyRequest(String templateCode, String environment, String relativePath, String content, String serviceName, boolean restartService, String healthUrl, String reason) {}
}
