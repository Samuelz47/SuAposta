package com.suaposta.auth.infrastructure.config;

import com.suaposta.auth.application.condition.JwtSecretConfiguredCondition;
import com.suaposta.auth.application.port.AccessTokenGenerator;
import com.suaposta.auth.infrastructure.security.Hs256JwtAccessTokenGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(JwtSecretConfiguredCondition.class)
public class JwtTokenConfiguration {

    @Bean
    public AccessTokenGenerator accessTokenGenerator(@Value("${JWT_SECRET}") String signingSecret) {
        return new Hs256JwtAccessTokenGenerator(signingSecret);
    }
}
