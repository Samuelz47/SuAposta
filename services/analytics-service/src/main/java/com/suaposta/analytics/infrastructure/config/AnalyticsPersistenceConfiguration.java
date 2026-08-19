package com.suaposta.analytics.infrastructure.config;

import com.suaposta.analytics.application.port.out.AnalyticsBetRepository;
import com.suaposta.analytics.application.port.out.ProcessedEventRepository;
import com.suaposta.analytics.application.service.BettingEventProcessor;
import com.suaposta.analytics.infrastructure.persistence.JdbcAnalyticsBetRepository;
import com.suaposta.analytics.infrastructure.persistence.JdbcProcessedEventRepository;
import java.time.Clock;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@EnableConfigurationProperties(DataSourceProperties.class)
@EnableTransactionManagement
public class AnalyticsPersistenceConfiguration {

    @Bean
    DataSource analyticsDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(initMethod = "migrate")
    Flyway analyticsFlyway(DataSource analyticsDataSource) {
        return Flyway.configure().dataSource(analyticsDataSource).load();
    }

    @Bean
    JdbcTemplate analyticsJdbcTemplate(DataSource analyticsDataSource, Flyway analyticsFlyway) {
        return new JdbcTemplate(analyticsDataSource);
    }

    @Bean
    PlatformTransactionManager analyticsTransactionManager(DataSource analyticsDataSource) {
        return new DataSourceTransactionManager(analyticsDataSource);
    }

    @Bean
    AnalyticsBetRepository analyticsBetRepository(JdbcTemplate analyticsJdbcTemplate) {
        return new JdbcAnalyticsBetRepository(analyticsJdbcTemplate);
    }

    @Bean
    ProcessedEventRepository processedEventRepository(JdbcTemplate analyticsJdbcTemplate) {
        return new JdbcProcessedEventRepository(analyticsJdbcTemplate);
    }

    @Bean
    Clock analyticsClock() {
        return Clock.systemUTC();
    }

    @Bean
    BettingEventProcessor bettingEventProcessor(
            AnalyticsBetRepository analyticsBetRepository,
            ProcessedEventRepository processedEventRepository,
            Clock analyticsClock) {
        return new BettingEventProcessor(analyticsBetRepository, processedEventRepository, analyticsClock);
    }
}
