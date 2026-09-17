package com.virtualenterprise.app.web.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class ReferenceForm {
     private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }
    @NotBlank @Size(max=50) @Pattern(regexp="[A-Za-z0-9_-]+", message="Код: латиница, цифры, дефис или подчёркивание") private String code;
    public String getCode() { return code; }
    public void setCode(String value) { this.code = value; }
    @NotBlank @Size(max=255) private String name;
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
     private boolean active = true;
    public boolean getActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
}
