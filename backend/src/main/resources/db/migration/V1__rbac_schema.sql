CREATE TABLE sys_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  is_super_admin TINYINT NOT NULL DEFAULT 0,
  phone VARCHAR(16) DEFAULT NULL,
  email VARCHAR(254) DEFAULT NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  auth_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username),
  CONSTRAINT chk_user_status CHECK (status IN (0,1)),
  CONSTRAINT chk_user_super CHECK (is_super_admin IN (0,1)),
  CONSTRAINT chk_user_super_enabled CHECK (is_super_admin=0 OR status=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
  description VARCHAR(500) DEFAULT NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_permission (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
  name VARCHAR(100) NOT NULL,
  type TINYINT NOT NULL,
  parent_id BIGINT UNSIGNED DEFAULT NULL,
  route_path VARCHAR(200) DEFAULT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_permission_code (code),
  KEY idx_sys_permission_parent (parent_id,sort_order,id),
  CONSTRAINT chk_permission_type CHECK (type IN (0,1,2)),
  CONSTRAINT fk_permission_parent FOREIGN KEY (parent_id) REFERENCES sys_permission(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
  user_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id,role_id),
  KEY idx_sys_user_role_role (role_id,user_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_permission (
  role_id BIGINT UNSIGNED NOT NULL,
  permission_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (role_id,permission_id),
  KEY idx_sys_role_permission_permission (permission_id,role_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_operation_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  operator_id BIGINT UNSIGNED DEFAULT NULL,
  operator_username VARCHAR(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  operator_display_name VARCHAR(100) DEFAULT NULL,
  module VARCHAR(32) NOT NULL,
  feature VARCHAR(100) NOT NULL,
  operation_type TINYINT NOT NULL,
  target_type TINYINT DEFAULT NULL,
  target_id BIGINT UNSIGNED DEFAULT NULL,
  request_method VARCHAR(10) NOT NULL,
  request_path VARCHAR(200) NOT NULL,
  summary JSON NOT NULL,
  request_id VARCHAR(36) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  result TINYINT NOT NULL,
  error_code INT NOT NULL DEFAULT 0,
  failure_reason VARCHAR(500) DEFAULT NULL,
  client_ip VARCHAR(45) DEFAULT NULL,
  user_agent VARCHAR(512) DEFAULT NULL,
  duration_ms BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_operation_log_request (request_id),
  KEY idx_sys_operation_log_time (occurred_at,id),
  KEY idx_sys_operation_log_operator (operator_username,occurred_at,id),
  KEY idx_sys_operation_log_result (result,occurred_at,id),
  KEY idx_sys_operation_log_type (operation_type,occurred_at,id),
  CONSTRAINT chk_operation_result CHECK (result IN (0,1)),
  CONSTRAINT chk_operation_type CHECK (operation_type BETWEEN 0 AND 7),
  CONSTRAINT chk_operation_target CHECK (target_type IS NULL OR target_type IN (0,1)),
  CONSTRAINT chk_operation_success_code CHECK (result=0 OR error_code=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_login_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED DEFAULT NULL,
  attempted_username VARCHAR(64) COLLATE utf8mb4_0900_bin DEFAULT NULL,
  request_id VARCHAR(36) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  result TINYINT NOT NULL,
  error_code INT NOT NULL DEFAULT 0,
  failure_reason VARCHAR(500) DEFAULT NULL,
  client_ip VARCHAR(45) DEFAULT NULL,
  user_agent VARCHAR(512) DEFAULT NULL,
  duration_ms BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_login_log_request (request_id),
  KEY idx_sys_login_log_time (occurred_at,id),
  KEY idx_sys_login_log_username (attempted_username,occurred_at,id),
  KEY idx_sys_login_log_result (result,occurred_at,id),
  CONSTRAINT chk_login_result CHECK (result IN (0,1)),
  CONSTRAINT chk_login_success_code CHECK (result=0 OR error_code=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
