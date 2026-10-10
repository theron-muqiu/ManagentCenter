package com.muqiu.managentcenter.system.service;

import com.muqiu.managentcenter.system.entity.PermissionRow;
import com.muqiu.managentcenter.system.enums.PermissionType;
import com.muqiu.managentcenter.system.repository.PermissionRepository;
import com.muqiu.managentcenter.system.vo.MenuNode;
import com.muqiu.managentcenter.system.vo.PermissionNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 权限树与可见菜单组织，父子关系不扩大授权。 */
@Service
public class PermissionService {
    /** 完整预置数据读取。 */
    private final PermissionRepository permissions;

    /** 注入预置仓库。 */
    public PermissionService(PermissionRepository permissions) { this.permissions = permissions; }

    /** 返回所有权限节点供显式授权选择。 */
    public List<PermissionNode> tree() { return nodes(permissions.listOrdered(), null); }

    /** 递归构造有序权限树，不隐式授予父子权限。 */
    private List<PermissionNode> nodes(List<PermissionRow> rows, Long parentId) {
        return rows.stream().filter(row -> Objects.equals(row.parentId(), parentId))
                .map(row -> new PermissionNode(Long.toString(row.id()), row.code(), row.name(), row.type(),
                        row.parentId() == null ? null : row.parentId().toString(), row.routePath(), row.sortOrder(),
                        nodes(rows, row.id()))).toList();
    }

    /** 已授权页面和必要容器构成菜单，不显示未授权操作。 */
    public List<MenuNode> menus(Set<String> codes) { return visible(tree(), codes); }

    /** 补全展示容器，不能把容器添加为有效权限。 */
    private List<MenuNode> visible(List<PermissionNode> nodes, Set<String> codes) {
        List<MenuNode> result = new ArrayList<>();
        for (PermissionNode node : nodes) {
            if (node.type() == PermissionType.ACTION.code()) continue;
            List<MenuNode> children = visible(node.children(), codes);
            if (node.type() == PermissionType.PAGE.code() && !codes.contains(node.code())) continue;
            if (node.type() == PermissionType.MENU.code() && children.isEmpty() && !codes.contains(node.code())) continue;
            result.add(new MenuNode(node.code(), node.name(), node.routePath(), children));
        }
        return result;
    }
}
