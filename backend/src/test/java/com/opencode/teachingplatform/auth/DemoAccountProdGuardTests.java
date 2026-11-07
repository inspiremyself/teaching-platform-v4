package com.opencode.teachingplatform.auth;

import com.opencode.teachingplatform.auth.service.DemoAccountProdGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DemoAccountProdGuardTests {

    @Test
    void disablesExactlyFiveDemoAccountsScopedToDemoSchool() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        new DemoAccountProdGuard(jdbcTemplate).run(mock(ApplicationArguments.class));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(1)).update(sql.capture());
        String statement = sql.getValue();
        assertTrue(statement.contains("UPDATE sys_user SET status = 'DISABLED'"));
        assertTrue(statement.contains("school_id = 1"));
        assertTrue(statement.contains("status = 'ACTIVE'"));
        assertTrue(statement.contains("'t9001'"));
        assertTrue(statement.contains("'20260001'"));
        assertTrue(statement.contains("'20260002'"));
        assertTrue(statement.contains("'admin9001'"));
        assertTrue(statement.contains("'t9002'"));
    }

    @Test
    void doesNotDeleteRowsOrTouchOtherTables() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        new DemoAccountProdGuard(jdbcTemplate).run(mock(ApplicationArguments.class));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture());
        String statement = sql.getValue().toUpperCase();
        assertFalse(statement.contains("DELETE"));
        assertFalse(statement.contains("DROP"));
        assertFalse(statement.contains("TRUNCATE"));
        assertTrue(statement.startsWith("UPDATE SYS_USER"));
    }

    @Test
    void executesSameUpdateOnRepeatedStartup() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DemoAccountProdGuard guard = new DemoAccountProdGuard(jdbcTemplate);
        ApplicationArguments args = mock(ApplicationArguments.class);

        guard.run(args);
        guard.run(args);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(sql.capture());
        assertEquals(sql.getAllValues().get(0), sql.getAllValues().get(1));
    }
}
