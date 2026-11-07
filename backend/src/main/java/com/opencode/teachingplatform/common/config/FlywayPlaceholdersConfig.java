package com.opencode.teachingplatform.common.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

/**
 * 按数据库方言注入 V15 / V16 / V18 所需的双方言占位符。
 *
 * <p>此前四个值散落在 application.yml、application-test.yml 与测试资源里，新增 profile
 * 必须手工补齐，漏配就会在迁移期失败。这里改为从 DataSource 元数据判定方言，所有
 * profile 共用同一套值；未知方言或连接失败一律让启动失败，不回退到任何默认方言。</p>
 */
@Configuration
public class FlywayPlaceholdersConfig {

    @Bean
    public FlywayConfigurationCustomizer flywayPlaceholders(DataSource dataSource) {
        return configuration -> configuration.placeholders(placeholdersFor(dataSource));
    }

    static Map<String, String> placeholdersFor(DataSource dataSource) {
        String product;
        try (Connection connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取数据库产品名以注入 Flyway 双方言占位符", ex);
        }
        if (product == null) {
            throw new IllegalStateException("数据库产品名为空，无法注入 Flyway 双方言占位符");
        }
        String normalized = product.toLowerCase(Locale.ROOT);
        if (normalized.contains("h2")) {
            return Map.of(
                    "alterModifyColumn", "ALTER COLUMN",
                    "fkChecksDisable", "SET REFERENTIAL_INTEGRITY FALSE",
                    "fkChecksEnable", "SET REFERENTIAL_INTEGRITY TRUE",
                    "mysqlOnly", "--");
        }
        if (normalized.contains("mysql")) {
            return Map.of(
                    "alterModifyColumn", "MODIFY COLUMN",
                    "fkChecksDisable", "SET FOREIGN_KEY_CHECKS = 0",
                    "fkChecksEnable", "SET FOREIGN_KEY_CHECKS = 1",
                    "mysqlOnly", "");
        }
        throw new IllegalStateException("未支持的数据库方言: " + product);
    }
}
