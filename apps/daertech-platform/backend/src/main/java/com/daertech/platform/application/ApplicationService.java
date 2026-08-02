package com.daertech.platform.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ApplicationService {
    private final JdbcTemplate jdbc;
    public ApplicationService(JdbcTemplate jdbc){ this.jdbc=jdbc; }

    public List<Map<String,Object>> list(){
        return jdbc.queryForList("SELECT id,code,name,description,repository_url,default_branch,technology,build_tool,dockerfile_path,context_path,internal_port,health_path,metrics_path,active,created_by,created_at,updated_at FROM platform.applications ORDER BY name");
    }

    public Map<String,Object> find(UUID id){
        Map<String,Object> app=jdbc.queryForMap("SELECT * FROM platform.applications WHERE id=?",id);
        app.put("environments",jdbc.queryForList("SELECT * FROM platform.application_environments WHERE application_id=? ORDER BY environment",id));
        app.put("variables",jdbc.queryForList("SELECT id,application_id,environment,variable_key,CASE WHEN secret THEN '********' ELSE variable_value END variable_value,secret,required,description FROM platform.application_variables WHERE application_id=? ORDER BY environment,variable_key",id));
        app.put("dependencies",jdbc.queryForList("SELECT * FROM platform.application_dependencies WHERE application_id=? ORDER BY dependency_type,dependency_name",id));
        app.put("versions",jdbc.queryForList("SELECT * FROM platform.application_versions WHERE application_id=? ORDER BY created_at DESC",id));
        return app;
    }

    @Transactional
    public Map<String,Object> save(ApplicationRequest r,String actor){
        validate(r);
        UUID id=r.id()==null?UUID.randomUUID():r.id();
        int updated=jdbc.update("UPDATE platform.applications SET code=?,name=?,description=?,repository_url=?,default_branch=?,technology=?,build_tool=?,dockerfile_path=?,context_path=?,internal_port=?,health_path=?,metrics_path=?,active=?,updated_at=NOW() WHERE id=?",
            upper(r.code()),r.name(),r.description(),r.repositoryUrl(),blank(r.defaultBranch(),"main"),upper(r.technology()),r.buildTool(),blank(r.dockerfilePath(),"Dockerfile"),blank(r.contextPath(),"."),r.internalPort(),r.healthPath(),r.metricsPath(),r.active(),id);
        if(updated==0) jdbc.update("INSERT INTO platform.applications(id,code,name,description,repository_url,default_branch,technology,build_tool,dockerfile_path,context_path,internal_port,health_path,metrics_path,active,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,upper(r.code()),r.name(),r.description(),r.repositoryUrl(),blank(r.defaultBranch(),"main"),upper(r.technology()),r.buildTool(),blank(r.dockerfilePath(),"Dockerfile"),blank(r.contextPath(),"."),r.internalPort(),r.healthPath(),r.metricsPath(),r.active(),actor);
        return find(id);
    }

    @Transactional public void delete(UUID id){ jdbc.update("DELETE FROM platform.applications WHERE id=?",id); }

    @Transactional
    public Map<String,Object> saveEnvironment(UUID appId,EnvironmentRequest r){
        UUID id=r.id()==null?UUID.randomUUID():r.id();
        int updated=jdbc.update("UPDATE platform.application_environments SET environment=?,branch=?,public_url=?,replicas=?,cpu_limit=?,memory_limit=?,enabled=? WHERE id=? AND application_id=?",
            upper(r.environment()),r.branch(),r.publicUrl(),Math.max(1,r.replicas()),r.cpuLimit(),r.memoryLimit(),r.enabled(),id,appId);
        if(updated==0) jdbc.update("INSERT INTO platform.application_environments(id,application_id,environment,branch,public_url,replicas,cpu_limit,memory_limit,enabled) VALUES(?,?,?,?,?,?,?,?,?)",
            id,appId,upper(r.environment()),r.branch(),r.publicUrl(),Math.max(1,r.replicas()),r.cpuLimit(),r.memoryLimit(),r.enabled());
        return find(appId);
    }

    @Transactional
    public Map<String,Object> saveVariable(UUID appId,VariableRequest r){
        UUID id=r.id()==null?UUID.randomUUID():r.id();
        int updated=jdbc.update("UPDATE platform.application_variables SET environment=?,variable_key=?,variable_value=?,secret=?,required=?,description=? WHERE id=? AND application_id=?",
            upper(r.environment()),upper(r.key()),r.value(),r.secret(),r.required(),r.description(),id,appId);
        if(updated==0) jdbc.update("INSERT INTO platform.application_variables(id,application_id,environment,variable_key,variable_value,secret,required,description) VALUES(?,?,?,?,?,?,?,?)",
            id,appId,upper(r.environment()),upper(r.key()),r.value(),r.secret(),r.required(),r.description());
        return find(appId);
    }

    @Transactional
    public Map<String,Object> addVersion(UUID appId,VersionRequest r,String actor){
        jdbc.update("INSERT INTO platform.application_versions(id,application_id,version,git_commit,image_tag,notes,created_by) VALUES(?,?,?,?,?,?,?)",
            UUID.randomUUID(),appId,r.version(),r.gitCommit(),r.imageTag(),r.notes(),actor);
        return find(appId);
    }

    private void validate(ApplicationRequest r){
        if(r.code()==null||r.code().isBlank()||r.name()==null||r.name().isBlank()||r.repositoryUrl()==null||r.repositoryUrl().isBlank()||r.technology()==null||r.technology().isBlank())
            throw new IllegalArgumentException("Código, nombre, repositorio y tecnología son obligatorios");
        if(r.internalPort()!=null&&(r.internalPort()<1||r.internalPort()>65535)) throw new IllegalArgumentException("Puerto interno inválido");
    }
    private String upper(String v){return v==null?null:v.trim().toUpperCase(Locale.ROOT);} private String blank(String v,String d){return v==null||v.isBlank()?d:v.trim();}

    public record ApplicationRequest(UUID id,String code,String name,String description,String repositoryUrl,String defaultBranch,String technology,String buildTool,String dockerfilePath,String contextPath,Integer internalPort,String healthPath,String metricsPath,boolean active){}
    public record EnvironmentRequest(UUID id,String environment,String branch,String publicUrl,int replicas,String cpuLimit,String memoryLimit,boolean enabled){}
    public record VariableRequest(UUID id,String environment,String key,String value,boolean secret,boolean required,String description){}
    public record VersionRequest(String version,String gitCommit,String imageTag,String notes){}
}
