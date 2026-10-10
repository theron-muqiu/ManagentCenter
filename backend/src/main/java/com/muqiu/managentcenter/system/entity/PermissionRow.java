package com.muqiu.managentcenter.system.entity;


/** PermissionRow 明确字段契约。 */
public record PermissionRow(
        /** 权限 ID。 */ long id,
        /** 稳定权限代码。 */ String code,
        /** 中文名称。 */ String name,
        /** 固定权限类型。 */ int type,
        /** 父权限 ID，根为空。 */ Long parentId,
        /** 页面路径。 */ String routePath,
        /** 同级顺序。 */ int sortOrder
) {}
