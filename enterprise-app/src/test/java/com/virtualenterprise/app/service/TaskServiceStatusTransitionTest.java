package com.virtualenterprise.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.security.CurrentUserService;
import com.virtualenterprise.app.web.dto.TaskStatusChangeRequest;
import com.virtualenterprise.app.web.dto.TaskReassignRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskServiceStatusTransitionTest {
    private final TaskRepository tasks=mock(TaskRepository.class);
    private final ProjectRepository projects=mock(ProjectRepository.class);
    private final ProjectMemberRepository members=mock(ProjectMemberRepository.class);
    private final EmployeeRepository employees=mock(EmployeeRepository.class);
    private final TaskStatusRepository statuses=mock(TaskStatusRepository.class);
    private final TaskPriorityRepository priorities=mock(TaskPriorityRepository.class);
    private final WorkloadContextService workload=mock(WorkloadContextService.class);
    private final CurrentUserService currentUser=mock(CurrentUserService.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final NotificationService notifications=mock(NotificationService.class);
    private final TaskService service=new TaskService(tasks,projects,members,employees,statuses,priorities,workload,currentUser,jdbc,new ObjectMapper(),notifications);
    private Task task;
    private TaskStatus planned;

    @BeforeEach
    void setUp(){
        planned=status(1L,"PLANNED");task=new Task();ReflectionTestUtils.setField(task,"id",12L);ReflectionTestUtils.setField(task,"version",2L);
        task.setStatus(planned);Employee assignee=new Employee();ReflectionTestUtils.setField(assignee,"id",3L);task.setAssignee(assignee);
        when(tasks.findById(12L)).thenReturn(Optional.of(task));when(tasks.saveAndFlush(task)).thenReturn(task);
        AppUser user=mock(AppUser.class);when(user.getId()).thenReturn(2L);Role role=mock(Role.class);when(role.getCode()).thenReturn("PROJECT_MANAGER");
        when(user.getRoles()).thenReturn(Set.of(role));when(currentUser.requireCurrentUser()).thenReturn(user);
    }

    @Test
    void plannedTaskCanMoveToInProgress(){
        TaskStatus inProgress=status(2L,"IN_PROGRESS");when(statuses.findByCode("IN_PROGRESS")).thenReturn(Optional.of(inProgress));
        Task changed=service.changeStatus(12L,new TaskStatusChangeRequest("IN_PROGRESS",2L,null,BigDecimal.valueOf(1000)));
        assertSame(inProgress,changed.getStatus());assertNotNull(changed.getActualStartAt());verify(tasks).saveAndFlush(task);
    }

    @Test
    void plannedTaskCannotSkipReviewAndMoveToDone(){
        AppException error=assertThrows(AppException.class,
                () -> service.changeStatus(12L,new TaskStatusChangeRequest("DONE",2L,null,null)));
        assertEquals("INVALID_STATUS_TRANSITION",error.getCode());verify(tasks,never()).saveAndFlush(any());
    }

    @Test
    void staleVersionIsRejected(){
        AppException error=assertThrows(AppException.class,
                () -> service.changeStatus(12L,new TaskStatusChangeRequest("IN_PROGRESS",1L,null,null)));
        assertEquals("OPTIMISTIC_LOCK",error.getCode());verifyNoInteractions(statuses);
    }

    @Test
    void staleVersionIsRejectedBeforeReassignmentCalculation(){
        AppException error=assertThrows(AppException.class,
                () -> service.reassign(12L,new TaskReassignRequest(4L,1L,UUID.randomUUID(),false,null)));
        assertEquals("OPTIMISTIC_LOCK",error.getCode());
        verifyNoInteractions(projects,members,employees,workload);
    }

    private TaskStatus status(Long id,String code){TaskStatus status=mock(TaskStatus.class);when(status.getId()).thenReturn(id);when(status.getCode()).thenReturn(code);return status;}
}
