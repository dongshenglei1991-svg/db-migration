package com.github.mengweijin.liquibase.oscar.snapshot;

import com.github.mengweijin.liquibase.oscar.database.OscarDatabase;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.CachedRow;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.ColumnSnapshotGenerator;
import liquibase.snapshot.jvm.ColumnSnapshotGeneratorOracle;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.Relation;

import java.sql.SQLException;
import java.util.List;

/**
 * OSCAR 列快照生成器。
 * <p>
 * 替换 Liquibase 核心的 {@link ColumnSnapshotGenerator}：核心 Oracle 分支的列快照 SQL
 * 会联查回收站视图（DBA_RECYCLEBIN / USER_RECYCLEBIN，取决于权限探测结果），OSCAR 均无此视图，
 * 改用 {@link OscarSnapshotQueries#columns} 的 OSCAR 字典 SQL（ALL_TAB_COLS + ALL_COL_COMMENTS）。
 */
public class OscarColumnSnapshotGenerator extends ColumnSnapshotGeneratorOracle {

    private static final String COMPLETE_ATTRIBUTE = "oscar-liquibase-complete";

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        return database instanceof OscarDatabase
                ? OscarSnapshotGeneratorPriority.fromCore(this, objectType, super.getPriority(objectType, database))
                : PRIORITY_NONE;
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[]{ColumnSnapshotGenerator.class, ColumnSnapshotGeneratorOracle.class};
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException {
        Column exampleColumn = (Column) example;
        if (exampleColumn.getAttribute(COMPLETE_ATTRIBUTE, false)) {
            exampleColumn.setAttribute(COMPLETE_ATTRIBUTE, null);
            return exampleColumn;
        }
        Relation relation = exampleColumn.getRelation();
        List<CachedRow> rows = OscarSnapshotQueries.columns(snapshot, relation.getSchema(), relation.getName(),
                exampleColumn.getName());
        if (rows.isEmpty()) {
            return null;
        }
        try {
            Column column = readColumn(rows.get(0), relation, snapshot.getDatabase());
            setAutoIncrementDetails(column, snapshot.getDatabase(), snapshot);
            return column;
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot) throws DatabaseException {
        if (!(foundObject instanceof Relation) || !snapshot.getSnapshotControl().shouldInclude(Column.class)) {
            return;
        }
        Relation relation = (Relation) foundObject;
        try {
            for (CachedRow row : OscarSnapshotQueries.columns(snapshot, relation.getSchema(), relation.getName(), null)) {
                Column column = readColumn(row, relation, snapshot.getDatabase());
                setAutoIncrementDetails(column, snapshot.getDatabase(), snapshot);
                column.setAttribute(COMPLETE_ATTRIBUTE, true);
                relation.getColumns().add(column);
            }
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }
}
