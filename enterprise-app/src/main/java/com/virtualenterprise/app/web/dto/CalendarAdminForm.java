package com.virtualenterprise.app.web.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class CalendarAdminForm {
     private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }
    @NotNull @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) private java.time.LocalDate calendarDate;
    public java.time.LocalDate getCalendarDate() { return calendarDate; }
    public void setCalendarDate(java.time.LocalDate value) { this.calendarDate = value; }
    @NotBlank @Pattern(regexp="WORKDAY|SHORTENED|HOLIDAY|WEEKEND") private String dayType = "HOLIDAY";
    public String getDayType() { return dayType; }
    public void setDayType(String value) { this.dayType = value; }
    @NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer=2, fraction=2) private java.math.BigDecimal workingHoursOverride = BigDecimal.ZERO;
    public java.math.BigDecimal getWorkingHoursOverride() { return workingHoursOverride; }
    public void setWorkingHoursOverride(java.math.BigDecimal value) { this.workingHoursOverride = value; }
    @Size(max=255) private String description;
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
}
