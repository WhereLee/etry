package com.wherelee.edu.modules.identity.security;

import com.wherelee.edu.modules.identity.user.User;
import com.wherelee.edu.modules.identity.user.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FilterChain chain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService, userRepository);
    }

    @Test
    void rejectsTokenWhenAccountVersionChanged() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.parse("token")).thenReturn(new JwtService.TokenPayload(2L, 0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user("ACTIVE", false, 1)));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("登录已失效");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsDisabledAccount() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/learning/series");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.parse("token")).thenReturn(new JwtService.TokenPayload(2L, 0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user("DISABLED", false, 0)));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("账号不可用");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void blocksBusinessApisUntilInitialPasswordChanged() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/learning/series");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.parse("token")).thenReturn(new JwtService.TokenPayload(2L, 0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user("ACTIVE", true, 0)));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(428);
        assertThat(response.getContentAsString()).contains("请先修改初始密码");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void allowsPasswordChangeWhileAccountIsMarkedForChange() throws Exception {
        MockHttpServletRequest request = request("PUT", "/api/auth/password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.parse("token")).thenReturn(new JwtService.TokenPayload(2L, 0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user("ACTIVE", true, 0)));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("userId")).isEqualTo(2L);
        assertThat(request.getAttribute("role")).isEqualTo("STUDENT");
        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsTeacherInvitationPreviewWithoutLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/auth/teacher-invitations/preview");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsAlipayNotifyWithoutLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments/alipay/notify");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer token");
        return request;
    }

    private User user(String status, boolean mustChangePassword, int tokenVersion) {
        return new User(2L, "student", "hash", "学生", "STUDENT", status, mustChangePassword, tokenVersion);
    }
}
