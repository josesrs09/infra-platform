package com.daertech.platform.configuration;

import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ConnectivityValidationService {
    public Map<String, Object> validate(Request request) {
        Instant started = Instant.now();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", request.type().toUpperCase());
        result.put("target", request.target());
        try {
            String message = switch (request.type().toUpperCase()) {
                case "HTTP", "REST", "SOAP", "TELEGRAM", "MINIO" -> validateHttp(request.target(), request.timeoutSeconds());
                case "TCP", "POSTGRESQL", "MYSQL", "REDIS", "RABBITMQ", "SMTP", "MQTT" -> validateTcp(request.host(), request.port(), request.timeoutSeconds());
                default -> throw new IllegalArgumentException("Tipo de validación no soportado");
            };
            result.put("success", true);
            result.put("message", message);
        } catch (Exception ex) {
            result.put("success", false);
            result.put("message", ex.getMessage());
        }
        result.put("durationMs", Duration.between(started, Instant.now()).toMillis());
        return result;
    }

    private String validateTcp(String host, Integer port, int timeoutSeconds) throws Exception {
        if (host == null || port == null) throw new IllegalArgumentException("host y port son obligatorios");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutSeconds * 1000);
            return "Conexión TCP exitosa";
        }
    }

    private String validateHttp(String target, int timeoutSeconds) throws Exception {
        if (target == null || target.isBlank()) throw new IllegalArgumentException("target es obligatorio");
        HttpURLConnection connection = (HttpURLConnection) URI.create(target).toURL().openConnection();
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        if (status >= 500) throw new IllegalStateException("Servicio respondió HTTP " + status);
        return "Servicio respondió HTTP " + status;
    }

    public record Request(String type, String target, String host, Integer port, Integer timeout) {
        public int timeoutSeconds() { return timeout == null ? 5 : Math.max(1, Math.min(timeout, 30)); }
    }
}
