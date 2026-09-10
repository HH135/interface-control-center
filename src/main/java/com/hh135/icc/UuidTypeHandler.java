package com.hh135.icc;

import java.sql.*;
import java.util.UUID;
import org.apache.ibatis.type.*;
import org.springframework.stereotype.Component;

@Component
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override public void setNonNullParameter(PreparedStatement ps, int i, UUID value, JdbcType type) throws SQLException { ps.setObject(i, value); }
    @Override public UUID getNullableResult(ResultSet rs, String column) throws SQLException { return uuid(rs.getObject(column)); }
    @Override public UUID getNullableResult(ResultSet rs, int column) throws SQLException { return uuid(rs.getObject(column)); }
    @Override public UUID getNullableResult(CallableStatement cs, int column) throws SQLException { return uuid(cs.getObject(column)); }
    private UUID uuid(Object value) { return value == null ? null : UUID.fromString(value.toString()); }
}
