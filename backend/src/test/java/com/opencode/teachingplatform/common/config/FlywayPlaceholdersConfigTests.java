package com.opencode.teachingplatform.common.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class FlywayPlaceholdersConfigTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void repairBeforeMigrateStrategyBeanIsRemoved() {
        assertFalse(applicationContext.containsBean("repairBeforeMigrateStrategy"));
    }

    @Test
    void h2DialectPlaceholders() throws Exception {
        Map<String, String> values = placeholdersFor("H2");
        assertEquals(4, values.size());
        assertEquals("ALTER COLUMN", values.get("alterModifyColumn"));
        assertEquals("SET REFERENTIAL_INTEGRITY FALSE", values.get("fkChecksDisable"));
        assertEquals("SET REFERENTIAL_INTEGRITY TRUE", values.get("fkChecksEnable"));
        assertEquals("--", values.get("mysqlOnly"));
    }

    @Test
    void mysqlDialectPlaceholders() throws Exception {
        Map<String, String> values = placeholdersFor("MySQL");
        assertEquals(4, values.size());
        assertEquals("MODIFY COLUMN", values.get("alterModifyColumn"));
        assertEquals("SET FOREIGN_KEY_CHECKS = 0", values.get("fkChecksDisable"));
        assertEquals("SET FOREIGN_KEY_CHECKS = 1", values.get("fkChecksEnable"));
        assertEquals("", values.get("mysqlOnly"));
    }

    @Test
    void unknownDialectFailsFast() throws Exception {
        DataSource dataSource = dataSourceReturning("PostgreSQL");
        assertThrows(IllegalStateException.class, () ->
                new FlywayPlaceholdersConfig().flywayPlaceholders(dataSource)
                        .customize(new FluentConfiguration()));
    }

    @Test
    void connectionFailureFailsFast() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getConnection()).thenThrow(new SQLException("database down"));
        assertThrows(IllegalStateException.class, () ->
                new FlywayPlaceholdersConfig().flywayPlaceholders(dataSource)
                        .customize(new FluentConfiguration()));
    }

    private static Map<String, String> placeholdersFor(String productName) throws Exception {
        FluentConfiguration configuration = new FluentConfiguration();
        new FlywayPlaceholdersConfig()
                .flywayPlaceholders(dataSourceReturning(productName))
                .customize(configuration);
        return configuration.getPlaceholders();
    }

    private static DataSource dataSourceReturning(String productName) throws SQLException {
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        Mockito.when(metaData.getDatabaseProductName()).thenReturn(productName);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(connection.getMetaData()).thenReturn(metaData);
        DataSource dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }
}
