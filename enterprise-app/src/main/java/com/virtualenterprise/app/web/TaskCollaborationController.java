package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.TaskCollaborationService;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/tasks/{taskId:[0-9]+}")
@Validated
public class TaskCollaborationController {
    private final TaskCollaborationService service;
    public TaskCollaborationController(TaskCollaborationService service){this.service=service;}

    @PostMapping("/comments")
    @PreAuthorize("@accessScopeService.canContributeTask(#taskId)")
    String comment(@PathVariable Long taskId,@RequestParam @Size(min=1,max=4000) String body){
        service.addComment(taskId,body);return "redirect:/tasks/"+taskId+"#taskComments";
    }
    @PostMapping("/checklist")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER','PROJECT_MANAGER')")
    String addChecklist(@PathVariable Long taskId,@RequestParam @Size(min=1,max=500) String title){
        service.addChecklistItem(taskId,title);return "redirect:/tasks/"+taskId+"#taskChecklist";
    }
    @PostMapping("/checklist/{itemId:[0-9]+}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER','PROJECT_MANAGER','EMPLOYEE')")
    String toggle(@PathVariable Long taskId,@PathVariable Long itemId,@RequestParam Long version){
        service.toggleChecklistItem(taskId,itemId,version);return "redirect:/tasks/"+taskId+"#taskChecklist";
    }
    @PostMapping("/checklist/{itemId:[0-9]+}/delete")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER','PROJECT_MANAGER')")
    String delete(@PathVariable Long taskId,@PathVariable Long itemId,@RequestParam Long version){
        service.deleteChecklistItem(taskId,itemId,version);return "redirect:/tasks/"+taskId+"#taskChecklist";
    }
}