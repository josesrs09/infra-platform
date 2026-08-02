package com.daertech.platform.deployment;

import com.daertech.platform.configuration.SecretCipher;
import com.daertech.platform.configuration.TelegramNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class DeploymentRuntimeService {
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final TelegramNotifier notifier;
    private final boolean executionEnabled;
    private final Path dynamicConfigRoot;
    private final int timeoutSeconds;

    public DeploymentRuntimeService(JdbcTemplate jdbc,
                                    SecretCipher cipher,
                                    TelegramNotifier notifier,
                                    @Value("${app.deployment.execution-enabled:false}") boolean executionEnabled,
                                    @Value("${app.deployment.traefik-dynamic-root:/opt/infra-platform/traefik/dynamic}") String dynamicConfigRoot,
                                    @Value("${app.deployment.command-timeout-seconds:900}") int timeoutSeconds) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.notifier = notifier;
        this.executionEnabled = executionEnabled;
        this.dynamicConfigRoot = Path.of(dynamicConfigRoot).toAbsolutePath().normalize();
        this.timeoutSeconds = Math.max(30, Math.min(timeoutSeconds, 3600));
    }

    public List<Map<String,Object>> events(UUID deploymentId) {
        return jdbc.queryForList("SELECT * FROM platform.deployment_runtime_events WHERE deployment_id=? ORDER BY created_at DESC", deploymentId);
    }

    public Map<String,Object> pushImage(UUID deploymentId, String actor) {
        requireExecution();
        Map<String,Object> deployment = deployment(deploymentId);
        if (!"SUCCESS".equals(Objects.toString(deployment.get("status")))) {
            throw new IllegalStateException("Solo se publican imágenes de despliegues exitosos");
        }
        if (deployment.get("registry_id") == null || deployment.get("registry_image") == null) {
            throw new IllegalStateException("El despliegue no tiene registro e imagen configurados");
        }
        Map<String,Object> registry = jdbc.queryForMap("SELECT * FROM platform.container_registries WHERE id=? AND active=true", deployment.get("registry_id"));
        if (!Boolean.TRUE.equals(registry.get("push_enabled"))) {
            throw new IllegalStateException("La publicación está deshabilitada para este registro");
        }
        String username = secret(Objects.toString(registry.get("username_secret_key")), Objects.toString(deployment.get("environment")));
        String password = secret(Objects.toString(registry.get("password_secret_key")), Objects.toString(deployment.get("environment")));
        String registryUrl = Objects.toString(registry.get("registry_url"));
        String localImage = Objects.toString(deployment.get("image_tag"));
        String targetImage = Objects.toString(deployment.get("registry_image"));
        UUID eventId = startEvent(deploymentId, "REGISTRY_PUSH", actor, "Publicación de " + targetImage);
        try {
            run(List.of("sh","-lc", "printf '%s' "$0" | docker login " + quote(registryUrl) + " --username " + quote(username) + " --password-stdin", password));
            run(List.of("docker","tag",localImage,targetImage));
            String output = run(List.of("docker","push",targetImage));
            jdbc.update("UPDATE platform.deployments SET registry_pushed_at=NOW(),notification_status='REGISTRY_PUSHED' WHERE id=?", deploymentId);
            finishEvent(eventId,"SUCCESS",abbreviate(output,12000));
            notifier.send("✅ Imagen publicada\nDespliegue: " + deploymentId + "\nImagen: " + targetImage + "\nUsuario: " + actor);
            return result(true,"Imagen publicada",targetImage);
        } catch (Exception ex) {
            finishEvent(eventId,"FAILED",abbreviate(ex.getMessage(),12000));
            notifier.send("❌ Falló publicación de imagen\nDespliegue: " + deploymentId + "\nError: " + abbreviate(ex.getMessage(),500));
            throw new IllegalStateException("No fue posible publicar la imagen: " + ex.getMessage(), ex);
        } finally {
            try { run(List.of("docker","logout",registryUrl)); } catch (Exception ignored) { }
        }
    }

    public Map<String,Object> switchTraffic(UUID deploymentId, String targetSlot, String actor) {
        requireExecution();
        String slot = normalizeSlot(targetSlot);
        Map<String,Object> deployment = deployment(deploymentId);
        if (!"BLUE_GREEN".equals(Objects.toString(deployment.get("strategy")))) {
            throw new IllegalStateException("La conmutación solo aplica a estrategia BLUE_GREEN");
        }
        if (!"SUCCESS".equals(Objects.toString(deployment.get("status")))) {
            throw new IllegalStateException("El despliegue debe estar exitoso antes de conmutar tráfico");
        }
        String appCode = Objects.toString(deployment.get("application_code")).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]","-");
        String environment = Objects.toString(deployment.get("environment")).toLowerCase(Locale.ROOT);
        Path file = dynamicConfigRoot.resolve(appCode + "-" + environment + "-active.yml").normalize();
        if (!file.startsWith(dynamicConfigRoot)) throw new IllegalArgumentException("Ruta dinámica inválida");
        UUID eventId = startEvent(deploymentId,"TRAFFIC_SWITCH",actor,"Conmutación a slot " + slot);
        try {
            Files.createDirectories(dynamicConfigRoot);
            String service = appCode + "-" + environment + "-" + slot.toLowerCase(Locale.ROOT);
            String yaml = "http:\n  services:\n    " + appCode + "-active:\n      loadBalancer:\n        servers:\n          - url: http://" + service + "\n";
            Path temporary = Files.createTempFile(dynamicConfigRoot,"switch-",".tmp");
            Files.writeString(temporary,yaml,StandardCharsets.UTF_8);
            Files.move(temporary,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            jdbc.update("UPDATE platform.deployments SET active_slot=?,traffic_switched_at=NOW(),notification_status='TRAFFIC_SWITCHED' WHERE id=?",slot,deploymentId);
            finishEvent(eventId,"SUCCESS","Archivo Traefik actualizado: " + file);
            notifier.send("🔀 Tráfico blue/green conmutado\nDespliegue: " + deploymentId + "\nSlot activo: " + slot + "\nUsuario: " + actor);
            return result(true,"Tráfico conmutado",slot);
        } catch (Exception ex) {
            finishEvent(eventId,"FAILED",abbreviate(ex.getMessage(),12000));
            notifier.send("❌ Falló conmutación blue/green\nDespliegue: " + deploymentId + "\nError: " + abbreviate(ex.getMessage(),500));
            throw new IllegalStateException("No fue posible conmutar el tráfico: " + ex.getMessage(), ex);
        }
    }

    private Map<String,Object> deployment(UUID id) {
        return jdbc.queryForMap("SELECT d.*,a.code application_code,a.name application_name FROM platform.deployments d JOIN platform.applications a ON a.id=d.application_id WHERE d.id=?",id);
    }

    private String secret(String key, String environment) {
        if (key == null || key.isBlank()) throw new IllegalStateException("Clave de secreto no configurada");
        return jdbc.query("SELECT config_value,secret FROM platform.configuration_items WHERE config_key=? AND environment=? AND active=true", rs -> {
            if (!rs.next()) throw new IllegalStateException("No existe el secreto " + key + " en " + environment);
            String value = rs.getString(1);
            return rs.getBoolean(2) ? cipher.decrypt(value) : value;
        }, key, environment);
    }

    private UUID startEvent(UUID deploymentId,String type,String actor,String details) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO platform.deployment_runtime_events(id,deployment_id,event_type,status,details,performed_by) VALUES (?,?,?,?,?,?)",id,deploymentId,type,"RUNNING",details,actor);
        return id;
    }

    private void finishEvent(UUID id,String status,String details) {
        jdbc.update("UPDATE platform.deployment_runtime_events SET status=?,details=? WHERE id=?",status,details,id);
    }

    private String run(List<String> command) throws Exception {
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output=new StringBuilder();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))){
            String line; while((line=reader.readLine())!=null){if(output.length()<500000) output.append(line).append('\n');}
        }
        if(!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Comando excedió el tiempo máximo");}
        if(process.exitValue()!=0) throw new IllegalStateException(output.toString());
        return output.toString();
    }

    private void requireExecution(){if(!executionEnabled)throw new IllegalStateException("La ejecución operativa está deshabilitada");}
    private String normalizeSlot(String slot){String value=Objects.toString(slot,"").toUpperCase(Locale.ROOT);if(!Set.of("BLUE","GREEN").contains(value))throw new IllegalArgumentException("targetSlot debe ser BLUE o GREEN");return value;}
    private String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    private String abbreviate(String value,int max){if(value==null)return "Error no especificado";return value.length()<=max?value:value.substring(0,max);}
    private Map<String,Object> result(boolean success,String message,String value){Map<String,Object> out=new LinkedHashMap<>();out.put("success",success);out.put("message",message);out.put("value",value);out.put("timestamp", OffsetDateTime.now());return out;}

    public record SwitchRequest(String targetSlot) { }
}