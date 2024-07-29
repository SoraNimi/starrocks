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

package com.starrocks.sql.optimizer.rule;

public enum RuleType {
    NONE,

    TF_JOIN_ASSOCIATIVITY_INNER,

    TF_JOIN_ASSOCIATIVITY_OUTER,
    TF_JOIN_COMMUTATIVITY,
    TF_JOIN_LEFT_ASSCOM_INNER,
    TF_JOIN_LEFT_ASSCOM_OUTER,
    TF_JOIN_COMMUTATIVITY_WITHOUT_INNER,
    TF_JOIN_SEMI_REORDER,
    TF_MULTI_JOIN_ORDER,
    TF_PARTITION_PRUNE,
    TF_DISTRIBUTION_PRUNE,
    TF_CBO_TABLE_PRUNE_RULE,
    TF_LIMIT_TABLETS_PRUNE,
    TF_SPLIT_AGGREGATE,
    TF_SPLIT_TWO_PHASE_AGGREGATE,
    TF_SPLIT_MULTI_PHASE_AGGREGATE,

    TF_SPLIT_TOPN,
    TF_SPLIT_SCAN_OR,
    TF_PUSH_DOWN_JOIN_AGG,
    TF_PARTITION_PREDICATE_PRUNE,

    TF_SPLIT_LIMIT,
    TF_OFFSET_LIMIT_TO_TOPN,
    TF_MERGE_LIMIT_DIRECT,
    TF_MERGE_LIMIT_WITH_SORT,
    TF_MERGE_LIMIT_WITH_LIMIT,
    TF_ELIMINATE_LIMIT_ZERO,

    TF_PUSH_DOWN_LIMIT,
    TF_PUSH_DOWN_PROJECT_LIMIT,
    TF_PUSH_DOWN_LIMIT_CTE_ANCHOR,
    TF_PUSH_DOWN_LIMIT_UNION,
    TF_PUSH_DOWN_LIMIT_JOIN,
    TF_PUSH_DOWN_LIMIT_RANKING_WINDOW,
    TF_PUSH_DOWN_PREDICATE_CTE_ANCHOR,
    TF_PUSH_DOWN_PREDICATE_SCAN,
    TF_PUSH_DOWN_PREDICATE_AGG,
    TF_PUSH_DOWN_PREDICATE_WINDOW,
    TF_PUSH_DOWN_TOPN_OUTER_JOIN,
    TF_PUSH_DOWN_TOPN_UNION,
    TF_PUSH_DOWN_PREDICATE_RANKING_WINDOW,
    TF_PUSH_DOWN_PREDICATE_JOIN,
    TF_PUSH_DOWN_JOIN_CLAUSE,
    TF_PUSH_DOWN_PREDICATE_PROJECT,
    TF_PUSH_DOWN_PREDICATE_UNION,
    TF_PUSH_DOWN_PREDICATE_EXCEPT,
    TF_PUSH_DOWN_PREDICATE_INTERSECT,
    TF_PUSH_DOWN_PREDICATE_VALUES,
    TF_PUSH_DOWN_PREDICATE_TABLE_FUNCTION,
    TF_PUSH_DOWN_PREDICATE_REPEAT,
    TF_PUSH_DOWN_AGG_TO_META_SCAN,
    TF_PUSH_DOWN_FLAT_JSON_TO_META_SCAN,
    TF_MERGE_PREDICATE_SCAN,
    TF_MERGE_TWO_FILTERS,
    TF_PUSH_DOWN_PREDICATE_CTE_CONSUME,
    TF_PUSH_DOWN_PREDICATE_TO_EXTERNAL_TABLE_SCAN,
    TF_PRUNE_TRUE_FILTER,

    TF_CAST_TO_EMPTY,
    TF_ADD_PROJECT_JOIN,

