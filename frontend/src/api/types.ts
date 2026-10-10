export interface RoleOption { id: string; name: string }
export interface Role { id: string; name: string; description: string | null; version: string; createdAt: string; updatedAt: string; permissionCodes: string[] }
export interface User { id: string; username: string; displayName: string; phone: string | null; email: string | null; status: number; superAdmin: boolean; version: string; createdAt: string; updatedAt: string; roles: RoleOption[] }
export interface Menu { code: string; name: string; routePath: string | null; children: Menu[] }
export interface Identity { user: User; roleIds: string[]; permissionCodes: string[]; menus: Menu[]; expiresAt: string }
export interface Login { user: User; token: string; tokenType: 'Bearer'; expiresAt: string }
export interface Page<T> { items: T[]; total: number; page: number; size: number }
export interface UserRoles { userId: string; version: string; roleIds: string[] }
export interface RolePermissions { roleId: string; version: string; permissionIds: string[] }
export interface PermissionNode { id: string; code: string; name: string; type: number; parentId: string | null; routePath: string | null; sortOrder: number; children: PermissionNode[] }
export interface AuditLog { id: string; requestId: string; occurredAt: string; result: number; errorCode: number; failureReason: string | null; clientIp: string | null; durationMs: number }
export interface OperationLog extends AuditLog { operatorId: string | null; operatorUsername: string | null; operatorDisplayName: string | null; module: string; feature: string; operationType: number; targetType: number | null; targetId: string | null }
export interface OperationLogDetail extends OperationLog { requestMethod: string; requestPath: string; summary: Record<string, string | number | string[]>; userAgent: string | null; createdAt: string }
export interface LoginLog extends AuditLog { userId: string | null; attemptedUsername: string | null }
export interface LoginLogDetail extends LoginLog { userAgent: string | null; createdAt: string }
