package org.apache.hbase.repair;

import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.*;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestMetaRepair {

    private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
    private static final String NAMESPACE = "TEST";
    private static final TableName TABLE_NAME_WITH_NAMESPACE =
            TableName.valueOf(NAMESPACE, TestMetaRepair.class.getSimpleName());
    private static final TableName TABLE_NAME =
            TableName.valueOf(TestMetaRepair.class.getSimpleName());
    private static final byte[] family = Bytes.toBytes("test");
    private Table table;

    @BeforeClass
    public static void beforeClass() throws Exception {
        TEST_UTIL.getConfiguration().set(HConstants.HREGION_MAX_FILESIZE,
                Long.toString(1024 * 1024 * 3));
        TEST_UTIL.startMiniCluster(3);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        TEST_UTIL.shutdownMiniCluster();
    }

    @Before
    public void setup() throws Exception {
        table = TEST_UTIL.createMultiRegionTable(TABLE_NAME, family, 10);
        TEST_UTIL.waitUntilAllRegionsAssigned(TABLE_NAME);
    }

    @After
    public void tearDown() throws Exception {
        TEST_UTIL.deleteTable(TABLE_NAME);
    }

    @Test
    public void testHbaseAndHdfsRegions() throws Exception {
        MetaRepair metaRepair = new MetaRepair(TEST_UTIL.getConfiguration());
        Map<String, byte[]> hbaseRegions = metaRepair.getMetaRegions(TABLE_NAME.getNameAsString());
        Map<String, RegionInfo> hdfsRegions = metaRepair.getHdfsRegions(TABLE_NAME.getNameAsString());
        assertEquals(10, hbaseRegions.size());
        assertEquals(10, hdfsRegions.size());
        assertTrue(hbaseRegions.keySet().containsAll(hdfsRegions.keySet()));
    }

    @Test
    public void testRepairMetadata() throws Exception {
        MetaRepair metaRepair = new MetaRepair(TEST_UTIL.getConfiguration());
        generateTableData(TABLE_NAME);
        final int originalCount = TEST_UTIL.countRows(table);
        metaRepair.repairMetadata(TABLE_NAME.getNameAsString());
        assertEquals("Row count before and after repair should be equal",
                originalCount, TEST_UTIL.countRows(table));
    }

    @Test
    public void testRepairMetadataWithNameSpace() throws Exception {
        try {
            TEST_UTIL.getAdmin().createNamespace(NamespaceDescriptor.create(NAMESPACE).build());
            Table tableWithNamespace = TEST_UTIL.createMultiRegionTable(TABLE_NAME_WITH_NAMESPACE, family, 6);
            TEST_UTIL.waitUntilAllRegionsAssigned(TABLE_NAME_WITH_NAMESPACE);
            MetaRepair metaRepair = new MetaRepair(TEST_UTIL.getConfiguration());
            generateTableData(TABLE_NAME_WITH_NAMESPACE);
            final int originalCount = TEST_UTIL.countRows(tableWithNamespace);
            metaRepair.repairMetadata(TABLE_NAME_WITH_NAMESPACE.getNameAsString());
            assertEquals("Row count before and after repair should be equal",
                    originalCount, TEST_UTIL.countRows(tableWithNamespace));
        } finally {
            TEST_UTIL.deleteTable(TABLE_NAME_WITH_NAMESPACE);
            TEST_UTIL.getAdmin().deleteNamespace(NAMESPACE);
        }
    }

    @Test
    public void testRepairMetadataInvalidParams() throws Exception {
        final int originalCount = TEST_UTIL.countRows(table);
        MetaRepair metaRepair = new MetaRepair(TEST_UTIL.getConfiguration());
        assertEquals(0, metaRepair.run(new String[]{TABLE_NAME.getNameAsString()}));
        assertEquals(1, metaRepair.run(new String[]{}));
        assertEquals(2, metaRepair.run(new String[]{"XXX"}));
        assertEquals("Row count before and after repair should be equal",
                originalCount, TEST_UTIL.countRows(table));
    }

    private void generateTableData(TableName tableName) throws Exception {
        TEST_UTIL.getAdmin().getRegions(tableName).forEach(r -> {
            byte[] key = r.getStartKey().length == 0 ? new byte[]{0} : r.getStartKey();
            Put put = new Put(key);
            put.addColumn(family, Bytes.toBytes("c"), new byte[1024 * 1024]);
            try {
                table.put(put);
            } catch (IOException e) {
                throw new Error("Failed to put row");
            }
        });
    }
}