    TF_PRUNE_OLAP_SCAN_COLUMNS,
    TF_PRUNE_PROJECT_COLUMNS,
    TF_PRUNE_FILTER_COLUMNS,
    TF_PRUNE_AGG_COLUMNS,
    TF_PRUNE_TOPN_COLUMNS,
    TF_PRUNE_SORT_COLUMNS,
    TF_PRUNE_JOIN_COLUMNS,
    TF_PRUNE_ANALYTIC_COLUMNS,
    TF_PRUNE_UNION_COLUMNS,
    TF_PRUNE_EXCEPT_COLUMNS,
    TF_PRUNE_INTERSECT_COLUMNS,
    TF_PRUNE_REPEAT_COLUMNS,
    TF_PRUNE_VALUES_COLUMNS,
    TF_PRUNE_TABLE_FUNCTION_COLUMNS,
    TF_PRUNE_CTE_CONSUME_COLUMNS,
    TF_PRUNE_GROUP_BY_KEYS,
    TF_PRUNE_SUBFIELD,
    TF_PRUNE_UKFK_JOIN,
    TF_SUBFILED_NOCOPY,

    TF_SCALAR_OPERATORS_REUSE,
    TF_PRUNE_EMPTY_WINDOW,

    TF_PRUNE_PROJECT,
    TF_MERGE_TWO_PROJECT,
    TF_PRUNE_PROJECT_EMPTY,

    TF_PUSH_DOWN_APPLY,
    TF_APPLY_TO_JOIN,
    TF_QUANTIFIED_APPLY_TO_JOIN,
    TF_EXISTENTIAL_APPLY_TO_JOIN,
    TF_SCALAR_APPLY_TO_ANALYTIC,
    TF_SCALAR_APPLY_TO_JOIN,
    TF_QUANTIFIED_APPLY_TO_OUTER_JOIN,
    TF_EXISTENTIAL_APPLY_TO_OUTER_JOIN,
    TF_MERGE_APPLY_WITH_TABLE_FUNCTION,
    TF_APPLY_EXCEPTION,

    TF_PUSH_DOWN_APPLY_PROJECT,
    TF_PUSH_DOWN_APPLY_FILTER,
    TF_PUSH_DOWN_APPLY_AGG,
    TF_PUSH_DOWN_PROJECT_TO_CTE_ANCHOR,

    TF_PRUNE_ASSERT_ONE_ROW,
    TF_PUSH_DOWN_ASSERT_ONE_ROW_PROJECT,

    TF_MATERIALIZED_VIEW,

    TF_MERGE_TWO_AGG_RULE,

    TF_REWRITE_MULTI_DISTINCT,
    TF_REWRITE_MULTI_DISTINCT_BY_CTE,
    TF_REWRITE_BITMAP_COUNT_DISTINCT,
    TF_REWRITE_HLL_COUNT_DISTINCT,
    TF_REWRITE_DUPLICATE_AGGREGATE_FN,
    TF_REWRITE_GROUP_BY_COUNT_DISTINCT,
    TF_REMOVE_AGGREGATION_BY_AGG_TABLE,
    TF_REWRITE_GROUPING_SET,
    TF_PUSHDOWN_AGG_GROUPING_SET,
    TF_REWRITE_SIMPLE_AGG,
    TF_REWRITE_MIN_MAX_COUNT_AGG,
    TF_REWRITE_PARTITION_COLUMN_ONLY_AGG,
    TF_REWRITE_SUM_BY_ASSOCIATIVE_RULE,
    TF_REWRITE_COUNT_IF_RULE,

    TF_INTERSECT_REORDER,
    TF_INTERSECT_DISTINCT,

    TF_MERGE_PROJECT_WITH_CHILD,

    TF_PUSH_DOWN_JOIN_ON_EXPRESSION_TO_CHILD_PROJECT,

    TF_COLLECT_CTE_PRODUCE,
    TF_COLLECT_CTE_CONSUME,
    TF_PUSH_CTE_PRODUCE,
    TF_INLINE_CTE_CONSUME,
    TF_PRUNE_CTE_CONSUME_PLAN,
    TF_PRUNE_CTE_PRODUCE,
    TF_COMPUTE_CTE_COSTS,
    TF_FORCE_CTE_REUSE,

