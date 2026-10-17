-- Optional, manually applied migration for databases that do not have this column.
-- The inspected local database already has profile_picture VARCHAR(255) NULL.
-- Select the intended FTMS database before running this file. No application code runs it.
SET @profile_picture_migration = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE users ADD COLUMN profile_picture VARCHAR(255) NULL DEFAULT NULL',
        'SELECT ''users.profile_picture already exists; no schema change'' AS migration_status')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'profile_picture'
);
PREPARE profile_picture_statement FROM @profile_picture_migration;
EXECUTE profile_picture_statement;
DEALLOCATE PREPARE profile_picture_statement;
