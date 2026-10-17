package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.MlDailyWorkflowService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class MlDailyWorkflowController {
    private final MlDailyWorkflowService workflow;
    public MlDailyWorkflowController(MlDailyWorkflowService workflow) { this.workflow=workflow; }
    @PostMapping("/admin/ml/models/daily")
    String daily(RedirectAttributes flash) {
        try { workflow.runDaily(); flash.addFlashAttribute("success","Кадровые события доставлены, снимки собраны, разметка обновлена."); }
        catch(RuntimeException ex) { flash.addFlashAttribute("formError","Обработка не завершена. События сохранены для повторной доставки."); }
        return "redirect:/admin/ml/models";
    }
}
