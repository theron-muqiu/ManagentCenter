package com.muqiu.managentcenter.system.repository;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.common.PageData;
import com.muqiu.managentcenter.system.vo.LoginLogDetail;
import com.muqiu.managentcenter.system.vo.LoginLogListItem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 登录历史只读数据访问，不参与认证凭据判断。 */
@Repository
public class LoginLogRepository {
    /** 历史读取。 */ private final JdbcTemplate jdbc;
    /** 注入只读数据访问。 */ public LoginLogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** 映射逻辑引用和安全历史字段。 */
    private LoginLogDetail map(ResultSet row, int index) throws SQLException {
        Long user = row.getObject("user_id", Long.class);
        return new LoginLogDetail(Long.toString(row.getLong("id")), row.getString("request_id"), row.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
            row.getInt("result"), row.getInt("error_code"), row.getString("failure_reason"), row.getString("client_ip"), row.getLong("duration_ms"), user == null ? null : user.toString(),
            row.getString("attempted_username"), row.getString("user_agent"), row.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }
    /** 精确账号筛选和半开时间范围，总数与条目由同一快照读取。 */
    public PageData<LoginLogListItem> page(int page, int size, String username, Integer result, LocalDateTime start, LocalDateTime end) {
        String where = " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (username != null && !username.isEmpty()) { where += " AND attempted_username=?"; args.add(username); }
        if (result != null) { where += " AND result=?"; args.add(result); }
        if (start != null) { where += " AND occurred_at>=?"; args.add(start); }
        if (end != null) { where += " AND occurred_at<?"; args.add(end); }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_login_log" + where, Long.class, args.toArray());
        args.add(size); args.add(((long) page - 1) * size);
        var rows = jdbc.query("SELECT * FROM sys_login_log" + where + " ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?", this::map, args.toArray());
        var items = rows.stream().map(row -> new LoginLogListItem(row.id(), row.requestId(), row.occurredAt(), row.result(), row.errorCode(), row.failureReason(), row.clientIp(), row.durationMs(), row.userId(), row.attemptedUsername())).toList();
        return new PageData<>(items, total, page, size);
    }
    /** 缺少历史时明确失败，不关联当前用户状态。 */
    public LoginLogDetail detail(long id) {
        var rows = jdbc.query("SELECT * FROM sys_login_log WHERE id=?", this::map, id);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.NOT_FOUND);
        return rows.getFirst();
    }
}
