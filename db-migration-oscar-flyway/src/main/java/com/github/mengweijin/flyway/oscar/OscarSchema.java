package com.github.mengweijin.flyway.oscar;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogFactory;
import org.flywaydb.core.internal.database.base.Schema;
import org.flywaydb.core.internal.database.base.Table;
import org.flywaydb.core.internal.jdbc.JdbcTemplate;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 神舟通用（神通 OSCAR）数据库 Flyway Schema 实现。
 *
 * @author mengweijin
 */
public class OscarSchema extends Schema<OscarDatabase, OscarTable> {

    private static final Log LOG = LogFactory.getLog(OscarSchema.class);

    /**
     * Creates a new OSCAR schema.
     *
     * @param jdbcTemplate The Jdbc Template for communicating with the DB.
     * @param database     The database-specific support.
     * @param name         The name of the schema.
     */
    OscarSchema(JdbcTemplate jdbcTemplate, OscarDatabase database, String name) {
        super(jdbcTemplate, database, name);
    }

    /**
     * Checks whether the schema is system, i.e. OSCAR-maintained, or not.
     *
     * @return {@code true} if it is system, {@code false} if not.
     */
    public boolean isSystem() {
        return database.getSystemSchemas().contains(name);
    }

    /**
     * Checks whether this schema is default for the current user.
     *
     * @return {@code true} if it is default, {@code false} if not.
     */
    boolean isDefaultSchemaForUser() throws SQLException {
        return name.equals(database.doGetCurrentUser());
    }

    /**
     * OSCAR 的 ALL_OBJECTS 没有 OBJECT_TYPE='SCH'（DM 用法），通过 ALL_USERS 判断用户同名模式是否存在。
     *
     * @return boolean
     * @throws SQLException SQLException
     */
    @Override
    protected boolean doExists() throws SQLException {
        return database.queryReturnsRows("SELECT * FROM ALL_USERS WHERE USERNAME = ?", name);
    }

    @Override
    protected boolean doEmpty() throws SQLException {
        return !ObjectType.supportedTypesExist(jdbcTemplate, database, this);
    }

    @Override
    protected void doCreate() throws SQLException {
        // OSCAR 用户创建语法为 PG 风格：CREATE USER xxx WITH PASSWORD 'yyy'
        jdbcTemplate.execute("CREATE USER " + database.quote(name) + " WITH PASSWORD "
                + "'" + "FFllyywwaayy00!!" + "'");
    }

    @Override
    protected void doDrop() throws SQLException {
        jdbcTemplate.execute("DROP USER " + database.quote(name) + " CASCADE");
    }

    @Override
    protected void doClean() throws SQLException {
        if (isSystem()) {
            throw new FlywayException("Clean not supported on OSCAR for system schema " + database.quote(name) + "! " +
                    "It must not be changed in any way except by running an OSCAR-supplied script!");
        }

        // Get existing object types in the schema.
        Set<String> objectTypeNames = ObjectType.getObjectTypeNames(jdbcTemplate, database, this);

        // Define the list of types to process, order is important.
        List<ObjectType> objectTypesToClean = Arrays.asList(
                // Tables first: OSCAR (PG 内核) 为外键约束生成内部 RI 触发器，
                // 先删表可随 CASCADE CONSTRAINTS 一并清理其关联对象，避免内部触发器阻塞后续清理。
                ObjectType.TABLE,
                ObjectType.INDEX,
                ObjectType.VIEW,
                ObjectType.SEQUENCE,
                ObjectType.PROCEDURE,
                ObjectType.FUNCTION,
                ObjectType.PACKAGE,
                ObjectType.TYPE,
                ObjectType.SYNONYM,
                // Triggers last, 过滤掉 OSCAR 为外键生成的内部 RI 触发器。
                ObjectType.TRIGGER
        );

        for (ObjectType objectType : objectTypesToClean) {
            if (objectTypeNames.contains(objectType.getName())) {
                LOG.debug("Cleaning objects of type " + objectType + " ...");
                objectType.dropObjects(jdbcTemplate, database, this);
            }
        }

        if (isDefaultSchemaForUser()) {
            jdbcTemplate.execute("PURGE RECYCLEBIN");
        }
    }

    @Override
    protected OscarTable[] doAllTables() throws SQLException {
        List<String> tableNames = ObjectType.TABLE.getObjectNames(jdbcTemplate, database, this);

        OscarTable[] tables = new OscarTable[tableNames.size()];
        for (int i = 0; i < tableNames.size(); i++) {
            tables[i] = new OscarTable(jdbcTemplate, database, this, tableNames.get(i));
        }
        return tables;
    }

    @Override
    public Table<OscarDatabase, OscarSchema> getTable(String tableName) {
        return new OscarTable(jdbcTemplate, database, this, tableName);
    }

