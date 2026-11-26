package com.github.mengweijin.flyway.oscar;

import org.flywaydb.core.internal.database.base.Connection;
import org.flywaydb.core.internal.database.base.Schema;

import java.sql.SQLException;

/**
 * 神舟通用（神通 OSCAR）数据库 Flyway Connection 实现。
 *
 * @author mengweijin
 */
public class OscarConnection extends Connection<OscarDatabase> {

    OscarConnection(OscarDatabase database, java.sql.Connection connection) {
        super(database, connection);
    }

    @Override
    protected String getCurrentSchemaNameOrSearchPath() throws SQLException {
        // OSCAR 驱动实现了 JDBC 标准的 getSchema()，与 doChangeCurrentSchemaOrSearchPathTo 保持同一数据源。
        return jdbcTemplate.getConnection().getSchema();
    }

    /**
     * OSCAR 不支持 ALTER SESSION SET CURRENT_SCHEMA / SET SCHEMA 语句，
     * 只能通过 JDBC 标准的 Connection#setSchema 切换当前模式（驱动会在服务端会话生效）。
     */
    @Override
    public void doChangeCurrentSchemaOrSearchPathTo(String schema) throws SQLException {
        jdbcTemplate.getConnection().setSchema(schema);
    }

    @Override
    public Schema getSchema(String name) {
        return new OscarSchema(jdbcTemplate, database, name);
    }
}
