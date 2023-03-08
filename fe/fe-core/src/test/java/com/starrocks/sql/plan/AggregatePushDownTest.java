// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.sql.plan;

import com.starrocks.common.FeConstants;
import com.starrocks.utframe.UtFrameUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class AggregatePushDownTest extends PlanTestBase {
    @BeforeClass
    public static void beforeClass() throws Exception {
        PlanTestBase.beforeClass();
        FeConstants.runningUnitTest = true;
        starRocksAssert.withDatabase("test_window_db");
        starRocksAssert.withTable("CREATE TABLE if not exists trans\n" +
                "(\n" +
                "region VARCHAR(128)  NULL,\n" +
                "order_date DATE NOT NULL,\n" +
                "income DECIMAL128(10, 2) NOT NULL\n" +
                ") ENGINE=OLAP\n" +
                "DUPLICATE KEY(`region`, `order_date`)\n" +
                "COMMENT \"OLAP\"\n" +
                "DISTRIBUTED BY HASH(`region`, `order_date`) BUCKETS 128\n" +
                "PROPERTIES(\n" +
                "\"replication_num\" = \"1\",\n" +
                "\"in_memory\" = \"false\",\n" +
                "\"storage_format\" = \"default\"\n" +
                ")");
        connectContext.getSessionVariable().setNewPlanerAggStage(1);
        connectContext.getSessionVariable().setCboPushDownAggregateMode(1);
    }

    @Test
    public void testPushDown() {
        runFileUnitTest("optimized-plan/agg-pushdown");
    }

    @Test
    public void testPushDownPreAgg() {
        connectContext.getSessionVariable().setCboPushDownAggregate("local");
        try {
            runFileUnitTest("optimized-plan/preagg-pushdown");
        } finally {
            connectContext.getSessionVariable().setCboPushDownAggregate("global");
        }
    }
    @Test
    public void testPushDownDistinctAggBelowWindow() throws Exception {
        String q1 = "SELECT DISTINCT \n" +
                "  COALESCE(region, 'Other') AS region, \n" +
                "  order_date, \n" +
                "  SUM(income) OVER ( PARTITION BY  COALESCE(region, 'Other'), " +
                "   order_date) AS gp_income,  \n" +
                "  SUM(income) OVER ( PARTITION BY  COALESCE(region, 'Other'), " +
                "   MONTH(order_date) ORDER BY order_date) AS gp_income_MTD,\n" +
                "  SUM(income) OVER ( PARTITION BY  COALESCE(region, 'Other'), " +
                "   YEAR (order_date), QUARTER(order_date) ORDER BY order_date) AS gp_income_QTD,\n" +
                "  SUM(income) OVER ( PARTITION BY  COALESCE(region, 'Other'), " +
                "   YEAR (order_date) ORDER BY order_date) AS gp_income_YTD  \n" +
                "FROM  trans\n" +
                "where month(order_date)=1\n" +
                "order by region, order_date";
        String plan = UtFrameUtils.getVerboseFragmentPlan(connectContext, q1);
        System.out.println(plan);
        Assert.assertTrue(plan.contains("  1:AGGREGATE (update finalize)\n" +
                "  |  aggregate: sum[([3: income, DECIMAL128(10,2), false]); args: DECIMAL128; " +
                "result: DECIMAL128(38,2); args nullable: false; result nullable: true]\n" +
                "  |  group by: [2: order_date, DATE, false], [1: region, VARCHAR, true]\n" +
                "  |  cardinality: 1\n" +
                "  |  \n" +
                "  0:OlapScanNode\n" +
                "     table: trans, rollup: trans\n" +
                "     preAggregation: on\n" +
                "     Predicates: month[([2: order_date, DATE, false]); args: DATE; result: TINYINT; " +
                "args nullable: false; result nullable: false] = 1\n" +
                "     partitionsRatio=1/1, tabletsRatio=128/128\n" +
                "     tabletList=16328,16330,16332,16334,16336,16338,16340,16342,16344,16346 ...\n" +
                "     actualRows=0, avgRowSize=3.0\n" +
                "     cardinality: 1\n" +
                ""));
    }
}