    /**
     * OSCAR object types（依据真机 ALL_OBJECTS 的 OBJECT_TYPE 清单：PACKAGE、FUNCTION、LOB、VIEW、TABLE、
     * TRIGGER、SEQUENCE、SYNONYM、PROCEDURE、TYPE、INDEX）。
     */
    public enum ObjectType {
        // Tables.
        TABLE("TABLE", "CASCADE CONSTRAINTS") {
            @Override
            public List<String> getObjectNames(JdbcTemplate jdbcTemplate, OscarDatabase database, OscarSchema schema) throws SQLException {
                return jdbcTemplate.queryForStringList(
                        "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ?",
                        schema.getName()
                );
            }
        },

        // All indexes, should be dropped after tables (if any left).
        INDEX("INDEX") {
            @Override
            public List<String> getObjectNames(JdbcTemplate jdbcTemplate, OscarDatabase database, OscarSchema schema) throws SQLException {
                return jdbcTemplate.queryForStringList(
                        "SELECT INDEX_NAME FROM ALL_INDEXES WHERE OWNER = ?",
                        schema.getName()
                );
            }
        },

        // Views.
        VIEW("VIEW"),

        // Sequences.
        SEQUENCE("SEQUENCE"),

        // Procedures.
        PROCEDURE("PROCEDURE"),

        // Functions. OSCAR 非 Oracle 兼容模式下要求 DROP FUNCTION 必须带参数列表。
        FUNCTION("FUNCTION") {
            @Override
            public String generateDropStatement(JdbcTemplate jdbcTemplate, OscarDatabase database, OscarSchema schema, String objectName) {
                return "DROP FUNCTION " + database.quote(schema.getName(), objectName) + "()";
            }
        },

        // Packages.
        PACKAGE("PACKAGE"),

        // Types.
        TYPE("TYPE", "FORCE"),

        // Local synonyms.
        SYNONYM("SYNONYM", "FORCE"),

        // Triggers of all types, dropped last after all other objects are gone.
        // OSCAR（PG 内核）会为外键约束自动生成 RI_ConstraintTrigger_* 内部触发器，
        // 它们随外键约束/表的生命周期管理，手动删除会报错（"Cannot drop trigger ... requires it"）。
        TRIGGER("TRIGGER") {
            @Override
            public List<String> getObjectNames(JdbcTemplate jdbcTemplate, OscarDatabase database, OscarSchema schema) throws SQLException {
                return jdbcTemplate.queryForStringList(
                        "SELECT DISTINCT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_TYPE = ?" +
                                " AND OBJECT_NAME NOT LIKE 'RI\\_ConstraintTrigger%' ESCAPE '\\'",
                        schema.getName(), this.getName()
                );
            }
        };

        /**
         * The name of the type as it mentioned in the Data Dictionary and the DROP statement.
         */
        private final String name;

        /**
         * The extra options used in the DROP statement to enforce the operation.
         */
        private final String dropOptions;

        ObjectType(String name, String dropOptions) {
            this.name = name;
            this.dropOptions = dropOptions;
        }

        ObjectType(String name) {
            this(name, "");
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return super.toString().replace('_', ' ');
        }

        /**
         * Returns the list of object names of this type.
         *
         * @throws SQLException if retrieving of objects failed.
         */
        public List<String> getObjectNames(JdbcTemplate jdbcTemplate, OscarDatabase database, OscarSchema schema) throws SQLException {
            return jdbcTemplate.queryForStringList(
                    "SELECT DISTINCT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_TYPE = ?",
                    schema.getName(), this.getName()
            );
        }

        /**
         * Generates the drop statement for the specified object.
         */
        public String generateDropStatement(JdbcTemplate jdbcTemplate, OscarDatabase database, OscarSchema schema, String objectName) {
            return "DROP " + this.getName() + " " + database.quote(schema.getName(), objectName) +
                    (dropOptions.isEmpty() ? "" : " " + dropOptions);
        }

        /**
         * Drops all objects of this type in the specified schema.
         *
         * @throws SQLException if cleaning failed.
         */
        public void dropObjects(JdbcTemplate jdbcTemplate, OscarDatabase database, OscarSchema schema) throws SQLException {
            for (String objectName : getObjectNames(jdbcTemplate, database, schema)) {
                jdbcTemplate.execute(generateDropStatement(jdbcTemplate, database, schema, objectName));
            }
        }

        /**
         * Returns the schema's existing object types.
         *
         * @return a set of object type names.
         * @throws SQLException if retrieving of object types failed.
         */
        public static Set<String> getObjectTypeNames(JdbcTemplate jdbcTemplate, OscarDatabase database, OscarSchema schema)
                throws SQLException {
            String query = "SELECT DISTINCT OBJECT_TYPE FROM ALL_OBJECTS WHERE OWNER = ?";
            return new HashSet<>(jdbcTemplate.queryForStringList(query, schema.getName()));
        }

        /**
         * Checks whether the specified schema contains object types that can be cleaned.
         *
         * @return {@code true} if it contains, {@code false} if not.
         * @throws SQLException if retrieving of object types failed.
         */
        public static boolean supportedTypesExist(JdbcTemplate jdbcTemplate, OscarDatabase database, OscarSchema schema) throws SQLException {
            Set<String> existingTypeNames = getObjectTypeNames(jdbcTemplate, database, schema);
            return !existingTypeNames.isEmpty();
        }
    }
}
