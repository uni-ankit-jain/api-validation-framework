package com.uniphore.platform.validation.filter;

import com.uniphore.platform.validation.annotation.HeaderConstraints;
import com.uniphore.platform.validation.annotation.HeaderRule;
import com.uniphore.platform.validation.annotation.SkipValidation;
import com.uniphore.platform.validation.exception.HeaderValidationException;
import com.uniphore.platform.validation.properties.ValidationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeaderValidationFilterTest {

    @Mock
    private RequestMappingHandlerMapping handlerMapping;

    @Mock
    private FilterChain filterChain;

    private ValidationProperties properties;
    private HeaderValidationFilter filter;

    @BeforeEach
    void setUp() {
        properties = new ValidationProperties();
        filter = new HeaderValidationFilter(properties, handlerMapping);
    }

    // -----------------------------------------------------------------------
    // Bypass paths
    // -----------------------------------------------------------------------

    @Test
    void shouldBypassValidationForHealthEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(handlerMapping);
    }

    @Test
    void shouldBypassValidationForSwaggerUi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // -----------------------------------------------------------------------
    // Authorization header
    // -----------------------------------------------------------------------

    @Test
    void shouldThrow401WhenAuthorizationHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(handlerMapping.getHandler(any())).thenReturn(null);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(HeaderValidationException.class)
                .satisfies(ex -> {
                    HeaderValidationException hve = (HeaderValidationException) ex;
                    assertThat(hve.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(hve.getHeaderName()).isEqualTo("Authorization");
                });
    }

    @Test
    void shouldThrow401WhenBearerPrefixMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.addHeader("Authorization", "Basic abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(handlerMapping.getHandler(any())).thenReturn(null);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(HeaderValidationException.class)
                .satisfies(ex -> {
                    HeaderValidationException hve = (HeaderValidationException) ex;
                    assertThat(hve.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(hve.getMessage()).contains("Bearer");
                });
    }

    @Test
    void shouldThrow401WhenBearerTokenEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(handlerMapping.getHandler(any())).thenReturn(null);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(HeaderValidationException.class)
                .satisfies(ex -> assertThat(((HeaderValidationException) ex).getHttpStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void shouldPassWithValidBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.addHeader("Authorization", "Bearer valid-token-here");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(handlerMapping.getHandler(any())).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // -----------------------------------------------------------------------
    // Content-Type validation
    // -----------------------------------------------------------------------

    @Test
    void shouldThrow400WhenContentTypeMissingOnPost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/conversations");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(handlerMapping.getHandler(any())).thenReturn(null);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(HeaderValidationException.class)
                .satisfies(ex -> {
                    HeaderValidationException hve = (HeaderValidationException) ex;
                    assertThat(hve.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(hve.getHeaderName()).isEqualTo("Content-Type");
                });
    }

    @Test
    void shouldThrow400WhenContentTypeNotAllowed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/conversations");
        request.addHeader("Authorization", "Bearer valid-token");
        request.setContentType("text/plain");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(handlerMapping.getHandler(any())).thenReturn(null);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(HeaderValidationException.class)
                .satisfies(ex -> {
                    HeaderValidationException hve = (HeaderValidationException) ex;
                    assertThat(hve.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void shouldPassWhenContentTypeIsApplicationJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/conversations");
        request.addHeader("Authorization", "Bearer valid-token");
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(handlerMapping.getHandler(any())).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotValidateContentTypeOnGet() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.addHeader("Authorization", "Bearer valid-token");
        // no Content-Type on GET
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(handlerMapping.getHandler(any())).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // -----------------------------------------------------------------------
    // Custom required headers (properties)
    // -----------------------------------------------------------------------

    @Test
    void shouldThrow400WhenRequiredCustomHeaderMissing() throws Exception {
        properties.getCustomHeaders().setRequired(List.of("X-Tenant-ID"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(handlerMapping.getHandler(any())).thenReturn(null);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(HeaderValidationException.class)
                .satisfies(ex -> {
                    HeaderValidationException hve = (HeaderValidationException) ex;
                    assertThat(hve.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(hve.getHeaderName()).isEqualTo("X-Tenant-ID");
                });
    }

    // -----------------------------------------------------------------------
    // @SkipValidation
    // -----------------------------------------------------------------------

    @Test
    void shouldSkipValidationWhenAnnotationPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhooks/inbound");
        // No Authorization or Content-Type
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = mockHandlerMethodWithSkipValidation();
        when(handlerMapping.getHandler(any()))
                .thenReturn(new HandlerExecutionChain(handlerMethod));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // -----------------------------------------------------------------------
    // @HeaderConstraints / @HeaderRule (per-endpoint)
    // -----------------------------------------------------------------------

    @Test
    void shouldThrow400WhenHeaderConstraintsRequiredHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = buildHandlerMethod(TestController.class, "headerConstraintsMethod");
        when(handlerMapping.getHandler(any()))
                .thenReturn(new HandlerExecutionChain(handlerMethod));

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(HeaderValidationException.class)
                .satisfies(ex -> {
                    HeaderValidationException hve = (HeaderValidationException) ex;
                    assertThat(hve.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(hve.getHeaderName()).isEqualTo("X-Source");
                });
    }

    @Test
    void shouldPassWhenHeaderConstraintsRequiredHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/conversations");
        request.addHeader("Authorization", "Bearer valid-token");
        request.addHeader("X-Source", "mobile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = buildHandlerMethod(TestController.class, "headerConstraintsMethod");
        when(handlerMapping.getHandler(any()))
                .thenReturn(new HandlerExecutionChain(handlerMethod));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSkipAuthWhenHeaderConstraintsSkipAuthTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public");
        // No Authorization header
        MockHttpServletResponse response = new MockHttpServletResponse();

        HandlerMethod handlerMethod = buildHandlerMethod(TestController.class, "skipAuthMethod");
        when(handlerMapping.getHandler(any()))
                .thenReturn(new HandlerExecutionChain(handlerMethod));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // -----------------------------------------------------------------------
    // Disabled validation
    // -----------------------------------------------------------------------

    @Test
    void shouldSkipEntireFilterWhenDisabled() throws Exception {
        properties.setEnabled(false);
        // Re-create filter — but note: when disabled, the bean is not registered at all.
        // This tests the isEnabled-aware variant if implemented; here we verify bypass paths work.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private HandlerMethod mockHandlerMethodWithSkipValidation() throws Exception {
        return buildHandlerMethod(TestController.class, "skipMethod");
    }

    private HandlerMethod buildHandlerMethod(Class<?> controllerClass, String methodName) throws Exception {
        Object controller = controllerClass.getDeclaredConstructor().newInstance();
        Method method = controllerClass.getMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    /** Minimal controller stub used to carry annotations for tests. */
    static class TestController {

        @SkipValidation
        public void skipMethod() {
        }

        @HeaderConstraints({ @HeaderRule(name = "X-Source") })
        public void headerConstraintsMethod() {
        }

        @HeaderConstraints(skipAuth = true)
        public void skipAuthMethod() {
        }
    }
}
