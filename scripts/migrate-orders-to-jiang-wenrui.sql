-- Bind all existing local demo registration orders to the default account:
-- jiang.wenrui@outlook.com.
--
-- Run with:
--   mysql -uroot -p1234 --default-character-set=utf8mb4 < scripts/migrate-orders-to-jiang-wenrui.sql

CREATE DATABASE IF NOT EXISTS yygh_user DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS yygh_order DEFAULT CHARACTER SET utf8mb4;

USE yygh_user;

SET @default_login_key = 'E1472976249';
SET @default_email = 'jiang.wenrui@outlook.com';

INSERT INTO user_info (
  nick_name, phone, name, certificates_type, certificates_no,
  certificates_url, auth_status, status, create_time, update_time, is_deleted
)
SELECT
  'jiang.wenrui', @default_login_key, 'jiang.wenrui', '10',
  '110101199001011249', NULL, 0, 1, NOW(), NOW(), 0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM user_info WHERE phone = @default_login_key AND is_deleted = 0
);

SELECT id INTO @default_user_id
FROM user_info
WHERE phone = @default_login_key AND is_deleted = 0
ORDER BY id
LIMIT 1;

INSERT INTO patient (
  user_id, name, certificates_type, certificates_no, sex, birthdate,
  phone, is_marry, province_code, city_code, district_code, address,
  contacts_name, contacts_certificates_type, contacts_certificates_no,
  contacts_phone, card_no, is_insure, status, create_time, update_time,
  is_deleted
)
SELECT
  @default_user_id, '张三', '10', '110101199001011249', 1, '1990-01-01',
  '13900000009', 0, '110000', '110100', '110102',
  '北京市西城区本地演示地址', '李四', '10', '110101199002022349',
  '13900000010', CONCAT('CARD-JIANG-', @default_user_id), 1, 1, NOW(), NOW(), 0
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM patient WHERE user_id = @default_user_id AND is_deleted = 0
);

SELECT id INTO @default_patient_id
FROM patient
WHERE user_id = @default_user_id AND is_deleted = 0
ORDER BY id
LIMIT 1;

UPDATE yygh_order.order_info
SET
  user_id = @default_user_id,
  patient_id = @default_patient_id,
  patient_name = COALESCE(patient_name, '张三'),
  patient_phone = COALESCE(patient_phone, '13900000009'),
  update_time = NOW()
WHERE is_deleted = 0;

SELECT @default_email AS account, @default_user_id AS user_id, @default_patient_id AS patient_id;
SELECT id, user_id, patient_id, patient_name, hosname
FROM yygh_order.order_info
WHERE is_deleted = 0
ORDER BY id;
