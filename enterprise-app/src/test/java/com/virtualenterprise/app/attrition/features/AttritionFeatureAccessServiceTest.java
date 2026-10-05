package com.virtualenterprise.app.attrition.features;

import com.virtualenterprise.app.domain.AppUser;
import com.virtualenterprise.app.domain.Department;
import com.virtualenterprise.app.domain.Employee;
import com.virtualenterprise.app.domain.Role;
import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.security.CurrentUserService;
import com.virtualenterprise.app.service.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttritionFeatureAccessServiceTest {
    private final CurrentUserService currentUsers = mock(CurrentUserService.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final AttritionFeatureAccessService access = new AttritionFeatureAccessService(currentUsers, employees);

    @Test
    void visibleDepartment_enterpriseManager_hasEnterpriseScope() {
        AppUser manager = user(1L, "ENTERPRISE_MANAGER");
        when(currentUsers.requireCurrentUser()).thenReturn(manager);

        assertThat(access.visibleDepartmentId()).isNull();
    }

    @Test
    void visibleDepartment_departmentManager_isLimitedToOwnDepartment() {
        AppUser manager = user(2L, "DEPARTMENT_MANAGER");
        when(currentUsers.requireCurrentUser()).thenReturn(manager);
        when(employees.findByUserId(2L)).thenReturn(Optional.of(employee(20L, 7L)));

        assertThat(access.visibleDepartmentId()).isEqualTo(7L);
    }

    @Test
    void assertCanView_departmentManagerCannotReadAnotherDepartment() {
        AppUser manager = user(2L, "DEPARTMENT_MANAGER");
        when(currentUsers.requireCurrentUser()).thenReturn(manager);
        when(employees.findByUserId(2L)).thenReturn(Optional.of(employee(20L, 7L)));
        when(employees.findById(30L)).thenReturn(Optional.of(employee(30L, 8L)));

        assertThatThrownBy(() -> access.assertCanView(30L))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void visibleDepartment_employeeRole_isForbidden() {
        AppUser employee = user(3L, "EMPLOYEE");
        when(currentUsers.requireCurrentUser()).thenReturn(employee);

        assertThatThrownBy(access::visibleDepartmentId)
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getCode())
                .isEqualTo("ACCESS_DENIED");
    }

    private AppUser user(Long id, String roleCode) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn(roleCode);
        user.setRoles(Set.of(role));
        return user;
    }

    private Employee employee(Long id, Long departmentId) {
        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", departmentId);
        Employee employee = new Employee();
        ReflectionTestUtils.setField(employee, "id", id);
        employee.setDepartment(department);
        return employee;
    }
}