    TF_MV_TEXT_MATCH_REWRITE_RULE,
    TF_MV_ONLY_SCAN_RULE,
    TF_MV_ONLY_JOIN_RULE,
    TF_MV_AGGREGATE_SCAN_RULE,
    TF_MV_AGGREGATE_JOIN_RULE,
    TF_MV_CBO_SINGLE_TABLE_REWRITE_RULE,
    TF_MV_TRANSPARENT_REWRITE_RULE,
    TF_MV_AGGREGATE_JOIN_PUSH_DOWN_RULE,

    TF_GROUP_BY_COUNT_DISTINCT_DATA_SKEW_ELIMINATE_RULE,

    TF_PRUNE_EMPTY_UNION,
    TF_PRUNE_EMPTY_INTERSECT,
    TF_PRUNE_EMPTY_EXCEPT,

    TF_PRUNE_EMPTY_SCAN,
    TF_PRUNE_EMPTY_JOIN,
    TF_PRUNE_EMPTY_DIRECT,

    TF_DERIVE_RANGE_JOIN_PREDICATE,
    TF_SKEW_JOIN_OPTIMIZE_RULE,

    TF_CONVERT_TO_EQUAL_FOR_NULL_RULE,
    TF_ARRAY_DISTINCT_AFTER_AGG,

    TF_FINE_GRAINED_RANGE_PREDICATE,

    TF_FINE_GRAINED_RANGE_PREDICATE_WITH_PROJECTION,

    TF_MERGE_CONSTANT_UNION,

    TF_ELIMINATE_GROUP_BY_CONSTANT,
    TF_ELIMINATE_AGG,

    TF_CTE_ADD_PROJECTION,

    TF_PREDICATE_PROPAGATE,

    // The following are implementation rules:
    IMP_OLAP_LSCAN_TO_PSCAN,
    IMP_HIVE_LSCAN_TO_PSCAN,
    IMP_FILE_LSCAN_TO_PSCAN,
    IMP_ICEBERG_LSCAN_TO_PSCAN,
    IMP_HUDI_LSCAN_TO_PSCAN,
    IMP_DELTALAKE_LSCAN_TO_PSCAN,
    IMP_PAIMON_LSCAN_TO_PSCAN,
    IMP_ODPS_LSCAN_TO_PSCAN,
    IMP_ICEBERG_METADATA_LSCAN_TO_PSCAN,
    IMP_KUDU_LSCAN_TO_PSCAN,
    IMP_SCHEMA_LSCAN_TO_PSCAN,
    IMP_MYSQL_LSCAN_TO_PSCAN,
    IMP_ES_LSCAN_TO_PSCAN,
    IMP_META_LSCAN_TO_PSCAN,
    IMP_JDBC_LSCAN_TO_PSCAN,
    IMP_EQ_JOIN_TO_HASH_JOIN,
    IMP_EQ_JOIN_TO_MERGE_JOIN,
    IMP_JOIN_TO_NESTLOOP_JOIN,
    IMP_UNION,
    IMP_EXCEPT,
    IMP_INTERSECT,
    IMP_HASH_AGGREGATE,
    IMP_PROJECT,
    IMP_SORT,
    IMP_TOPN,
    IMP_ASSERT_ONE_ROW,
    IMP_ANALYTIC,
    IMP_VALUES,
    IMP_REPEAT,
    IMP_FILTER,
    IMP_TABLE_FUNCTION,
    IMP_TABLE_FUNCTION_TABLE_LSCAN_TO_PSCAN,
    IMP_LIMIT,
    IMP_CTE_CONSUME_REUSE,
    IMP_CTE_CONSUME_INLINE,
    IMP_CTE_ANCHOR,
    IMP_CTE_ANCHOR_TO_NO_CTE,
    IMP_CTE_PRODUCE,

    IMP_STREAM_AGG,
    IMP_STREAM_JOIN,
    IMP_BINLOG_SCAN,

    NUM_RULES;

    public int id() {
        return ordinal();
    }
}
