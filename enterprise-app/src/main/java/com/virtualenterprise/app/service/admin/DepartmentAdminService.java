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
public class DepartmentAdminService {
    private final DepartmentRepository departments;
    private final EmployeeRepository employees;
    private final AdminRules rules;
    public DepartmentAdminService(DepartmentRepository departments, EmployeeRepository employees, AdminRules rules) { this.departments=departments;this.employees=employees;this.rules=rules; }

    public DepartmentAdminForm form(Long id) {
        DepartmentAdminForm f=new DepartmentAdminForm();
        if(id!=null) { Department e=departments.findById(id).orElseThrow(AdminRules::missing);
            f.setVersion(e.getVersion()); f.setCode(e.getCode()); f.setName(e.getName()); f.setActive(e.isActive());
            f.setParentId(e.getParent()==null?null:e.getParent().getId());
            f.setManagerId(e.getManager()==null?null:e.getManager().getId()); }
        return f;
    }
    public Long save(Long id,DepartmentAdminForm f) {
        Enterprise organization=rules.lock();
        Department e=id==null?new Department():departments.findById(id).orElseThrow(AdminRules::missing);
        if(id!=null) version(e.getVersion(),f.getVersion());
        Department parent=f.getParentId()==null?null:departments.findById(f.getParentId()).orElseThrow(AdminRules::missing);
        Set<Long> visited=new HashSet<>();
        for(Department node=parent;node!=null;node=node.getParent()) {
            check(!Objects.equals(node.getId(),id) && visited.add(node.getId()),"Подразделение не может быть родителем самого себя или своего предка");
        }
        check(parent==null || parent.isActive(),"Родительское подразделение находится в архиве");
        Employee manager=f.getManagerId()==null?null:employees.findById(f.getManagerId()).orElseThrow(AdminRules::missing);
        check(manager==null || (manager.isActive() && "ACTIVE".equals(manager.getStatus())),"Руководитель должен быть действующим сотрудником");
        e.setEnterprise(organization); e.setCode(f.getCode().trim()); e.setName(f.getName().trim());
        e.setActive(f.getActive()); e.setParent(parent); e.setManager(manager);
        return departments.saveAndFlush(e).getId();
    }
    public void delete(Long id,Long submittedVersion) {
        rules.lock(); Department e=departments.findById(id).orElseThrow(AdminRules::missing); version(e.getVersion(),submittedVersion);
        check(!departments.existsByParentId(id) && !employees.existsByDepartmentId(id),"Подразделение используется: перенесите дочерние подразделения и сотрудников либо снимите активность");
        departments.delete(e); departments.flush();
    }
}
