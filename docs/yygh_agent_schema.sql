CREATE DATABASE IF NOT EXISTS yygh_agent DEFAULT CHARACTER SET utf8mb4;
USE yygh_agent;

CREATE TABLE IF NOT EXISTS agent_pretriage_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  order_id BIGINT NULL,
  chief_complaint VARCHAR(255) NULL,
  symptom_summary TEXT NULL,
  duration VARCHAR(64) NULL,
  severity VARCHAR(64) NULL,
  accompanying_symptoms TEXT NULL,
  negative_symptoms TEXT NULL,
  patient_context_json TEXT NULL,
  department_recommendation_json TEXT NULL,
  booking_draft_json TEXT NULL,
  doctor_copy_text TEXT NULL,
  confirmed TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_tool_call (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  tool_name VARCHAR(128) NOT NULL,
  arguments_text TEXT NULL,
  status VARCHAR(32) NOT NULL,
  result_summary TEXT NULL,
  cost_ms BIGINT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
