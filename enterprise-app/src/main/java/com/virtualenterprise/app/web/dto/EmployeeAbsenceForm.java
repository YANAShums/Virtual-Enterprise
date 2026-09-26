package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

public class EmployeeAbsenceForm {
    private Long version;
    @NotBlank @Pattern(regexp="VACATION|SICK_LEAVE|BUSINESS_TRIP|UNPAID_LEAVE|OTHER")
    private String absenceType="VACATION";
    @NotBlank @Pattern(regexp="REQUESTED|APPROVED|REJECTED|CANCELLED")
    private String status="APPROVED";
    @NotNull @DateTimeFormat(pattern="yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startAt;
    @NotNull @DateTimeFormat(pattern="yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endAt;
    private boolean allDay=true;
    @Size(max=500) private String reason;
    public Long getVersion(){return version;} public void setVersion(Long v){version=v;}
    public String getAbsenceType(){return absenceType;} public void setAbsenceType(String v){absenceType=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public LocalDateTime getStartAt(){return startAt;} public void setStartAt(LocalDateTime v){startAt=v;}
    public LocalDateTime getEndAt(){return endAt;} public void setEndAt(LocalDateTime v){endAt=v;}
    public boolean isAllDay(){return allDay;} public void setAllDay(boolean v){allDay=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
}