package com.virtualenterprise.app.web.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class DepartmentAdminForm extends ReferenceForm {
     private Long parentId;
    public Long getParentId() { return parentId; }
    public void setParentId(Long value) { this.parentId = value; }
     private Long managerId;
    public Long getManagerId() { return managerId; }
    public void setManagerId(Long value) { this.managerId = value; }
}
