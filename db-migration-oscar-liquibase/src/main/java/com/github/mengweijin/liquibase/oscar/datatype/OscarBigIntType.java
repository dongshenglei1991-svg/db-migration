package com.github.mengweijin.liquibase.oscar.datatype;

import com.github.mengweijin.liquibase.oscar.database.OscarDatabase;
import liquibase.database.Database;
import liquibase.datatype.DataTypeInfo;
import liquibase.datatype.DatabaseDataType;
import liquibase.datatype.LiquibaseDataType;
import liquibase.datatype.core.BigIntType;

/**
 * OSCAR bigint 类型。
 * <p>
 * OSCAR 不支持 Oracle 的 {@code GENERATED ... AS IDENTITY} 语法，
 * 自增列使用 PG 风格的 {@code BIGSERIAL}（列类型本身即自增，由 OSCAR 自动创建隐式序列）。
 */
@DataTypeInfo(
        name = "bigint",
        aliases = {"java.sql.Types.BIGINT", "java.math.BigInteger", "java.lang.Long", "integer8", "bigserial", "serial8", "int8"},
        minParameters = 0,
        maxParameters = 1,
        priority = LiquibaseDataType.PRIORITY_DATABASE
)
public class OscarBigIntType extends BigIntType {

    @Override
    public boolean supports(Database database) {
        return database instanceof OscarDatabase;
    }

    @Override
    public DatabaseDataType toDatabaseDataType(Database database) {
        if (isAutoIncrement()) {
            return new DatabaseDataType("BIGSERIAL");
        }
        return new DatabaseDataType("BIGINT");
    }
}
