package com.github.mengweijin.liquibase.oscar.snapshot;

import com.github.mengweijin.liquibase.oscar.database.OscarDatabase;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.CachedRow;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.TableSnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;

import java.sql.SQLException;
import java.util.List;

/**
 * OSCAR 表快照生成器。
 * <p>
 * 替换 Liquibase 核心的 {@link TableSnapshotGenerator}：核心版本对 {@code OracleDatabase}
 * 的表快照 SQL 联查 {@code ALL_QUEUE_TABLES}（Oracle AQ），OSCAR 无此视图，改用
 * {@link OscarSnapshotQueries#tables} 的 OSCAR 适配 SQL。
 */
public class OscarTableSnapshotGenerator extends TableSnapshotGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        return database instanceof OscarDatabase
                ? OscarSnapshotGeneratorPriority.fromCore(this, objectType, super.getPriority(objectType, database))
                : PRIORITY_NONE;
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[]{TableSnapshotGenerator.class};
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException {
        List<CachedRow> rows = OscarSnapshotQueries.tables(snapshot, example.getSchema(), example.getName());
        if (rows.isEmpty()) {
            return null;
        }
        try {
            return readTable(rows.get(0), snapshot.getDatabase());
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!(foundObject instanceof Schema) || !snapshot.getSnapshotControl().shouldInclude(Table.class)) {
            return;
        }
        Schema schema = (Schema) foundObject;
        for (CachedRow row : OscarSnapshotQueries.tables(snapshot, schema, null)) {
            schema.addDatabaseObject(new Table().setName(row.getString("TABLE_NAME")).setSchema(schema));
        }
    }
}
