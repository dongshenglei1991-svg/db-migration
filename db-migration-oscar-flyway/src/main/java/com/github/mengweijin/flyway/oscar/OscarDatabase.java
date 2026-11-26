package com.github.mengweijin.flyway.oscar;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.extensibility.Tier;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 神舟通用（神通 OSCAR）数据库 Flyway Database 实现。
 *
 * @author mengweijin
 */
public class OscarDatabase extends Database<OscarConnection> {

    public OscarDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory, StatementInterceptor statementInterceptor) {
        super(configuration, jdbcConnectionFactory, statementInterceptor);
    }

    @Override
    protected OscarConnection doGetConnection(Connection connection) {
        return new OscarConnection(this, connection);
    }

    /**
     * 兼容低版本
     * @param identifier identifier
     * @return String
     */
    @Override
    public String doQuote(String identifier) {
        return "\"" + identifier + "\"";
    }

    @Override
    public void ensureSupported(Configuration configuration) {
        // OSCAR 版本号体系（如 7.1）与 Oracle 无对应关系，不做 Oracle 版本门禁。
        ensureDatabaseIsRecentEnough("7");
    }

    @Override
    public String getRawCreateScript(Table table, boolean baseline) {
        String tablespace = configuration.getTablespace() == null
                ? ""
                : " TABLESPACE \"" + configuration.getTablespace() + "\"";

        return "CREATE TABLE " + table + " (\n" +
                "    \"installed_rank\" INT NOT NULL,\n" +
                "    \"version\" VARCHAR2(50),\n" +
                "    \"description\" VARCHAR2(200) NOT NULL,\n" +
                "    \"type\" VARCHAR2(20) NOT NULL,\n" +
                "    \"script\" VARCHAR2(1000) NOT NULL,\n" +
                "    \"checksum\" INT,\n" +
                "    \"installed_by\" VARCHAR2(100) NOT NULL,\n" +
                "    \"installed_on\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,\n" +
                "    \"execution_time\" INT NOT NULL,\n" +
                "    \"success\" NUMBER(1) NOT NULL,\n" +
                "    CONSTRAINT \"" + table.getName() + "_pk\" PRIMARY KEY (\"installed_rank\")\n" +
                ")" + tablespace + ";\n" +
                (baseline ? getBaselineStatement(table) + ";\n" : "") +
                "CREATE INDEX \"" + table.getSchema().getName() + "\".\"" + table.getName() + "_s_idx\" ON " + table + " (\"success\");\n";
    }

    @Override
    public boolean supportsEmptyMigrationDescription() {
        // OSCAR 保留空串（不会像 DM/Oracle 一样隐式转换为 NULL），允许空描述。
        return true;
    }

    @Override
    protected String doGetCurrentUser() throws SQLException {
        return getMainConnection().getJdbcTemplate().queryForString("SELECT USER FROM DUAL");
    }

    @Override
    public boolean supportsDdlTransactions() {
        return false;
    }

    @Override
    public String getBooleanTrue() {
        return "1";
    }

    @Override
    public String getBooleanFalse() {
        return "0";
    }

    @Override
    public boolean catalogIsSchema() {
        return false;
    }

    /**
     * Checks whether the specified query returns rows or not. Wraps the query in EXISTS() SQL function and executes it.
     *
     * @param query The query to check.
     * @param params The query parameters.
     * @return {@code true} if the query returns rows, {@code false} if not.
     * @throws SQLException when the query execution failed.
     */
    boolean queryReturnsRows(String query, String... params) throws SQLException {
        return getMainConnection().getJdbcTemplate().queryForBoolean("SELECT CASE WHEN EXISTS(" + query + ") THEN 1 ELSE 0 END FROM DUAL", params);
    }

    /**
     * Returns the set of OSCAR system schemas that must not be changed in any other way.
     * OSCAR 的 ALL_USERS 没有 ORACLE_MAINTAINED / DM_MAINTAINED 之类的标记列，使用固定清单。
     *
     * @return the set of system schema names
     */
    Set<String> getSystemSchemas() {
        return new HashSet<>(Arrays.asList(
                "SYSDBA", // 默认 DBA 用户
                "SYSAUDIT", // 审计
                "SYSSECURE", // 安全
                "SYSFTSDBA", // 全文检索
                "STAGENT", // 代理
                "DIRECTORIES", // 目录命名空间
                "INFO_SCHEM", // 信息模式
                "PUBLIC", // 公共命名空间
                "REPLICATION", // 复制
                "SYS_GLOBAL_TEMP", // 全局临时表
                "WMSYS" // 工作空间管理
        ));
    }

    /**
     * Fixed Github issue 17：当有 initSql 时，initSql 会被执行 2 次
     * <a href="https://github.com/mengweijin/db-migration/issues/17">https://github.com/mengweijin/db-migration/issues/17</a>
     */
    @Override
    public boolean useSingleConnection() {
        return true;
    }
}
