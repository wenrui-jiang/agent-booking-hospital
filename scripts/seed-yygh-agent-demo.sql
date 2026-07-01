-- Agent demo seed data for the local Shangyitong backend.
-- Run with:
--   mysql -uroot -p1234 --default-character-set=utf8mb4 < scripts/seed-yygh-agent-demo.sql
--
-- This SQL only covers MySQL-backed data used by the agent booking flow:
-- user_info, patient, and the order_info compatibility column.
-- Hospital, department, and schedule data are MongoDB collections and must be
-- loaded with scripts/import-yygh-sample-data.js.

CREATE DATABASE IF NOT EXISTS yygh_user DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS yygh_order DEFAULT CHARACTER SET utf8mb4;

USE yygh_order;

DROP PROCEDURE IF EXISTS add_schedule_id_if_missing;
DELIMITER //
CREATE PROCEDURE add_schedule_id_if_missing()
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'yygh_order'
      AND TABLE_NAME = 'order_info'
      AND COLUMN_NAME = 'schedule_id'
  ) THEN
    ALTER TABLE yygh_order.order_info
      ADD COLUMN schedule_id varchar(50) NULL AFTER depname;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'yygh_order'
      AND TABLE_NAME = 'order_info'
      AND INDEX_NAME = 'idx_schedule_id'
  ) THEN
    ALTER TABLE yygh_order.order_info
      ADD INDEX idx_schedule_id (schedule_id);
  END IF;
END //
DELIMITER ;
CALL add_schedule_id_if_missing();
DROP PROCEDURE add_schedule_id_if_missing;

USE yygh_user;

INSERT INTO user_info (
  id, openid, nick_name, phone, name, certificates_type, certificates_no,
  certificates_url, auth_status, status, create_time, update_time, is_deleted
) VALUES (
  1, NULL, '本地演示用户', '13900000000', '本地演示用户', '10',
  '110101199001011234', NULL, 2, 1, NOW(), NOW(), 0
) ON DUPLICATE KEY UPDATE
  nick_name = VALUES(nick_name),
  phone = VALUES(phone),
  name = VALUES(name),
  certificates_type = VALUES(certificates_type),
  certificates_no = VALUES(certificates_no),
  auth_status = VALUES(auth_status),
  status = VALUES(status),
  update_time = NOW(),
  is_deleted = 0;

INSERT INTO patient (
  id, user_id, name, certificates_type, certificates_no, sex, birthdate,
  phone, is_marry, province_code, city_code, district_code, address,
  contacts_name, contacts_certificates_type, contacts_certificates_no,
  contacts_phone, card_no, is_insure, status, create_time, update_time,
  is_deleted
) VALUES (
  1, 1, '张三', '10', '110101199001011234', 1, '1990-01-01',
  '13900000000', 0, '110000', '110100', '110102',
  '北京市西城区本地演示地址', '李四', '10', '110101199002022345',
  '13900000001', 'CARD-DEMO-001', 1, 1, NOW(), NOW(), 0
) ON DUPLICATE KEY UPDATE
  user_id = VALUES(user_id),
  name = VALUES(name),
  certificates_type = VALUES(certificates_type),
  certificates_no = VALUES(certificates_no),
  sex = VALUES(sex),
  birthdate = VALUES(birthdate),
  phone = VALUES(phone),
  is_marry = VALUES(is_marry),
  province_code = VALUES(province_code),
  city_code = VALUES(city_code),
  district_code = VALUES(district_code),
  address = VALUES(address),
  contacts_name = VALUES(contacts_name),
  contacts_certificates_type = VALUES(contacts_certificates_type),
  contacts_certificates_no = VALUES(contacts_certificates_no),
  contacts_phone = VALUES(contacts_phone),
  card_no = VALUES(card_no),
  is_insure = VALUES(is_insure),
  status = VALUES(status),
  update_time = NOW(),
  is_deleted = 0;

SELECT 'yygh_user.user_info' AS item, COUNT(*) AS rows_total
FROM yygh_user.user_info
WHERE id = 1 AND phone = '13900000000' AND is_deleted = 0
UNION ALL
SELECT 'yygh_user.patient' AS item, COUNT(*) AS rows_total
FROM yygh_user.patient
WHERE id = 1 AND user_id = 1 AND is_deleted = 0
UNION ALL
SELECT 'yygh_order.order_info.schedule_id' AS item, COUNT(*) AS rows_total
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'yygh_order'
  AND TABLE_NAME = 'order_info'
  AND COLUMN_NAME = 'schedule_id';
