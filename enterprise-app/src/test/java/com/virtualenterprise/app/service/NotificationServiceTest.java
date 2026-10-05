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
    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, employees, currentUsers);
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
