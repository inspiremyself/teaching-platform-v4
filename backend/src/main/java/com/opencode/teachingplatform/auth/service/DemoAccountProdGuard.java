package com.opencode.teachingplatform.auth.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * prod 启动后禁用演示账号。Flyway 仍按原有加载范围创建结构与种子数据，
 * 本守卫在 migrate() 之后把演示账号置为 DISABLED，避免公开口令可登录。
 */
@Component
@Profile("prod")
public class DemoAccountProdGuard implements ApplicationRunner {

    private static final String SQL = "UPDATE sys_user SET status = 'DISABLED' "
            + "WHERE school_id = 1 AND status = 'ACTIVE' "
            + "AND username IN ('t9001','20260001','20260002','admin9001','t9002')";

    private final JdbcTemplate jdbcTemplate;

    public DemoAccountProdGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.update(SQL);
    }
}
