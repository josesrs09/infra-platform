package com.daertech.platform.monitoring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class MonitoringService {
    private final JdbcTemplate jdbc;
    public MonitoringService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public List<Map<String,Object>> targets(String environment){
        if(environment==null||environment.isBlank()) return jdbc.queryForList("SELECT t.*,c.status last_status,c.response_time_ms,c.checked_at FROM platform.monitoring_targets t LEFT JOIN LATERAL (SELECT status,response_time_ms,checked_at FROM platform.monitoring_checks WHERE target_id=t.id ORDER BY checked_at DESC LIMIT 1)c ON true ORDER BY t.environment,t.name");
        return jdbc.queryForList("SELECT t.*,c.status last_status,c.response_time_ms,c.checked_at FROM platform.monitoring_targets t LEFT JOIN LATERAL (SELECT status,response_time_ms,checked_at FROM platform.monitoring_checks WHERE target_id=t.id ORDER BY checked_at DESC LIMIT 1)c ON true WHERE t.environment=? ORDER BY t.name",environment.toUpperCase(Locale.ROOT));
    }

    public Map<String,Object> dashboard(){
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("generatedAt",OffsetDateTime.now());
        out.put("totals",jdbc.queryForMap("SELECT COUNT(*) total,COUNT(*) FILTER(WHERE enabled) enabled FROM platform.monitoring_targets"));
        out.put("status",jdbc.queryForList("SELECT COALESCE(c.status,'UNKNOWN') status,COUNT(*) total FROM platform.monitoring_targets t LEFT JOIN LATERAL(SELECT status FROM platform.monitoring_checks WHERE target_id=t.id ORDER BY checked_at DESC LIMIT 1)c ON true WHERE t.enabled=true GROUP BY COALESCE(c.status,'UNKNOWN') ORDER BY status"));
        out.put("recentFailures",jdbc.queryForList("SELECT t.code,t.name,t.environment,c.status,c.message,c.checked_at FROM platform.monitoring_checks c JOIN platform.monitoring_targets t ON t.id=c.target_id WHERE c.status<>'UP' ORDER BY c.checked_at DESC LIMIT 20"));
        return out;
    }

    @Transactional
    public Map<String,Object> save(Request r){
        if(r.code()==null||!r.code().matches("[A-Za-z0-9._-]{2,100}")) throw new IllegalArgumentException("code inválido");
        UUID id=r.id()==null?UUID.randomUUID():r.id();
        jdbc.update("INSERT INTO platform.monitoring_targets(id,application_id,code,name,environment,target_type,health_url,metrics_url,enabled,timeout_ms,check_interval_seconds) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET application_id=EXCLUDED.application_id,code=EXCLUDED.code,name=EXCLUDED.name,environment=EXCLUDED.environment,target_type=EXCLUDED.target_type,health_url=EXCLUDED.health_url,metrics_url=EXCLUDED.metrics_url,enabled=EXCLUDED.enabled,timeout_ms=EXCLUDED.timeout_ms,check_interval_seconds=EXCLUDED.check_interval_seconds,updated_at=NOW()",
            id,r.applicationId(),r.code().toUpperCase(Locale.ROOT),r.name(),r.environment().toUpperCase(Locale.ROOT),Objects.toString(r.targetType(),"HTTP").toUpperCase(Locale.ROOT),r.healthUrl(),r.metricsUrl(),r.enabled(),Math.max(500,Math.min(r.timeoutMs(),30000)),Math.max(10,r.checkIntervalSeconds()));
        return jdbc.queryForMap("SELECT * FROM platform.monitoring_targets WHERE id=?",id);
    }

    public Map<String,Object> check(UUID id){
        Map<String,Object> target=jdbc.queryForMap("SELECT * FROM platform.monitoring_targets WHERE id=? AND enabled=true",id);
        long start=System.nanoTime(); String status="DOWN"; Integer httpStatus=null; String message;
        try{
            String url=Objects.toString(target.get("health_url"),"");
            if(url.isBlank()) throw new IllegalStateException("Objetivo sin health_url");
            HttpURLConnection connection=(HttpURLConnection) URI.create(url).toURL().openConnection();
            int timeout=((Number)target.get("timeout_ms")).intValue();
            connection.setConnectTimeout(timeout);connection.setReadTimeout(timeout);connection.setRequestMethod("GET");
            httpStatus=connection.getResponseCode(); status=httpStatus>=200&&httpStatus<400?"UP":"DOWN"; message="HTTP "+httpStatus;
        }catch(Exception ex){message=Objects.toString(ex.getMessage(),"Error de verificación");}
        long elapsed=(System.nanoTime()-start)/1_000_000;
        UUID checkId=UUID.randomUUID();
        jdbc.update("INSERT INTO platform.monitoring_checks(id,target_id,status,http_status,response_time_ms,message) VALUES(?,?,?,?,?,?)",checkId,id,status,httpStatus,elapsed,message);
        return jdbc.queryForMap("SELECT c.*,t.code,t.name,t.environment FROM platform.monitoring_checks c JOIN platform.monitoring_targets t ON t.id=c.target_id WHERE c.id=?",checkId);
    }

    public record Request(UUID id,UUID applicationId,String code,String name,String environment,String targetType,String healthUrl,String metricsUrl,boolean enabled,int timeoutMs,int checkIntervalSeconds){}
}