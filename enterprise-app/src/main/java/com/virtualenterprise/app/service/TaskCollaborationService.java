package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.security.CurrentUserService;
import com.virtualenterprise.app.service.admin.AdminRules;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class TaskCollaborationService {
    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final TaskChecklistItemRepository checklist;
    private final CurrentUserService currentUser;
    private final EmployeeRepository employees;
    private final AuditTrailService audit;
    public TaskCollaborationService(TaskRepository tasks,TaskCommentRepository comments,
                                    TaskChecklistItemRepository checklist,CurrentUserService currentUser,
                                    EmployeeRepository employees,AuditTrailService audit){
        this.tasks=tasks;this.comments=comments;this.checklist=checklist;
        this.currentUser=currentUser;this.employees=employees;this.audit=audit;
    }

    @org.springframework.security.access.prepost.PreAuthorize("@accessScopeService.canContributeTask(#taskId)")
    public Long addComment(Long taskId,String body){
        Task task=requireTask(taskId);AppUser user=currentUser.requireCurrentUser();
        AdminRules.check(body!=null&&!body.isBlank(),"Комментарий не может быть пустым");
        TaskComment comment=new TaskComment();comment.setTask(task);comment.setAuthor(user);comment.setBody(body.trim());
        comments.saveAndFlush(comment);
        audit.record("TASK_COMMENT_ADDED","TASK_COMMENT",comment.getId(),null,Map.of("taskId",taskId,"body",comment.getBody()));
        return comment.getId();
    }

    @org.springframework.security.access.prepost.PreAuthorize("@accessScopeService.canManageTask(#taskId)")
    public Long addChecklistItem(Long taskId,String title){
        Task task=requireTask(taskId);AppUser user=currentUser.requireCurrentUser();
        AdminRules.check(title!=null&&!title.isBlank(),"Пункт чек-листа не может быть пустым");
        TaskChecklistItem item=new TaskChecklistItem();item.setTask(task);item.setTitle(title.trim());
        item.setCreatedBy(user);item.setItemOrder((int)checklist.countByTaskId(taskId)+1);item.setCompleted(false);
        checklist.saveAndFlush(item);
        audit.record("TASK_CHECKLIST_ADDED","TASK_CHECKLIST_ITEM",item.getId(),null,Map.of("taskId",taskId,"title",item.getTitle()));
        return item.getId();
    }

    @org.springframework.security.access.prepost.PreAuthorize("@accessScopeService.canContributeTask(#taskId)")
    public void toggleChecklistItem(Long taskId,Long itemId,Long version){
        Task task=requireTask(taskId);TaskChecklistItem item=requireItem(taskId,itemId);
        AdminRules.version(item.getVersion(),version);AppUser user=currentUser.requireCurrentUser();
        ensureEmployeeCanChangeOwnTask(task,user);
        boolean next=!item.isCompleted();item.setCompleted(next);item.setCompletedBy(next?user:null);
        item.setCompletedAt(next?LocalDateTime.now():null);checklist.saveAndFlush(item);
        audit.record("TASK_CHECKLIST_TOGGLED","TASK_CHECKLIST_ITEM",item.getId(),
                Map.of("completed",!next),Map.of("completed",next,"taskId",taskId));
    }

    @org.springframework.security.access.prepost.PreAuthorize("@accessScopeService.canManageTask(#taskId)")
    public void deleteChecklistItem(Long taskId,Long itemId,Long version){
        requireTask(taskId);TaskChecklistItem item=requireItem(taskId,itemId);AdminRules.version(item.getVersion(),version);
        audit.record("TASK_CHECKLIST_REMOVED","TASK_CHECKLIST_ITEM",item.getId(),Map.of("taskId",taskId,"title",item.getTitle()),null);
        checklist.delete(item);checklist.flush();
    }

    private void ensureEmployeeCanChangeOwnTask(Task task,AppUser user){
        boolean onlyEmployee=user.getRoles().stream().anyMatch(role -> role.isActive() && "EMPLOYEE".equals(role.getCode())) && user.getRoles().stream().noneMatch(role -> role.isActive() && Set.of("ADMIN","ENTERPRISE_MANAGER","DEPARTMENT_MANAGER","PROJECT_MANAGER").contains(role.getCode()));
        if(!onlyEmployee)return;
        Employee employee=employees.findByUserId(user.getId()).orElse(null);
        if(employee==null||task.getAssignee()==null||!Objects.equals(task.getAssignee().getId(),employee.getId()))
            throw new AppException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Сотрудник может менять чек-лист только своей задачи");
    }
    private Task requireTask(Long id){return tasks.findById(id).orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,"ENTITY_NOT_FOUND","Задача не найдена"));}
    private TaskChecklistItem requireItem(Long taskId,Long id){
        TaskChecklistItem item=checklist.findById(id).orElseThrow(AdminRules::missing);
        AdminRules.check(Objects.equals(item.getTask().getId(),taskId),"Пункт относится к другой задаче");return item;
    }
}