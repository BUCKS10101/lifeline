-- IANA timezone identifier (for example Asia/Kolkata). "Today" for a user is computed in this zone.
ALTER TABLE user_profiles ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
