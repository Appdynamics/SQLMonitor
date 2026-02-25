/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.sql;

import com.appdynamics.extensions.util.AssertUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public class ColumnGenerator {

    public List<Column> getColumns(Map query) {
        Object columnsObj = query.get("columns");
        AssertUtils.assertNotNull(columnsObj, "Queries need to have columns configured.");

        Map<String, Object> wrapper = Maps.newLinkedHashMap();
        wrapper.put("columns", columnsObj);

        final ObjectMapper mapper = new ObjectMapper();
        final Columns columns = mapper.convertValue(wrapper, Columns.class);
        return columns.getColumns();
    }
}
