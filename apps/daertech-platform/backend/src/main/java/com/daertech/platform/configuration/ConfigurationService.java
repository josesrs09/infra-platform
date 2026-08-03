package com.daertech.platform.configuration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ConfigurationService {
    private final ConfigurationRepository repository; private final SecretCipher cipher; private final JdbcTemplate jdbc;
    public ConfigurationService(ConfigurationRepository repository,SecretCipher cipher,JdbcTemplate jdbc){this.repository=repository;this.cipher=cipher;this.jdbc=jdbc;}
    public List<Map<String,Object>> list(String environment){return repository.findAllByEnvironmentOrderByCategoryAscKeyAsc(environment.toUpperCase()).stream().map(this::view).toList();}
    @Transactional public Map<String,Object> save(Request r,String actor){
        validate(r); String env=r.environment().toUpperCase();
        ConfigurationItem item=repository.findByEnvironmentAndKey(env,r.key()).orElseGet(ConfigurationItem::new);
        String previous=item.getValue(); long next=item.getId()==null?1:item.getVersion()+1;
        item.setEnvironment(env);item.setCategory(r.category().toUpperCase());item.setKey(r.key());item.setSecret(r.secret());item.setValueType(r.valueType().toUpperCase());item.setDescription(r.description());item.setValidationRule(r.validationRule());item.setActive(r.active());item.setVersion(next);
        item.setValue(r.secret()?cipher.encrypt(r.value()):r.value()); repository.save(item);
        jdbc.update("INSERT INTO platform.configuration_history(id,configuration_id,previous_value,new_value,reason,operation,version,success) VALUES (?,?,?,?,?,?,?,true)",UUID.randomUUID(),item.getId(),previous,item.getValue(),r.reason(),previous==null?"CREATE":"UPDATE",next);
        return view(item);
    }
    @Transactional public Map<String,Object> rollback(UUID id,long version,String reason){
        ConfigurationItem item=repository.findById(id).orElseThrow();
        String value=jdbc.query("SELECT new_value FROM platform.configuration_history WHERE configuration_id=? AND version=? AND success=true ORDER BY changed_at DESC LIMIT 1",rs->rs.next()?rs.getString(1):null,id,version);
        if(value==null) throw new IllegalArgumentException("Versión no encontrada"); String previous=item.getValue(); item.setValue(value);item.setVersion(item.getVersion()+1);repository.save(item);
        jdbc.update("INSERT INTO platform.configuration_history(id,configuration_id,previous_value,new_value,reason,operation,version,success) VALUES (?,?,?,?,?,'ROLLBACK',?,true)",UUID.randomUUID(),id,previous,value,reason,item.getVersion());return view(item);
    }
    public List<Map<String,Object>> history(UUID id){return jdbc.queryForList("SELECT id,operation,version,reason,changed_at,success FROM platform.configuration_history WHERE configuration_id=? ORDER BY changed_at DESC",id);}
    private void validate(Request r){if(r.key()==null||!r.key().matches("[A-Z0-9_.-]{3,180}"))throw new IllegalArgumentException("Clave inválida; use mayúsculas, números, punto, guion o guion bajo");if(r.value()==null)throw new IllegalArgumentException("Valor requerido");if(r.validationRule()!=null&&!r.validationRule().isBlank()&&!Pattern.compile(r.validationRule()).matcher(r.value()).matches())throw new IllegalArgumentException("El valor no cumple la regla de validación");if(!Set.of("STRING","NUMBER","BOOLEAN","URL","JSON","PASSWORD","TOKEN","CERTIFICATE").contains(r.valueType().toUpperCase()))throw new IllegalArgumentException("Tipo de valor no permitido");}
    private Map<String,Object> view(ConfigurationItem i){Map<String,Object> m=new LinkedHashMap<>();m.put("id",i.getId());m.put("category",i.getCategory());m.put("key",i.getKey());m.put("value",i.isSecret()?"********":i.getValue());m.put("secret",i.isSecret());m.put("environment",i.getEnvironment());m.put("valueType",i.getValueType());m.put("description",i.getDescription());m.put("active",i.isActive());m.put("version",i.getVersion());m.put("updatedAt",i.getUpdatedAt());return m;}
    public record Request(String category,String key,String value,boolean secret,String environment,String valueType,String description,String validationRule,boolean active,String reason){}
}
