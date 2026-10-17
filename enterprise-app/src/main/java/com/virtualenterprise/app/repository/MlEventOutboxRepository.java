package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.Employee;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Repository
public class MlEventOutboxRepository {
    private final JdbcTemplate jdbc;
    public MlEventOutboxRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueue(Employee employee) {
        jdbc.update("INSERT INTO ml_event_outbox(event_id,employee_id,revision,termination_date,reason_category,cancelled) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID().toString(),employee.getId(),employee.getVersion(),employee.getTerminationDate(),
                employee.getTerminationCategory()==null?"OTHER":employee.getTerminationCategory(),
                !"DISMISSED".equals(employee.getStatus()));
    }
    public List<Map<String,Object>> pending() {
        return jdbc.queryForList("SELECT * FROM ml_event_outbox WHERE delivered_at IS NULL ORDER BY created_at,revision LIMIT 100");
    }
    public void delivered(String id) {
        jdbc.update("UPDATE ml_event_outbox SET delivered_at=CURRENT_TIMESTAMP(6), attempts=attempts+1,last_error=NULL WHERE event_id=?",id);
    }
    public void failed(String id) {
        jdbc.update("UPDATE ml_event_outbox SET attempts=attempts+1,last_error='ML_SERVICE_UNAVAILABLE' WHERE event_id=?",id);
    }
}
