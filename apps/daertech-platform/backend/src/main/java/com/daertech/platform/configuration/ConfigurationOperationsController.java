package com.daertech.platform.configuration;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/admin/configuration-operations")
public class ConfigurationOperationsController {
    private final ConfigurationOperationsService service;

    public ConfigurationOperationsController(ConfigurationOperationsService service) {
        this.service = service;
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    public Map<String,Object> validate(@RequestBody ConfigurationOperationsService.ValidationRequest request) {
        return service.validate(request);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('CONFIG_READ')")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "PRODUCTION") String environment,
                                         @RequestParam(defaultValue = "ENV") String format,
                                         @RequestParam(defaultValue = "false") boolean includeSecrets) {
        String normalized = format.equalsIgnoreCase("YAML") || format.equalsIgnoreCase("YML") ? "yaml" : "env";
        String content = service.export(environment, normalized, includeSecrets);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        MediaType mediaType = normalized.equals("yaml") ? MediaType.parseMediaType("application/yaml") : MediaType.TEXT_PLAIN;
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("daertech-" + environment.toLowerCase() + "." + normalized, StandardCharsets.UTF_8)
                .build().toString())
            .body(bytes);
    }
}
