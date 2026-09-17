package com.virtualenterprise.app.security;
import com.virtualenterprise.app.domain.AppUser;
import com.virtualenterprise.app.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SessionIntegrityFilterTest {
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void changedAccountRevokesExistingSessionOnNextRequest() throws Exception {
        AppUser user=new AppUser();user.setUsername("employee");user.setPasswordHash("hash");
        SessionPrincipal principal=new SessionPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
        user.revokeSessions();
        AppUserRepository repository=mock(AppUserRepository.class);when(repository.findById(null)).thenReturn(Optional.of(user));
        MockHttpServletRequest request=new MockHttpServletRequest("GET","/dashboard");MockHttpSession session=new MockHttpSession();request.setSession(session);
        MockHttpServletResponse response=new MockHttpServletResponse();
        new SessionIntegrityFilter(repository).doFilter(request,response,new MockFilterChain());
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?expired");assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
