package com.opencode.teachingplatform.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 迁移策略：先 repair 再 migrate。
 *
 * <p>Flyway repair 会自动清理两类元数据层问题，避免 DBA 手动操作 flyway_schema_history：</p>
 * <ol>
 *   <li><b>checksum 不匹配</b>：migration 文件内容被修改（如双方言化修复、拆内联 INDEX）后，
 *       已执行的历史 migration 在新启动实例上会触发 "Migration checksum mismatch for migration version X"。
 *       repair 会基于磁盘上的当前文件重新计算 checksum 并更新历史记录，使得 validate 通过。</li>
 *   <li><b>失败标记</b>：上次运行中途崩溃导致当前版本标记为失败，repair 会清除该标记以便重跑。</li>
 * </ol>
 *
 * <p>等价于 CLI 的 {@code flyway repair} 动作，且对 MySQL（local /profile A）、
 * H2 文件库（backend-h2.sh）、H2 内存库（单测 @DataJpaTest）三种 profile 均有效。</p>
 *
 * <p>项目内无自定义 Flyway 配置，完全吃 Spring Boot 自动装配。
 * 这里通过覆盖默认的 FlywayMigrationStrategy Bean 来注入 repair 前置步骤。</p>
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairBeforeMigrateStrategy() {
        return flyway -> {
            // repair 不会触碰数据本身，只修正 flyway_schema_history 的元数据
            flyway.repair();
            flyway.migrate();
        };
    }
}
