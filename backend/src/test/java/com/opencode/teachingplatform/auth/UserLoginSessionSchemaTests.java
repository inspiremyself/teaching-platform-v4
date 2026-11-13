package com.opencode.teachingplatform.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserLoginSessionSchemaTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sessionTableShouldHaveNoForeignKeyToSysUser() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE UPPER(TABLE_SCHEMA) = 'PUBLIC' "
                        + "AND UPPER(CONSTRAINT_NAME) = 'FK_USER_LOGIN_SESSION_USER'",
                Integer.class
        );
        assertThat(count).isZero();
    }

    @Test
    void sessionTableShouldHaveUserStatusIndex() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                        + "WHERE UPPER(TABLE_SCHEMA) = 'PUBLIC' "
                        + "AND UPPER(TABLE_NAME) = 'USER_LOGIN_SESSION' "
                        + "AND UPPER(INDEX_NAME) = 'IDX_USER_LOGIN_SESSION_USER_STATUS'",
                Integer.class
        );
        assertThat(count).isNotNull().isGreaterThanOrEqualTo(1);
    }
}
