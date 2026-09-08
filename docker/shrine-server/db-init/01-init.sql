-- Local SHRINE database bootstrap for docker-compose.
-- Creates the four schemas the SHRINE Tomcat datasources expect
-- (see shrine-server/src/configs/local/mu/context.xml) and a user whose
-- credentials match those datasources.
--
-- NOTE: schemas are created empty. SHRINE/QEP/steward tables are created by
-- SHRINE on startup (Slick/Liquibase) where applicable; if your build expects
-- pre-seeded tables, load them after the containers are up.

CREATE DATABASE IF NOT EXISTS shrine_query_history CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS adapterAuditDB      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS qepAuditDB          CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS stewardDB           CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Credentials must match context.xml (username="shrineAdmin").
CREATE USER IF NOT EXISTS 'shrineAdmin'@'%' IDENTIFIED BY 'Y3DPRf24sYLqIG76Hw';

GRANT ALL PRIVILEGES ON shrine_query_history.* TO 'shrineAdmin'@'%';
GRANT ALL PRIVILEGES ON adapterAuditDB.*      TO 'shrineAdmin'@'%';
GRANT ALL PRIVILEGES ON qepAuditDB.*          TO 'shrineAdmin'@'%';
GRANT ALL PRIVILEGES ON stewardDB.*           TO 'shrineAdmin'@'%';

FLUSH PRIVILEGES;
