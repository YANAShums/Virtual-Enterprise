package com.virtualenterprise.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualenterprise.app.security.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditTrailService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUser;
    public AuditTrailService(JdbcTemplate jdbc,ObjectMapper objectMapper,CurrentUserService currentUser){
        this.jdbc=jdbc;this.objectMapper=objectMapper;this.currentUser=currentUser;
    }
    public void record(String action,String entityType,Long entityId,Object oldValues,Object newValues){
        Long userId=currentUser.requireCurrentUser().getId();
        jdbc.update("INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,old_values,new_values,created_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP(6))",
                userId,action,entityType,entityId,oldValues==null?null:json(oldValues),newValues==null?null:json(newValues));
    }
    public void event(String aggregateType,Long aggregateId,String eventType,Object payload){
        jdbc.update("INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,event_type,payload,status,attempt_count,created_at) VALUES (?,?,?,?,?,'NEW',0,CURRENT_TIMESTAMP(6))",
                UUID.randomUUID().toString(),aggregateType,aggregateId,eventType,json(payload));
    }
    private String json(Object value){
        try{return objectMapper.writeValueAsString(value);}
        catch(JsonProcessingException ex){throw new IllegalStateException("Не удалось сериализовать запись аудита",ex);}
    }
}