package com.virtualenterprise.app.attrition.features;

import com.virtualenterprise.app.domain.AppUser;
import com.virtualenterprise.app.domain.Employee;
import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.security.CurrentUserService;
import com.virtualenterprise.app.service.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttritionFeatureAccessService {
    private final CurrentUserService currentUsers;
    private final EmployeeRepository employees;

    public AttritionFeatureAccessService(CurrentUserService currentUsers, EmployeeRepository employees) {
        this.currentUsers = currentUsers;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    @org.springframework.security.access.prepost.PreAuthorize("@accessScopeService.canViewAttrition(#employeeId)")
    public void assertCanView(Long employeeId) {
        AppUser user = currentUsers.requireCurrentUser();
        if (hasRole(user, "ADMIN") || hasRole(user, "ENTERPRISE_MANAGER")) {
            return;
        }
        Employee currentEmployee = employees.findByUserId(user.getId())
                .orElseThrow(() -> forbidden("Для пользователя не найден профиль сотрудника"));
        Employee target = employees.findById(employeeId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND", "Сотрудник не найден"));
        if (!hasRole(user, "DEPARTMENT_MANAGER")
                || !currentEmployee.getDepartment().getId().equals(target.getDepartment().getId())) {
            throw forbidden("Нет доступа к признакам сотрудника другого подразделения");
        }
    }

    @Transactional(readOnly = true)
    public Long visibleDepartmentId() {
        AppUser user = currentUsers.requireCurrentUser();
        if (hasRole(user, "ADMIN") || hasRole(user, "ENTERPRISE_MANAGER")) {
            return null;
        }
        if (!hasRole(user, "DEPARTMENT_MANAGER")) {
            throw forbidden("Нет доступа к кадровым прогнозам");
        }
        return employees.findByUserId(user.getId())
                .map(employee -> employee.getDepartment().getId())
                .orElseThrow(() -> forbidden("Для пользователя не найден профиль сотрудника"));
    }

    @Transactional(readOnly = true)
    public Long visibleEnterpriseId() {
        AppUser user=currentUsers.requireCurrentUser();
        if(hasRole(user,"ADMIN")) return null;
        if(!hasRole(user,"ENTERPRISE_MANAGER") && !hasRole(user,"DEPARTMENT_MANAGER")) throw forbidden("Нет доступа к кадровым прогнозам");
        return employees.findByUserId(user.getId()).map(e -> e.getEnterprise().getId())
                .orElseThrow(() -> forbidden("Для пользователя не найден профиль сотрудника"));
    }

    private boolean hasRole(AppUser user, String code) {
        return user.getRoles().stream().anyMatch(role -> role.isActive() && code.equals(role.getCode()));
    }

    private AppException forbidden(String message) {
        return new AppException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }
}
