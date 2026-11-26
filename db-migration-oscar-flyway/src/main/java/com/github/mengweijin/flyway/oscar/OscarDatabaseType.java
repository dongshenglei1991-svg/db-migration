package com.github.mengweijin.flyway.oscar;

import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.callback.CallbackExecutor;
import org.flywaydb.core.internal.database.DatabaseType;
import org.flywaydb.core.internal.database.base.BaseDatabaseType;
import org.flywaydb.core.internal.jdbc.JdbcConnectionFactory;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.core.internal.parser.Parser;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.sqlscript.SqlScriptExecutorFactory;

import java.sql.Connection;
import java.sql.Types;

/**
 * 神舟通用（神通 OSCAR）数据库 Flyway DatabaseType。
 * <p>
 * 方言实测（OSCAR 7.0.8，驱动 7.20）：
 * <ul>
 *     <li>数据库产品名：OSCAR</li>
 *     <li>驱动类：com.oscar.Driver，URL：jdbc:oscar://host:2003/db，默认端口 2003</li>
 *     <li>Oracle 兼容：DUAL、ROWNUM、SYS_CONTEXT、VARCHAR2/NUMBER、序列、PL/SQL 块、PACKAGE/TYPE</li>
 *     <li>无 ALTER SESSION / SET SCHEMA 语句，切 schema 需使用 JDBC API Connection#setSchema</li>
 * </ul>
 *
 * @author mengweijin
 */
public class OscarDatabaseType extends BaseDatabaseType {

    @Override
    public String getName() {
        return "OSCAR";
    }

    @Override
    public int getNullType() {
        return Types.VARCHAR;
    }

    @Override
    public boolean handlesJDBCUrl(String url) {
        return url.startsWith("jdbc:oscar") || url.startsWith("jdbc:p6spy:oscar");
    }

    @Override
    public String getDriverClass(String url, ClassLoader classLoader) {
        if (url.startsWith("jdbc:p6spy:oscar")) {
            return "com.p6spy.engine.spy.P6SpyDriver";
        }
        return "com.oscar.Driver";
    }

    @Override
    public boolean handlesDatabaseProductNameAndVersion(String databaseProductName, String databaseProductVersion, Connection connection) {
        return databaseProductName.startsWith("OSCAR");
    }

    @Override
    public org.flywaydb.core.internal.database.base.Database createDatabase(Configuration configuration, JdbcConnectionFactory jdbcConnectionFactory, StatementInterceptor statementInterceptor) {
        return new OscarDatabase(configuration, jdbcConnectionFactory, statementInterceptor);
    }

    @Override
    public Parser createParser(Configuration configuration, ResourceProvider resourceProvider, ParsingContext parsingContext) {
        return new OscarParser(configuration, parsingContext);
    }

    @Override
    public SqlScriptExecutorFactory createSqlScriptExecutorFactory(JdbcConnectionFactory jdbcConnectionFactory,
                                                                   final CallbackExecutor callbackExecutor,
                                                                   final StatementInterceptor statementInterceptor) {
        final boolean supportsBatch = jdbcConnectionFactory.isSupportsBatch();

        final DatabaseType thisRef = this;

        return (connection, undo, batch, outputQueryResults) -> {
            if (!supportsBatch) {
                batch = false;
            }

            return new OscarSqlScriptExecutor(new JdbcTemplate(connection, thisRef), callbackExecutor, undo, batch, outputQueryResults, statementInterceptor);
        };
    }
}
