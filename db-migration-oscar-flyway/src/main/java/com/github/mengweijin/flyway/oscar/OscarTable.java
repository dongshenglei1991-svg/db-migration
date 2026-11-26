package com.github.mengweijin.flyway.oscar;

import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;

import java.sql.SQLException;

/**
 * 神舟通用（神通 OSCAR）数据库 Flyway Table 实现。
 *
 * @author mengweijin
 */
public class OscarTable extends Table<OscarDatabase, OscarSchema> {
    /**
     * Creates a new OSCAR table.
     *
     * @param jdbcTemplate The Jdbc Template for communicating with the DB.
     * @param database The database-specific support.
     * @param schema The schema this table lives in.
     * @param name The name of the table.
     */
    public OscarTable(JdbcTemplate jdbcTemplate, OscarDatabase database, OscarSchema schema, String name) {
        super(jdbcTemplate, database, schema, name);
    }

    @Override
    protected void doDrop() throws SQLException {
        // OSCAR 不支持 DROP TABLE ... PURGE（无 Oracle 回收站直通语法），只带 CASCADE CONSTRAINTS。
        jdbcTemplate.execute("DROP TABLE " + database.quote(schema.getName(), name) + " CASCADE CONSTRAINTS");
    }

    @Override
    protected boolean doExists() throws SQLException {
        return exists(null, schema, name);
    }

    @Override
    protected void doLock() throws SQLException {
        jdbcTemplate.execute("LOCK TABLE " + this + " IN EXCLUSIVE MODE");
    }
}
