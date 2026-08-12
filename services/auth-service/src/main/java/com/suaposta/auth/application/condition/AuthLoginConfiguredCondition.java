package com.suaposta.auth.application.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Enables login only when persistence and the shared JWT secret are configured. */
public final class AuthLoginConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return new AuthPersistenceConfiguredCondition().matches(context, metadata)
                && new JwtSecretConfiguredCondition().matches(context, metadata);
    }
}
