package com.virtualenterprise.app.service;

import com.virtualenterprise.app.repository.*;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

/** Read models for server-rendered employee pages; does not modify personnel data. */
@Service
public class EmployeeViewService {
    private final EmployeeRepository employees;
    private final TaskRepository tasks;
    private final EmployeeAbsenceRepository absences;
    private final EmployeeSkillRepository skills;
    private final EmployeeAssignmentHistoryRepository assignments;
    private final EmployeeStatusHistoryRepository statuses;
    private final WorkloadContextService workload;

    public EmployeeViewService(EmployeeRepository employees,TaskRepository tasks,EmployeeAbsenceRepository absences,
                               EmployeeSkillRepository skills,EmployeeAssignmentHistoryRepository assignments,
                               EmployeeStatusHistoryRepository statuses,WorkloadContextService workload) {
        this.employees=employees;this.tasks=tasks;this.absences=absences;this.skills=skills;
        this.assignments=assignments;this.statuses=statuses;this.workload=workload;
    }

    public Optional<Map<String,Object>> detail(Long id){return detail(id,false);}

    public Optional<Map<String,Object>> detail(Long id,boolean includeInactive) {
        return employees.findById(id).filter(e -> includeInactive || e.isActive()).map(employee -> {
            Map<String,Object> view=new HashMap<>();
            view.put("employee",employee);
            view.put("tasks",tasks.findByAssigneeIdAndStatusCodeIn(id,List.of("PLANNED","IN_PROGRESS","REVIEW","DONE")));
            view.put("absences",absences.findApprovedOverlapping(id,LocalDate.now().atStartOfDay(),LocalDate.now().plusMonths(3).atTime(23,59)));
            view.put("absenceHistory",absences.findByEmployeeIdOrderByStartAtDesc(id));
            view.put("skills",skills.findByEmployeeIdAndActiveTrueOrderBySkillNameAsc(id));
            view.put("assignmentHistory",assignments.findByEmployeeIdOrderByEffectiveFromDescIdDesc(id));
            view.put("statusHistory",statuses.findByEmployeeIdOrderByEffectiveDateDescIdDesc(id));
            if(employee.isActive()) {
                try{view.put("loads",workload.currentForEmployees(List.of(employee),LocalDate.now(),LocalDate.now().plusDays(6)).candidates());}
                catch(Exception ex){view.put("loads",List.of());view.put("workloadError","Сервис загрузки временно недоступен. Попробуйте позже.");}
            } else view.put("loads",List.of());
            return view;
        });
    }
}