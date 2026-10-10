package com.muqiu.managentcenter.system.vo;

import java.util.List;

/** PermissionNode 明确字段契约。 */
public record PermissionNode(
        /** 权限 ID 字符串。 */ String id,
        /** 稳定权限代码。 */ String code,
        /** 中文名称。 */ String name,
        /** 固定权限类型。 */ int type,
        /** 父 ID 字符串，根为空。 */ String parentId,
        /** 页面路径。 */ String routePath,
        /** 同级顺序。 */ int sortOrder,
        /** 权限子树。 */ List<PermissionNode> children
) {}
