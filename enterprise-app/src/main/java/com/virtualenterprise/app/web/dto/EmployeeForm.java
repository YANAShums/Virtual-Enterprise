package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class EmployeeForm {
    @NotBlank @Size(max=50) private String personnelNumber;
    @NotBlank @Size(max=100) private String lastName;
    @NotBlank @Size(max=100) private String firstName;
    @Size(max=100) private String middleName;
    @Email @Size(max=255) private String email;
    @NotNull private Long departmentId;
    @NotNull private Long positionId;
    private Long managerId;
    @NotNull private Long workScheduleId;
    @NotNull @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private LocalDate hireDate;
    @NotNull @DecimalMin("0.1") @DecimalMax("1.0") private BigDecimal employmentRate = BigDecimal.ONE;
    public String getPersonnelNumber(){return personnelNumber;} public void setPersonnelNumber(String v){personnelNumber=v;}
    public String getLastName(){return lastName;} public void setLastName(String v){lastName=v;}
    public String getFirstName(){return firstName;} public void setFirstName(String v){firstName=v;}
    public String getMiddleName(){return middleName;} public void setMiddleName(String v){middleName=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public Long getDepartmentId(){return departmentId;} public void setDepartmentId(Long v){departmentId=v;}
    public Long getPositionId(){return positionId;} public void setPositionId(Long v){positionId=v;}
    public Long getManagerId(){return managerId;} public void setManagerId(Long v){managerId=v;}
    public Long getWorkScheduleId(){return workScheduleId;} public void setWorkScheduleId(Long v){workScheduleId=v;}
    public LocalDate getHireDate(){return hireDate;} public void setHireDate(LocalDate v){hireDate=v;}
    public BigDecimal getEmploymentRate(){return employmentRate;} public void setEmploymentRate(BigDecimal v){employmentRate=v;}
}
