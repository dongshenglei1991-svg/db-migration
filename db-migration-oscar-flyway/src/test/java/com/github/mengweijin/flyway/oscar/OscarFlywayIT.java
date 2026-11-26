package com.github.mengweijin.flyway.oscar;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 神通 OSCAR 真机集成测试。
 * <p>
 * 测试在独立的 {@code OSCAR_IT_SCHEMA}（默认值，可通过 oscar.jdbc.schema 系统属性覆盖）中进行，
 * 前后自动创建/删除该测试模式，绝不触碰其它业务模式的数据。
 * 由 oscar-integration Maven profile 提供 JDBC 驱动与连接信息（环境变量 OSCAR_JDBC_URL 等）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OscarFlywayIT {

    private String url;
    private String username;
    private String password;
    private String schema;

    @BeforeAll
    void setup() throws Exception {
        url = requiredProperty("oscar.jdbc.url");
        username = requiredProperty("oscar.jdbc.username");
        password = requiredProperty("oscar.jdbc.password");
        schema = System.getProperty("oscar.jdbc.schema", "OSCAR_IT_SCHEMA");

        recreateTestSchema();
    }

    @AfterAll
    void tearDown() throws Exception {
        dropTestSchemaQuietly();
    }

    @Test
    void cleanMigrateInfoRepeatMigrateAndClean() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .cleanDisabled(false)
                .load();

        flyway.clean();
        flyway.clean();

        MigrateResult migrateResult = flyway.migrate();
        assertEquals(2, migrateResult.migrationsExecuted);

        assertAll(
                () -> assertEquals(2, flyway.info().applied().length),
                () -> assertNotNull(flyway.info().current()),
                () -> assertEquals("2", flyway.info().current().getVersion().toString()),
                () -> assertEquals(0, flyway.info().pending().length)
        );

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {

            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM " + schema + ".OSCAR_FLYWAY_DEMO")) {
                rs.next();
                assertEquals(2, rs.getInt(1), "迁移脚本应插入 2 行数据");
            }

            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM " + schema + ".\"flyway_schema_history\" WHERE \"success\" = 1")) {
                rs.next();
                assertEquals(2, rs.getInt(1), "history 表应有 2 条成功记录");
            }
        }

        // 重复 migrate 应为空操作
        flyway.migrate();
        assertEquals(2, flyway.info().applied().length);

        // clean 清理所有对象
        flyway.clean();
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT COUNT(*) FROM ALL_OBJECTS WHERE OWNER = '" + schema + "' AND OBJECT_TYPE IN ('TABLE', 'VIEW', 'INDEX', 'SEQUENCE', 'TRIGGER')")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "clean 后 schema 中不应残留业务对象");
        }
    }

    private void recreateTestSchema() throws SQLException {
        dropTestSchemaQuietly();
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE USER " + schema + " WITH PASSWORD 'Fflyy00!!2026'");
        }
    }

    private void dropTestSchemaQuietly() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            try {
                statement.execute("DROP USER " + schema + " CASCADE");
            } catch (SQLException ignored) {
                // 模式不存在，忽略
            }
        }
    }

    private String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw new IllegalStateException(name + " must be configured by the oscar-integration Maven profile");
        }
        return value;
    }
}
