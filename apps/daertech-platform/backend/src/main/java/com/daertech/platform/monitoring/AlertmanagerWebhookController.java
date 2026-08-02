package com.daertech.platform.monitoring;

import com.daertech.platform.configuration.TelegramNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/internal/alerts")
public class AlertmanagerWebhookController {
    private final TelegramNotifier notifier;
    private final String webhookToken;

    public AlertmanagerWebhookController(TelegramNotifier notifier,
            @Value("${app.monitoring.alertmanager-webhook-token:CHANGE_ME}") String webhookToken){
        this.notifier=notifier;this.webhookToken=webhookToken;
    }

    @PostMapping("/alertmanager")
    public Map<String,Object> receive(@RequestHeader(name="X-Alert-Token",required=false) String token,
                                      @RequestBody Map<String,Object> payload){
        if(webhookToken.isBlank()||"CHANGE_ME".equals(webhookToken)||!Objects.equals(webhookToken,token))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token de Alertmanager inválido");
        String status=Objects.toString(payload.get("status"),"unknown");
        List<?> alerts=payload.get("alerts") instanceof List<?> list?list:List.of();
        StringBuilder message=new StringBuilder("🚨 Alertmanager ").append(status.toUpperCase()).append('\n');
        for(Object item:alerts){
            if(item instanceof Map<?,?> alert){
                Object labels=alert.get("labels");Object annotations=alert.get("annotations");
                message.append("• ").append(labels instanceof Map<?,?> m?Objects.toString(m.get("alertname"),"Alerta"):"Alerta");
                if(annotations instanceof Map<?,?> m) message.append(": ").append(Objects.toString(m.get("summary"),Objects.toString(m.get("description"),"")));
                message.append('\n');
            }
        }
        notifier.send(message.toString());
        return Map.of("received",true,"alerts",alerts.size());
    }
}