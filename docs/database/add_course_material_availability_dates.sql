-- Optional, manually applied migration for databases without material availability dates.
-- Select the intended FTMS database before running this file. No application code runs it.
SET @available_from_migration = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE course_materials ADD COLUMN available_from DATE NULL',
        'SELECT ''course_materials.available_from already exists; no schema change'' AS migration_status')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'course_materials' AND column_name = 'available_from'
);
PREPARE available_from_statement FROM @available_from_migration;
EXECUTE available_from_statement;
DEALLOCATE PREPARE available_from_statement;

SET @expires_on_migration = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE course_materials ADD COLUMN expires_on DATE NULL',
        'SELECT ''course_materials.expires_on already exists; no schema change'' AS migration_status')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'course_materials' AND column_name = 'expires_on'
);
PREPARE expires_on_statement FROM @expires_on_migration;
EXECUTE expires_on_statement;
DEALLOCATE PREPARE expires_on_statement;