package com.daertech.platform.configuration;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/admin/configurations")
public class ConfigurationController {
    private final ConfigurationService service;
    public ConfigurationController(ConfigurationService service){this.service=service;}
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
}
