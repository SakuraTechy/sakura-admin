-- 调度服务使用独立数据库，避免与管理端业务表混用。
CREATE DATABASE IF NOT EXISTS `continew_admin_job`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;
