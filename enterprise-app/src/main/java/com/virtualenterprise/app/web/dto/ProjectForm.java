package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class ProjectForm {
    private Long version;
    @NotBlank @Size(max=50) private String code;
    @NotBlank @Size(min=3,max=255) private String name;
    @Size(max=10000) private String description;
    @NotNull private Long managerId;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate plannedStartDate;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate plannedEndDate;
    @DecimalMin("0") private BigDecimal plannedHours;
    @NotBlank @Pattern(regexp="LOW|MEDIUM|HIGH|CRITICAL") private String priority="MEDIUM";
    @NotBlank @Pattern(regexp="PLANNED|ACTIVE|PAUSED|COMPLETED|CANCELLED") private String status="ACTIVE";
    @NotBlank @Pattern(regexp="LOW|MEDIUM|HIGH") private String riskLevel="LOW";
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal progressPercent=BigDecimal.ZERO;
    private List<Long> memberIds=new ArrayList<>();
    public Long getVersion(){return version;} public void setVersion(Long v){version=v;}
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Long getManagerId(){return managerId;} public void setManagerId(Long v){managerId=v;}
    public LocalDate getPlannedStartDate(){return plannedStartDate;} public void setPlannedStartDate(LocalDate v){plannedStartDate=v;}
    public LocalDate getPlannedEndDate(){return plannedEndDate;} public void setPlannedEndDate(LocalDate v){plannedEndDate=v;}
    public BigDecimal getPlannedHours(){return plannedHours;} public void setPlannedHours(BigDecimal v){plannedHours=v;}
    public String getPriority(){return priority;} public void setPriority(String v){priority=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getRiskLevel(){return riskLevel;} public void setRiskLevel(String v){riskLevel=v;}
    public BigDecimal getProgressPercent(){return progressPercent;} public void setProgressPercent(BigDecimal v){progressPercent=v;}
    public List<Long> getMemberIds(){return memberIds;} public void setMemberIds(List<Long> v){memberIds=v==null?new ArrayList<>():v;}
}