// This file is licensed under the Elastic License 2.0. Copyright 2021 StarRocks Limited.

#include "percentile_functions.h"

#include "column/column_builder.h"
#include "column/column_helper.h"
#include "column/column_viewer.h"
#include "gutil/strings/substitute.h"
#include "util/string_parser.hpp"

namespace starrocks::vectorized {

ColumnPtr PercentileFunctions::percentile_hash(FunctionContext* context, const Columns& columns) {
    ColumnViewer<TYPE_DOUBLE> viewer(columns[0]);

    auto percentile_column = PercentileColumn::create();
    size_t size = columns[0]->size();
    for (int row = 0; row < size; ++row) {
        PercentileValue value;
        if (!viewer.is_null(row)) {
            value.add(viewer.value(row));
        }
        percentile_column->append(&value);
    }

    if (ColumnHelper::is_all_const(columns)) {
        return ConstColumn::create(percentile_column, columns[0]->size());
    } else {
        return percentile_column;
    }
}

ColumnPtr PercentileFunctions::percentile_empty(FunctionContext* context, const Columns& columns) {
    PercentileValue value;
    return ColumnHelper::create_const_column<TYPE_PERCENTILE>(&value, 1);
}

ColumnPtr PercentileFunctions::percentile_approx_raw(FunctionContext* context, const Columns& columns) {
    ColumnViewer<TYPE_PERCENTILE> viewer1(columns[0]);
    ColumnViewer<TYPE_DOUBLE> viewer2(columns[1]);
    ColumnBuilder<TYPE_DOUBLE> builder;
    size_t size = columns[0]->size();
    for (int row = 0; row < size; ++row) {
        double result = viewer1.value(row)->quantile(viewer2.value(row));
        builder.append(result);
    }
    return builder.build(columns[0]->is_constant());
}

} // namespace starrocks::vectorized