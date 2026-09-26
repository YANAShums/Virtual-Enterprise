package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.Task;
import com.virtualenterprise.app.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class TaskViewService {
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final TaskStatusRepository statuses;
    private final TaskCommentRepository comments;
    private final TaskChecklistItemRepository checklist;
    public TaskViewService(TaskRepository tasks,ProjectRepository projects,TaskStatusRepository statuses,
                           TaskCommentRepository comments,TaskChecklistItemRepository checklist){
        this.tasks=tasks;this.projects=projects;this.statuses=statuses;this.comments=comments;this.checklist=checklist;
    }

    public Map<String,Object> list(String q,Long projectId,String status,boolean mine,boolean overdue,String username){
        String query=q==null?"":q.trim().toLowerCase(Locale.ROOT);
        var source=projectId==null?tasks.findAllByOrderByStatusColumnOrderAscKanbanOrderAsc():tasks.findByProjectIdOrderByStatusColumnOrderAscKanbanOrderAsc(projectId);
        var filtered=source.stream()
                .filter(task -> status==null||status.isBlank()||status.equals(task.getStatus().getCode()))
                .filter(task -> !overdue||task.isOverdue())
                .filter(task -> !mine||(task.getAssignee()!=null&&task.getAssignee().getUser()!=null&&username.equals(task.getAssignee().getUser().getUsername())))
                .filter(task -> query.isBlank()||searchableText(task).contains(query)).toList();
        Map<String,Object> view=new HashMap<>();
        view.put("tasks",filtered);view.put("projects",projects.findByActiveTrueOrderByNameAsc());
        view.put("statuses",statuses.findByActiveTrueOrderByColumnOrderAsc());view.put("q",q==null?"":q);
        view.put("projectId",projectId);view.put("status",status);view.put("mine",mine);view.put("overdue",overdue);
        return view;
    }

    public Optional<Map<String,Object>> detail(Long id){
        return tasks.findById(id).map(task -> {
            Map<String,Object> view=new HashMap<>();
            view.put("task",task);view.put("statuses",statuses.findByActiveTrueOrderByColumnOrderAsc());
            view.put("comments",comments.findByTaskIdOrderByCreatedAtAscIdAsc(id));
            view.put("checklist",checklist.findByTaskIdOrderByItemOrderAscIdAsc(id));
            return view;
        });
    }

    private String searchableText(Task task){
        return String.join(" ",Objects.toString(task.getTitle(),""),Objects.toString(task.getTaskNumber(),""),
                Objects.toString(task.getProject().getName(),""),
                task.getAssignee()==null?"":task.getAssignee().getFullName()).toLowerCase(Locale.ROOT);
    }
}