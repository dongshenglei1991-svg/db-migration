package com.github.mengweijin.liquibase.oscar.snapshot;

import com.github.mengweijin.liquibase.oscar.database.OscarDatabase;
import liquibase.CatalogAndSchema;
import liquibase.database.Database;
import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.CachedRow;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.ViewSnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.View;
import liquibase.util.StringUtil;

import java.util.List;

/**
 * OSCAR 视图快照生成器。
 * <p>
 * 替换 Liquibase 核心的 {@link ViewSnapshotGenerator}：核心版本对 {@code OracleDatabase}
 * 的视图快照 SQL 会联查 {@code ALL_REGISTERED_MVIEWS}，OSCAR 无此视图，
 * 改用 {@link OscarSnapshotQueries#views} 的 OSCAR 适配 SQL（ALL_VIEWS + ALL_TAB_COMMENTS）。
 */
public class OscarViewSnapshotGenerator extends ViewSnapshotGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        return database instanceof OscarDatabase
                ? OscarSnapshotGeneratorPriority.fromCore(this, objectType, super.getPriority(objectType, database))
                : PRIORITY_NONE;
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[]{ViewSnapshotGenerator.class};
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException {
        if (((View) example).getDefinition() != null) {
            return example;
        }
        Database database = snapshot.getDatabase();
        List<CachedRow> rows = OscarSnapshotQueries.views(snapshot, example.getSchema(), example.getName());
        if (rows.isEmpty()) {
            return null;
        }
        CachedRow row = rows.get(0);
        CatalogAndSchema catalogAndSchema = ((AbstractJdbcDatabase) database).getSchemaFromJdbcInfo(
                row.getString("TABLE_CAT"), row.getString("TABLE_SCHEM"));
        View view = new View().setName(cleanNameFromDatabase(row.getString("TABLE_NAME"), database));
        view.setSchema(new Schema(catalogAndSchema.getCatalogName(), catalogAndSchema.getSchemaName()));
        view.setRemarks(StringUtil.trimToNull(row.getString("REMARKS")));

        ObjectQuotingStrategy original = database.getObjectQuotingStrategy();
        try {
            database.setObjectQuotingStrategy(ObjectQuotingStrategy.QUOTE_ALL_OBJECTS);
            view.setDefinition(StringUtil.trimToNull(database.getViewDefinition(catalogAndSchema, view.getName())));
        } finally {
            database.setObjectQuotingStrategy(original);
        }
        return view;
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!(foundObject instanceof Schema) || !snapshot.getSnapshotControl().shouldInclude(View.class)) {
            return;
        }
        Schema schema = (Schema) foundObject;
        for (CachedRow row : OscarSnapshotQueries.views(snapshot, schema, null)) {
            View view = new View().setName(
                    cleanNameFromDatabase(row.getString("TABLE_NAME"), snapshot.getDatabase()));
            view.setSchema(schema);
            view.setRemarks(StringUtil.trimToNull(row.getString("REMARKS")));
            view.setDefinition(StringUtil.standardizeLineEndings(row.getString("OBJECT_BODY")));
            schema.addDatabaseObject(view);
        }
    }
}
