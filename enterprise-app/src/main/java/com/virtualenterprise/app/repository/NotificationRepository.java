package com.virtualenterprise.app.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<NotificationItem> findForUser(Long userId, int limit) {
        return jdbc.query("""
                SELECT id,notification_type,title,message,entity_type,entity_id,read_flag,read_at,created_at
                FROM notifications WHERE user_id=? ORDER BY read_flag,created_at DESC LIMIT ?
                """, (rs, row) -> new NotificationItem(rs.getLong("id"), rs.getString("notification_type"),
                rs.getString("title"), rs.getString("message"), rs.getString("entity_type"),
                rs.getObject("entity_id", Long.class), rs.getBoolean("read_flag"),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime()), userId, limit);
    }

    public long unreadCount(Long userId) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND read_flag=0",
                Long.class, userId);
        return value == null ? 0 : value;
    }

    public void markRead(Long notificationId, Long userId) {
        jdbc.update("UPDATE notifications SET read_flag=1,read_at=CURRENT_TIMESTAMP(6) WHERE id=? AND user_id=?",
                notificationId, userId);
    }

    public void markAllRead(Long userId) {
        jdbc.update("UPDATE notifications SET read_flag=1,read_at=CURRENT_TIMESTAMP(6) "
                + "WHERE user_id=? AND read_flag=0", userId);
    }

    public int notifyManagers(Long departmentId, String type, String title, String message,
                              String entityType, Long entityId) {
        return jdbc.update("""
                INSERT INTO notifications(user_id,notification_type,title,message,entity_type,entity_id)
                SELECT DISTINCT u.id,?,?,?,?,?
                FROM users u
                JOIN user_roles ur ON ur.user_id=u.id
                JOIN roles r ON r.id=ur.role_id AND r.active=1
                LEFT JOIN employees manager_employee ON manager_employee.user_id=u.id
                WHERE u.active=1 AND u.status='ACTIVE'
                  AND (r.code IN ('ADMIN','ENTERPRISE_MANAGER')
                       OR (r.code='DEPARTMENT_MANAGER' AND manager_employee.department_id=?))
                  AND NOT EXISTS (SELECT 1 FROM notifications n
                      WHERE n.user_id=u.id AND n.notification_type=? AND n.entity_type=? AND n.entity_id=?
                        AND n.created_at>=CURRENT_DATE)
                """, type, title, message, entityType, entityId, departmentId, type, entityType, entityId);
    }

    public record NotificationItem(Long id, String type, String title, String message, String entityType,
                                   Long entityId, boolean read, LocalDateTime readAt, LocalDateTime createdAt) {}
}
