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
public class PositionAdminService {
    private final PositionRepository positions;
    private final EmployeeRepository employees;
    private final AdminRules rules;
    public PositionAdminService(PositionRepository positions, EmployeeRepository employees, AdminRules rules) { this.positions=positions;this.employees=employees;this.rules=rules; }

    public PositionAdminForm form(Long id) {
        PositionAdminForm f=new PositionAdminForm();
        if(id!=null) { PositionEntity e=positions.findById(id).orElseThrow(AdminRules::missing);
            f.setVersion(e.getVersion());f.setCode(e.getCode());f.setName(e.getName());f.setGrade(e.getGrade());f.setDescription(e.getDescription());f.setActive(e.isActive()); }
        return f;
    }
    public Long save(Long id,PositionAdminForm f) {
        Enterprise org=rules.lock(); PositionEntity e=id==null?new PositionEntity():positions.findById(id).orElseThrow(AdminRules::missing);
        if(id!=null) version(e.getVersion(),f.getVersion());
        e.setEnterprise(org);e.setCode(f.getCode().trim());e.setName(f.getName().trim());e.setGrade(f.getGrade());e.setDescription(f.getDescription());e.setActive(f.getActive());
        return positions.saveAndFlush(e).getId();
    }
    public void delete(Long id,Long submittedVersion) {
        rules.lock();PositionEntity e=positions.findById(id).orElseThrow(AdminRules::missing);version(e.getVersion(),submittedVersion);
        check(!employees.existsByPositionId(id),"Должность назначена сотрудникам. Вместо удаления снимите активность");
        positions.delete(e);positions.flush();
    }
}
