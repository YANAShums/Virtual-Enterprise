package com.virtualenterprise.app.web.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class PositionAdminForm extends ReferenceForm {
    @Size(max=50) private String grade;
    public String getGrade() { return grade; }
    public void setGrade(String value) { this.grade = value; }
    @Size(max=5000) private String description;
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
}
