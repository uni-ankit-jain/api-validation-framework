package com.uniphore.platform.validation.autoconfigure;

import com.uniphore.platform.validation.aspect.FieldConstraintsAspect;
import com.uniphore.platform.validation.filter.HeaderValidationFilter;
import com.uniphore.platform.validation.handler.ValidationExceptionHandler;
import com.uniphore.platform.validation.properties.ValidationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spring Boot auto-configuration entry point for the API Validation Framework.
 *
 * <p>Activated only for servlet-based web applications when
 * {@code uniphore.validation.enabled=true} (the default).
 *
 * <p>Both the filter and the exception handler are guarded by {@code @ConditionalOnMissingBean}
 * so consuming services can subclass or fully replace either component without classpath conflicts.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "uniphore.validation", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ValidationProperties.class)
public class ValidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HeaderValidationFilter.class)
    public FilterRegistrationBean<HeaderValidationFilter> headerValidationFilterRegistration(
            ValidationProperties properties,
            RequestMappingHandlerMapping requestMappingHandlerMapping) {

        HeaderValidationFilter filter = new HeaderValidationFilter(properties, requestMappingHandlerMapping);
        FilterRegistrationBean<HeaderValidationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(properties.getFilterOrder());
        registration.addUrlPatterns("/*");
        registration.setName("headerValidationFilter");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(FieldConstraintsAspect.class)
    public FieldConstraintsAspect fieldConstraintsAspect() {
        return new FieldConstraintsAspect();
    }

    @Bean
    @ConditionalOnMissingBean(ValidationExceptionHandler.class)
    @ConditionalOnProperty(
            prefix = "uniphore.validation.exception-handler",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public ValidationExceptionHandler validationExceptionHandler() {
        return new ValidationExceptionHandler();
    }
}
