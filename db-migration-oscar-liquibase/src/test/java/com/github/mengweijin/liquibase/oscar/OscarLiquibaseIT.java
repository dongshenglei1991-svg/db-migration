package com.github.mengweijin.liquibase.oscar;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.changelog.ChangeLogHistoryServiceFactory;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.diff.DiffGeneratorFactory;
import liquibase.diff.DiffResult;
import liquibase.diff.compare.CompareControl;
import liquibase.executor.ExecutorService;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.core.ForeignKey;
import liquibase.structure.core.Index;
import liquibase.structure.core.PrimaryKey;
import liquibase.structure.core.Table;
import liquibase.structure.core.UniqueConstraint;
import liquibase.structure.core.View;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.SQLException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 神通 OSCAR 真机集成测试。
 * <p>
 * 测试在独立的 {@code OSCAR_IT_SCHEMA}（默认值，可通过 oscar.jdbc.schema 系统属性覆盖）中进行，
 * 前后自动创建/删除该测试模式，绝不触碰其它业务模式的数据。
 * 由 oscar-integration Maven profile 提供 JDBC 驱动与连接信息（环境变量 OSCAR_JDBC_URL 等）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OscarLiquibaseIT {

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

    @Test
    void updateStatusRollbackReupdateLockSnapshotAndDiff() throws Exception {
        ensureClean(url, username, password);
        resetLiquibaseServices();
        updateStatusAndRollback(url, username, password);
        resetLiquibaseServices();
        reupdateLockSnapshotAndDiff(url, username, password);
    }

    private Database connect(String url, String username, String password) throws Exception {
        Connection connection = DriverManager.getConnection(url, username, password);
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName(schema);
        return database;
    }

    private void ensureClean(String url, String username, String password) throws Exception {
        Database database = connect(url, username, password);
        try {
            try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor();
                 Liquibase liquibase = new Liquibase("oscar-integration-changelog.xml", accessor, database)) {
                Contexts contexts = new Contexts();
                LabelExpression labels = new LabelExpression();
                int totalChangeSets = liquibase.getDatabaseChangeLog().getChangeSets().size();
                int appliedChangeSets = totalChangeSets - liquibase.listUnrunChangeSets(contexts, labels).size();
                if (appliedChangeSets > 0) {
                    liquibase.rollback(appliedChangeSets, contexts, labels);
                }
            }
        } finally {
            closeQuietly(database);
        }
    }

    private void resetLiquibaseServices() {
        ChangeLogHistoryServiceFactory.getInstance().resetAll();
        LockServiceFactory.getInstance().resetAll();
        Scope.getCurrentScope().getSingleton(ExecutorService.class).reset();
    }

    private void updateStatusAndRollback(String url, String username, String password) throws Exception {
        Database database = connect(url, username, password);
        try {
            assertEquals("oscar", database.getShortName());

            try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor();
                 Liquibase liquibase = new Liquibase("oscar-integration-changelog.xml", accessor, database)) {
                Contexts contexts = new Contexts();
                LabelExpression labels = new LabelExpression();
                int totalChangeSets = liquibase.getDatabaseChangeLog().getChangeSets().size();

                liquibase.update(contexts, labels);
                assertTrue(liquibase.listUnrunChangeSets(contexts, labels).isEmpty());

                liquibase.rollback(totalChangeSets, contexts, labels);
                assertEquals(totalChangeSets, liquibase.listUnrunChangeSets(contexts, labels).size());
            }
        } finally {
            closeQuietly(database);
        }
    }

    private void reupdateLockSnapshotAndDiff(String url, String username, String password) throws Exception {
        Database database = connect(url, username, password);
        try {
            try (ClassLoaderResourceAccessor accessor = new ClassLoaderResourceAccessor();
                 Liquibase liquibase = new Liquibase("oscar-integration-changelog.xml", accessor, database)) {
                Contexts contexts = new Contexts();
                LabelExpression labels = new LabelExpression();
                liquibase.update(contexts, labels);
                assertTrue(liquibase.listUnrunChangeSets(contexts, labels).isEmpty());

                LockService lockService = LockServiceFactory.getInstance().getLockService(database);
                lockService.init();
                lockService.forceReleaseLock();
                assertTrue(lockService.acquireLock());
                assertTrue(lockService.hasChangeLogLock());
                lockService.releaseLock();
                assertFalse(lockService.hasChangeLogLock());

                SnapshotControl control = new SnapshotControl(database);
                DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance()
                        .createSnapshot(database.getDefaultSchema(), database, control);
                Table parent = snapshot.get(Table.class).stream()
                        .filter(table -> "OSCAR_LB_PARENT".equalsIgnoreCase(table.getName()))
                        .findFirst().orElseThrow();
                Table child = snapshot.get(Table.class).stream()
                        .filter(table -> "OSCAR_LB_CHILD".equalsIgnoreCase(table.getName()))
                        .findFirst().orElseThrow();
                Table autoinc = snapshot.get(Table.class).stream()
                        .filter(table -> "OSCAR_LB_AUTOINC".equalsIgnoreCase(table.getName()))
                        .findFirst().orElseThrow();
                ForeignKey childToParent = child.getOutgoingForeignKeys().stream()
                        .filter(foreignKey -> "FK_OSCAR_LB_CHILD_PARENT".equalsIgnoreCase(foreignKey.getName()))
                        .findFirst().orElseThrow();
                assertAll(
                        () -> assertTrue(control.shouldInclude(UniqueConstraint.class)),
                        () -> assertEquals(2, parent.getColumns().size()),
                        () -> assertTrue(parent.getPrimaryKey() != null),
                        () -> assertFalse(parent.getUniqueConstraints().isEmpty()),
                        () -> assertEquals(3, child.getColumns().size()),
                        () -> assertTrue(child.getPrimaryKey() != null),
                        () -> assertEquals(child.getSchema().getName(),
                                childToParent.getForeignKeyTable().getSchema().getName()),
                        () -> assertTrue(child.getIndexes().stream().map(Index::getName)
                                .anyMatch("IDX_OSCAR_LB_CHILD_PARENT"::equalsIgnoreCase)),
                        () -> assertTrue(snapshot.get(View.class).stream()
                                .anyMatch(view -> "OSCAR_LB_CHILD_VIEW".equalsIgnoreCase(view.getName())))
                );

                // autoIncrement 列：两条 INSERT 未显式指定 ID，应被隐式序列填充为 1、2。
                // 注意：getWrappedConnection() 返回的是 Database 共享的底层连接，不能关闭，
                // 只关闭 Statement 与 ResultSet。
                Connection wrappedConnection = ((JdbcConnection) database.getConnection()).getWrappedConnection();
                try (Statement statement = wrappedConnection.createStatement();
                     ResultSet rs = statement.executeQuery("SELECT ID, NAME FROM " + schema + ".OSCAR_LB_AUTOINC ORDER BY ID")) {
                    assertTrue(rs.next());
                    assertAll(
                            () -> assertEquals(1, rs.getInt("ID")),
                            () -> assertEquals("auto-one", rs.getString("NAME")),
                            () -> assertTrue(rs.next()),
                            () -> assertEquals(2, rs.getInt("ID"))
                    );
                }

                DatabaseSnapshot comparison = SnapshotGeneratorFactory.getInstance()
                        .createSnapshot(database.getDefaultSchema(), database, control);
                DiffResult diff = DiffGeneratorFactory.getInstance()
                        .compare(snapshot, comparison, new CompareControl());
                assertTrue(diff.areEqual());
            }
        } finally {
            closeQuietly(database);
        }
    }

    private void closeQuietly(Database database) {
        try {
            database.close();
        } catch (Throwable closeError) {
            System.err.println("Error closing database (ignored): " + closeError.getMessage());
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
