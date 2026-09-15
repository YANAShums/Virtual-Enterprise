package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ProjectForm {
    @NotBlank @Size(max=50) private String code;
    @NotBlank @Size(min=3,max=255) private String name;
    @Size(max=10000) private String description;
    @NotNull private Long managerId;
    @NotNull @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private LocalDate plannedStartDate;
    @NotNull @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private LocalDate plannedEndDate;
    @DecimalMin("0") private BigDecimal plannedHours;
    private String priority="MEDIUM";
    private List<Long> memberIds=new ArrayList<>();
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Long getManagerId(){return managerId;} public void setManagerId(Long v){managerId=v;}
    public LocalDate getPlannedStartDate(){return plannedStartDate;} public void setPlannedStartDate(LocalDate v){plannedStartDate=v;}
    public LocalDate getPlannedEndDate(){return plannedEndDate;} public void setPlannedEndDate(LocalDate v){plannedEndDate=v;}
    public BigDecimal getPlannedHours(){return plannedHours;} public void setPlannedHours(BigDecimal v){plannedHours=v;}
    public String getPriority(){return priority;} public void setPriority(String v){priority=v;}
    public List<Long> getMemberIds(){return memberIds;} public void setMemberIds(List<Long> v){memberIds=v==null?new ArrayList<>():v;}
}
