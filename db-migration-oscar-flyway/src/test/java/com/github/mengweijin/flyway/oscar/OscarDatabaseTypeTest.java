package com.github.mengweijin.flyway.oscar;

import org.flywaydb.core.extensibility.Plugin;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单元测试：SPI 服务发现与 URL/产品名识别。不依赖数据库和 JDBC 驱动。
 */
class OscarDatabaseTypeTest {

    private OscarDatabaseType loadByServiceLoader() {
        Stream<Plugin> plugins = StreamSupport.stream(
                ServiceLoader.load(Plugin.class).spliterator(), false);
        return plugins
                .filter(OscarDatabaseType.class::isInstance)
                .map(OscarDatabaseType.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("OscarDatabaseType 未被 SPI 发现，检查 META-INF/services/org.flywaydb.core.extensibility.Plugin"));
    }

    @Test
    void spiDiscoveryShouldFindOscarDatabaseType() {
        assertNotNull(loadByServiceLoader());
    }

    @Test
    void nameAndNullType() {
        OscarDatabaseType type = loadByServiceLoader();
        assertEquals("OSCAR", type.getName());
        assertEquals(java.sql.Types.VARCHAR, type.getNullType());
    }

    @Test
    void handlesOscarJdbcUrls() {
        OscarDatabaseType type = loadByServiceLoader();
        assertTrue(type.handlesJDBCUrl("jdbc:oscar://172.16.9.140:2003/OSRDB"));
        assertTrue(type.handlesJDBCUrl("jdbc:p6spy:oscar://172.16.9.140:2003/OSRDB"));
        assertFalse(type.handlesJDBCUrl("jdbc:dm://localhost:5236"));
        assertFalse(type.handlesJDBCUrl("jdbc:oracle:thin:@localhost:1521:orcl"));
        assertFalse(type.handlesJDBCUrl("jdbc:postgresql://localhost:5432/postgres"));
    }

    @Test
    void driverClass() {
        OscarDatabaseType type = loadByServiceLoader();
        assertEquals("com.oscar.Driver", type.getDriverClass("jdbc:oscar://localhost:2003/OSRDB", null));
        assertEquals("com.p6spy.engine.spy.P6SpyDriver", type.getDriverClass("jdbc:p6spy:oscar://localhost:2003/OSRDB", null));
    }

    @Test
    void handlesOscarProductName() {
        OscarDatabaseType type = loadByServiceLoader();
        assertTrue(type.handlesDatabaseProductNameAndVersion("OSCAR", "神通数据库7.0.8 for Linux", null));
        assertFalse(type.handlesDatabaseProductNameAndVersion("DM DBMS", "DM8", null));
        assertFalse(type.handlesDatabaseProductNameAndVersion("Oracle", "Oracle 19c", null));
    }
}
