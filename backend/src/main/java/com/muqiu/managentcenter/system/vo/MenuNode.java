package com.muqiu.managentcenter.system.vo;

import java.util.List;

/** MenuNode 明确字段契约。 */
public record MenuNode(
        /** 权限代码或展示容器。 */ String code,
        /** 菜单名称。 */ String name,
        /** 页面路径，容器为空。 */ String routePath,
        /** 可见子菜单。 */ List<MenuNode> children
) {}
