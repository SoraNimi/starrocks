// This file is licensed under the Elastic License 2.0. Copyright 2021 StarRocks Limited.

#include "aggregate_streaming_sink_operator.h"

#include "simd/simd.h"
namespace starrocks::pipeline {

Status AggregateStreamingSinkOperator::prepare(RuntimeState* state) {
    RETURN_IF_ERROR(Operator::prepare(state));
    // _aggregator is shared by sink operator and source operator
    // we must only prepare it at sink operator
    return _aggregator->prepare(state, state->obj_pool(), get_memtracker(), get_runtime_profile());
}

bool AggregateStreamingSinkOperator::is_finished() const {
    return _is_finished;
}

void AggregateStreamingSinkOperator::finish(RuntimeState* state) {
    _is_finished = true;
    _aggregator->sink_complete();
}

StatusOr<vectorized::ChunkPtr> AggregateStreamingSinkOperator::pull_chunk(RuntimeState* state) {
    return Status::InternalError("Not support");
}

Status AggregateStreamingSinkOperator::push_chunk(RuntimeState* state, const vectorized::ChunkPtr& chunk) {
    size_t chunk_size = chunk->num_rows();

    _aggregator->update_num_input_rows(chunk_size);
    COUNTER_SET(_aggregator->input_row_count(), _aggregator->num_input_rows());
    RETURN_IF_ERROR(_aggregator->check_hash_map_memory_usage(state));

    _aggregator->evaluate_exprs(chunk.get());

    if (_aggregator->streaming_preaggregation_mode() == TStreamingPreaggregationMode::FORCE_STREAMING) {
        return _push_chunk_by_force_streaming();
    } else if (_aggregator->streaming_preaggregation_mode() == TStreamingPreaggregationMode::FORCE_PREAGGREGATION) {
        return _push_chunk_by_force_preaggregation(chunk->num_rows());
    } else {
        return _push_chunk_by_auto(chunk->num_rows());
    }
}

Status AggregateStreamingSinkOperator::_push_chunk_by_force_streaming() {
    // force execute streaming
    SCOPED_TIMER(_aggregator->streaming_timer());
    vectorized::ChunkPtr chunk = std::make_shared<vectorized::Chunk>();
    _aggregator->output_chunk_by_streaming(&chunk);
    _aggregator->offer_chunk_to_buffer(chunk);
    return Status::OK();
}

Status AggregateStreamingSinkOperator::_push_chunk_by_force_preaggregation(const size_t chunk_size) {
    SCOPED_TIMER(_aggregator->agg_compute_timer());
    if (false) {
    }
#define HASH_MAP_METHOD(NAME)                                                                          \
    else if (_aggregator->hash_map_variant().type == vectorized::HashMapVariant::Type::NAME)           \
            _aggregator->build_hash_map<decltype(_aggregator->hash_map_variant().NAME)::element_type>( \
                    *_aggregator->hash_map_variant().NAME, chunk_size);
    APPLY_FOR_VARIANT_ALL(HASH_MAP_METHOD)
#undef HASH_MAP_METHOD
    else {
        DCHECK(false);
    }

    if (_aggregator->is_none_group_by_exprs()) {
        _aggregator->compute_single_agg_state(chunk_size);
    } else {
        _aggregator->compute_batch_agg_states(chunk_size);
    }

    _aggregator->try_convert_to_two_level_map();
    COUNTER_SET(_aggregator->hash_table_size(), (int64_t)_aggregator->hash_map_variant().size());
    return Status::OK();
}

Status AggregateStreamingSinkOperator::_push_chunk_by_auto(const size_t chunk_size) {
    size_t real_capacity = _aggregator->hash_map_variant().capacity() - _aggregator->hash_map_variant().capacity() / 8;
    size_t remain_size = real_capacity - _aggregator->hash_map_variant().size();
    bool ht_needs_expansion = remain_size < chunk_size;
    if (!ht_needs_expansion ||
        _aggregator->should_expand_preagg_hash_tables(chunk_size, _aggregator->mem_pool()->total_allocated_bytes(),
                                                      _aggregator->hash_map_variant().size())) {
        // hash table is not full or allow expand the hash table according reduction rate
        SCOPED_TIMER(_aggregator->agg_compute_timer());
        if (false) {
        }
#define HASH_MAP_METHOD(NAME)                                                                          \
    else if (_aggregator->hash_map_variant().type == vectorized::HashMapVariant::Type::NAME)           \
            _aggregator->build_hash_map<decltype(_aggregator->hash_map_variant().NAME)::element_type>( \
                    *_aggregator->hash_map_variant().NAME, chunk_size);
        APPLY_FOR_VARIANT_ALL(HASH_MAP_METHOD)
#undef HASH_MAP_METHOD
        else {
            DCHECK(false);
        }

        if (_aggregator->is_none_group_by_exprs()) {
            _aggregator->compute_single_agg_state(chunk_size);
        } else {
            _aggregator->compute_batch_agg_states(chunk_size);
        }

        _aggregator->try_convert_to_two_level_map();
        COUNTER_SET(_aggregator->hash_table_size(), (int64_t)_aggregator->hash_map_variant().size());
    } else {
        {
            SCOPED_TIMER(_aggregator->agg_compute_timer());
            if (false) {
            }
#define HASH_MAP_METHOD(NAME)                                                                                       \
    else if (_aggregator->hash_map_variant().type == vectorized::HashMapVariant::Type::NAME) _aggregator            \
            ->build_hash_map_with_selection<typename decltype(_aggregator->hash_map_variant().NAME)::element_type>( \
                    *_aggregator->hash_map_variant().NAME, chunk_size);
            APPLY_FOR_VARIANT_ALL(HASH_MAP_METHOD)
#undef HASH_MAP_METHOD
            else {
                DCHECK(false);
            }
        }

        size_t zero_count = SIMD::count_zero(_aggregator->streaming_selection());
        // very poor aggregation
        if (zero_count == 0) {
            SCOPED_TIMER(_aggregator->streaming_timer());
            vectorized::ChunkPtr chunk = std::make_shared<vectorized::Chunk>();
            _aggregator->output_chunk_by_streaming(&chunk);
            _aggregator->offer_chunk_to_buffer(chunk);
        }
        // very high aggregation
        else if (zero_count == _aggregator->streaming_selection().size()) {
            SCOPED_TIMER(_aggregator->agg_compute_timer());
            _aggregator->compute_batch_agg_states(chunk_size);
        } else {
            // middle cases, first aggregate locally and output by stream
            {
                SCOPED_TIMER(_aggregator->agg_compute_timer());
                _aggregator->compute_batch_agg_states_with_selection(chunk_size);
            }
            {
                SCOPED_TIMER(_aggregator->streaming_timer());
                vectorized::ChunkPtr chunk = std::make_shared<vectorized::Chunk>();
                _aggregator->output_chunk_by_streaming(&chunk);
                _aggregator->offer_chunk_to_buffer(chunk);
            }
        }

        COUNTER_SET(_aggregator->hash_table_size(), (int64_t)_aggregator->hash_map_variant().size());
    }

    return Status::OK();
}
} // namespace starrocks::pipeline