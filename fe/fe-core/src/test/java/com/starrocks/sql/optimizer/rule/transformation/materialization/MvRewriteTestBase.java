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

package com.starrocks.sql.optimizer.rule.transformation.materialization;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.starrocks.analysis.TableName;
import com.starrocks.catalog.Database;
import com.starrocks.catalog.LocalTablet;
import com.starrocks.catalog.MaterializedIndex;
import com.starrocks.catalog.MaterializedView;
import com.starrocks.catalog.Partition;
import com.starrocks.catalog.Replica;
import com.starrocks.catalog.Table;
import com.starrocks.catalog.Tablet;
import com.starrocks.common.util.UUIDUtil;
import com.starrocks.pseudocluster.PseudoCluster;
import com.starrocks.qe.ConnectContext;
import com.starrocks.qe.StmtExecutor;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.sql.analyzer.Analyzer;
import com.starrocks.sql.ast.CreateMaterializedViewStatement;
import com.starrocks.sql.ast.QueryRelation;
import com.starrocks.sql.ast.QueryStatement;
import com.starrocks.sql.ast.StatementBase;
import com.starrocks.sql.optimizer.OptExpression;
import com.starrocks.sql.optimizer.Optimizer;
import com.starrocks.sql.optimizer.base.ColumnRefFactory;
import com.starrocks.sql.optimizer.base.ColumnRefSet;
import com.starrocks.sql.optimizer.base.PhysicalPropertySet;
import com.starrocks.sql.optimizer.operator.physical.PhysicalScanOperator;
import com.starrocks.sql.optimizer.transformer.LogicalPlan;
import com.starrocks.sql.optimizer.transformer.RelationTransformer;
import com.starrocks.sql.parser.ParsingException;
import com.starrocks.sql.plan.ConnectorPlanTestBase;
import com.starrocks.sql.plan.PlanTestBase;
import com.starrocks.thrift.TExplainLevel;
import com.starrocks.utframe.StarRocksAssert;
import com.starrocks.utframe.UtFrameUtils;
import mockit.Mock;
import mockit.MockUp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class MvRewriteTestBase {
    protected static final Logger LOG = LogManager.getLogger(MvRewriteTestBase.class);
    protected static ConnectContext connectContext;
    protected static PseudoCluster cluster;
    protected static StarRocksAssert starRocksAssert;

    protected static String DB_NAME = "test";

    @BeforeClass
    public static void beforeClass() throws Exception {
        PseudoCluster.getOrCreateWithRandomPort(true, 1);
        GlobalStateMgr.getCurrentState().getTabletChecker().setInterval(100);
        cluster = PseudoCluster.getInstance();

        connectContext = UtFrameUtils.createDefaultCtx();
        ConnectorPlanTestBase.mockCatalog(connectContext);
        starRocksAssert = new StarRocksAssert(connectContext);
        starRocksAssert.withDatabase(DB_NAME).useDatabase(DB_NAME);

        // set default config for async mvs
        UtFrameUtils.setDefaultConfigForAsyncMVTest(connectContext);

        new MockUp<PlanTestBase>() {
            /**
             * {@link com.starrocks.sql.plan.PlanTestNoneDBBase#isIgnoreExplicitColRefIds()}
             */
            @Mock
            boolean isIgnoreExplicitColRefIds() {
                return true;
            }
        };
    }

    @AfterClass
    public static void tearDown() throws Exception {
        PseudoCluster.getInstance().shutdown(true);
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

    public String getFragmentPlan(String sql) throws Exception {
        String s = UtFrameUtils.getPlanAndFragment(connectContext, sql).second.
                getExplainString(TExplainLevel.NORMAL);
        return s;
    }

    public static Table getTable(String dbName, String mvName) {
        Database db = GlobalStateMgr.getCurrentState().getDb(dbName);
        Table table = db.getTable(mvName);
        Assert.assertNotNull(table);
        return table;
    }

    protected MaterializedView getMv(String dbName, String mvName) {
        Table table = getTable(dbName, mvName);
        Assert.assertTrue(table instanceof MaterializedView);
        MaterializedView mv = (MaterializedView) table;
        return mv;
    }

    protected void refreshMaterializedViewWithPartition(String dbName, String mvName, String partitionStart,
                                                        String partitionEnd) throws SQLException {
        cluster.runSql(dbName, String.format("refresh materialized view %s partition start (\"%s\") " +
                "end (\"%s\") with sync mode", mvName, partitionStart, partitionEnd));
        cluster.runSql(dbName, String.format("analyze table %s with sync mode", mvName));
    }

    protected void refreshMaterializedView(String dbName, String mvName) throws SQLException {
        cluster.runSql(dbName, String.format("refresh materialized view %s with sync mode", mvName));
        cluster.runSql(dbName, String.format("analyze table %s with sync mode", mvName));
    }

    protected static void withRefreshedMV(String sql, StarRocksAssert.ExceptionRunnable action) {
        TableName mvTableName = null;
        try {
            StatementBase stmt = UtFrameUtils.parseStmtWithNewParser(sql, connectContext);
            Assert.assertTrue(stmt instanceof CreateMaterializedViewStatement);
            CreateMaterializedViewStatement createMaterializedViewStatement = (CreateMaterializedViewStatement) stmt;
            mvTableName = createMaterializedViewStatement.getTableName();
            Assert.assertTrue(mvTableName != null);

            createAndRefreshMv(sql);
            action.run();
        } catch (Exception e) {
            Assert.fail();
        } finally {
            String dbName = mvTableName.getDb() == null ? DB_NAME : mvTableName.getDb();
            try {
                dropMv(dbName, mvTableName.getTbl());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected static void createAndRefreshMv(String sql) throws Exception {
        StatementBase stmt = UtFrameUtils.parseStmtWithNewParser(sql, connectContext);
        Assert.assertTrue(stmt instanceof CreateMaterializedViewStatement);
        CreateMaterializedViewStatement createMaterializedViewStatement = (CreateMaterializedViewStatement) stmt;
        TableName mvTableName = createMaterializedViewStatement.getTableName();
        Assert.assertTrue(mvTableName != null);
        String dbName = Strings.isNullOrEmpty(mvTableName.getDb()) ? DB_NAME : mvTableName.getDb();
        String mvName = mvTableName.getTbl();
        starRocksAssert.withMaterializedView(sql);
        cluster.runSql(dbName, String.format("refresh materialized view %s with sync mode", mvName));
    }

    protected static void dropMv(String dbName, String mvName) throws Exception {
        starRocksAssert.dropMaterializedView(mvName);
    }

    public static OptExpression getOptimizedPlan(String sql, ConnectContext connectContext) {
        StatementBase mvStmt;
        try {
            List<StatementBase> statementBases =
                    com.starrocks.sql.parser.SqlParser.parse(sql, connectContext.getSessionVariable());
            Preconditions.checkState(statementBases.size() == 1);
            mvStmt = statementBases.get(0);
        } catch (ParsingException parsingException) {
            return null;
        }
        Preconditions.checkState(mvStmt instanceof QueryStatement);
        Analyzer.analyze(mvStmt, connectContext);
        QueryRelation query = ((QueryStatement) mvStmt).getQueryRelation();
        ColumnRefFactory columnRefFactory = new ColumnRefFactory();
        LogicalPlan logicalPlan =
                new RelationTransformer(columnRefFactory, connectContext).transformWithSelectLimit(query);
        Optimizer optimizer = new Optimizer();
        return optimizer.optimize(
                connectContext,
                logicalPlan.getRoot(),
                new PhysicalPropertySet(),
                new ColumnRefSet(logicalPlan.getOutputColumn()),
                columnRefFactory);
    }

    public List<PhysicalScanOperator> getScanOperators(OptExpression root, String name) {
        List<PhysicalScanOperator> results = Lists.newArrayList();
        getScanOperators(root, name, results);
        return results;
    }

    private void getScanOperators(OptExpression root, String name, List<PhysicalScanOperator> results) {
        if (root.getOp() instanceof PhysicalScanOperator
                && ((PhysicalScanOperator) root.getOp()).getTable().getName().equals(name)) {
            results.add((PhysicalScanOperator) root.getOp());
        }
        for (OptExpression child : root.getInputs()) {
            getScanOperators(child, name, results);
        }
    }

    public static Set<String> getPartitionNamesToRefreshForMv(MaterializedView mv) {
        Set<String> toRefreshPartitions = Sets.newHashSet();
        mv.getPartitionNamesToRefreshForMv(toRefreshPartitions, true);
        return toRefreshPartitions;
    }

    public static void executeInsertSql(ConnectContext connectContext, String sql) throws Exception {
        connectContext.setQueryId(UUIDUtil.genUUID());
        new StmtExecutor(connectContext, sql).execute();
    }
}
