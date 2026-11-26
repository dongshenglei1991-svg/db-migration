package com.github.mengweijin.flyway.oscar;

import org.flywaydb.core.internal.callback.CallbackExecutor;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;
import org.flywaydb.core.internal.jdbc.StatementInterceptor;
import org.flywaydb.core.internal.sqlscript.DefaultSqlScriptExecutor;

/**
 * 神舟通用（神通 OSCAR）数据库 Flyway SqlScriptExecutor 实现。
 *
 * @author mengweijin
 */
@SuppressWarnings("SqlResolve")
public class OscarSqlScriptExecutor extends DefaultSqlScriptExecutor {

    public OscarSqlScriptExecutor(JdbcTemplate jdbcTemplate, CallbackExecutor callbackExecutor, boolean undo, boolean batch, boolean outputQueryResults, StatementInterceptor statementInterceptor) {
        super(jdbcTemplate, callbackExecutor, undo, batch, outputQueryResults, statementInterceptor);
    }

}
