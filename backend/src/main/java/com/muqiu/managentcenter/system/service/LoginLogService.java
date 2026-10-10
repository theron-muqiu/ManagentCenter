package com.muqiu.managentcenter.system.service;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.common.PageData;
import com.muqiu.managentcenter.system.repository.LoginLogRepository;
import com.muqiu.managentcenter.system.vo.LoginLogDetail;
import com.muqiu.managentcenter.system.vo.LoginLogListItem;

import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 登录历史的 UTC 筛选和一致只读事务。 */
@Service
public class LoginLogService {
    /** 历史仓库。 */ private final LoginLogRepository logs;
    /** 注入只读仓库。 */ public LoginLogService(LoginLogRepository logs) { this.logs = logs; }
    /** 拒绝非法及反向时间范围，不把数据库错误转为空集合。 */
    @Transactional(readOnly=true)
    public PageData<LoginLogListItem> list(int page, int size, String username, Integer result, String startTime, String endTime) {
        LocalDateTime start = null; LocalDateTime end = null;
        try {
            if (startTime != null) { var parsed = OffsetDateTime.parse(startTime); if (!parsed.getOffset().equals(ZoneOffset.UTC)) throw new ApiException(ErrorCode.INVALID_INPUT); start = parsed.toLocalDateTime(); }
            if (endTime != null) { var parsed = OffsetDateTime.parse(endTime); if (!parsed.getOffset().equals(ZoneOffset.UTC)) throw new ApiException(ErrorCode.INVALID_INPUT); end = parsed.toLocalDateTime(); }
        } catch (DateTimeException invalid) { throw new ApiException(ErrorCode.INVALID_INPUT); }
        if ((start != null && (start.getYear() < 1000 || start.getYear() > 9999)) || (end != null && (end.getYear() < 1000 || end.getYear() > 9999))
                || (start != null && end != null && !end.isAfter(start))) throw new ApiException(ErrorCode.INVALID_INPUT);
        return logs.page(page, size, username, result, start, end);
    }
    /** 独立查看登录历史详情。 */
    @Transactional(readOnly=true)
    public LoginLogDetail detail(long id) { return logs.detail(id); }
}
