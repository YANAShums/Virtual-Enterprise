package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

public class EmployeeForm {
    private Long version;
    @NotBlank @Size(max=50) private String personnelNumber;
    @NotBlank @Size(max=100) private String lastName;
    @NotBlank @Size(max=100) private String firstName;
    @Size(max=100) private String middleName;
    @Email @Size(max=255) private String email;
    @NotNull private Long departmentId;
    @NotNull private Long positionId;
    private Long managerId;
    @NotNull private Long workScheduleId;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)
    private LocalDate hireDate;
    @NotNull @DecimalMin("0.1") @DecimalMax("1.0")
    private BigDecimal employmentRate = BigDecimal.ONE;
    @NotBlank @Pattern(regexp="ACTIVE|INACTIVE|DISMISSED")
    private String status = "ACTIVE";
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)
    private LocalDate terminationDate;
    @Size(max=500)
    private String terminationReason;
    @Pattern(regexp="VOLUNTARY|INVOLUNTARY|CONTRACT_END|OTHER")
    private String terminationCategory="OTHER";
    public String getTerminationCategory(){return terminationCategory;}
    public void setTerminationCategory(String value){terminationCategory=value;}
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)
    private LocalDate changeEffectiveDate;
    @Size(max=500)
    private String changeReason;

    @AssertTrue(message="Для уволенного сотрудника укажите дату и причину увольнения")
    public boolean isTerminationDetailsValid() {
        return !"DISMISSED".equals(status)
                || (terminationDate != null && terminationReason != null && !terminationReason.isBlank());
    }

    public Long getVersion(){return version;} public void setVersion(Long v){version=v;}
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
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public LocalDate getTerminationDate(){return terminationDate;} public void setTerminationDate(LocalDate v){terminationDate=v;}
    public String getTerminationReason(){return terminationReason;} public void setTerminationReason(String v){terminationReason=v;}
    public LocalDate getChangeEffectiveDate(){return changeEffectiveDate;} public void setChangeEffectiveDate(LocalDate v){changeEffectiveDate=v;}
    public String getChangeReason(){return changeReason;} public void setChangeReason(String v){changeReason=v;}
}
