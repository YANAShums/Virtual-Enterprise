package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskViewServiceTest {
    private final TaskRepository tasks=mock(TaskRepository.class);
    private final ProjectRepository projects=mock(ProjectRepository.class);
    private final TaskStatusRepository statuses=mock(TaskStatusRepository.class);
    private final TaskViewService service=new TaskViewService(tasks,projects,statuses);

    private Task task(String title, String statusCode, String username, boolean overdue) {
        Task task=new Task(); task.setTitle(title); task.setTaskNumber("TEST-1"); task.setOverdue(overdue);
        Project project=new Project(); project.setName("Проект"); task.setProject(project);
        TaskStatus status=mock(TaskStatus.class); when(status.getCode()).thenReturn(statusCode);task.setStatus(status);
        if(username!=null) {
            Employee employee=mock(Employee.class); AppUser user=mock(AppUser.class);
            when(user.getUsername()).thenReturn(username);when(employee.getUser()).thenReturn(user);
            when(employee.getFullName()).thenReturn("Иванов Иван");task.setAssignee(employee);
        }
        return task;
    }

    @Test
    void mineAndOverdueAndStatus_areCombinedWithoutUnassignedTasks() {
        Task expected=task("Форма","PLANNED","manager",true);
        var source=List.of(expected,
                task("Форма","PLANNED","other",true),task("Форма","PLANNED",null,true),
                task("Форма","DONE","manager",false));
        when(tasks.findAllByOrderByStatusColumnOrderAscKanbanOrderAsc()).thenReturn(source);
        assertEquals(List.of(expected),service.list("",null,"PLANNED",true,true,"manager").get("tasks"));
    }

    @Test
    void projectSearch_isCaseInsensitiveAndSupportsAssignee() {
        Task expected=task("Авторизация","PLANNED","manager",false);
        when(tasks.findByProjectIdOrderByStatusColumnOrderAscKanbanOrderAsc(7L)).thenReturn(List.of(expected));
        assertEquals(List.of(expected),service.list(" ИВАНОВ ",7L,null,false,false,"manager").get("tasks"));
        assertEquals(List.of(),service.list("несуществующая",7L,null,false,false,"manager").get("tasks"));
        verify(tasks,never()).findAllByOrderByStatusColumnOrderAscKanbanOrderAsc();
    }
}
