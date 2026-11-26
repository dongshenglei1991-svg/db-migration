# Oscar Liquibase extension

This module is a standalone Liquibase extension. It depends only on `liquibase-core` and does not package or
override Liquibase core classes.

## 方言适配要点（OSCAR 7.0.8，驱动 7.20 实测）

- 产品名 `OSCAR`，驱动 `com.oscar.Driver`，URL `jdbc:oscar://host:2003/db`，默认端口 2003。
- OSCAR 高度兼容 Oracle（DUAL / ROWNUM / VARCHAR2 / NUMBER / 序列 / PL/SQL），因此
  `OscarDatabase extends OracleDatabase`。
- OSCAR 不支持 `GENERATED ... AS IDENTITY`：自增列由 `OscarBigIntType` / `OscarIntType` 映射为
  PG 风格的 `BIGSERIAL` / `SERIAL`（OSCAR 自动创建隐式序列，`DROP TABLE` 时随之删除）。
- OSCAR 缺少 Oracle 的部分字典视图（`ALL_QUEUE_TABLES`、`ALL_REGISTERED_MVIEWS`、回收站视图等），
  Liquibase 核心 Oracle 分支的快照 SQL 会引用它们，因此本模块用字典 SQL 替换了 7 个快照生成器：
  Column / Table / View / Index / PrimaryKey / ForeignKey / UniqueConstraint。
  - OSCAR 的 `ALL_CONS_COLUMNS.POSITION` 对外键约束存储的是「列在表中的序号」，外键列与被引用
    主键列按位配对在 `OscarForeignKeySnapshotGenerator` 中完成（SQL 跨字典视图的列对列联接在
    OSCAR 上还会被优化器错误地返回空集，已规避）。

## Tests

Unit tests include service discovery and identification checks (no database or JDBC driver required).

Run the OSCAR database regression suite with a disposable schema:

```shell
# 神通 JDBC 驱动不在 Maven Central，先安装到本地仓库
mvn install:install-file -Dfile=/path/to/oscarJDBC8.jar \
    -DgroupId=com.shentongdata -DartifactId=oscarJDBC8 -Dversion=7.20 -Dpackaging=jar

export OSCAR_JDBC_URL='jdbc:oscar://172.16.9.140:2003/OSRDB'
export OSCAR_JDBC_USERNAME='sysdba'
export OSCAR_JDBC_PASSWORD='change-me'
export OSCAR_JDBC_SCHEMA='OSCAR_IT_SCHEMA'
mvn -pl db-migration-oscar-liquibase -am -Poscar-integration integration-test
```

The integration test runs against an isolated schema (`OSCAR_IT_SCHEMA`, created and dropped by the test),
covering update, status, rollback, re-update, lock acquisition/release, snapshot, auto-increment
(`BIGSERIAL` implicit sequence), and diff.
