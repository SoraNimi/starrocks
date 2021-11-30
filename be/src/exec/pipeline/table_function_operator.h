// This file is licensed under the Elastic License 2.0. Copyright 2021 StarRocks Limited.

#pragma once

#include "column/column_helper.h"
#include "column/type_traits.h"
#include "column/vectorized_fwd.h"
#include "exec/pipeline/operator.h"
#include "exprs/expr.h"
#include "exprs/table_function/table_function_factory.h"
#include "runtime/descriptors.h"
#include "runtime/runtime_state.h"

namespace starrocks::pipeline {
class TableFunctionOperator final : public Operator {
public:
    TableFunctionOperator(OperatorFactory* factory, int32_t id, int32_t plan_node_id, const TPlanNode& tnode)
            : Operator(factory, id, "table_function", plan_node_id), _tnode(tnode) {}

    ~TableFunctionOperator() override = default;

    Status prepare(RuntimeState* state) override;

    bool has_output() const override;

    bool need_input() const override;

    bool is_finished() const override;

    void set_finishing(RuntimeState* state) override;

    StatusOr<vectorized::ChunkPtr> pull_chunk(RuntimeState* state) override;

    Status push_chunk(RuntimeState* state, const vectorized::ChunkPtr& chunk) override;

private:
    vectorized::ChunkPtr _build_chunk(const std::vector<vectorized::ColumnPtr>& output_columns);
    void _process_table_function();

    const TPlanNode& _tnode;
    const vectorized::TableFunction* _table_function;

    //Slots of output by table function
    std::vector<SlotId> _fn_result_slots;
    //External column slots of the join logic generated by the table function
    std::vector<SlotId> _outer_slots;
    //Slots of table function input parameters
    std::vector<SlotId> _param_slots;

    //Chunk context between multi get_next

    //Input chunk currently being processed
    vectorized::ChunkPtr _input_chunk;
    //The current chunk is processed to which row
    size_t _input_chunk_index;
    //The current outer line needs to be repeated several times
    size_t _remain_repeat_times;
    //table function result
    std::pair<vectorized::Columns, vectorized::ColumnPtr> _table_function_result;
    //table function return result end ?
    bool _table_function_result_eos;
    //table function param and return offset
    vectorized::TableFunctionState* _table_function_state;

    //Profile
    RuntimeProfile::Counter* _table_function_exec_timer = nullptr;

    bool _is_finished = false;
};

class TableFunctionOperatorFactory final : public OperatorFactory {
public:
    TableFunctionOperatorFactory(int32_t id, int32_t plan_node_id, const TPlanNode& tnode)
            : OperatorFactory(id, "table_function", plan_node_id), _tnode(tnode) {}

    OperatorPtr create(int32_t degree_of_parallelism, int32_t driver_sequence) override {
        return std::make_shared<TableFunctionOperator>(this, _id, _plan_node_id, _tnode);
    }

private:
    const TPlanNode& _tnode;
};

} // namespace starrocks::pipeline
