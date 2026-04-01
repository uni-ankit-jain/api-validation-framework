package com.uniphore.platform.validation.properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationPropertiesTest {

    // -----------------------------------------------------------------------
    // Default values (no application.properties)
    // -----------------------------------------------------------------------

    @Test
    void defaultsShouldBeCorrect() {
        ValidationProperties props = new ValidationProperties();

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getAuthorizationHeader().isRequired()).isTrue();
        assertThat(props.getAuthorizationHeader().isRequireBearerPrefix()).isTrue();
        assertThat(props.getContentType().isValidateOnMutating()).isTrue();
        assertThat(props.getContentType().getAllowedTypes()).containsExactly("application/json");
        assertThat(props.getCustomHeaders().getRequired()).isEmpty();
        assertThat(props.getCustomHeaders().getNotBlankIfPresent()).isEmpty();
        assertThat(props.getBypassPaths())
                .contains("/health/**", "/swagger-ui/**", "/v3/api-docs/**");
        assertThat(props.getFilterOrder()).isEqualTo(Integer.MIN_VALUE + 10);
        assertThat(props.getExceptionHandler().isEnabled()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Mutation — verify setters work (used by Spring's binding)
    // -----------------------------------------------------------------------

    @Test
    void shouldAllowDisablingValidation() {
        ValidationProperties props = new ValidationProperties();
        props.setEnabled(false);

        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    void shouldAllowDisablingAuthorizationHeaderRequirement() {
        ValidationProperties props = new ValidationProperties();
        props.getAuthorizationHeader().setRequired(false);

        assertThat(props.getAuthorizationHeader().isRequired()).isFalse();
    }

    @Test
    void shouldAllowDisablingBearerPrefixRequirement() {
        ValidationProperties props = new ValidationProperties();
        props.getAuthorizationHeader().setRequireBearerPrefix(false);

        assertThat(props.getAuthorizationHeader().isRequireBearerPrefix()).isFalse();
    }

    @Test
    void shouldAllowCustomBypassPaths() {
        ValidationProperties props = new ValidationProperties();
        props.setBypassPaths(List.of("/public/**", "/actuator/**"));

        assertThat(props.getBypassPaths()).containsExactlyInAnyOrder("/public/**", "/actuator/**");
    }

    @Test
    void shouldAllowMultipleCustomRequiredHeaders() {
        ValidationProperties props = new ValidationProperties();
        props.getCustomHeaders().setRequired(List.of("X-Tenant-ID", "X-Correlation-ID"));

        assertThat(props.getCustomHeaders().getRequired())
                .containsExactly("X-Tenant-ID", "X-Correlation-ID");
    }

    @Test
    void shouldAllowMultipleAllowedContentTypes() {
        ValidationProperties props = new ValidationProperties();
        props.getContentType().setAllowedTypes(List.of("application/json", "application/xml"));

        assertThat(props.getContentType().getAllowedTypes())
                .containsExactly("application/json", "application/xml");
    }

    @Test
    void shouldAllowCustomFilterOrder() {
        ValidationProperties props = new ValidationProperties();
        props.setFilterOrder(-100);

        assertThat(props.getFilterOrder()).isEqualTo(-100);
    }

    @Test
    void shouldAllowDisablingExceptionHandler() {
        ValidationProperties props = new ValidationProperties();
        props.getExceptionHandler().setEnabled(false);

        assertThat(props.getExceptionHandler().isEnabled()).isFalse();
    }

    @Test
    void shouldAllowDisablingContentTypeValidation() {
        ValidationProperties props = new ValidationProperties();
        props.getContentType().setValidateOnMutating(false);

        assertThat(props.getContentType().isValidateOnMutating()).isFalse();
    }

    @Test
    void shouldAllowNotBlankIfPresentHeaders() {
        ValidationProperties props = new ValidationProperties();
        props.getCustomHeaders().setNotBlankIfPresent(List.of("X-Optional-Tenant"));

        assertThat(props.getCustomHeaders().getNotBlankIfPresent())
                .containsExactly("X-Optional-Tenant");
    }

    // -----------------------------------------------------------------------
    // Spring Boot binding test
    // -----------------------------------------------------------------------

    @SpringBootTest(classes = ValidationPropertiesTest.PropertiesTestConfig.class)
    @TestPropertySource(properties = {
            "uniphore.validation.enabled=false",
            "uniphore.validation.authorization-header.required=false",
            "uniphore.validation.authorization-header.require-bearer-prefix=false",
            "uniphore.validation.content-type.validate-on-mutating=false",
            "uniphore.validation.content-type.allowed-types=application/json,application/xml",
            "uniphore.validation.custom-headers.required=X-Tenant-ID,X-Correlation-ID",
            "uniphore.validation.bypass-paths=/public/**,/actuator/**",
            "uniphore.validation.filter-order=-200",
            "uniphore.validation.exception-handler.enabled=false"
    })
    static class SpringBindingTest {

        @Autowired
        ValidationProperties props;

        @Test
        void shouldBindAllPropertiesFromApplicationProperties() {
            assertThat(props.isEnabled()).isFalse();
            assertThat(props.getAuthorizationHeader().isRequired()).isFalse();
            assertThat(props.getAuthorizationHeader().isRequireBearerPrefix()).isFalse();
            assertThat(props.getContentType().isValidateOnMutating()).isFalse();
            assertThat(props.getContentType().getAllowedTypes())
                    .containsExactlyInAnyOrder("application/json", "application/xml");
            assertThat(props.getCustomHeaders().getRequired())
                    .containsExactlyInAnyOrder("X-Tenant-ID", "X-Correlation-ID");
            assertThat(props.getBypassPaths())
                    .containsExactlyInAnyOrder("/public/**", "/actuator/**");
            assertThat(props.getFilterOrder()).isEqualTo(-200);
            assertThat(props.getExceptionHandler().isEnabled()).isFalse();
        }
    }

    @EnableConfigurationProperties(ValidationProperties.class)
    static class PropertiesTestConfig {
    }
}
