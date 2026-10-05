package com.virtualenterprise.app.service;

import com.virtualenterprise.app.attrition.prediction.StoredAttritionPrediction;
import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.repository.NotificationRepository;
import com.virtualenterprise.app.repository.NotificationRepository.NotificationItem;
import com.virtualenterprise.app.security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notifications;
    private final EmployeeRepository employees;
    private final CurrentUserService currentUsers;

    public NotificationService(NotificationRepository notifications, EmployeeRepository employees,
                               CurrentUserService currentUsers) {
        this.notifications = notifications;
        this.employees = employees;
        this.currentUsers = currentUsers;
    }

    @Transactional(readOnly = true)
    public List<NotificationItem> current(int limit) {
        return notifications.findForUser(currentUsers.requireCurrentUser().getId(), Math.max(1, Math.min(limit, 100)));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.unreadCount(currentUsers.requireCurrentUser().getId());
    }

    @Transactional
    public void markRead(Long id) {
        notifications.markRead(id, currentUsers.requireCurrentUser().getId());
    }

    @Transactional
    public void markAllRead() {
        notifications.markAllRead(currentUsers.requireCurrentUser().getId());
    }

    public void attritionRiskChanged(Long employeeId, StoredAttritionPrediction previous,
                                     StoredAttritionPrediction current) {
        boolean enteredHighRisk = riskRank(current.riskLevel()) >= riskRank("HIGH")
                && (previous == null || riskRank(previous.riskLevel()) < riskRank(current.riskLevel()));
        boolean materialGrowth = previous != null && current.probability().subtract(previous.probability())
                .compareTo(BigDecimal.valueOf(0.10)) >= 0;
        if (!enteredHighRisk && !materialGrowth) return;
        try {
            employees.findById(employeeId).ifPresent(employee -> {
                String title = "CRITICAL".equals(current.riskLevel())
                        ? "Критический риск текучести" : "Рост риска текучести";
                String message = employee.getFullName() + ": вероятность ухода "
                        + current.probability().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                        + "%, уровень " + current.riskLevel() + ".";
                notifications.notifyManagers(employee.getDepartment().getId(), "ATTRITION_RISK_CHANGED", title,
                        message, "EMPLOYEE", employeeId);
            });
        } catch (RuntimeException exception) {
            log.warn("Could not create attrition notification for employee {}", employeeId, exception);
        }
    }

    public void criticalOverload(Long employeeId, Long taskId, BigDecimal projectedLoad) {
        try {
            employees.findById(employeeId).ifPresent(employee -> notifications.notifyManagers(
                    employee.getDepartment().getId(), "CRITICAL_OVERLOAD", "Критическая перегрузка",
                    employee.getFullName() + ": прогнозная загрузка " + projectedLoad + "% после назначения задачи.",
                    "TASK", taskId));
        } catch (RuntimeException exception) {
            log.warn("Could not create overload notification for employee {}", employeeId, exception);
        }
    }

    private int riskRank(String value) {
        return switch (value) { case "LOW" -> 1; case "MEDIUM" -> 2; case "HIGH" -> 3; case "CRITICAL" -> 4; default -> 0; };
    }
}
