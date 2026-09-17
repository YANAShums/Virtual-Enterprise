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
public class UserAdminService {
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final EmployeeRepository employees;
    private final AdminRules rules;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;
    public UserAdminService(AppUserRepository users, RoleRepository roles, EmployeeRepository employees, AdminRules rules, org.springframework.security.crypto.password.PasswordEncoder encoder) { this.users=users;this.roles=roles;this.employees=employees;this.rules=rules;this.encoder=encoder; }

    public UserAdminForm form(Long id) {
        UserAdminForm f=new UserAdminForm();
        if(id!=null) { AppUser e=users.findById(id).orElseThrow(AdminRules::missing);f.setVersion(e.getVersion());f.setUsername(e.getUsername());f.setEmail(e.getEmail());f.setStatus(e.getStatus());
            f.setRoleIds(e.getRoles().stream().map(Role::getId).collect(java.util.stream.Collectors.toSet()));
            f.setEmployeeId(employees.findByUserId(id).map(Employee::getId).orElse(null)); }
        return f;
    }
    public Long save(Long id,UserAdminForm f) {
        rules.lock();AppUser e=id==null?new AppUser():users.findById(id).orElseThrow(AdminRules::missing);
        if(id!=null) version(e.getVersion(),f.getVersion());
        Set<Role> selected=new LinkedHashSet<>(roles.findAllById(f.getRoleIds()));
        check(selected.size()==f.getRoleIds().size() && !selected.isEmpty() && selected.stream().allMatch(Role::isActive),"Выберите действующие роли");
        boolean remainsAdmin="ACTIVE".equals(f.getStatus()) && selected.stream().anyMatch(r -> r.getCode().equals("ADMIN"));
        if(id!=null && isAdmin(e) && !remainsAdmin) {
            check(users.countOtherActiveAdmins(id)>0,"Нельзя заблокировать или лишить роли последнего администратора");
        }
        String current=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        check(id==null || !e.getUsername().equals(current) || remainsAdmin,"Нельзя заблокировать себя или снять у себя роль администратора");
        String password=f.getPassword();
        check(id!=null || (password!=null && !password.isBlank()),"Задайте пароль для нового пользователя");
        if(password!=null && !password.isBlank()) {
            check(password.length()>=10 && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72,"Пароль: не менее 10 символов и не более 72 байт UTF-8");
            e.setPasswordHash(encoder.encode(password));
        }
        Employee linked=f.getEmployeeId()==null?null:employees.findById(f.getEmployeeId()).orElseThrow(AdminRules::missing);
        check(linked==null || linked.getUser()==null || Objects.equals(linked.getUser().getId(),id),"Этот сотрудник уже связан с другой учётной записью");
        e.setUsername(f.getUsername().trim());e.setEmail(f.getEmail().trim());e.setStatus(f.getStatus());e.setActive(!"ARCHIVED".equals(f.getStatus()));e.setRoles(selected);e.revokeSessions();
        users.saveAndFlush(e);
        if(id!=null) employees.findByUserId(id).filter(old -> linked==null || !old.getId().equals(linked.getId())).ifPresent(old -> {old.setUser(null);employees.flush();});
        if(linked!=null) linked.setUser(e);
        return e.getId();
    }
    public void delete(Long id,Long submittedVersion) {
        UserAdminForm f=form(id);version(f.getVersion(),submittedVersion);f.setStatus("ARCHIVED");save(id,f);
    }
    private boolean isAdmin(AppUser e) {
        return e.isActive() && "ACTIVE".equals(e.getStatus()) && e.getRoles().stream().anyMatch(r -> r.isActive() && "ADMIN".equals(r.getCode()));
    }
}
