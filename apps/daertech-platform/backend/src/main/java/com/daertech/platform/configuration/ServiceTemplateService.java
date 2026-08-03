package com.daertech.platform.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ServiceTemplateService {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([A-Z0-9_]+)}}");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ServiceTemplateService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<Map<String,Object>> list() {
        return jdbc.queryForList("SELECT id,code,name,service_type,description,output_format,active FROM platform.service_templates WHERE active=true ORDER BY service_type,name");
    }

    public Map<String,Object> details(String code) {
        return jdbc.query("SELECT code,name,service_type,description,schema_json,output_format FROM platform.service_templates WHERE code=? AND active=true",
            rs -> {
                if (!rs.next()) throw new NoSuchElementException("Plantilla no encontrada");
                Map<String,Object> result = new LinkedHashMap<>();
                result.put("code", rs.getString("code"));
                result.put("name", rs.getString("name"));
                result.put("serviceType", rs.getString("service_type"));
                result.put("description", rs.getString("description"));
                try { result.put("schema", mapper.readValue(rs.getString("schema_json"), new TypeReference<Map<String,Object>>(){})); }
                catch (Exception e) { throw new IllegalStateException("Esquema de plantilla inválido", e); }
                result.put("outputFormat", rs.getString("output_format"));
                return result;
            }, code);
    }

    public RenderedTemplate render(String code, Map<String,String> values) {
        return jdbc.query("SELECT template_text,output_format,schema_json FROM platform.service_templates WHERE code=? AND active=true",
            rs -> {
                if (!rs.next()) throw new NoSuchElementException("Plantilla no encontrada");
                validateRequired(rs.getString("schema_json"), values);
                String template = rs.getString("template_text");
                Matcher matcher = TOKEN.matcher(template);
                StringBuffer output = new StringBuffer();
                while (matcher.find()) {
                    String key = matcher.group(1);
                    String replacement = values.get(key);
                    if (replacement == null) throw new IllegalArgumentException("Falta el valor " + key);
                    matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
                }
                matcher.appendTail(output);
                if (TOKEN.matcher(output).find()) throw new IllegalArgumentException("La plantilla contiene variables sin resolver");
                return new RenderedTemplate(code, rs.getString("output_format"), output.toString());
            }, code);
    }

    private void validateRequired(String schemaJson, Map<String,String> values) {
        try {
            Map<String,Object> schema = mapper.readValue(schemaJson, new TypeReference<>(){});
            Object required = schema.get("required");
            if (required instanceof Collection<?> fields) {
                for (Object field : fields) {
                    String key = String.valueOf(field);
                    if (values == null || values.get(key) == null || values.get(key).isBlank()) {
                        throw new IllegalArgumentException("Campo requerido: " + key);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible validar la plantilla", e);
        }
    }

    public record RenderedTemplate(String code, String format, String content) {}
}
