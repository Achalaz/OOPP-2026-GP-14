-- Inspected target: MySQL 8.0.46, ftms_group14.assessments (2026-10-30).
-- Neither publication column existed at inspection. Apply once, after review.
-- No data is deleted or backfilled as released. No marks/results/GPA tables are changed.
-- Existing and newly created assessments default to Draft.
ALTER TABLE assessments
    ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN published_at TIMESTAMP NULL DEFAULT NULL;
