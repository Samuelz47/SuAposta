package com.suaposta.auth.infrastructure.config;

import com.suaposta.auth.application.condition.AuthPersistenceConfiguredCondition;
import com.suaposta.auth.infrastructure.persistence.UserEntity;
import com.suaposta.auth.infrastructure.persistence.UserJpaRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.StringUtils;

@Configuration
@Conditional(AuthPersistenceConfiguredCondition.class)
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackageClasses = UserJpaRepository.class,
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager")
public class AuthPersistenceConfiguration {

    @Bean
    public DataSource dataSource(Environment environment) {
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(firstText(
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("AUTH_DB_JDBC_URL"),
                "jdbc:postgresql://"
                        + environment.getProperty("POSTGRES_HOST", "localhost")
                        + ":"
                        + environment.getProperty("POSTGRES_PORT", "5432")
                        + "/"
                        + environment.getProperty("AUTH_DB_NAME", "suaposta_auth")));
        dataSource.setUsername(firstText(
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("AUTH_DB_USER")));
        dataSource.setPassword(firstText(
                environment.getProperty("spring.datasource.password"),
                environment.getProperty("AUTH_DB_PASSWORD")));
        return dataSource;
    }

    @Bean
    public Flyway flyway(DataSource dataSource) {
        var flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean(name = "entityManagerFactory")
    @DependsOn("flyway")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        var vendorAdapter = new HibernateJpaVendorAdapter();
        var entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPackagesToScan(UserEntity.class.getPackageName());
        entityManagerFactory.setJpaVendorAdapter(vendorAdapter);
        entityManagerFactory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.jdbc.time_zone", "UTC"));
        return entityManagerFactory;
    }

    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static String firstText(String first, String second, String fallback) {
        var selected = firstText(first, second);
        return StringUtils.hasText(selected) ? selected : fallback;
    }
}
