package com.suaposta.betting.infrastructure.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public final class BettingPersistenceConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        var springDataSourceConfigured =
                hasText(environment.getProperty("spring.datasource.url"))
                        && hasText(environment.getProperty("spring.datasource.username"))
                        && hasText(environment.getProperty("spring.datasource.password"));
        var bettingDataSourceConfigured =
                hasText(environment.getProperty("BETTING_DB_JDBC_URL"))
                        && hasText(environment.getProperty("BETTING_DB_USER"))
                        && hasText(environment.getProperty("BETTING_DB_PASSWORD"));
        var composedDataSourceConfigured =
                hasText(environment.getProperty("POSTGRES_HOST"))
                        && hasText(environment.getProperty("POSTGRES_PORT"))
                        && hasText(environment.getProperty("BETTING_DB_NAME"))
                        && hasText(environment.getProperty("BETTING_DB_USER"))
                        && hasText(environment.getProperty("BETTING_DB_PASSWORD"));
        return springDataSourceConfigured || bettingDataSourceConfigured || composedDataSourceConfigured;
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
