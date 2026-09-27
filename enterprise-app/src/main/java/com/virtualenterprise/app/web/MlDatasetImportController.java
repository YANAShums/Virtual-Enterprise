package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.AppException;
import com.virtualenterprise.app.service.MlDatasetImportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class MlDatasetImportController {
    private static final String CSV_HEADER = "employee_snapshot_id,employee_id,snapshot_date,tenure_months,employment_rate,department_code,position_code,active_projects_count,active_tasks_count,overdue_tasks_30d,completed_tasks_90d,avg_task_delay_days_90d,avg_workload_30d,avg_workload_90d,overload_days_30d,overload_days_90d,absence_days_90d,task_reassignments_90d,project_changes_180d,attrition_within_90d\n";
    private final MlDatasetImportService service;

    public MlDatasetImportController(MlDatasetImportService service) { this.service = service; }

    @GetMapping("/admin/ml")
    String page() { return "admin/ml-import"; }

    @PostMapping("/admin/ml/import")
    String importDataset(@RequestParam("file") MultipartFile file, Model model) {
        try {
            model.addAttribute("result", service.importCsv(file));
        } catch (AppException ex) {
            model.addAttribute("formError", ex.getMessage());
        }
        return "admin/ml-import";
    }

    @GetMapping("/admin/ml/template.csv")
    ResponseEntity<byte[]> template() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("flowstaff-ml-template.csv").build());
        return ResponseEntity.ok().headers(headers).body(CSV_HEADER.getBytes(StandardCharsets.UTF_8));
    }
}
