package com.daertech.platform.deployment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DeploymentService {
    private final JdbcTemplate jdbc;
    private final Path workspaceRoot;
    private final boolean executionEnabled;
    private final int timeoutSeconds;

    public DeploymentService(JdbcTemplate jdbc,
            @Value("${app.deployment.workspace-root:/opt/infra-platform/workspaces}") String workspaceRoot,
            @Value("${app.deployment.execution-enabled:false}") boolean executionEnabled,
            @Value("${app.deployment.command-timeout-seconds:900}") int timeoutSeconds) {
        this.jdbc = jdbc;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.executionEnabled = executionEnabled;
        this.timeoutSeconds = Math.max(30, Math.min(timeoutSeconds, 3600));
    }

    public List<Map<String,Object>> list(UUID applicationId, String environment) {
        StringBuilder sql = new StringBuilder("SELECT d.*, a.code application_code, a.name application_name FROM platform.deployments d JOIN platform.applications a ON a.id=d.application_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (applicationId != null) { sql.append(" AND d.application_id=?"); args.add(applicationId); }
        if (environment != null && !environment.isBlank()) { sql.append(" AND d.environment=?"); args.add(environment.toUpperCase(Locale.ROOT)); }
        sql.append(" ORDER BY d.created_at DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String,Object> detail(UUID id) {
        Map<String,Object> deployment = jdbc.queryForMap("SELECT d.*,a.code application_code,a.name application_name FROM platform.deployments d JOIN platform.applications a ON a.id=d.application_id WHERE d.id=?", id);
        deployment.put("steps", jdbc.queryForList("SELECT * FROM platform.deployment_steps WHERE deployment_id=? ORDER BY step_order", id));
        deployment.put("artifacts", jdbc.queryForList("SELECT * FROM platform.deployment_artifacts WHERE deployment_id=? ORDER BY created_at", id));
        return deployment;
    }

    @Transactional
    public Map<String,Object> create(Request request, String actor) {
        validateRequest(request);
        Map<String,Object> app = jdbc.queryForMap("SELECT * FROM platform.applications WHERE id=? AND active=true", request.applicationId());
        UUID id = UUID.randomUUID();
        String environment = request.environment().toUpperCase(Locale.ROOT);
        String branch = request.branch() == null || request.branch().isBlank() ? Objects.toString(app.get("default_branch"), "main") : request.branch();
        String imageTag = Objects.toString(app.get("code")).toLowerCase(Locale.ROOT) + ":" + request.version();
        UUID previous = jdbc.query("SELECT id FROM platform.deployments WHERE application_id=? AND environment=? AND status='SUCCESS' ORDER BY finished_at DESC LIMIT 1", rs -> rs.next() ? UUID.fromString(rs.getString(1)) : null, request.applicationId(), environment);
        jdbc.update("INSERT INTO platform.deployments(id,application_id,environment,version,git_branch,image_tag,status,previous_deployment_id,requested_by,reason) VALUES (?,?,?,?,?,?,?,?,?,?)",
            id, request.applicationId(), environment, request.version(), branch, imageTag, "PENDING", previous, actor, request.reason());
        return detail(id);
    }

    public Map<String,Object> execute(UUID id) {
        if (!executionEnabled) throw new IllegalStateException("La ejecución real de despliegues está deshabilitada. Active APP_DEPLOYMENT_EXECUTION_ENABLED después de validar Docker y Git.");
        Map<String,Object> d = detail(id);
        if (!Set.of("PENDING","FAILED").contains(Objects.toString(d.get("status")))) throw new IllegalStateException("El despliegue no está en estado ejecutable");
        jdbc.update("UPDATE platform.deployments SET status='RUNNING',started_at=NOW() WHERE id=?", id);
        try {
            Map<String,Object> app = jdbc.queryForMap("SELECT * FROM platform.applications WHERE id=?", d.get("application_id"));
            Path workspace = safeWorkspace(Objects.toString(app.get("code")), id);
            Files.createDirectories(workspace.getParent());
            int order = 1;
            runStep(id, order++, "CHECKOUT", workspace.getParent(), List.of("sh","-lc", checkoutCommand(app, d, workspace)));
            runStep(id, order++, "BUILD_IMAGE", workspace, List.of("docker","build","-f", Objects.toString(app.get("dockerfile_path"),"Dockerfile"), "-t", Objects.toString(d.get("image_tag")), Objects.toString(app.get("build_context"),".")));
            Path compose = workspace.resolve("docker-compose.yml");
            if (!Files.exists(compose)) throw new IllegalStateException("No existe docker-compose.yml en el repositorio");
            runStep(id, order++, "DEPLOY", workspace, List.of("docker","compose","-f",compose.toString(),"up","-d","--remove-orphans"));
            String healthMessage = healthCheck(app, d);
            recordStep(id, order, "HEALTH_CHECK", "SUCCESS", null, healthMessage, 0, OffsetDateTime.now(), OffsetDateTime.now());
            String commit = runAndCapture(workspace, List.of("git","rev-parse","HEAD")).trim();
            jdbc.update("UPDATE platform.deployments SET status='SUCCESS',git_commit=?,health_status='UP',health_message=?,finished_at=NOW() WHERE id=?", commit, healthMessage, id);
        } catch (Exception ex) {
            jdbc.update("UPDATE platform.deployments SET status='FAILED',health_status='DOWN',health_message=?,finished_at=NOW() WHERE id=?", abbreviate(ex.getMessage(), 4000), id);
        }
        return detail(id);
    }

    public Map<String,Object> rollback(UUID id, String actor, String reason) {
        Map<String,Object> current = detail(id);
        Object previousId = current.get("previous_deployment_id");
        if (previousId == null) throw new IllegalStateException("No existe un despliegue previo exitoso para revertir");
        Map<String,Object> previous = detail(UUID.fromString(previousId.toString()));
        Request request = new Request(UUID.fromString(previous.get("application_id").toString()), Objects.toString(previous.get("environment")), Objects.toString(previous.get("version")), Objects.toString(previous.get("git_branch")), "Rollback de " + id + ": " + Objects.toString(reason,"sin motivo"));
        Map<String,Object> rollback = create(request, actor);
        jdbc.update("UPDATE platform.deployments SET previous_deployment_id=? WHERE id=?", id, rollback.get("id"));
        return rollback;
    }

    private void runStep(UUID deploymentId, int order, String name, Path directory, List<String> command) throws Exception {
        OffsetDateTime start = OffsetDateTime.now();
        UUID stepId = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.deployment_steps(id,deployment_id,step_order,step_name,status,command,started_at) VALUES (?,?,?,?,?,?,?)", stepId, deploymentId, order, name, "RUNNING", String.join(" ",command), start);
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line; while ((line=reader.readLine())!=null) { if (output.length()<500000) output.append(line).append('\n'); }
        }
        boolean completed = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        if (!completed) { process.destroyForcibly(); throw new IllegalStateException(name + " excedió el tiempo máximo"); }
        int exit = process.exitValue();
        jdbc.update("UPDATE platform.deployment_steps SET status=?,output=?,finished_at=NOW(),exit_code=? WHERE id=?", exit==0?"SUCCESS":"FAILED", output.toString(), exit, stepId);
        if (exit != 0) throw new IllegalStateException(name + " falló con código " + exit);
    }

    private void recordStep(UUID deploymentId,int order,String name,String status,String command,String output,int exit,OffsetDateTime started,OffsetDateTime finished){
        jdbc.update("INSERT INTO platform.deployment_steps(id,deployment_id,step_order,step_name,status,command,output,started_at,finished_at,exit_code) VALUES (?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),deploymentId,order,name,status,command,output,started,finished,exit);
    }

    private String checkoutCommand(Map<String,Object> app, Map<String,Object> d, Path workspace) {
        String repo = shellQuote(Objects.toString(app.get("git_repository")));
        String branch = shellQuote(Objects.toString(d.get("git_branch"),"main"));
        String path = shellQuote(workspace.toString());
        return "rm -rf " + path + " && git clone --depth 1 --branch " + branch + " " + repo + " " + path;
    }

    private String healthCheck(Map<String,Object> app, Map<String,Object> deployment) throws Exception {
        String endpoint = Objects.toString(app.get("health_endpoint"),"");
        if (endpoint.isBlank()) return "Health check omitido: aplicación sin endpoint";
        String environment = Objects.toString(deployment.get("environment"));
        String publicUrl = jdbc.query("SELECT public_url FROM platform.application_environments WHERE application_id=? AND environment=?", rs -> rs.next()?rs.getString(1):null, app.get("id"), environment);
        if (publicUrl == null || publicUrl.isBlank()) return "Health check omitido: ambiente sin URL pública";
        String url = publicUrl.replaceAll("/$","") + (endpoint.startsWith("/")?endpoint:"/"+endpoint);
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(20000); connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 400) throw new IllegalStateException("Health check HTTP " + status + " en " + url);
        return "Health check HTTP " + status + " en " + url;
    }

    private String runAndCapture(Path directory,List<String> command)throws Exception{Process p=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();String out=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);if(p.waitFor()!=0)throw new IllegalStateException(out);return out;}
    private Path safeWorkspace(String appCode, UUID deploymentId){Path path=workspaceRoot.resolve(appCode.replaceAll("[^A-Za-z0-9._-]","_")).resolve(deploymentId.toString()).normalize();if(!path.startsWith(workspaceRoot))throw new IllegalArgumentException("Ruta de workspace inválida");return path;}
    private void validateRequest(Request r){if(r.applicationId()==null)throw new IllegalArgumentException("applicationId requerido");if(r.environment()==null||r.environment().isBlank())throw new IllegalArgumentException("environment requerido");if(r.version()==null||!r.version().matches("[A-Za-z0-9._-]{1,120}"))throw new IllegalArgumentException("version inválida");}
    private String shellQuote(String value){return "'"+value.replace("'","'\\''")+"'";}
    private String abbreviate(String v,int max){if(v==null)return "Error no especificado";return v.length()<=max?v:v.substring(0,max);}

    public record Request(UUID applicationId, String environment, String version, String branch, String reason) {}
}
