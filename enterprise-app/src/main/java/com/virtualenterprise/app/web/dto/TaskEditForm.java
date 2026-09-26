package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TaskEditForm {
    private Long version;
    private Long projectId;
    @NotBlank @Size(min=3,max=255) private String title;
    @Size(max=10000) private String description;
    @NotNull private Long assigneeEmployeeId;
    @NotBlank private String priorityCode;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate plannedStartDate;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate plannedEndDate;
    @NotNull @DecimalMin("0.25") @DecimalMax("10000") private BigDecimal plannedHours;
    public Long getVersion(){return version;} public void setVersion(Long v){version=v;}
    public Long getProjectId(){return projectId;} public void setProjectId(Long v){projectId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Long getAssigneeEmployeeId(){return assigneeEmployeeId;} public void setAssigneeEmployeeId(Long v){assigneeEmployeeId=v;}
    public String getPriorityCode(){return priorityCode;} public void setPriorityCode(String v){priorityCode=v;}
    public LocalDate getPlannedStartDate(){return plannedStartDate;} public void setPlannedStartDate(LocalDate v){plannedStartDate=v;}
    public LocalDate getPlannedEndDate(){return plannedEndDate;} public void setPlannedEndDate(LocalDate v){plannedEndDate=v;}
    public BigDecimal getPlannedHours(){return plannedHours;} public void setPlannedHours(BigDecimal v){plannedHours=v;}
}