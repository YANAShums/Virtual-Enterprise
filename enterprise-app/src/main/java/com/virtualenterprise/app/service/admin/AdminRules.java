package com.virtualenterprise.app.service.admin;
import com.virtualenterprise.app.domain.Enterprise;
import com.virtualenterprise.app.repository.EnterpriseRepository;
import com.virtualenterprise.app.service.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.Objects;
@Component
public class AdminRules {
    private final EnterpriseRepository enterprises;
    public AdminRules(EnterpriseRepository enterprises) { this.enterprises=enterprises; }
    // Serializes hierarchy updates and last-admin checks for this single-organization app.
    public Enterprise lock() { return enterprises.lockOrganization().orElseThrow(() -> missing()); }
    public static AppException missing() { return new AppException(HttpStatus.NOT_FOUND,"NOT_FOUND","Запись не найдена"); }
    public static void check(boolean valid,String message) {
        if(!valid) throw new AppException(HttpStatus.CONFLICT,"ADMIN_VALIDATION",message);
    }
    public static void version(Long actual,Long submitted) {
        check(Objects.equals(actual,submitted),"Запись уже изменена. Обновите страницу перед сохранением.");
    }
    public static void timezone(String value) {
        try { java.time.ZoneId.of(value); } catch(java.time.DateTimeException ex) {
            check(false,"Укажите действительный часовой пояс, например Europe/Minsk");
        }
    }
}
