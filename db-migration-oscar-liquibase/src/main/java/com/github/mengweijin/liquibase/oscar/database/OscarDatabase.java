package com.github.mengweijin.liquibase.oscar.database;

import liquibase.Scope;
import liquibase.database.DatabaseConnection;
import liquibase.database.core.OracleDatabase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.UnexpectedLiquibaseException;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Liquibase database extension for Shentong OSCAR.
 * <p>
 * OSCAR 高度兼容 Oracle（DUAL、ROWNUM、VARCHAR2/NUMBER、序列、PL/SQL、PACKAGE/TYPE 等），
 * 因此继承 {@link OracleDatabase}，仅覆盖识别信息与已知差异点：
 * <ul>
 *     <li>产品名：OSCAR；驱动：com.oscar.Driver；URL：jdbc:oscar://host:2003/db；默认端口 2003</li>
 *     <li>版本号体系为 OSCAR 自有版本（如 7.1），父类中依赖 Oracle 12c+ 的 identity 自增逻辑不适用，
 *     序列+触发器路径在 OSCAR 上实测可用</li>
 *     <li>标识符长度上限为 127，不能用 Oracle 低版本的 30</li>
 * </ul>
 */
public class OscarDatabase extends OracleDatabase {

    public static final String PRODUCT_NAME = "OSCAR";

    private Integer databaseMajorVersion;
    private Integer databaseMinorVersion;

    @Override
    public void setConnection(DatabaseConnection connection) {
        Connection jdbcConnection = null;
        if (connection instanceof JdbcConnection) {
            try {
                jdbcConnection = ((JdbcConnection) connection).getWrappedConnection();
            } catch (Exception e) {
                throw new UnexpectedLiquibaseException(e);
            }
        }

        if (jdbcConnection != null) {
            initializeMetadata(jdbcConnection);
        }
        super.setConnection(connection);
    }

    private void initializeMetadata(Connection connection) {
        try {
            DatabaseMetaData metadata = connection.getMetaData();
            databaseMajorVersion = metadata.getDatabaseMajorVersion();
            databaseMinorVersion = metadata.getDatabaseMinorVersion();
        } catch (SQLException e) {
            Scope.getCurrentScope().getLog(getClass()).info("Could not determine OSCAR database version: " + e.getMessage());
        }
    }

    @Override
    public String getShortName() {
        return "oscar";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return PRODUCT_NAME;
    }

    @Override
    public Integer getDefaultPort() {
        return 2003;
    }

    @Override
    public int getDatabaseMajorVersion() throws DatabaseException {
        return databaseMajorVersion == null ? super.getDatabaseMajorVersion() : databaseMajorVersion;
    }

    @Override
    public int getDatabaseMinorVersion() throws DatabaseException {
        return databaseMinorVersion == null ? super.getDatabaseMinorVersion() : databaseMinorVersion;
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection connection) throws DatabaseException {
        return PRODUCT_NAME.equalsIgnoreCase(connection.getDatabaseProductName());
    }

    @Override
    public String getDefaultDriver(String url) {
        return url != null && url.startsWith("jdbc:oscar") ? "com.oscar.Driver" : null;
    }

    @Override
    public boolean supportsAutoIncrement() {
        return true;
    }

    /**
     * OSCAR 不支持 Oracle 的 {@code GENERATED ... AS IDENTITY} 内联自增子句。
     * 自增列由数据类型层直接生成为 {@code BIGSERIAL}/{@code SERIAL}（见
     * {@code OscarBigIntType}/{@code OscarIntType}），因此这里返回空子句，
     * 避免 CreateTableGenerator 追加 OSCAR 无法解析的 identity 语法。
     */
    @Override
    protected String getAutoIncrementClause() {
        return "";
    }

    @Override
    protected String getAutoIncrementClause(final String generationType, final Boolean defaultOnNull) {
        return "";
    }

    @Override
    public String getAutoIncrementClause(final BigInteger startWith, final BigInteger incrementBy, final String generationType, final Boolean defaultOnNull) {
        return "";
    }

    @Override
    public int getIdentifierMaximumLength() {
        // OSCAR 标识符上限 127，不能回落到 Oracle 11g 及以下的 30。
        return LONG_IDENTIFIERS_LEGNTH;
    }
}
