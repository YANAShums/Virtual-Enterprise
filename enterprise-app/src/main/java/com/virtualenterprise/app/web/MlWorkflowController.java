package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.MlWorkflowService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.nio.charset.StandardCharsets;

@Controller
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/admin/ml/models")
public class MlWorkflowController {
    private final MlWorkflowService service;
    public MlWorkflowController(MlWorkflowService service) { this.service = service; }
    @GetMapping String page(Model model) { model.addAttribute("workflow",service.overview()); return "admin/ml-models"; }
    @PostMapping("/train") String train(RedirectAttributes flash) {
        var result = service.train(); flash.addFlashAttribute("success","Создана версия " + result.version() + ". Статус: " + result.status());
        return "redirect:/admin/ml/models";
    }
    @PostMapping("/{version}/activate") String activate(@PathVariable String version, RedirectAttributes flash) {
        var result = service.activate(version); flash.addFlashAttribute("success","Активирована модель " + result.version());
        return "redirect:/admin/ml/models";
    }
    @PostMapping("/import") String upload(@RequestParam("file") MultipartFile file, RedirectAttributes flash) {
        var result = service.importCsv(file); flash.addFlashAttribute("importResult",result); return "redirect:/admin/ml/models";
    }
    @PostMapping("/sync") String sync(RedirectAttributes flash) {
        var result = service.synchronize(); flash.addFlashAttribute("success","Синхронизация завершена. Прочитано снимков: " + result.get("read"));
        return "redirect:/admin/ml/models";
    }
    @GetMapping("/template.csv") ResponseEntity<byte[]> template() {
        String header = "employee_snapshot_id,employee_id,snapshot_date,attrition_within_90d,tenure_months,employment_rate,active_projects_count,active_tasks_count,overdue_tasks_30d,completed_tasks_90d,avg_task_delay_days_90d,avg_workload_30d,avg_workload_90d,overload_days_30d,overload_days_90d,absence_days_90d,task_reassignments_90d,project_changes_180d,workload_days_available_30d,workload_days_available_90d,department_code,position_code\n";
        header=header.strip()+",data_origin\n";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("Content-Disposition","attachment; filename=flowstaff-ml-v2.csv").body(header.getBytes(StandardCharsets.UTF_8));
    }
}
