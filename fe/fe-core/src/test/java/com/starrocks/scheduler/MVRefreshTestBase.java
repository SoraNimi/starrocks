// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.starrocks.scheduler;

import com.google.common.collect.Sets;
import com.starrocks.analysis.TableName;
import com.starrocks.catalog.Database;
import com.starrocks.catalog.LocalTablet;
import com.starrocks.catalog.MaterializedIndex;
import com.starrocks.catalog.MaterializedView;
import com.starrocks.catalog.OlapTable;
import com.starrocks.catalog.Partition;
import com.starrocks.catalog.Replica;
import com.starrocks.catalog.Table;
import com.starrocks.catalog.Tablet;
import com.starrocks.common.Config;
import com.starrocks.common.FeConstants;
import com.starrocks.common.util.RuntimeProfile;
import com.starrocks.common.util.UUIDUtil;
import com.starrocks.persist.gson.GsonUtils;
import com.starrocks.pseudocluster.PseudoCluster;
import com.starrocks.qe.ConnectContext;
import com.starrocks.qe.StmtExecutor;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.sql.ast.DmlStmt;
import com.starrocks.sql.ast.InsertStmt;
import com.starrocks.sql.optimizer.QueryMaterializationContext;
import com.starrocks.sql.optimizer.rule.transformation.materialization.MvRewriteTestBase;
import com.starrocks.sql.plan.ConnectorPlanTestBase;
import com.starrocks.sql.plan.ExecPlan;
import com.starrocks.statistic.StatisticsMetaManager;
import com.starrocks.utframe.StarRocksAssert;
import com.starrocks.utframe.UtFrameUtils;
import mockit.Mock;
import mockit.MockUp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MVRefreshTestBase {
    private static final Logger LOG = LogManager.getLogger(MvRewriteTestBase.class);
    protected static ConnectContext connectContext;
    protected static PseudoCluster cluster;
    protected static StarRocksAssert starRocksAssert;
    @ClassRule
    public static TemporaryFolder temp = new TemporaryFolder();

    protected static long startSuiteTime = 0;
    protected long startCaseTime = 0;

    protected static final String TEST_DB_NAME = "test";

    @BeforeClass
    public static void beforeClass() throws Exception {
        FeConstants.runningUnitTest = true;
        Config.enable_experimental_mv = true;
        UtFrameUtils.createMinStarRocksCluster();
        connectContext = UtFrameUtils.createDefaultCtx();
        ConnectorPlanTestBase.mockCatalog(connectContext);
        starRocksAssert = new StarRocksAssert(connectContext);
        if (!starRocksAssert.databaseExist("_statistics_")) {
            StatisticsMetaManager m = new StatisticsMetaManager();
            m.createStatisticsTablesForTest();
        }
        starRocksAssert.withDatabase("test");
        starRocksAssert.useDatabase("test");

        new MockUp<StmtExecutor>() {
            @Mock
            public void handleDMLStmt(ExecPlan execPlan, DmlStmt stmt) throws Exception {
                if (stmt instanceof InsertStmt) {
                    InsertStmt insertStmt = (InsertStmt) stmt;
                    TableName tableName = insertStmt.getTableName();
                    Database testDb = GlobalStateMgr.getCurrentState().getDb("test");
                    OlapTable tbl = ((OlapTable) testDb.getTable(tableName.getTbl()));
                    if (tbl != null) {
                        for (Partition partition : tbl.getPartitions()) {
                            if (insertStmt.getTargetPartitionIds().contains(partition.getId())) {
                                setPartitionVersion(partition, partition.getVisibleVersion() + 1);
                            }
                        }
                    }
                }
            }
        };
    }

    private static void setPartitionVersion(Partition partition, long version) {
        partition.setVisibleVersion(version, System.currentTimeMillis());
        MaterializedIndex baseIndex = partition.getBaseIndex();
        List<Tablet> tablets = baseIndex.getTablets();
        for (Tablet tablet : tablets) {
            List<Replica> replicas = ((LocalTablet) tablet).getImmutableReplicas();
            for (Replica replica : replicas) {
                replica.updateVersionInfo(version, -1, version);
            }
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
    }

    public void executeInsertSql(ConnectContext connectContext, String sql) throws Exception {
        connectContext.setQueryId(UUIDUtil.genUUID());
        new StmtExecutor(connectContext, sql).execute();
    }

    protected MaterializedView getMv(String dbName, String mvName) {
        Database db = GlobalStateMgr.getCurrentState().getDb(dbName);
        Table table = db.getTable(mvName);
        Assert.assertNotNull(table);
        Assert.assertTrue(table instanceof MaterializedView);
        MaterializedView mv = (MaterializedView) table;
        return mv;
    }

    protected Table getTable(String dbName, String tableName) {
        Database db = GlobalStateMgr.getCurrentState().getDb(dbName);
        Table table = db.getTable(tableName);
        Assert.assertNotNull(table);
        return table;
    }

    protected TaskRun buildMVTaskRun(MaterializedView mv, String dbName) {
        Task task = TaskBuilder.buildMvTask(mv, dbName);
        Map<String, String> testProperties = task.getProperties();
        TaskRun taskRun = TaskRunBuilder.newBuilder(task).build();
        return taskRun;
    }

    protected void refreshMVRange(String mvName, boolean force) throws Exception {
        refreshMVRange(mvName, null, null, force);
    }

    public static Set<String> getPartitionNamesToRefreshForMv(MaterializedView mv) {
        Set<String> toRefreshPartitions = Sets.newHashSet();
        mv.getPartitionNamesToRefreshForMv(toRefreshPartitions, true);
        return toRefreshPartitions;
    }

    PartitionBasedMvRefreshProcessor refreshMV(String dbName, MaterializedView mv) throws Exception {
        Task task = TaskBuilder.buildMvTask(mv, dbName);
        TaskRun taskRun = TaskRunBuilder.newBuilder(task).build();
        taskRun.initStatus(UUIDUtil.genUUID().toString(), System.currentTimeMillis());
        taskRun.executeTaskRun();
        return (PartitionBasedMvRefreshProcessor) taskRun.getProcessor();
    }

    protected void refreshMVRange(String mvName, String start, String end, boolean force) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("refresh materialized view " + mvName);
        if (start != null && end != null) {
            sb.append(String.format(" partition start('%s') end('%s')", start, end));
        }
        if (force) {
            sb.append(" force");
        }
        sb.append(" with sync mode");
        String sql = sb.toString();
        starRocksAssert.getCtx().executeSql(sql);
    }

    protected QueryMaterializationContext.QueryCacheStats getQueryCacheStats(RuntimeProfile profile) {
        Map<String, String> infoStrings = profile.getInfoStrings();
        Assert.assertTrue(infoStrings.containsKey("MVQueryCacheStats"));
        String cacheStats = infoStrings.get("MVQueryCacheStats");
        System.out.println(cacheStats);
        return GsonUtils.GSON.fromJson(cacheStats,
                QueryMaterializationContext.QueryCacheStats.class);
    }

    protected static void initAndExecuteTaskRun(TaskRun taskRun) throws Exception {
        taskRun.initStatus(UUIDUtil.genUUID().toString(), System.currentTimeMillis());
        taskRun.executeTaskRun();
    }
}
