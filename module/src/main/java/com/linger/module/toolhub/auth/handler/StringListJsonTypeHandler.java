package com.linger.module.toolhub.auth.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.linger.module.util.JsonUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.List;

public class StringListJsonTypeHandler extends BaseTypeHandler<List<String>> {

    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<List<String>>() { };

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, List<String> parameter, JdbcType jdbcType)
            throws SQLException {
        statement.setObject(index, JsonUtils.toJsonString(parameter), Types.OTHER);
    }

    @Override
    public List<String> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private List<String> parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = JsonUtils.parseObject(value, LIST_TYPE);
        return result == null ? Collections.emptyList() : result;
    }
}
