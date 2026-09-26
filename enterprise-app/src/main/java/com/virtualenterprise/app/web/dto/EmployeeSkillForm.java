package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

public class EmployeeSkillForm {
    private Long version;
    @NotBlank @Size(max=120) private String skillName;
    @NotNull @Min(1) @Max(5) private Integer proficiencyLevel=3;
    @DecimalMin("0.0") @DecimalMax("99.0") private BigDecimal yearsExperience;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate lastUsedDate;
    public Long getVersion(){return version;} public void setVersion(Long v){version=v;}
    public String getSkillName(){return skillName;} public void setSkillName(String v){skillName=v;}
    public Integer getProficiencyLevel(){return proficiencyLevel;} public void setProficiencyLevel(Integer v){proficiencyLevel=v;}
    public BigDecimal getYearsExperience(){return yearsExperience;} public void setYearsExperience(BigDecimal v){yearsExperience=v;}
    public LocalDate getLastUsedDate(){return lastUsedDate;} public void setLastUsedDate(LocalDate v){lastUsedDate=v;}
}