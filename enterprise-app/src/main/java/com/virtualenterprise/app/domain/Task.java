package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "project_id")
    private Project project;
    @Column(nullable = false, length = 50)
    private String taskNumber;
    @Column(nullable = false)
    private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "creator_user_id")
    private AppUser creator;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignee_employee_id")
    private Employee assignee;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assignee_department_id")
    private Department assigneeDepartment;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "status_id")
    private TaskStatus status;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "priority_id")
    private TaskPriority priority;
    private LocalDate plannedStartDate;
    @Column(nullable = false)
    private LocalDate plannedEndDate;
    private LocalDateTime actualStartAt;
    private LocalDateTime actualEndAt;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal plannedHours;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal actualHours = BigDecimal.ZERO;
    @Column(nullable = false, length = 20)
    private String distributionMode = "EQUAL";
    @Column(nullable = false)
    private boolean overdue;
    @Column(nullable = false)
    private boolean overloadConfirmed;
    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal kanbanOrder = BigDecimal.ZERO;
    @Version private Long version;

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getTaskNumber() { return taskNumber; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public AppUser getCreator() { return creator; }
    public Employee getAssignee() { return assignee; }
    public Department getAssigneeDepartment() { return assigneeDepartment; }
    public TaskStatus getStatus() { return status; }
    public TaskPriority getPriority() { return priority; }
    public LocalDate getPlannedStartDate() { return plannedStartDate; }
    public LocalDate getPlannedEndDate() { return plannedEndDate; }
    public LocalDateTime getActualStartAt() { return actualStartAt; }
    public LocalDateTime getActualEndAt() { return actualEndAt; }
    public BigDecimal getPlannedHours() { return plannedHours; }
    public BigDecimal getActualHours() { return actualHours; }
    public boolean isOverdue() { return overdue; }
    public boolean isOverloadConfirmed() { return overloadConfirmed; }
    public BigDecimal getKanbanOrder() { return kanbanOrder; }
    public Long getVersion() { return version; }

    public void setProject(Project project) { this.project = project; }
    public void setTaskNumber(String taskNumber) { this.taskNumber = taskNumber; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setCreator(AppUser creator) { this.creator = creator; }
    public void setAssignee(Employee assignee) { this.assignee = assignee; }
    public void setAssigneeDepartment(Department assigneeDepartment) { this.assigneeDepartment = assigneeDepartment; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public void setPlannedStartDate(LocalDate plannedStartDate) { this.plannedStartDate = plannedStartDate; }
    public void setPlannedEndDate(LocalDate plannedEndDate) { this.plannedEndDate = plannedEndDate; }
    public void setPlannedHours(BigDecimal plannedHours) { this.plannedHours = plannedHours; }
    public void setActualStartAt(LocalDateTime actualStartAt) { this.actualStartAt = actualStartAt; }
    public void setActualEndAt(LocalDateTime actualEndAt) { this.actualEndAt = actualEndAt; }
    public void setOverdue(boolean overdue) { this.overdue = overdue; }
    public void setOverloadConfirmed(boolean overloadConfirmed) { this.overloadConfirmed = overloadConfirmed; }
    public void setKanbanOrder(BigDecimal kanbanOrder) { this.kanbanOrder = kanbanOrder; }
}
