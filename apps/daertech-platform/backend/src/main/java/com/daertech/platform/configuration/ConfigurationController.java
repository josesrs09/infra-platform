package com.daertech.platform.configuration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/admin/configurations")
public class ConfigurationController {
    private final ConfigurationService service;
    private final ConfigurationExportService exportService;
    private final ConnectivityValidationService validationService;

    public ConfigurationController(ConfigurationService service, ConfigurationExportService exportService, ConnectivityValidationService validationService){
        this.service=service; this.exportService=exportService; this.validationService=validationService;
    }
    @GetMapping @PreAuthorize("hasAuthority('CONFIG_READ')")
    public List<Map<String,Object>> list(@RequestParam(defaultValue="PRODUCTION") String environment){return service.list(environment);}
    @PostMapping @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    public Map<String,Object> save(@RequestBody ConfigurationService.Request request, Authentication authentication){return service.save(request,authentication.getName());}
    @GetMapping("/{id}/history") @PreAuthorize("hasAuthority('CONFIG_READ')")
    public List<Map<String,Object>> history(@PathVariable UUID id){return service.history(id);}
    @PostMapping("/{id}/rollback/{version}") @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    public Map<String,Object> rollback(@PathVariable UUID id,@PathVariable long version,@RequestBody(required=false) Map<String,String> body){return service.rollback(id,version,body==null?"Rollback manual":body.getOrDefault("reason","Rollback manual"));}
    @GetMapping("/environments") @PreAuthorize("hasAuthority('CONFIG_READ')")
    public List<String> environments(){return List.of("DEVELOPMENT","QA","CERTIFICATION","PRODUCTION");}
    @PostMapping("/validate") @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    public Map<String,Object> validate(@RequestBody ConnectivityValidationService.Request request){return validationService.validate(request);}
    @GetMapping(value="/export/env", produces=MediaType.TEXT_PLAIN_VALUE) @PreAuthorize("hasAuthority('CONFIG_READ')")
    public ResponseEntity<String> exportEnv(@RequestParam(defaultValue="PRODUCTION") String environment){
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+environment.toLowerCase()+".env\"").body(exportService.exportEnv(environment));
    }
    @GetMapping(value="/export/yaml", produces="application/yaml") @PreAuthorize("hasAuthority('CONFIG_READ')")
    public ResponseEntity<String> exportYaml(@RequestParam(defaultValue="PRODUCTION") String environment){
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+environment.toLowerCase()+".yaml\"").body(exportService.exportYaml(environment));
    }
}
