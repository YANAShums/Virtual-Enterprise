package com.virtualenterprise.app.web.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class UserAdminForm {
     private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long value) { this.version = value; }
    @NotBlank @Size(max=100) @Pattern(regexp="[A-Za-z0-9_.-]+", message="Логин: латиница, цифры, точка, дефис или подчёркивание") private String username;
    public String getUsername() { return username; }
    public void setUsername(String value) { this.username = value; }
    @NotBlank @Email @Size(max=255) private String email;
    public String getEmail() { return email; }
    public void setEmail(String value) { this.email = value; }
    @Size(max=72) private String password;
    public String getPassword() { return password; }
    public void setPassword(String value) { this.password = value; }
    @NotBlank @Pattern(regexp="ACTIVE|BLOCKED|ARCHIVED") private String status = "ACTIVE";
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    @NotEmpty private java.util.Set<Long> roleIds = new java.util.LinkedHashSet<>();
    public java.util.Set<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(java.util.Set<Long> value) { this.roleIds = value; }
     private Long employeeId;
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long value) { this.employeeId = value; }
}
