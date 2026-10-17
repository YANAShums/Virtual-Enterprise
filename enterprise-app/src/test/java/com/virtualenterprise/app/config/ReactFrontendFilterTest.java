package com.virtualenterprise.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ReactFrontendFilterTest {
    private final ReactFrontendFilter filter = new ReactFrontendFilter();

    @Test
    void browserNavigationServesReactWithoutExecutingMvcPage() throws Exception {
        var request = request("GET", "/employees/5", "text/html,application/xhtml+xml");
        var response = new MockHttpServletResponse();
        var reachedMvc = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> reachedMvc.set(true));
        assertThat(reachedMvc).isFalse();
        assertThat(response.getContentAsString()).contains("id=\"root\"", "type=\"module\"");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void contractRequestsAlwaysReachExistingAuthorizationAndControllers() throws Exception {
        var request = request("GET", "/admin/users/1/edit", "text/html");
        request.addHeader("X-FlowStaff-View", "1");
        assertPassthrough(request);
    }

    @Test
    void mutationsDownloadsAndApisAreNeverReplacedByReactShell() throws Exception {
        assertPassthrough(request("POST", "/employees", "text/html"));
        assertPassthrough(request("GET", "/reports/export", "text/html"));
        assertPassthrough(request("GET", "/admin/ml/models/template.csv", "text/html"));
        assertPassthrough(request("GET", "/api/v1/tasks/1", "application/json"));
        assertPassthrough(request("GET", "/js/react/assets/index.js", "*/*"));
    }

    @Test
    void nonBrowserClientsKeepMvcContract() throws Exception {
        assertPassthrough(request("GET", "/tasks", "*/*"));
    }

    private void assertPassthrough(MockHttpServletRequest request) throws Exception {
        var reachedMvc = new AtomicBoolean();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> reachedMvc.set(true));
        assertThat(reachedMvc).isTrue();
        assertThat(response.getContentAsString()).isEmpty();
    }

    private MockHttpServletRequest request(String method, String path, String accept) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader("Accept", accept);
        return request;
    }
}
