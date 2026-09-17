package com.virtualenterprise.app.web.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class EnterpriseAdminForm {
     private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }
    @NotBlank @Size(max=50) private String code;
    public String getCode() { return code; }
    public void setCode(String value) { this.code = value; }
    @NotBlank @Size(max=255) private String name;
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    @NotBlank @Size(max=64) private String timezone = "Europe/Minsk";
    public String getTimezone() { return timezone; }
    public void setTimezone(String value) { this.timezone = value; }
}
