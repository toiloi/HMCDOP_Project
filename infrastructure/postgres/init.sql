-- =============================================================
--  init.sql — Khởi tạo Database PostgreSQL cho MiniPaaS
-- =============================================================
-- Chạy khi khởi tạo PostgreSQL lần đầu.
-- Spring Boot (JPA ddl-auto=update) sẽ tự tạo bảng,
-- file này dùng để đặt thêm index và constraints rõ ràng.
-- =============================================================

-- Tạo database và user (chạy với superuser)
-- CREATE DATABASE minipaas;
-- CREATE USER minipaas WITH PASSWORD 'minipaas';
-- GRANT ALL PRIVILEGES ON DATABASE minipaas TO minipaas;

-- Extension UUID
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Index cho performance queries (sẽ được Spring Data JPA tự tạo nếu cần, hoặc tạo sau khi Backend đã sinh tables)
-- CREATE INDEX IF NOT EXISTS idx_deployments_user_id ON deployments(user_id);
-- CREATE INDEX IF NOT EXISTS idx_deployments_status ON deployments(status);
-- CREATE INDEX IF NOT EXISTS idx_build_logs_deployment_id ON build_logs(deployment_id);
