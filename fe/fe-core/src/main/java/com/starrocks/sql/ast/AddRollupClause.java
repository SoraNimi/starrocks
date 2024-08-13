// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.starrocks.sql.ast;

import com.starrocks.alter.AlterOpType;
import com.starrocks.sql.parser.NodePosition;

import java.util.List;
import java.util.Map;

// used to create one rollup
// syntax:
//      ALTER TABLE table_name
//          ADD ROLLUP rollup_name (column, ..) FROM base_rollup
public class AddRollupClause extends AlterTableClause {
    private final String rollupName;
    private final List<String> columnNames;
    private String baseRollupName;
    private final List<String> dupKeys;

    private final Map<String, String> properties;

    public String getRollupName() {
        return rollupName;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public List<String> getDupKeys() {
        return dupKeys;
    }

    public String getBaseRollupName() {
        return baseRollupName;
    }

    public AddRollupClause(String rollupName, List<String> columnNames,
                           List<String> dupKeys, String baseRollupName,
                           Map<String, String> properties) {
        this(rollupName, columnNames, dupKeys, baseRollupName, properties, NodePosition.ZERO);
    }

    public AddRollupClause(String rollupName, List<String> columnNames,
                           List<String> dupKeys, String baseRollupName,
                           Map<String, String> properties, NodePosition pos) {
        super(AlterOpType.ADD_ROLLUP, pos);
        this.rollupName = rollupName;
        this.columnNames = columnNames;
        this.dupKeys = dupKeys;
        this.baseRollupName = baseRollupName;
        this.properties = properties;
    }

    public void setBaseRollupName(String baseRollupName) {
        this.baseRollupName = baseRollupName;
    }

    public Map<String, String> getProperties() {
        return this.properties;
    }

    @Override
    public String toSql() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ADD ROLLUP `").append(rollupName).append("` (");
        int idx = 0;
        for (String column : columnNames) {
            if (idx != 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append("`").append(column).append("`");
            idx++;
        }
        stringBuilder.append(")");
        if (baseRollupName != null) {
            stringBuilder.append(" FROM `").append(baseRollupName).append("`");
        }
        return stringBuilder.toString();
    }

    @Override
    public String toString() {
        return toSql();
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitAddRollupClause(this, context);
    }
}
