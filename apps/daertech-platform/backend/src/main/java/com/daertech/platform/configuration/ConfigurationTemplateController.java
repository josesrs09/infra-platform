package com.daertech.platform.configuration;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/configuration-management")
public class ConfigurationTemplateController {
    private final ConfigurationTemplateService service;
    public ConfigurationTemplateController(ConfigurationTemplateService service){this.service=service;}

    @GetMapping("/templates") @PreAuthorize("hasAuthority('CONFIG_READ')")
    public List<Map<String,Object>> templates(){return service.templates();}
    @PostMapping("/templates") @PreAuthorize("hasAuthority('CONFIG_TEMPLATE')")
    public Map<String,Object> saveTemplate(@RequestBody ConfigurationTemplateService.TemplateRequest request){return service.saveTemplate(request);}

    @GetMapping("/profiles") @PreAuthorize("hasAuthority('CONFIG_READ')")
    public List<Map<String,Object>> profiles(){return service.profiles();}
    @PostMapping("/profiles") @PreAuthorize("hasAuthority('CONFIG_TEMPLATE')")
    public Map<String,Object> saveProfile(@RequestBody ConfigurationTemplateService.ProfileRequest request){return service.saveProfile(request);}

    @PostMapping("/profiles/{id}/apply") @PreAuthorize("hasAuthority('CONFIG_APPLY')")
    public Map<String,Object> apply(@PathVariable UUID id,@RequestBody Map<String,String> body,Authentication auth) throws IOException {
        return service.apply(id,auth.getName(),body.getOrDefault("reason","Aplicación controlada"));
    }

    @PostMapping("/history/{id}/rollback") @PreAuthorize("hasAuthority('CONFIG_APPLY')")
    public Map<String,Object> rollback(@PathVariable UUID id,Authentication auth) throws IOException {return service.rollback(id,auth.getName());}

    @GetMapping("/history") @PreAuthorize("hasAuthority('CONFIG_READ')")
    public List<Map<String,Object>> history(){return service.history();}
}
