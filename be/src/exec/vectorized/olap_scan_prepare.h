// This file is licensed under the Elastic License 2.0. Copyright 2021 StarRocks Limited.

#pragma once

#include "common/status.h"
#include "exec/olap_common.h"
#include "exprs/expr.h"
#include "exprs/expr_context.h"

namespace starrocks {
class RuntimeState;
namespace vectorized {

class RuntimeFilterProbeCollector;
class PredicateParser;
class ColumnPredicate;

class OlapScanConjunctsManager {
public:
    // fields from olap scan node
    const std::vector<ExprContext*>* conjunct_ctxs_ptr;
    const TupleDescriptor* tuple_desc;
    ObjectPool* obj_pool;
    const std::vector<std::string>* key_column_names;
    const RuntimeFilterProbeCollector* runtime_filters;
    RuntimeState* runtime_state;

private:
    // fields generated by parsing conjunct ctxs.
    // same size with |_conjunct_ctxs|, indicate which element has been normalized.
    std::vector<bool> normalized_conjuncts;
    std::map<std::string, ColumnValueRangeType> column_value_ranges;  // from conjunct_ctxs
    OlapScanKeys scan_keys;                                           // from _column_value_ranges
    std::vector<TCondition> olap_filters;                             // from _column_value_ranges
    std::vector<TCondition> is_null_vector;                           // from conjunct_ctxs
    std::map<int, std::vector<ExprContext*>> slot_index_to_expr_ctxs; // from conjunct_ctxs

public:
    static void eval_const_conjuncts(const std::vector<ExprContext*>& conjunct_ctxs, Status* status);

    void get_column_predicates(PredicateParser* parser, std::vector<ColumnPredicate*>* preds);

    Status get_key_ranges(std::vector<std::unique_ptr<OlapScanRange>>* key_ranges);

    void get_not_push_down_conjuncts(std::vector<ExprContext*>* predicates);

    Status parse_conjuncts(bool scan_keys_unlimited, int32_t max_scan_key_num,
                           bool enable_column_expr_predicate = false);

private:
    Status normalize_conjuncts();
    Status build_olap_filters();
    Status build_scan_keys(bool unlimited, int32_t max_scan_key_num);

    template <PrimitiveType SlotType, typename RangeValueType>
    void normalize_predicate(const SlotDescriptor& slot, ColumnValueRange<RangeValueType>* range);

    template <PrimitiveType SlotType, typename RangeValueType>
    void normalize_in_or_equal_predicate(const SlotDescriptor& slot, ColumnValueRange<RangeValueType>* range);

    template <PrimitiveType SlotType, typename RangeValueType>
    void normalize_binary_predicate(const SlotDescriptor& slot, ColumnValueRange<RangeValueType>* range);

    template <PrimitiveType SlotType, typename RangeValueType>
    void normalize_join_runtime_filter(const SlotDescriptor& slot, ColumnValueRange<RangeValueType>* range);

    template <PrimitiveType SlotType, typename RangeValueType>
    void normalize_not_in_or_not_equal_predicate(const SlotDescriptor& slot, ColumnValueRange<RangeValueType>* range);

    void normalize_is_null_predicate(const SlotDescriptor& slot);

    // To build `ColumnExprPredicate`s from conjuncts passed from olap scan node.
    // `ColumnExprPredicate` would be used in late materialization, zone map filtering,
    // dict encoded column filtering and bitmap value column filtering etc.
    void build_column_expr_predicates();
};

} // namespace vectorized
} // namespace starrocks