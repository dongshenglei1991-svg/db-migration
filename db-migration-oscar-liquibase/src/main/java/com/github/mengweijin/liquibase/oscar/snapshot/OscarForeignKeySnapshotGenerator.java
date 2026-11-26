package com.github.mengweijin.liquibase.oscar.snapshot;

import com.github.mengweijin.liquibase.oscar.database.OscarDatabase;
import liquibase.CatalogAndSchema;
import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.Database;
import liquibase.diff.compare.DatabaseObjectComparatorFactory;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.CachedRow;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.ForeignKeySnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.ForeignKey;
import liquibase.structure.core.Index;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OSCAR 外键快照生成器。
 * <p>
 * 替换 Liquibase 核心的 {@link ForeignKeySnapshotGenerator}：核心 Oracle 分支的外键快照 SQL
 * 会联查回收站视图，OSCAR 无此视图，改用 {@link OscarSnapshotQueries#foreignKeys} 的 OSCAR 字典 SQL。
 */
public class OscarForeignKeySnapshotGenerator extends ForeignKeySnapshotGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        return database instanceof OscarDatabase
                ? OscarSnapshotGeneratorPriority.fromCore(this, objectType, super.getPriority(objectType, database))
                : PRIORITY_NONE;
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[]{ForeignKeySnapshotGenerator.class};
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!(foundObject instanceof Table) || !snapshot.getSnapshotControl().shouldInclude(ForeignKey.class)) {
            return;
        }
        Table table = (Table) foundObject;
        Set<String> seen = new HashSet<>();
        for (CachedRow row : OscarSnapshotQueries.foreignKeys(snapshot, table.getSchema(), table.getName(), null)) {
            String name = row.getString("FK_NAME");
            if (seen.add(name)) {
                table.getOutgoingForeignKeys().add(new ForeignKey().setName(name).setForeignKeyTable(table));
            }
        }
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        ForeignKey requested = (ForeignKey) example;
        Table requestedTable = requested.getForeignKeyTable();
        List<CachedRow> rows = OscarSnapshotQueries.foreignKeys(snapshot, requestedTable.getSchema(),
                requestedTable.getName(), example.getName());
        if (rows.isEmpty()) {
            return null;
        }
        // rows 已按 FK 约束名 + 外键列序号（KEY_SEQ）排序，第一个行携带约束元数据。
        CachedRow first = rows.get(0);
        String exampleName = example.getName();
        if (exampleName != null && !exampleName.equalsIgnoreCase(first.getString("FK_NAME"))) {
            return null;
        }
        ForeignKey result = createForeignKey(first, snapshot);

        // 被引用约束的列，按约束内序号排序，与外键列按位配对（OSCAR 的 ALL_CONS_COLUMNS.POSITION
        // 对外键存储的是表内序号，两侧无法在 SQL 中对齐）。
        List<CachedRow> pkColumns = OscarSnapshotQueries.referencedKeyColumns(snapshot,
                first.getString("R_OWNER"), first.getString("R_CONSTRAINT_NAME"));

        int paired = 0;
        for (CachedRow row : rows) {
            Column fkColumn = new Column(cleanNameFromDatabase(row.getString("FKCOLUMN_NAME"), snapshot.getDatabase()))
                    .setRelation(result.getForeignKeyTable());
            result.addForeignKeyColumn(fkColumn);
            if (paired < pkColumns.size()) {
                Column pkColumn = new Column(cleanNameFromDatabase(
                        pkColumns.get(paired).getString("COLUMN_NAME"), snapshot.getDatabase()))
                        .setRelation(result.getPrimaryKeyTable());
                result.addPrimaryKeyColumn(pkColumn);
            }
            paired++;
        }

        Index backingIndex = new Index().setRelation(result.getForeignKeyTable());
        backingIndex.getColumns().addAll(result.getForeignKeyColumns());
        backingIndex.addAssociatedWith(Index.MARK_FOREIGN_KEY);
        result.setBackingIndex(backingIndex);
        if (snapshot.get(ForeignKey.class).contains(result)) {
            return null;
        }
        return result;
    }

    private ForeignKey createForeignKey(CachedRow row, DatabaseSnapshot snapshot) throws DatabaseException {
        Database database = snapshot.getDatabase();
        ForeignKey foreignKey = new ForeignKey().setName(row.getString("FK_NAME"));
        CatalogAndSchema fkSchema = ((AbstractJdbcDatabase) database).getSchemaFromJdbcInfo(
                row.getString("FKTABLE_CAT"), row.getString("FKTABLE_SCHEM"));
        Table fkTable = new Table().setName(row.getString("FKTABLE_NAME"));
        fkTable.setSchema(new Schema(fkSchema.getCatalogName(), fkSchema.getSchemaName()));
        foreignKey.setForeignKeyTable(fkTable);

        CatalogAndSchema pkSchema = ((AbstractJdbcDatabase) database).getSchemaFromJdbcInfo(
                row.getString("PKTABLE_CAT"), row.getString("PKTABLE_SCHEM"));
        Table pkTable = (Table) new Table().setName(row.getString("PKTABLE_NAME"))
                .setSchema(new Schema(pkSchema.getCatalogName(), pkSchema.getSchemaName()));
        foreignKey.setPrimaryKeyTable(pkTable);
        foreignKey.setUpdateRule(convertToForeignKeyConstraintType(row.getInt("UPDATE_RULE"), database));
        foreignKey.setDeleteRule(convertToForeignKeyConstraintType(row.getInt("DELETE_RULE"), database));

        setDeferrability(foreignKey, row);
        foreignKey.setShouldValidate("VALIDATED".equalsIgnoreCase(row.getString("FK_VALIDATE")));
        return foreignKey;
    }

    static void setDeferrability(ForeignKey foreignKey, CachedRow row) {
        boolean deferrable = matchesCatalogValue(row.getString("FK_DEFERRABLE"), "DEFERRABLE");
        foreignKey.setDeferrable(deferrable);
        foreignKey.setInitiallyDeferred(deferrable
                && matchesCatalogValue(row.getString("FK_DEFERRED"), "DEFERRED"));
    }

    private static boolean matchesCatalogValue(String actual, String expected) {
        return actual != null && expected.equalsIgnoreCase(actual.trim());
    }

    private boolean containsColumn(ForeignKey foreignKey, Column candidate, DatabaseSnapshot snapshot) {
        for (Column existing : foreignKey.getForeignKeyColumns()) {
            if (DatabaseObjectComparatorFactory.getInstance().isSameObject(existing, candidate,
                    snapshot.getSchemaComparisons(), snapshot.getDatabase())) {
                return true;
            }
        }
        return false;
    }
}
