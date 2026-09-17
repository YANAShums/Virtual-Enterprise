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
public class ScheduleAdminService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final WorkScheduleRepository schedules;
    private final EmployeeRepository employees;
    private final AdminRules rules;
    public ScheduleAdminService(WorkScheduleRepository schedules, EmployeeRepository employees, AdminRules rules) { this.schedules=schedules;this.employees=employees;this.rules=rules; }

    public ScheduleAdminForm form(Long id) {
        ScheduleAdminForm f=new ScheduleAdminForm();
        if(id!=null) { WorkSchedule e=schedules.findById(id).orElseThrow(AdminRules::missing);
            f.setVersion(e.getVersion());f.setCode(e.getCode());f.setName(e.getName());f.setTimezone(e.getTimezone());f.setActive(e.isActive());
            f.setHours(new ArrayList<>(Collections.nCopies(7,java.math.BigDecimal.ZERO)));
            e.getDays().forEach(day -> f.getHours().set(day.getDayOfWeek()-1,day.getPlannedHours())); }
        return f;
    }
    public Long save(Long id,ScheduleAdminForm f) {
        Enterprise org=rules.lock();timezone(f.getTimezone());
        WorkSchedule e=id==null?new WorkSchedule():schedules.findById(id).orElseThrow(AdminRules::missing);
        if(id!=null) version(e.getVersion(),f.getVersion());
        e.setEnterprise(org);e.setCode(f.getCode().trim());e.setName(f.getName().trim());e.setActive(f.getActive());e.setTimezone(f.getTimezone());
        e.setWeeklyHours(f.getHours().stream().reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add));
        for(int index=0;index<7;index++) {
            final int dayNumber=index+1;
            WorkScheduleDay day=e.getDays().stream().filter(d -> d.getDayOfWeek()==dayNumber).findFirst().orElseGet(() -> {
                WorkScheduleDay d=new WorkScheduleDay(); d.setSchedule(e);d.setDayOfWeek(dayNumber);e.getDays().add(d);return d;
            });
            day.setPlannedHours(f.getHours().get(index));day.setWorkingDay(f.getHours().get(index).signum()>0);
        }
        schedules.saveAndFlush(e);
        if(id!=null) entityManager.lock(e,jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        return e.getId();
    }
    public void delete(Long id,Long submittedVersion) {
        rules.lock();WorkSchedule e=schedules.findById(id).orElseThrow(AdminRules::missing);version(e.getVersion(),submittedVersion);
        check(!employees.existsByWorkScheduleId(id),"График используется сотрудниками. Вместо удаления снимите активность");
        schedules.delete(e);schedules.flush();
    }
}
