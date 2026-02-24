-- Migration script to rename 'email' to 'user' and add 'is_first_login' field in 'users' table

-- 1. Rename column 'email' to 'user'
-- Note: Check if your database supports 'CHANGE COLUMN' or uses a different syntax (e.g. RENAME COLUMN)
-- MySQL/MariaDB syntax:
ALTER TABLE users RENAME COLUMN email TO "user";

-- 2. Drop the old index on email (if the name didn't update automatically or you want to enforce naming convention)
-- ALTER TABLE users DROP INDEX idx_user_email; -- Only if needed
-- ALTER TABLE users DROP INDEX uk_user_email; -- Only if needed

-- 3. Create new index for user (if renaming didn't preserve/rename it)
-- CREATE UNIQUE INDEX idx_user_user ON users (user);
-- CREATE UNIQUE INDEX uk_user_user ON users (user);

-- 4. Add 'is_first_login' column with default value TRUE
ALTER TABLE users ADD COLUMN is_first_login BOOLEAN NOT NULL DEFAULT TRUE;

-- 5. (Optional) Update existing users to have is_first_login = FALSE if necessary
-- UPDATE users SET is_first_login = FALSE;

-- 6. Add 'is_hidden' column with default value FALSE (usuarios no ocultos por defecto)
ALTER TABLE users ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT FALSE;
