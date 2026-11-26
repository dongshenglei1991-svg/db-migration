package com.github.mengweijin.liquibase.oscar;

import com.github.mengweijin.liquibase.oscar.database.OscarDatabase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单元测试：SPI 服务发现与基本识别逻辑。不依赖数据库和 JDBC 驱动。
 */
class OscarDatabaseTest {

    @Test
    void spiDiscoveryShouldFindOscarDatabase() {
        Database database = DatabaseFactory.getInstance().getImplementedDatabases().stream()
                .filter(OscarDatabase.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("OscarDatabase 未被 SPI 发现，检查 META-INF/services/liquibase.database.Database"));
        assertEquals("oscar", database.getShortName());
    }

    @Test
    void identification() throws Exception {
        OscarDatabase database = new OscarDatabase();
        assertEquals(2003, database.getDefaultPort());
        assertEquals("com.oscar.Driver", database.getDefaultDriver("jdbc:oscar://localhost:2003/OSRDB"));
        assertNull(database.getDefaultDriver("jdbc:dm://localhost:5236"));
        assertEquals(128, database.getIdentifierMaximumLength());
        assertTrue(database.supportsAutoIncrement());
    }

    @Test
    void productNameConstant() {
        assertEquals("OSCAR", OscarDatabase.PRODUCT_NAME);
        assertNotNull(new OscarDatabase());
    }
}
