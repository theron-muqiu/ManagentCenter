package com.muqiu.managentcenter.system.repository;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.common.PageData;
import com.muqiu.managentcenter.system.vo.OperationLogDetail;
import com.muqiu.managentcenter.system.vo.OperationLogListItem;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 操作历史只读访问，不 JOIN 当前业务对象。 */
@Repository
public class OperationLogRepository {
    /** 历史读取。 */ private final JdbcTemplate jdbc;
    /** 安全摘要解码。 */ private final ObjectMapper mapper;
    /** 注入只读依赖。 */ public OperationLogRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    /** 映射历史快照，ID 输出十进制字符串。 */
    private OperationLogDetail map(ResultSet row, int index) throws SQLException {
        Long operator = row.getObject("operator_id", Long.class);
        Long target = row.getObject("target_id", Long.class);
        com.fasterxml.jackson.databind.JsonNode summary;
        try { summary = mapper.readTree(row.getString("summary")); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new SQLException("安全摘要无法解码"); }
        return new OperationLogDetail(Long.toString(row.getLong("id")), row.getString("request_id"), row.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
            row.getInt("result"), row.getInt("error_code"), row.getString("failure_reason"), row.getString("client_ip"), row.getLong("duration_ms"),
            operator == null ? null : operator.toString(), row.getString("operator_username"), row.getString("operator_display_name"), row.getString("module"), row.getString("feature"),
            row.getInt("operation_type"), row.getObject("target_type", Integer.class), target == null ? null : target.toString(), row.getString("request_method"), row.getString("request_path"),
            summary, row.getString("user_agent"), row.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }
    /** 参数化筛选；事务快照由服务边界提供，列表不返回详情字段。 */
    public PageData<OperationLogListItem> page(int page, int size, String username, Integer type, Integer result, LocalDateTime start, LocalDateTime end) {
        String where = " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (username != null && !username.isEmpty()) { where += " AND operator_username=?"; args.add(username); }
        if (type != null) { where += " AND operation_type=?"; args.add(type); }
        if (result != null) { where += " AND result=?"; args.add(result); }
        if (start != null) { where += " AND occurred_at>=?"; args.add(start); }
        if (end != null) { where += " AND occurred_at<?"; args.add(end); }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log" + where, Long.class, args.toArray());
        args.add(size); args.add(((long) page - 1) * size);
        var rows = jdbc.query("SELECT * FROM sys_operation_log" + where + " ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?", this::map, args.toArray());
        var items = rows.stream().map(row -> new OperationLogListItem(row.id(), row.requestId(), row.occurredAt(), row.result(), row.errorCode(), row.failureReason(), row.clientIp(), row.durationMs(),
            row.operatorId(), row.operatorUsername(), row.operatorDisplayName(), row.module(), row.feature(), row.operationType(), row.targetType(), row.targetId())).toList();
        return new PageData<>(items, total, page, size);
    }
    /** 历史缺失明确返回 404，查询错误不伪装空记录。 */
    public OperationLogDetail detail(long id) {
        var rows = jdbc.query("SELECT * FROM sys_operation_log WHERE id=?", this::map, id);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.NOT_FOUND);
        return rows.getFirst();
    }
}
