package com.virtualenterprise.app.service;

import com.virtualenterprise.app.repository.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Component
public class TaskMaintenanceJob {
    private final TaskRepository taskRepository;
    public TaskMaintenanceJob(TaskRepository taskRepository) { this.taskRepository = taskRepository; }
    @Scheduled(initialDelay = 5000, fixedDelay = 3600000)
    @Transactional
    public void updateOverdueFlags() {
        taskRepository.findOverdueCandidates(LocalDate.now()).forEach(task -> task.setOverdue(true));
    }
}
