package com.virtualenterprise.app.web.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class ScheduleAdminForm extends ReferenceForm {
    @NotBlank @Size(max=64) private String timezone = "Europe/Minsk";
    public String getTimezone() { return timezone; }
    public void setTimezone(String value) { this.timezone = value; }
    @NotNull @Size(min=7,max=7) private java.util.List<@NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer=2, fraction=2) BigDecimal> hours = new java.util.ArrayList<>(java.util.List.of(new BigDecimal("8"),new BigDecimal("8"),new BigDecimal("8"),new BigDecimal("8"),new BigDecimal("8"),BigDecimal.ZERO,BigDecimal.ZERO));
    public java.util.List<BigDecimal> getHours() { return hours; }
    public void setHours(java.util.List<BigDecimal> value) { this.hours = value; }
}
