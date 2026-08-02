package com.daertech.platform.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ConfigurationTemplateService {
    private final JdbcTemplate jdbc;
    private final ConfigurationOperationsService operations;
    private final TelegramNotifier telegram;
    private final ObjectMapper mapper;
    private final Path allowedRoot;

    public ConfigurationTemplateService(JdbcTemplate jdbc, ConfigurationOperationsService operations,
                                        TelegramNotifier telegram, ObjectMapper mapper,
                                        @Value("${app.configuration.allowed-root:/opt/daertech/config}") String allowedRoot) {
        this.jdbc = jdbc; this.operations = operations; this.telegram = telegram; this.mapper = mapper;
        this.allowedRoot = Paths.get(allowedRoot).toAbsolutePath().normalize();
    }

    public List<Map<String,Object>> templates() {
        return jdbc.queryForList("SELECT id,code,name,service_type,format,active,created_at FROM platform.configuration_templates ORDER BY service_type,name");
    }

    @Transactional
    public Map<String,Object> saveTemplate(TemplateRequest r) {
        UUID id = r.id()==null ? UUID.randomUUID() : r.id();
        jdbc.update("INSERT INTO platform.configuration_templates(id,code,name,service_type,format,content,active) VALUES (?,?,?,?,?,?,?) " +
                "ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,service_type=EXCLUDED.service_type,format=EXCLUDED.format,content=EXCLUDED.content,active=EXCLUDED.active",
            id,r.code().toUpperCase(Locale.ROOT),r.name(),r.serviceType().toUpperCase(Locale.ROOT),r.format().toUpperCase(Locale.ROOT),r.content(),r.active());
        return Map.of("id",id,"code",r.code().toUpperCase(Locale.ROOT));
    }

    public List<Map<String,Object>> profiles() {
        return jdbc.queryForList("SELECT p.id,p.code,p.name,p.environment,p.target_path,p.active,t.code template_code,t.service_type FROM platform.configuration_profiles p LEFT JOIN platform.configuration_templates t ON t.id=p.template_id ORDER BY p.environment,p.name");
    }

    @Transactional
    public Map<String,Object> saveProfile(ProfileRequest r) {
        UUID id = r.id()==null ? UUID.randomUUID() : r.id();
        jdbc.update("INSERT INTO platform.configuration_profiles(id,code,name,environment,template_id,target_path,active) VALUES (?,?,?,?,?,?,?) " +
                "ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,environment=EXCLUDED.environment,template_id=EXCLUDED.template_id,target_path=EXCLUDED.target_path,active=EXCLUDED.active",
            id,r.code().toUpperCase(Locale.ROOT),r.name(),r.environment().toUpperCase(Locale.ROOT),r.templateId(),r.targetPath(),r.active());
        return Map.of("id",id,"code",r.code().toUpperCase(Locale.ROOT));
    }

    @Transactional
    public Map<String,Object> apply(UUID profileId, String actor, String reason) throws IOException {
        Map<String,Object> profile = jdbc.queryForMap("SELECT p.environment,p.target_path,t.content,t.format,p.code FROM platform.configuration_profiles p JOIN platform.configuration_templates t ON t.id=p.template_id WHERE p.id=? AND p.active AND t.active", profileId);
        Path target = securePath(String.valueOf(profile.get("target_path")));
        Files.createDirectories(target.getParent());
        String environment = String.valueOf(profile.get("environment"));
        String rendered = render(String.valueOf(profile.get("content")), environment);
        Path backup = null; String before = null;
        if (Files.exists(target)) {
            before = sha256(Files.readAllBytes(target));
            Path backupDir = allowedRoot.resolve("backups").resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(OffsetDateTime.now()));
            Files.createDirectories(backupDir);
            backup = backupDir.resolve(target.getFileName());
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
        Path temp = Files.createTempFile(target.getParent(), ".daertech-", ".tmp");
        Files.writeString(temp, rendered, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        String after = sha256(Files.readAllBytes(target));
        UUID historyId = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.configuration_apply_history(id,profile_id,environment,target_path,backup_path,checksum_before,checksum_after,status,reason,applied_by,details) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb)",
            historyId,profileId,environment,target.toString(),backup==null?null:backup.toString(),before,after,"APPLIED",reason,actor,mapper.writeValueAsString(Map.of("profile",profile.get("code"))));
        telegram.send("✅ Configuración aplicada\nPerfil: "+profile.get("code")+"\nAmbiente: "+environment+"\nDestino: "+target+"\nUsuario: "+actor);
        return Map.of("id",historyId,"status","APPLIED","targetPath",target.toString(),"backupPath",backup==null?"":backup.toString(),"checksum",after);
    }

    @Transactional
    public Map<String,Object> rollback(UUID historyId, String actor) throws IOException {
        Map<String,Object> h = jdbc.queryForMap("SELECT target_path,backup_path,environment FROM platform.configuration_apply_history WHERE id=?", historyId);
        if (h.get("backup_path")==null) throw new IllegalArgumentException("La aplicación seleccionada no tiene respaldo");
        Path target=securePath(String.valueOf(h.get("target_path"))); Path backup=securePath(String.valueOf(h.get("backup_path")));
        if (!Files.exists(backup)) throw new IllegalStateException("El archivo de respaldo ya no existe");
        Files.copy(backup,target,StandardCopyOption.REPLACE_EXISTING);
        jdbc.update("UPDATE platform.configuration_apply_history SET status='ROLLED_BACK',details=COALESCE(details,'{}'::jsonb)||?::jsonb WHERE id=?", mapper.writeValueAsString(Map.of("rolledBackBy",actor,"rolledBackAt",OffsetDateTime.now().toString())),historyId);
        telegram.send("↩️ Configuración revertida\nDestino: "+target+"\nUsuario: "+actor);
        return Map.of("status","ROLLED_BACK","targetPath",target.toString());
    }

    public List<Map<String,Object>> history(){ return jdbc.queryForList("SELECT * FROM platform.configuration_apply_history ORDER BY applied_at DESC LIMIT 200"); }

    private String render(String template,String environment){
        String generated=operations.export(environment,"env",true); Map<String,String> values=new HashMap<>();
        generated.lines().filter(l->!l.startsWith("#")&&l.contains("=")).forEach(l->{int i=l.indexOf('=');values.put(l.substring(0,i),strip(l.substring(i+1)));});
        String out=template; for(var e:values.entrySet()) out=out.replace("${"+e.getKey()+"}",e.getValue()); return out;
    }
    private String strip(String v){return v.length()>1&&v.startsWith("\"")&&v.endsWith("\"")?v.substring(1,v.length()-1):v;}
    private Path securePath(String path){Path p=Paths.get(path); if(!p.isAbsolute()) p=allowedRoot.resolve(p); p=p.normalize().toAbsolutePath(); if(!p.startsWith(allowedRoot)) throw new SecurityException("Ruta fuera del directorio permitido"); return p;}
    private String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}

    public record TemplateRequest(UUID id,String code,String name,String serviceType,String format,String content,boolean active){}
    public record ProfileRequest(UUID id,String code,String name,String environment,UUID templateId,String targetPath,boolean active){}
}
