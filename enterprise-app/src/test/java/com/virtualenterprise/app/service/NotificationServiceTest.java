package com.virtualenterprise.app.service;

import com.virtualenterprise.app.attrition.prediction.StoredAttritionPrediction;
import com.virtualenterprise.app.domain.Department;
import com.virtualenterprise.app.domain.Employee;
import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.repository.NotificationRepository;
import com.virtualenterprise.app.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository notifications;
    @Mock EmployeeRepository employees;
    @Mock CurrentUserService currentUsers;
    @Mock com.virtualenterprise.app.security.AccessScopeService scope;
    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, employees, currentUsers, scope);
    }

    @Test
    void revokedScopeHidesOldNotificationsAndUnreadCount() {
        var user=mock(com.virtualenterprise.app.domain.AppUser.class);
        when(user.getId()).thenReturn(10L);when(currentUsers.requireCurrentUser()).thenReturn(user);
        var now=LocalDateTime.now();
        var blocked=new NotificationRepository.NotificationItem(1L,"ATTRITION_RISK_CHANGED","Risk","Private","EMPLOYEE",1L,false,null,now);
        var allowed=new NotificationRepository.NotificationItem(2L,"ATTRITION_RISK_CHANGED","Risk","Visible","EMPLOYEE",2L,false,null,now);
        when(notifications.findForUser(10L,Integer.MAX_VALUE)).thenReturn(List.of(blocked,allowed));
        when(scope.canViewAttrition(1L)).thenReturn(false);
        when(scope.canViewAttrition(2L)).thenReturn(true);
        org.junit.jupiter.api.Assertions.assertEquals(List.of(allowed),service.current(1));
        org.junit.jupiter.api.Assertions.assertEquals(1,service.unreadCount());
    }

    @Test
    void enteringHighRisk_notifiesManagersInEmployeeDepartment() {
        Employee employee = mock(Employee.class);
        Department department = mock(Department.class);
        when(employee.getDepartment()).thenReturn(department);
        when(employee.getFullName()).thenReturn("Иванов Иван");
        when(department.getId()).thenReturn(7L);
        when(employees.findById(1L)).thenReturn(Optional.of(employee));

        service.attritionRiskChanged(1L, prediction("MEDIUM", "0.49"), prediction("HIGH", "0.63"));

        verify(notifications).notifyManagers(eq(7L), eq("ATTRITION_RISK_CHANGED"),
                eq("Рост риска текучести"), contains("63.0%"), eq("EMPLOYEE"), eq(1L));
    }

    @Test
    void unchangedRisk_doesNotCreateNoise() {
        service.attritionRiskChanged(1L, prediction("HIGH", "0.63"), prediction("HIGH", "0.69"));

        verifyNoInteractions(employees, notifications);
    }

    private StoredAttritionPrediction prediction(String risk, String probability) {
        return new StoredAttritionPrediction(1L, 1L, LocalDate.now(), LocalDateTime.now(), 90,
                new BigDecimal(probability), risk, 2L, "lr-v2", List.of(), false, "CURRENT", null);
    }
}
