// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

#include "exec/vectorized/schema_scanner/schema_tables_config_scanner.h"

#include "common/logging.h"
#include "exec/vectorized/schema_scanner/schema_helper.h"
#include "runtime/primitive_type.h"
#include "runtime/string_value.h"

namespace starrocks::vectorized {

SchemaScanner::ColumnDesc SchemaTablesConfigScanner::_s_table_tables_config_columns[] = {
        //   name,       type,          size,     is_null
        {"TABLE_SCHEMA", TYPE_VARCHAR, sizeof(StringValue), false},
        {"TABLE_NAME", TYPE_VARCHAR, sizeof(StringValue), false},
        {"TABLE_ENGINE", TYPE_VARCHAR, sizeof(StringValue), false},
        {"TABLE_MODEL", TYPE_VARCHAR, sizeof(StringValue), false},
        {"PRIMARY_KEY", TYPE_VARCHAR, sizeof(StringValue), false},
        {"PARTITION_KEY", TYPE_VARCHAR, sizeof(StringValue), false},
        {"DISTRIBUTE_KEY", TYPE_VARCHAR, sizeof(StringValue), false},
        {"DISTRIBUTE_TYPE", TYPE_VARCHAR, sizeof(StringValue), false},
        {"DISTRIBUTE_BUCKET", TYPE_INT, sizeof(int32_t), false},
        {"SORT_KEY", TYPE_VARCHAR, sizeof(StringValue), false},
        {"PROPERTIES", TYPE_VARCHAR, sizeof(StringValue), false},
};

SchemaTablesConfigScanner::SchemaTablesConfigScanner()
        : SchemaScanner(_s_table_tables_config_columns,
                        sizeof(_s_table_tables_config_columns) / sizeof(SchemaScanner::ColumnDesc)) {}

SchemaTablesConfigScanner::~SchemaTablesConfigScanner() = default;

Status SchemaTablesConfigScanner::start(RuntimeState* state) {
    if (!_is_init) {
        return Status::InternalError("used before initialized.");
    }
    TAuthInfo auth_info;
    if (nullptr != _param->db) {
        auth_info.__set_pattern(*(_param->db));
    }
    if (nullptr != _param->current_user_ident) {
        auth_info.__set_current_user_ident(*(_param->current_user_ident));
    } else {
        if (nullptr != _param->user) {
            auth_info.__set_user(*(_param->user));
        }
        if (nullptr != _param->user_ip) {
            auth_info.__set_user_ip(*(_param->user_ip));
        }
    }
    TGetTablesConfigRequest tables_config_req;
    tables_config_req.__set_auth_info(auth_info);

    if (nullptr != _param->ip && 0 != _param->port) {
        RETURN_IF_ERROR(SchemaHelper::get_tables_config(*(_param->ip), _param->port, tables_config_req,
                                                        &_tables_config_response));
    } else {
        return Status::InternalError("IP or port doesn't exists");
    }
    return Status::OK();
}

Status SchemaTablesConfigScanner::get_next(ChunkPtr* chunk, bool* eos) {
    if (!_is_init) {
        return Status::InternalError("Used before initialized.");
    }
    if (nullptr == chunk || nullptr == eos) {
        return Status::InternalError("input pointer is nullptr.");
    }
    if (_tables_config_index >= _tables_config_response.tables_config_infos.size()) {
        *eos = true;
        return Status::OK();
    }
    *eos = false;
    return fill_chunk(chunk);
}

Status SchemaTablesConfigScanner::fill_chunk(ChunkPtr* chunk) {
    const TTableConfigInfo& info = _tables_config_response.tables_config_infos[_tables_config_index];
    const auto& slot_id_to_index_map = (*chunk)->get_slot_id_to_index_map();
    for (const auto& [slot_id, index] : slot_id_to_index_map) {
        switch (slot_id) {
        case 1: {
            // table_schema
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(1);
                const std::string* str = &info.table_schema;
                Slice value(str->c_str(), str->length());
                fill_column_with_slot<TYPE_VARCHAR>(column.get(), (void*)&value);
            }
            break;
        }
        case 2: {
            // table_name
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(2);
                const std::string* str = &info.table_name;
                Slice value(str->c_str(), str->length());
                fill_column_with_slot<TYPE_VARCHAR>(column.get(), (void*)&value);
            }
            break;
        }
        case 3: {
            // table_engine
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(3);
                const std::string* str = &info.table_engine;
                Slice value(str->c_str(), str->length());
                fill_column_with_slot<TYPE_VARCHAR>(column.get(), (void*)&value);
            }
            break;
        }
        case 4: {
            // table_model
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(4);
                const std::string* str = &info.table_model;
                Slice value(str->c_str(), str->length());
                fill_column_with_slot<TYPE_VARCHAR>(column.get(), (void*)&value);
            }
            break;
        }
        case 5: {
            // primary_key
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(5);
                const std::string* str = &info.primary_key;
                Slice value(str->c_str(), str->length());
                fill_column_with_slot<TYPE_VARCHAR>(column.get(), (void*)&value);
            }
            break;
        }
        case 6: {
            // partition_key
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(6);
                const std::string* str = &info.partition_key;
                Slice value(str->c_str(), str->length());
                fill_column_with_slot<TYPE_VARCHAR>(column.get(), (void*)&value);
            }
            break;
        }
        case 7: {
            // distribute_key
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(7);
                const std::string* str = &info.distribute_key;
                Slice value(str->c_str(), str->length());
                fill_column_with_slot<TYPE_VARCHAR>(column.get(), (void*)&value);
            }
            break;
        }
        case 8: {
            // distribute_type
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(8);
                const std::string* str = &info.distribute_type;
                Slice value(str->c_str(), str->length());
                fill_column_with_slot<TYPE_VARCHAR>(column.get(), (void*)&value);
            }
            break;
        }
        case 9: {
            // distribute_bucket
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(9);
                fill_column_with_slot<TYPE_INT>(column.get(), (void*)&info.distribute_bucket);
            }
            break;
        }
        case 10: {
            // sort_key
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(10);
                const std::string* str = &info.sort_key;
                Slice value(str->c_str(), str->length());
                fill_column_with_slot<TYPE_VARCHAR>(column.get(), (void*)&value);
            }
            break;
        }
        case 11: {
            // properties
            {
                ColumnPtr column = (*chunk)->get_column_by_slot_id(11);
                const std::string* str = &info.properties;
                Slice value(str->c_str(), str->length());
                fill_column_with_slot<TYPE_VARCHAR>(column.get(), (void*)&value);
            }
            break;
        }
        default:
            break;
        }
    }
    _tables_config_index++;
    return Status::OK();
}

} // namespace starrocks::vectorized