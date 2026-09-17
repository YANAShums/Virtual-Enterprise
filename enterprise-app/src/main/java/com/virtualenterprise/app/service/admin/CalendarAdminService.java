package com.virtualenterprise.app.service.admin;
import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.web.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;
import static com.virtualenterprise.app.service.admin.AdminRules.*;
@Service @Transactional @PreAuthorize("hasRole('ADMIN')")
public class CalendarAdminService {
    private final ProductionCalendarDayRepository calendar;
    private final AdminRules rules;
    public CalendarAdminService(ProductionCalendarDayRepository calendar, AdminRules rules) { this.calendar=calendar;this.rules=rules; }

    public CalendarAdminForm form(Long id) {
        CalendarAdminForm f=new CalendarAdminForm();
        if(id!=null) { ProductionCalendarDay e=calendar.findById(id).orElseThrow(AdminRules::missing);
            f.setVersion(e.getVersion());f.setCalendarDate(e.getCalendarDate());f.setDayType(e.getDayType());f.setWorkingHoursOverride(e.getWorkingHoursOverride());f.setDescription(e.getDescription()); }
        return f;
    }
    public Long save(Long id,CalendarAdminForm f) {
        Enterprise org=rules.lock(); ProductionCalendarDay e=id==null?new ProductionCalendarDay():calendar.findById(id).orElseThrow(AdminRules::missing);
        if(id!=null) version(e.getVersion(),f.getVersion());
        boolean holiday=Set.of("HOLIDAY","WEEKEND").contains(f.getDayType());
        check(!holiday || f.getWorkingHoursOverride().signum()==0,"Для праздника или выходного укажите 0 часов");
        check(holiday || f.getWorkingHoursOverride().signum()>0,"Для рабочего или сокращённого дня укажите часы больше нуля");
        e.setEnterprise(org);e.setCalendarDate(f.getCalendarDate());e.setDayType(f.getDayType());e.setWorkingHoursOverride(f.getWorkingHoursOverride());e.setDescription(f.getDescription());
        return calendar.saveAndFlush(e).getId();
    }
    public void delete(Long id,Long submittedVersion) {
        rules.lock();ProductionCalendarDay e=calendar.findById(id).orElseThrow(AdminRules::missing);version(e.getVersion(),submittedVersion);
        calendar.delete(e);calendar.flush();
    }
}
