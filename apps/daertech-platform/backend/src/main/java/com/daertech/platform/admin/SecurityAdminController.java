package com.daertech.platform.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/admin")
public class SecurityAdminController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public SecurityAdminController(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc; this.encoder = encoder;
    }

    public record UserRequest(@NotBlank String username, @Email @NotBlank String email,
                              @NotBlank String fullName, @Size(min=12) String password,
                              Boolean enabled, Boolean locked, Set<UUID> roleIds) {}
    public record RoleRequest(@NotBlank String code, @NotBlank String name, String description,
                              Boolean active, Set<UUID> permissionIds) {}

    @GetMapping("/users") @PreAuthorize("hasAuthority('USER_READ')")
    public List<Map<String,Object>> users(){
        return jdbc.queryForList("SELECT id,username,email,full_name,enabled,locked,last_login_at,created_at,updated_at FROM platform.users ORDER BY username");
    }

    @GetMapping("/users/{id}") @PreAuthorize("hasAuthority('USER_READ')")
    public Map<String,Object> user(@PathVariable UUID id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,username,email,full_name,enabled,locked,last_login_at,created_at,updated_at FROM platform.users WHERE id=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Usuario no encontrado");
        Map<String,Object> result=new LinkedHashMap<>(rows.getFirst());
        result.put("roleIds",jdbc.queryForList("SELECT role_id FROM platform.user_roles WHERE user_id=? ORDER BY role_id",UUID.class,id));
        return result;
    }

    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('USER_CREATE')")
    public Map<String,Object> createUser(@Valid @RequestBody UserRequest request){
        UUID id=UUID.randomUUID();
        if(request.password()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La contraseña es obligatoria");
        jdbc.update("INSERT INTO platform.users(id,username,email,full_name,password_hash,enabled,locked) VALUES (?,?,?,?,?,?,?)",
            id,request.username().trim(),request.email().trim().toLowerCase(),request.fullName().trim(),encoder.encode(request.password()),
            request.enabled()==null||request.enabled(),request.locked()!=null&&request.locked());
        replaceUserRoles(id,request.roleIds());
        return Map.of("id",id);
    }

    @PutMapping("/users/{id}") @PreAuthorize("hasAuthority('USER_UPDATE')")
    public void updateUser(@PathVariable UUID id,@Valid @RequestBody UserRequest request){
        int count=jdbc.update("UPDATE platform.users SET username=?,email=?,full_name=?,enabled=?,locked=?,updated_at=NOW() WHERE id=?",
            request.username().trim(),request.email().trim().toLowerCase(),request.fullName().trim(),request.enabled()==null||request.enabled(),request.locked()!=null&&request.locked(),id);
        if(count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Usuario no encontrado");
        if(request.password()!=null&&!request.password().isBlank()) jdbc.update("UPDATE platform.users SET password_hash=? WHERE id=?",encoder.encode(request.password()),id);
        replaceUserRoles(id,request.roleIds());
    }

    @DeleteMapping("/users/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('USER_DELETE')")
    public void deleteUser(@PathVariable UUID id){ if(jdbc.update("DELETE FROM platform.users WHERE id=?",id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Usuario no encontrado"); }

    @GetMapping("/roles") @PreAuthorize("hasAuthority('ROLE_READ')")
    public List<Map<String,Object>> roles(){ return jdbc.queryForList("SELECT id,code,name,description,active,created_at FROM platform.roles ORDER BY code"); }

    @GetMapping("/roles/{id}") @PreAuthorize("hasAuthority('ROLE_READ')")
    public Map<String,Object> role(@PathVariable UUID id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,code,name,description,active,created_at FROM platform.roles WHERE id=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Rol no encontrado");
        Map<String,Object> result=new LinkedHashMap<>(rows.getFirst());
        result.put("permissionIds",jdbc.queryForList("SELECT permission_id FROM platform.role_permissions WHERE role_id=? ORDER BY permission_id",UUID.class,id));
        return result;
    }

    @PostMapping("/roles") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('ROLE_CREATE')")
    public Map<String,Object> createRole(@Valid @RequestBody RoleRequest request){
        UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO platform.roles(id,code,name,description,active) VALUES (?,?,?,?,?)",id,request.code().trim().toUpperCase(),request.name().trim(),request.description(),request.active()==null||request.active());
        replaceRolePermissions(id,request.permissionIds()); return Map.of("id",id);
    }

    @PutMapping("/roles/{id}") @PreAuthorize("hasAuthority('ROLE_UPDATE')")
    public void updateRole(@PathVariable UUID id,@Valid @RequestBody RoleRequest request){
        int count=jdbc.update("UPDATE platform.roles SET code=?,name=?,description=?,active=? WHERE id=?",request.code().trim().toUpperCase(),request.name().trim(),request.description(),request.active()==null||request.active(),id);
        if(count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Rol no encontrado"); replaceRolePermissions(id,request.permissionIds());
    }

    @DeleteMapping("/roles/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public void deleteRole(@PathVariable UUID id){ if(jdbc.update("DELETE FROM platform.roles WHERE id=?",id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Rol no encontrado"); }

    @GetMapping("/permissions") @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public List<Map<String,Object>> permissions(){ return jdbc.queryForList("SELECT id,code,name,module,created_at FROM platform.permissions ORDER BY module,code"); }

    private void replaceUserRoles(UUID userId,Set<UUID> ids){ jdbc.update("DELETE FROM platform.user_roles WHERE user_id=?",userId); if(ids!=null) ids.forEach(id->jdbc.update("INSERT INTO platform.user_roles(user_id,role_id) VALUES (?,?)",userId,id)); }
    private void replaceRolePermissions(UUID roleId,Set<UUID> ids){ jdbc.update("DELETE FROM platform.role_permissions WHERE role_id=?",roleId); if(ids!=null) ids.forEach(id->jdbc.update("INSERT INTO platform.role_permissions(role_id,permission_id) VALUES (?,?)",roleId,id)); }
}
