package com.virtualenterprise.app.config;

import com.virtualenterprise.app.repository.AppUserRepository;
import com.virtualenterprise.app.service.AdminViewService;
import com.virtualenterprise.app.service.WorkloadContextService;
import com.virtualenterprise.app.web.LoginController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class SecurityHeadersTest {
    @Autowired MockMvc mvc;
    @MockitoBean AppUserRepository users;
    @MockitoBean AdminViewService adminViews;
    @MockitoBean WorkloadContextService workload;

    @Test
    void login_includesBrowserSecurityHeaders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
