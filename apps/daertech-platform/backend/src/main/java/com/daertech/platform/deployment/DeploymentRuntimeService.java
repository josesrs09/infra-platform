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
    private final Path dynamicRoot;
    private final int timeoutSeconds;

    public DeploymentRuntimeService(JdbcTemplate jdbc, SecretCipher cipher, TelegramNotifier notifier,
            @Value("${app.deployment.execution-enabled:false}") boolean executionEnabled,
            @Value("${app.deployment.traefik-dynamic-root:/opt/infra-platform/traefik/dynamic}") String dynamicRoot,
            @Value("${app.deployment.command-timeout-seconds:900}") int timeoutSeconds) {
        this.jdbc=jdbc; this.cipher=cipher; this.notifier=notifier; this.executionEnabled=executionEnabled;
        this.dynamicRoot=Path.of(dynamicRoot).toAbsolutePath().normalize();
        this.timeoutSeconds=Math.max(30,Math.min(timeoutSeconds,3600));
    }

    public List<Map<String,Object>> events(UUID deploymentId){
        return jdbc.queryForList("SELECT * FROM platform.deployment_runtime_events WHERE deployment_id=? ORDER BY created_at DESC",deploymentId);
    }

    public Map<String,Object> pushImage(UUID deploymentId,String actor){
        requireExecution();
        Map<String,Object> d=deployment(deploymentId);
        if(!"SUCCESS".equals(Objects.toString(d.get("status")))) throw new IllegalStateException("Solo se publican despliegues exitosos");
        if(d.get("registry_id")==null||d.get("registry_image")==null) throw new IllegalStateException("Registro o imagen no configurados");
        Map<String,Object> r=jdbc.queryForMap("SELECT * FROM platform.container_registries WHERE id=? AND active=true",d.get("registry_id"));
        if(!Boolean.TRUE.equals(r.get("push_enabled"))) throw new IllegalStateException("Push deshabilitado para el registro");
        String environment=Objects.toString(d.get("environment"));
        String username=secret(Objects.toString(r.get("username_secret_key")),environment);
        String password=secret(Objects.toString(r.get("password_secret_key")),environment);
        String registry=Objects.toString(r.get("registry_url"));
        String localImage=Objects.toString(d.get("image_tag"));
        String targetImage=Objects.toString(d.get("registry_image"));
        UUID event=startEvent(deploymentId,"REGISTRY_PUSH",actor,"Publicación de "+targetImage);
        try{
            dockerLogin(registry,username,password);
            run(List.of("docker","tag",localImage,targetImage));
            String output=run(List.of("docker","push",targetImage));
            jdbc.update("UPDATE platform.deployments SET registry_pushed_at=NOW(),notification_status='REGISTRY_PUSHED' WHERE id=?",deploymentId);
            finishEvent(event,"SUCCESS",shorten(output,12000));
            notifier.send("✅ Imagen publicada\nDespliegue: "+deploymentId+"\nImagen: "+targetImage+"\nUsuario: "+actor);
            return result("Imagen publicada",targetImage);
        }catch(Exception ex){
            finishEvent(event,"FAILED",shorten(ex.getMessage(),12000));
            notifier.send("❌ Falló publicación de imagen\nDespliegue: "+deploymentId+"\nError: "+shorten(ex.getMessage(),500));
            throw new IllegalStateException("No fue posible publicar la imagen: "+ex.getMessage(),ex);
        }finally{try{run(List.of("docker","logout",registry));}catch(Exception ignored){}}
    }

    public Map<String,Object> switchTraffic(UUID deploymentId,String targetSlot,String actor){
        requireExecution();
        String slot=normalizeSlot(targetSlot);
        Map<String,Object> d=deployment(deploymentId);
        if(!"BLUE_GREEN".equals(Objects.toString(d.get("strategy")))) throw new IllegalStateException("La estrategia debe ser BLUE_GREEN");
        if(!"SUCCESS".equals(Objects.toString(d.get("status")))) throw new IllegalStateException("El despliegue debe estar exitoso");
        String app=Objects.toString(d.get("application_code")).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]","-");
        String env=Objects.toString(d.get("environment")).toLowerCase(Locale.ROOT);
        Path file=dynamicRoot.resolve(app+"-"+env+"-active.yml").normalize();
        if(!file.startsWith(dynamicRoot)) throw new IllegalArgumentException("Ruta dinámica inválida");
        UUID event=startEvent(deploymentId,"TRAFFIC_SWITCH",actor,"Conmutación a "+slot);
        try{
            Files.createDirectories(dynamicRoot);
            String service=app+"-"+env+"-"+slot.toLowerCase(Locale.ROOT);
            String yaml="http:\n  services:\n    "+app+"-active:\n      loadBalancer:\n        servers:\n          - url: http://"+service+"\n";
            Path tmp=Files.createTempFile(dynamicRoot,"switch-",".tmp");
            Files.writeString(tmp,yaml,StandardCharsets.UTF_8);
            try{Files.move(tmp,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException ex){Files.move(tmp,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            jdbc.update("UPDATE platform.deployments SET active_slot=?,traffic_switched_at=NOW(),notification_status='TRAFFIC_SWITCHED' WHERE id=?",slot,deploymentId);
            finishEvent(event,"SUCCESS","Archivo Traefik actualizado: "+file);
            notifier.send("🔀 Tráfico conmutado\nDespliegue: "+deploymentId+"\nSlot: "+slot+"\nUsuario: "+actor);
            return result("Tráfico conmutado",slot);
        }catch(Exception ex){
            finishEvent(event,"FAILED",shorten(ex.getMessage(),12000));
            throw new IllegalStateException("No fue posible conmutar el tráfico: "+ex.getMessage(),ex);
        }
    }

    private void dockerLogin(String registry,String username,String password)throws Exception{
        Process p=new ProcessBuilder("docker","login",registry,"--username",username,"--password-stdin").redirectErrorStream(true).start();
        try(var out=p.getOutputStream()){out.write(password.getBytes(StandardCharsets.UTF_8));out.write('\n');}
        String output=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        if(!p.waitFor(timeoutSeconds,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("docker login excedió el tiempo máximo");}
        if(p.exitValue()!=0)throw new IllegalStateException(output);
    }

    private String run(List<String> command)throws Exception{
        Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output=new StringBuilder();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){if(output.length()<500000)output.append(line).append('\n');}}
        if(!p.waitFor(timeoutSeconds,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("Comando excedió el tiempo máximo");}
        if(p.exitValue()!=0)throw new IllegalStateException(output.toString());
        return output.toString();
    }

    private Map<String,Object> deployment(UUID id){return jdbc.queryForMap("SELECT d.*,a.code application_code,a.name application_name FROM platform.deployments d JOIN platform.applications a ON a.id=d.application_id WHERE d.id=?",id);}
    private String secret(String key,String environment){
        if(key==null||key.isBlank())throw new IllegalStateException("Clave de secreto no configurada");
        return jdbc.query("SELECT config_value,secret FROM platform.configuration_items WHERE config_key=? AND environment=? AND active=true",rs->{if(!rs.next())throw new IllegalStateException("No existe el secreto "+key+" en "+environment);String value=rs.getString(1);return rs.getBoolean(2)?cipher.decrypt(value):value;},key,environment);
    }
    private UUID startEvent(UUID deploymentId,String type,String actor,String details){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO platform.deployment_runtime_events(id,deployment_id,event_type,status,details,performed_by) VALUES (?,?,?,?,?,?)",id,deploymentId,type,"RUNNING",details,actor);return id;}
    private void finishEvent(UUID id,String status,String details){jdbc.update("UPDATE platform.deployment_runtime_events SET status=?,details=? WHERE id=?",status,details,id);}
    private void requireExecution(){if(!executionEnabled)throw new IllegalStateException("La ejecución operativa está deshabilitada");}
    private String normalizeSlot(String slot){String value=Objects.toString(slot,"").toUpperCase(Locale.ROOT);if(!Set.of("BLUE","GREEN").contains(value))throw new IllegalArgumentException("targetSlot debe ser BLUE o GREEN");return value;}
    private String shorten(String value,int max){if(value==null)return "Error no especificado";return value.length()<=max?value:value.substring(0,max);}
    private Map<String,Object> result(String message,String value){Map<String,Object> out=new LinkedHashMap<>();out.put("success",true);out.put("message",message);out.put("value",value);out.put("timestamp",OffsetDateTime.now());return out;}
}