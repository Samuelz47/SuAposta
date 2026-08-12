package com.suaposta.auth.application.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Enables Auth persistence only when a complete datasource configuration is available.
 */
public final class AuthPersistenceConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();

        var springDataSourceConfigured =
                hasText(environment.getProperty("spring.datasource.url"))
                        && hasText(environment.getProperty("spring.datasource.username"))
                        && hasText(environment.getProperty("spring.datasource.password"));

        var authJdbcUrlConfigured =
                hasText(environment.getProperty("AUTH_DB_JDBC_URL"))
                        && hasText(environment.getProperty("AUTH_DB_USER"))
                        && hasText(environment.getProperty("AUTH_DB_PASSWORD"));

        var authComposedDataSourceConfigured =
                hasText(environment.getProperty("POSTGRES_HOST"))
                        && hasText(environment.getProperty("POSTGRES_PORT"))
                        && hasText(environment.getProperty("AUTH_DB_NAME"))
                        && hasText(environment.getProperty("AUTH_DB_USER"))
                        && hasText(environment.getProperty("AUTH_DB_PASSWORD"));

        return springDataSourceConfigured
                || authJdbcUrlConfigured
                || authComposedDataSourceConfigured;
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}