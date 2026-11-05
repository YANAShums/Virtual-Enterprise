package com.virtualenterprise.app.web;

import com.virtualenterprise.app.config.SecurityConfig;
import com.virtualenterprise.app.repository.AppUserRepository;
import com.virtualenterprise.app.service.AdminViewService;
import com.virtualenterprise.app.service.AttritionAnalyticsService;
import com.virtualenterprise.app.service.ManagementReportService;
import com.virtualenterprise.app.service.WorkloadContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ManagementReportService reports;
    @MockitoBean AppUserRepository users;
    @MockitoBean AdminViewService adminViews;
    @MockitoBean WorkloadContextService workload;

    @Test @WithMockUser(roles = "EMPLOYEE")
    void reports_employee_isForbidden() throws Exception {
        mvc.perform(get("/reports")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void reports_emptySelection_rendersHonestEmptyState() throws Exception {
        when(reports.report(any())).thenReturn(emptyOverview());

        mvc.perform(get("/reports"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Нет данных для отчёта")));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void pdfExport_preservesFilterAndReturnsAttachment() throws Exception {
        when(reports.report(any())).thenReturn(emptyOverview());
        when(reports.pdf(any())).thenReturn(new byte[]{'%', 'P', 'D', 'F', '-'});
        mvc.perform(get("/reports/export").param("format", "pdf").param("riskLevel", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                .andExpect(content().contentType("application/pdf"));
        var filter = org.mockito.ArgumentCaptor.forClass(AttritionAnalyticsService.Filter.class);
        verify(reports).report(filter.capture());
        org.assertj.core.api.Assertions.assertThat(filter.getValue().riskLevel()).isEqualTo("HIGH");
    }

    @Test @WithMockUser(roles = "ADMIN")
    void csvExport_isRejected() throws Exception {
        when(reports.report(any())).thenReturn(emptyOverview());
        mvc.perform(get("/reports/export").param("format", "csv"))
                .andExpect(status().isBadRequest());
    }

    @Test @WithMockUser(roles = "DEPARTMENT_MANAGER")
    void xlsxExport_departmentManager_isAllowed() throws Exception {
        when(reports.report(any())).thenReturn(emptyOverview());
        when(reports.xlsx(any())).thenReturn(new byte[]{'P','K'});
        mvc.perform(get("/reports/export").param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private AttritionAnalyticsService.Overview emptyOverview() {
        var distribution = new LinkedHashMap<String, Long>();
        List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach(level -> distribution.put(level, 0L));
        var filter = new AttritionAnalyticsService.Filter(LocalDate.now().minusDays(89), LocalDate.now(),
                null, null, null, null, null, null);
        return new AttritionAnalyticsService.Overview(filter, List.of(), 0, 0, null, null,
                distribution, "background:#eee", List.of(), "", List.of(), List.of(), List.of());
    }
}
