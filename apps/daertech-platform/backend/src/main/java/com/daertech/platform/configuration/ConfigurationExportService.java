package com.daertech.platform.configuration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ConfigurationExportService {
    private final JdbcTemplate jdbc;

    public ConfigurationExportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String exportEnv(String environment) {
        return rows(environment).stream()
            .filter(row -> !Boolean.TRUE.equals(row.get("secret")))
            .map(row -> normalizeKey(row.get("config_key").toString()) + "=" + envValue(row.get("config_value")))
            .reduce("", (left, right) -> left + right + "\n");
    }

    public String exportYaml(String environment) {
        StringBuilder yaml = new StringBuilder("environment: ").append(environment).append("\nconfiguration:\n");
        for (Map<String, Object> row : rows(environment)) {
            String value = Boolean.TRUE.equals(row.get("secret")) ? "${SECRET_" + normalizeKey(row.get("config_key").toString()) + "}" : String.valueOf(row.get("config_value"));
            yaml.append("  ").append(row.get("config_key")).append(": ").append(yamlValue(value)).append("\n");
        }
        return yaml.toString();
    }

    private List<Map<String, Object>> rows(String environment) {
        return jdbc.queryForList("SELECT config_key, config_value, secret FROM platform.configuration_items WHERE environment=? AND active=TRUE ORDER BY category, config_key", environment.toUpperCase());
    }

    private String normalizeKey(String value) {
        return value.replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
    }

    private String envValue(Object value) {
        String text = value == null ? "" : value.toString();
        return text.matches("[A-Za-z0-9_./:@-]*") ? text : "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String yamlValue(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
