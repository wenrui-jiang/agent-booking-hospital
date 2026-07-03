-- Auth v1 migration for user_info.
-- Run against yygh_user before enabling password login.

ALTER TABLE user_info
  ADD COLUMN email varchar(128) DEFAULT NULL COMMENT 'email login account' AFTER phone,
  ADD COLUMN password_hash varchar(255) DEFAULT NULL COMMENT 'password hash' AFTER email,
  ADD COLUMN refresh_token_version int DEFAULT 0 COMMENT 'refresh token revoke version' AFTER password_hash;

CREATE UNIQUE INDEX uk_user_info_email ON user_info(email);

-- Backfill known email-demo accounts that were previously stored as hashed phone login keys.
-- Example:
-- UPDATE user_info SET email='jiang.wenrui@outlook.com' WHERE phone='E1472976249' AND email IS NULL;
