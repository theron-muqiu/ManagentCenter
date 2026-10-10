package com.muqiu.managentcenter.system.service;

import com.muqiu.managentcenter.common.ApiException;
import com.muqiu.managentcenter.common.ErrorCode;
import com.muqiu.managentcenter.common.PageData;
import com.muqiu.managentcenter.system.repository.OperationLogRepository;
import com.muqiu.managentcenter.system.vo.OperationLogDetail;
import com.muqiu.managentcenter.system.vo.OperationLogListItem;

import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 操作记录时间边界与一致只读事务。 */
@Service
public class OperationLogService {
    /** 历史仓库。 */ private final OperationLogRepository logs;
    /** 注入只读仓库。 */ public OperationLogService(OperationLogRepository logs) { this.logs = logs; }
    /** 验证 UTC 时间范围，限制为 MySQL DATETIME 可表示日期。 */
    @Transactional(readOnly=true)
    public PageData<OperationLogListItem> list(int page, int size, String username, Integer type, Integer result, String startTime, String endTime) {
        LocalDateTime start = null; LocalDateTime end = null;
        try {
            if (startTime != null) { var parsed = OffsetDateTime.parse(startTime); if (!parsed.getOffset().equals(ZoneOffset.UTC)) throw new ApiException(ErrorCode.INVALID_INPUT); start = parsed.toLocalDateTime(); }
            if (endTime != null) { var parsed = OffsetDateTime.parse(endTime); if (!parsed.getOffset().equals(ZoneOffset.UTC)) throw new ApiException(ErrorCode.INVALID_INPUT); end = parsed.toLocalDateTime(); }
        } catch (DateTimeException invalid) { throw new ApiException(ErrorCode.INVALID_INPUT); }
        if ((start != null && (start.getYear() < 1000 || start.getYear() > 9999)) || (end != null && (end.getYear() < 1000 || end.getYear() > 9999))
                || (start != null && end != null && !end.isAfter(start))) throw new ApiException(ErrorCode.INVALID_INPUT);
        return logs.page(page, size, username, type, result, start, end);
    }
    /** 历史详情读取同样受独立查看权限保护。 */
    @Transactional(readOnly=true)
    public OperationLogDetail detail(long id) { return logs.detail(id); }
}
