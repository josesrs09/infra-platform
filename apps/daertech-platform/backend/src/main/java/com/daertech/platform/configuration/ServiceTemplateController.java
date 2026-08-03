package com.daertech.platform.configuration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/service-templates")
public class ServiceTemplateController {
    private final ServiceTemplateService service;

    public ServiceTemplateController(ServiceTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CONFIG_READ')")
    public List<Map<String,Object>> list() {
        return service.list();
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('CONFIG_READ')")
    public Map<String,Object> details(@PathVariable String code) {
        return service.details(code.toUpperCase());
    }

    @PostMapping("/{code}/render")
    @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    public ServiceTemplateService.RenderedTemplate render(@PathVariable String code, @RequestBody Map<String,String> values) {
        return service.render(code.toUpperCase(), values);
    }

    @PostMapping("/{code}/download")
    @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    public ResponseEntity<byte[]> download(@PathVariable String code, @RequestBody Map<String,String> values) {
        var rendered = service.render(code.toUpperCase(), values);
        String extension = "YAML".equalsIgnoreCase(rendered.format()) ? "yaml" : "env";
        String filename = rendered.code().toLowerCase() + "." + extension;
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.TEXT_PLAIN)
            .body(rendered.content().getBytes(StandardCharsets.UTF_8));
    }
}
