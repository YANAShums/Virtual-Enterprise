package com.virtualenterprise.app.service.admin;
import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.service.AuditTrailService;
import com.virtualenterprise.app.web.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;
import static com.virtualenterprise.app.service.admin.AdminRules.*;
@Service @Transactional @PreAuthorize("hasRole('ADMIN')")
public class EnterpriseAdminService {
    private final EnterpriseRepository enterprises;
    private final AdminRules rules;
    private final AuditTrailService audit;
    public EnterpriseAdminService(EnterpriseRepository enterprises, AdminRules rules,AuditTrailService audit) { this.enterprises=enterprises;this.rules=rules;this.audit=audit; }

    public EnterpriseAdminForm form() {
        Enterprise e=enterprises.findById(1L).orElseThrow(AdminRules::missing);EnterpriseAdminForm f=new EnterpriseAdminForm();
        f.setVersion(e.getVersion());f.setCode(e.getCode());f.setName(e.getName());f.setTimezone(e.getTimezone());return f;
    }
    public void save(EnterpriseAdminForm f) {
        Enterprise e=rules.lock();version(e.getVersion(),f.getVersion());timezone(f.getTimezone());
        Map<String,Object> before=Map.of("code",e.getCode(),"name",e.getName(),"timezone",e.getTimezone());
        e.setCode(f.getCode().trim());e.setName(f.getName().trim());e.setTimezone(f.getTimezone());enterprises.flush();
        audit.record("ENTERPRISE_SETTINGS_UPDATED","ENTERPRISE",e.getId(),before,
                Map.of("code",e.getCode(),"name",e.getName(),"timezone",e.getTimezone()));
    }
}
