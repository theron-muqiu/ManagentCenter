package com.muqiu.managentcenter.common;

import java.util.List;

/** 固定分页契约，查询与总数来自同一快照。 */
public record PageData<T>(
        /** 当前页条目。 */ List<T> items,
        /** 精确总数。 */ long total,
        /** 从一开始的页码。 */ int page,
        /** 每页容量。 */ int size) {}
