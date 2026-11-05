-- ============================================================
-- FTMS GROUP 14: GITHUB ONE-FILE DATABASE SETUP (MySQL 8)
-- Source: user-provided full database SQL + demo rows, 2026-10-30.
-- Includes: profile_picture, medical attendance links, and assessment publication.
-- IMPORTANT: This is NOT a live mysqldump of the user's PC.
-- It reconstructs the SHARED schema and example/demo data only.
--
-- PUBLIC REPOSITORY WARNING:
-- Demo users below are TEST ACCOUNTS with a publicly known common password.
-- This is synthetic classroom data. NEVER import these sample users into
-- a real/production instance or reuse real passwords. Change demo passwords
-- before making any instance publicly accessible.
-- The file also has synthetic medical, marks and results rows.
--
-- BEFORE IMPORT:
-- 1) Take a database backup, especially if ftms_group14 already exists.
-- 2) Prefer importing into a CLEAN LOCAL DEMO database.
-- 3) MySQL Workbench: File > Open SQL Script > Execute All.
--    Or command line: mysql -u YOUR_USER -p < ftms_group14.sql
-- 4) This file creates schema, then inserts EXAMPLE data via INSERT IGNORE.
--    It will not synchronize or export current live user records.
-- 5) This script never DROP/TRUNCATEs data; no destructive reset.
-- ============================================================

-- ============================================================
-- FTMS - Faculty Technology Management System
-- Full MySQL Database Schema
-- Target: MySQL 8.x
-- ============================================================

CREATE DATABASE IF NOT EXISTS ftms_group14
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE ftms_group14;

-- Foreign keys remain enabled so broken demo relationships are not silently accepted.

-- ------------------------------------------------------------
-- 1. DEPARTMENTS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS departments (
    department_id INT AUTO_INCREMENT PRIMARY KEY,
    department_code VARCHAR(20) NOT NULL UNIQUE,
    department_name VARCHAR(150) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 2. USERS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('ADMIN','LECTURER','STUDENT','TECHNICAL_OFFICER') NOT NULL,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    email VARCHAR(150) UNIQUE,
    phone VARCHAR(30),
    profile_picture VARCHAR(255) NULL,
    department_id INT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 3. ROLE-SPECIFIC PROFILES
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    student_id INT PRIMARY KEY,
    registration_no VARCHAR(40) NOT NULL UNIQUE,
    intake_year YEAR NOT NULL,
    current_level INT NULL,
    CONSTRAINT fk_students_user
        FOREIGN KEY (student_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS lecturers (
    lecturer_id INT PRIMARY KEY,
    staff_no VARCHAR(40) NOT NULL UNIQUE,
    designation VARCHAR(100),
    CONSTRAINT fk_lecturers_user
        FOREIGN KEY (lecturer_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS technical_officers (
    technical_officer_id INT PRIMARY KEY,
    staff_no VARCHAR(40) NOT NULL UNIQUE,
    CONSTRAINT fk_technical_officers_user
        FOREIGN KEY (technical_officer_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS admins (
    admin_id INT PRIMARY KEY,
    staff_no VARCHAR(40) NOT NULL UNIQUE,
    CONSTRAINT fk_admins_user
        FOREIGN KEY (admin_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 4. ACADEMIC YEARS & SEMESTERS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS academic_years (
    academic_year_id INT AUTO_INCREMENT PRIMARY KEY,
    academic_year_name VARCHAR(20) NOT NULL UNIQUE,
    start_date DATE NULL,
    end_date DATE NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS semesters (
    semester_id INT AUTO_INCREMENT PRIMARY KEY,
    academic_year_id INT NOT NULL,
    semester_no TINYINT NOT NULL,
    semester_name VARCHAR(50),
    start_date DATE NULL,
    end_date DATE NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_semester_year UNIQUE (academic_year_id, semester_no),
    CONSTRAINT chk_semester_no CHECK (semester_no > 0),
    CONSTRAINT fk_semesters_academic_year
        FOREIGN KEY (academic_year_id)
        REFERENCES academic_years(academic_year_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 5. COURSES
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS courses (
    course_id INT AUTO_INCREMENT PRIMARY KEY,
    course_code VARCHAR(30) NOT NULL UNIQUE,
    course_name VARCHAR(180) NOT NULL,
    credits DECIMAL(4,1) NOT NULL,
    department_id INT NOT NULL,
    course_type ENUM('CORE','ELECTIVE','OPTIONAL') NOT NULL DEFAULT 'CORE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_course_credits CHECK (credits > 0),
    CONSTRAINT fk_courses_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 6. COURSE OFFERINGS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_offerings (
    offering_id INT AUTO_INCREMENT PRIMARY KEY,
    course_id INT NOT NULL,
    semester_id INT NOT NULL,
    offering_group VARCHAR(30) NOT NULL DEFAULT 'A',
    status ENUM('PLANNED','ACTIVE','COMPLETED','CANCELLED')
        NOT NULL DEFAULT 'PLANNED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_course_offering
        UNIQUE (course_id, semester_id, offering_group),
    CONSTRAINT fk_offering_course
        FOREIGN KEY (course_id)
        REFERENCES courses(course_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT fk_offering_semester
        FOREIGN KEY (semester_id)
        REFERENCES semesters(semester_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 7. LECTURER COURSE ASSIGNMENTS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lecturer_course_assignments (
    assignment_id INT AUTO_INCREMENT PRIMARY KEY,
    offering_id INT NOT NULL,
    lecturer_id INT NOT NULL,
    assignment_type ENUM('PRIMARY','ASSISTANT','PRACTICAL')
        NOT NULL DEFAULT 'PRIMARY',
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lecturer_assignment
        UNIQUE (offering_id, lecturer_id, assignment_type),
    CONSTRAINT fk_assignment_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_assignment_lecturer
        FOREIGN KEY (lecturer_id)
        REFERENCES lecturers(lecturer_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 8. ENROLLMENTS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id INT AUTO_INCREMENT PRIMARY KEY,
    offering_id INT NOT NULL,
    student_id INT NOT NULL,
    attempt_no INT NOT NULL DEFAULT 1,
    status ENUM('ENROLLED','WITHDRAWN','COMPLETED','REPEAT')
        NOT NULL DEFAULT 'ENROLLED',
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_enrollment
        UNIQUE (offering_id, student_id, attempt_no),
    CONSTRAINT chk_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT fk_enrollment_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_enrollment_student
        FOREIGN KEY (student_id)
        REFERENCES students(student_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 9. COURSE MATERIALS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_materials (
    material_id INT AUTO_INCREMENT PRIMARY KEY,
    offering_id INT NOT NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT,
    material_type ENUM('LECTURE_NOTE','PRACTICAL','ASSIGNMENT','LINK','OTHER')
        NOT NULL DEFAULT 'OTHER',
    resource_path VARCHAR(500),
    uploaded_by INT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_material_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_material_uploader
        FOREIGN KEY (uploaded_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 10. ASSESSMENTS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assessments (
    assessment_id INT AUTO_INCREMENT PRIMARY KEY,
    offering_id INT NOT NULL,
    assessment_name VARCHAR(120) NOT NULL,
    assessment_type ENUM(
        'QUIZ',
        'ASSIGNMENT',
        'MID',
        'PRACTICAL',
        'PROJECT',
        'PRESENTATION',
        'FINAL_EXAM',
        'OTHER'
    ) NOT NULL DEFAULT 'OTHER',
    max_mark DECIMAL(7,2) NOT NULL,
    weight_percentage DECIMAL(5,2) NOT NULL,
    assessment_date DATE NULL,
    description TEXT,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP NULL DEFAULT NULL,
    created_by INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_assessment_name
        UNIQUE (offering_id, assessment_name),
    CONSTRAINT chk_assessment_max_mark CHECK (max_mark > 0),
    CONSTRAINT chk_assessment_weight
        CHECK (weight_percentage >= 0 AND weight_percentage <= 100),
    CONSTRAINT fk_assessment_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_assessment_creator
        FOREIGN KEY (created_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 11. MARKS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS marks (
    mark_id INT AUTO_INCREMENT PRIMARY KEY,
    assessment_id INT NOT NULL,
    enrollment_id INT NOT NULL,
    marks_obtained DECIMAL(7,2) NULL,
    mark_status ENUM('PENDING','PRESENT','ABSENT','MEDICAL')
        NOT NULL DEFAULT 'PENDING',
    entered_by INT NULL,
    entered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_mark
        UNIQUE (assessment_id, enrollment_id),
    CONSTRAINT chk_mark_non_negative
        CHECK (marks_obtained IS NULL OR marks_obtained >= 0),
    CONSTRAINT fk_mark_assessment
        FOREIGN KEY (assessment_id)
        REFERENCES assessments(assessment_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_mark_enrollment
        FOREIGN KEY (enrollment_id)
        REFERENCES enrollments(enrollment_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_mark_entered_by
        FOREIGN KEY (entered_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 12. GRADE RULES
-- Important: insert your faculty/SRS-approved grade boundaries later.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS grade_rules (
    grade_rule_id INT AUTO_INCREMENT PRIMARY KEY,
    grade_letter VARCHAR(10) NOT NULL,
    min_mark DECIMAL(5,2) NOT NULL,
    max_mark DECIMAL(5,2) NOT NULL,
    grade_point DECIMAL(4,2) NOT NULL,
    is_pass BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_grade_rule_range
        UNIQUE (grade_letter, min_mark, max_mark),
    CONSTRAINT chk_grade_min CHECK (min_mark >= 0),
    CONSTRAINT chk_grade_max CHECK (max_mark <= 100),
    CONSTRAINT chk_grade_range CHECK (max_mark >= min_mark),
    CONSTRAINT chk_grade_point CHECK (grade_point >= 0)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 13. ATTENDANCE SESSIONS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_sessions (
    session_id INT AUTO_INCREMENT PRIMARY KEY,
    offering_id INT NOT NULL,
    session_date DATE NOT NULL,
    start_time TIME NULL,
    end_time TIME NULL,
    session_type ENUM('THEORY','PRACTICAL','TUTORIAL','OTHER')
        NOT NULL DEFAULT 'THEORY',
    topic VARCHAR(255),
    conducted_by INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_session_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_attendance_conducted_by
        FOREIGN KEY (conducted_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 14. ATTENDANCE RECORDS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_records (
    attendance_record_id INT AUTO_INCREMENT PRIMARY KEY,
    session_id INT NOT NULL,
    enrollment_id INT NOT NULL,
    attendance_status ENUM('PRESENT','ABSENT','LATE','EXCUSED','MEDICAL')
        NOT NULL DEFAULT 'ABSENT',
    marked_by INT NULL,
    marked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attendance_record
        UNIQUE (session_id, enrollment_id),
    CONSTRAINT fk_attendance_record_session
        FOREIGN KEY (session_id)
        REFERENCES attendance_sessions(session_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_attendance_record_enrollment
        FOREIGN KEY (enrollment_id)
        REFERENCES enrollments(enrollment_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_attendance_record_marked_by
        FOREIGN KEY (marked_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 15. MEDICAL RECORDS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS medicals (
    medical_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    offering_id INT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    reason VARCHAR(500),
    document_path VARCHAR(500),
    status ENUM('PENDING','APPROVED','REJECTED')
        NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by INT NULL,
    reviewed_at TIMESTAMP NULL,
    review_note VARCHAR(500),
    CONSTRAINT chk_medical_dates CHECK (to_date >= from_date),
    CONSTRAINT fk_medical_student
        FOREIGN KEY (student_id)
        REFERENCES students(student_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_medical_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_medical_reviewer
        FOREIGN KEY (reviewed_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 15A. ATTENDANCE <-> MEDICAL LINKS (required by MedicalAttendanceLinkDAO)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_medical_links (
    link_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    medical_id INT NOT NULL,
    attendance_record_id INT NOT NULL,
    linked_by INT NOT NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_med_att_med
        FOREIGN KEY (medical_id) REFERENCES medicals(medical_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_med_att_record
        FOREIGN KEY (attendance_record_id) REFERENCES attendance_records(attendance_record_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_med_att_user
        FOREIGN KEY (linked_by) REFERENCES users(user_id),
    UNIQUE KEY uq_med_att_record (attendance_record_id),
    KEY ix_med_att_medical (medical_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 16. ELIGIBILITY RECORDS
-- Stores a calculated snapshot for reporting/history.
-- Thresholds should come from your project/SRS business rules.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS eligibility_records (
    eligibility_id INT AUTO_INCREMENT PRIMARY KEY,
    enrollment_id INT NOT NULL,
    ca_score DECIMAL(7,2) NULL,
    ca_eligible BOOLEAN NULL,
    attendance_percentage DECIMAL(5,2) NULL,
    attendance_eligible BOOLEAN NULL,
    overall_eligible BOOLEAN NULL,
    reason VARCHAR(500),
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    calculated_by INT NULL,
    CONSTRAINT uq_eligibility_enrollment UNIQUE (enrollment_id),
    CONSTRAINT chk_attendance_percentage
        CHECK (
            attendance_percentage IS NULL OR
            (attendance_percentage >= 0 AND attendance_percentage <= 100)
        ),
    CONSTRAINT fk_eligibility_enrollment
        FOREIGN KEY (enrollment_id)
        REFERENCES enrollments(enrollment_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_eligibility_calculated_by
        FOREIGN KEY (calculated_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 17. COURSE RESULTS
-- Stores the published/calculated result snapshot.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_results (
    result_id INT AUTO_INCREMENT PRIMARY KEY,
    enrollment_id INT NOT NULL,
    ca_mark DECIMAL(7,2) NULL,
    final_exam_mark DECIMAL(7,2) NULL,
    overall_mark DECIMAL(7,2) NULL,
    grade_rule_id INT NULL,
    grade_letter VARCHAR(10) NULL,
    grade_point DECIMAL(4,2) NULL,
    result_status ENUM(
        'PENDING',
        'PASS',
        'FAIL',
        'ABSENT',
        'MEDICAL',
        'NOT_ELIGIBLE',
        'INCOMPLETE'
    ) NOT NULL DEFAULT 'PENDING',
    published BOOLEAN NOT NULL DEFAULT FALSE,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    calculated_by INT NULL,
    CONSTRAINT uq_course_result_enrollment UNIQUE (enrollment_id),
    CONSTRAINT chk_overall_mark
        CHECK (overall_mark IS NULL OR (overall_mark >= 0 AND overall_mark <= 100)),
    CONSTRAINT chk_result_grade_point
        CHECK (grade_point IS NULL OR grade_point >= 0),
    CONSTRAINT fk_result_enrollment
        FOREIGN KEY (enrollment_id)
        REFERENCES enrollments(enrollment_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_result_grade_rule
        FOREIGN KEY (grade_rule_id)
        REFERENCES grade_rules(grade_rule_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT fk_result_calculated_by
        FOREIGN KEY (calculated_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 18. SEMESTER RESULTS / GPA
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS semester_results (
    semester_result_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    semester_id INT NOT NULL,
    total_registered_credits DECIMAL(7,2) NOT NULL DEFAULT 0,
    total_earned_credits DECIMAL(7,2) NOT NULL DEFAULT 0,
    total_quality_points DECIMAL(10,2) NOT NULL DEFAULT 0,
    sgpa DECIMAL(4,2) NULL,
    cgpa DECIMAL(4,2) NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_semester_result
        UNIQUE (student_id, semester_id),
    CONSTRAINT chk_sgpa CHECK (sgpa IS NULL OR sgpa >= 0),
    CONSTRAINT chk_cgpa CHECK (cgpa IS NULL OR cgpa >= 0),
    CONSTRAINT fk_semester_result_student
        FOREIGN KEY (student_id)
        REFERENCES students(student_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_semester_result_semester
        FOREIGN KEY (semester_id)
        REFERENCES semesters(semester_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 19. NOTICES
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notices (
    notice_id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    content TEXT NOT NULL,
    audience ENUM(
        'ALL',
        'STUDENTS',
        'LECTURERS',
        'TECHNICAL_OFFICERS',
        'ADMINS'
    ) NOT NULL DEFAULT 'ALL',
    created_by INT NOT NULL,
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_notice_creator
        FOREIGN KEY (created_by)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 20. TIMETABLE
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS timetable_entries (
    timetable_id INT AUTO_INCREMENT PRIMARY KEY,
    offering_id INT NOT NULL,
    day_of_week ENUM(
        'MONDAY',
        'TUESDAY',
        'WEDNESDAY',
        'THURSDAY',
        'FRIDAY',
        'SATURDAY',
        'SUNDAY'
    ) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    room VARCHAR(80),
    session_type ENUM('LECTURE','PRACTICAL','TUTORIAL','OTHER')
        NOT NULL DEFAULT 'LECTURE',
    lecturer_id INT NULL,
    effective_from DATE NULL,
    effective_to DATE NULL,
    CONSTRAINT chk_timetable_time CHECK (end_time > start_time),
    CONSTRAINT chk_timetable_dates
        CHECK (
            effective_to IS NULL OR
            effective_from IS NULL OR
            effective_to >= effective_from
        ),
    CONSTRAINT fk_timetable_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_timetable_lecturer
        FOREIGN KEY (lecturer_id)
        REFERENCES lecturers(lecturer_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- NON-DESTRUCTIVE UPGRADES FOR EXISTING FTMS DATABASES
-- (safe to re-run; newly created tables already have these columns).
-- ------------------------------------------------------------
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'ALTER TABLE users ADD COLUMN profile_picture VARCHAR(255) NULL',
      'SELECT ''users.profile_picture already exists'' AS info')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'profile_picture'
);
PREPARE ftms_upgrade FROM @ftms_stmt;
EXECUTE ftms_upgrade;
DEALLOCATE PREPARE ftms_upgrade;

SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'ALTER TABLE assessments ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT FALSE',
      'SELECT ''assessments.is_published already exists'' AS info')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'assessments'
      AND COLUMN_NAME = 'is_published'
);
PREPARE ftms_upgrade FROM @ftms_stmt;
EXECUTE ftms_upgrade;
DEALLOCATE PREPARE ftms_upgrade;

SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'ALTER TABLE assessments ADD COLUMN published_at TIMESTAMP NULL DEFAULT NULL',
      'SELECT ''assessments.published_at already exists'' AS info')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'assessments'
      AND COLUMN_NAME = 'published_at'
);
PREPARE ftms_upgrade FROM @ftms_stmt;
EXECUTE ftms_upgrade;
DEALLOCATE PREPARE ftms_upgrade;

-- NOTE: latest OOPP_Group_14(4) code uses is_published/published_at.
-- The older assessment_release.sql used is_released/released_at.
-- Do NOT treat those two pairs as interchangeable without adapting Java code.

-- ------------------------------------------------------------
-- INDEXES
-- ------------------------------------------------------------
-- idx_users_role: users(role)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_users_role ON users(role)',
      'SELECT ''idx_users_role already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_role'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_course_offerings_semester: course_offerings(semester_id)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_course_offerings_semester ON course_offerings(semester_id)',
      'SELECT ''idx_course_offerings_semester already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_offerings'
      AND INDEX_NAME = 'idx_course_offerings_semester'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_enrollments_student: enrollments(student_id)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_enrollments_student ON enrollments(student_id)',
      'SELECT ''idx_enrollments_student already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments'
      AND INDEX_NAME = 'idx_enrollments_student'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_enrollments_offering: enrollments(offering_id)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_enrollments_offering ON enrollments(offering_id)',
      'SELECT ''idx_enrollments_offering already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'enrollments'
      AND INDEX_NAME = 'idx_enrollments_offering'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_assessments_offering: assessments(offering_id)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_assessments_offering ON assessments(offering_id)',
      'SELECT ''idx_assessments_offering already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'assessments'
      AND INDEX_NAME = 'idx_assessments_offering'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_marks_enrollment: marks(enrollment_id)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_marks_enrollment ON marks(enrollment_id)',
      'SELECT ''idx_marks_enrollment already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'marks'
      AND INDEX_NAME = 'idx_marks_enrollment'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_attendance_sessions_offering: attendance_sessions(offering_id)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_attendance_sessions_offering ON attendance_sessions(offering_id)',
      'SELECT ''idx_attendance_sessions_offering already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_sessions'
      AND INDEX_NAME = 'idx_attendance_sessions_offering'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_attendance_records_enrollment: attendance_records(enrollment_id)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_attendance_records_enrollment ON attendance_records(enrollment_id)',
      'SELECT ''idx_attendance_records_enrollment already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attendance_records'
      AND INDEX_NAME = 'idx_attendance_records_enrollment'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_medicals_student: medicals(student_id)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_medicals_student ON medicals(student_id)',
      'SELECT ''idx_medicals_student already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medicals'
      AND INDEX_NAME = 'idx_medicals_student'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_results_enrollment: course_results(enrollment_id)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_results_enrollment ON course_results(enrollment_id)',
      'SELECT ''idx_results_enrollment already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_results'
      AND INDEX_NAME = 'idx_results_enrollment'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_semester_results_student: semester_results(student_id)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_semester_results_student ON semester_results(student_id)',
      'SELECT ''idx_semester_results_student already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'semester_results'
      AND INDEX_NAME = 'idx_semester_results_student'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- idx_notices_audience: notices(audience)
SET @ftms_stmt = (
    SELECT IF(COUNT(*) = 0,
      'CREATE INDEX idx_notices_audience ON notices(audience)',
      'SELECT ''idx_notices_audience already exists'' AS info')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notices'
      AND INDEX_NAME = 'idx_notices_audience'
);
PREPARE ftms_index FROM @ftms_stmt;
EXECUTE ftms_index;
DEALLOCATE PREPARE ftms_index;

-- Foreign keys stay enabled throughout this import.

-- ============================================================
-- OPTIONAL STARTER DATA
-- ============================================================

-- Example departments:
INSERT IGNORE INTO departments (department_code, department_name)
VALUES
    ('ICT', 'Department of Information and Communication Technology'),
    ('ET',  'Department of Engineering Technology'),
    ('BST', 'Department of Biosystems Technology');

-- IMPORTANT:
-- Do NOT guess or hard-code faculty grade boundaries here.
-- Add grade_rules only after confirming them from your official SRS/faculty rules.
--
-- Example format ONLY:
-- INSERT INTO grade_rules
--     (grade_letter, min_mark, max_mark, grade_point, is_pass)
-- VALUES
--     ('<GRADE>', <MIN>, <MAX>, <POINT>, TRUE);

-- Also create application users through the Java application using PasswordUtil
-- so password_hash values are stored securely rather than as plain-text passwords.


-- ============================================================
-- FTMS - COMPLETE SAMPLE / DEMO DATA
-- For MySQL 8.x
-- Included after the schema above; no separate SQL file is needed
--
-- TEST LOGIN PASSWORD FOR ALL SAMPLE USERS:
--     Test@123
--
-- IMPORTANT:
-- 1) Grade boundaries below are SAMPLE/DEMO values only.
--    Replace them with the official faculty/SRS rules.
-- 2) Eligibility values are demo snapshots for UI/testing.
--    Your Java service should calculate them using official rules.
-- 3) This script uses fixed IDs so relationships are easy to inspect.
-- ============================================================

USE ftms_group14;

-- Foreign keys remain enabled so broken demo relationships are not silently accepted.

-- ------------------------------------------------------------
-- 1. DEPARTMENTS
-- ------------------------------------------------------------
INSERT IGNORE INTO departments
(department_id, department_code, department_name)
VALUES
(1, 'ICT', 'Department of Information and Communication Technology'),
(2, 'ET',  'Department of Engineering Technology'),
(3, 'BST', 'Department of Biosystems Technology');

-- ------------------------------------------------------------
-- 2. USERS
-- All sample accounts use password: Test@123
-- ------------------------------------------------------------
INSERT IGNORE INTO users
(user_id, username, password_hash, role, first_name, last_name, email, phone, department_id, is_active)
VALUES
(1, 'admin',      '120000:zQ3C0ZJh+lOpx11tqmi7Ew==:YMZ8vRef7Wyz7S2mke8weRud/9Qx6JGyokxFR74yDQk=',  'ADMIN',              'System',  'Admin',       'admin@ftms.local',      '0710000001', 1, TRUE),
(2, 'lecturer1',  '120000:0wy6BIAFLgfGIA3j0f2NvA==:7J/cqv0e3CsEDSigJQ3rz0S0gQkjmaeX3/IzKUNwTWI=',  'LECTURER',           'Nimal',   'Perera',      'nimal@ftms.local',      '0710000002', 1, TRUE),
(3, 'lecturer2',  '120000:PEzEG5yPrljToEoDrewADw==:K7pE/B6avwlM67P6jqwtnuSvGHruk4H//wBojvyRH5Y=',  'LECTURER',           'Kavindi', 'Silva',       'kavindi@ftms.local',    '0710000003', 1, TRUE),
(4, 'tech1',      '120000:wJpN5hAdz7CCNx5P4YEBMg==:HC80n7+cV5Cd6OlLzIsjr1FRRiBiLIixq+abXa611ZQ=',  'TECHNICAL_OFFICER',  'Saman',   'Fernando',    'saman@ftms.local',      '0710000004', 1, TRUE),
(10,'tg2101',     '120000:nyWkqhYLjsrqHx4BoiE1Vg==:4nWhKum2ZbQMkalv6aH2GemPLNE8dUkI+LprKAXo/UI=', 'STUDENT',            'Amal',    'Jayasinghe',   'tg2101@ftms.local',     '0710000010', 1, TRUE),
(11,'tg2102',     '120000:k173WEcrvpeRQIOMIGcwdg==:MapDnJL5RJ4z5JlmyRlP275A5rb6ui2vTJQnAO44H/I=', 'STUDENT',            'Nadeesha','Kumari',      'tg2102@ftms.local',     '0710000011', 1, TRUE),
(12,'tg2103',     '120000:VJP2zDwgKaDeA6qzCQwRfA==:RGZaXfZaAQcMeVIUh171bo9EEG3h3mrXfwdqvDvlecU=', 'STUDENT',            'Kasun',   'Bandara',      'tg2103@ftms.local',     '0710000012', 1, TRUE),
(13,'tg2104',     '120000:MNDzn8AeQ0yrxBzNuBCDig==:8TidCUTHF3IYHSg/sG3+pYaN6aBhhIoX0rfJYF2ga2I=', 'STUDENT',            'Ishara',  'Madushani',    'tg2104@ftms.local',     '0710000013', 1, TRUE),
(14,'tg2105',     '120000:RHJ93WUxW6VbvnjHuEg7PQ==:sK8YAc2gJa2Qx0tuF0Mm6PDwQ3lY7ZGF0iNiyrMHRqQ=', 'STUDENT',            'Ravindu', 'Lakshan',     'tg2105@ftms.local',     '0710000014', 1, TRUE),
(15,'tg2106',     '120000:4gmm79dpAOYTk+6ngDkhoQ==:OmBPTHmUoylZbcYT7uanVKeYSpztQeppL4yqVbaA+/w=', 'STUDENT',            'Dinithi', 'Sewwandi',     'tg2106@ftms.local',     '0710000015', 1, TRUE);

INSERT IGNORE INTO admins (admin_id, staff_no)
VALUES (1, 'ADM001');

INSERT IGNORE INTO lecturers (lecturer_id, staff_no, designation)
VALUES
(2, 'LEC001', 'Senior Lecturer'),
(3, 'LEC002', 'Lecturer');

INSERT IGNORE INTO technical_officers (technical_officer_id, staff_no)
VALUES (4, 'TO001');

INSERT IGNORE INTO students (student_id, registration_no, intake_year, current_level)
VALUES
(10, 'TG/2024/2101', 2024, 2),
(11, 'TG/2024/2102', 2024, 2),
(12, 'TG/2024/2103', 2024, 2),
(13, 'TG/2024/2104', 2024, 2),
(14, 'TG/2024/2105', 2024, 2),
(15, 'TG/2024/2106', 2024, 2);

-- ------------------------------------------------------------
-- 3. ACADEMIC YEARS / SEMESTERS
-- ------------------------------------------------------------
INSERT IGNORE INTO academic_years
(academic_year_id, academic_year_name, start_date, end_date, is_active)
VALUES
(1, '2025/2026', '2025-09-01', '2026-08-31', FALSE),
(2, '2026/2027', '2026-09-01', '2027-08-31', TRUE);

INSERT IGNORE INTO semesters
(semester_id, academic_year_id, semester_no, semester_name, start_date, end_date, is_active)
VALUES
(1, 1, 1, 'Semester 1', '2025-09-01', '2026-01-31', FALSE),
(2, 1, 2, 'Semester 2', '2026-02-01', '2026-06-30', FALSE),
(3, 2, 1, 'Semester 1', '2026-09-01', '2027-01-31', TRUE),
(4, 2, 2, 'Semester 2', '2027-02-01', '2027-06-30', FALSE);

-- ------------------------------------------------------------
-- 4. COURSES
-- ------------------------------------------------------------
INSERT IGNORE INTO courses
(course_id, course_code, course_name, credits, department_id, course_type, is_active)
VALUES
(1, 'ICT2113', 'Programming Fundamentals', 3.0, 1, 'CORE', TRUE),
(2, 'ICT2122', 'Object Oriented Programming', 3.0, 1, 'CORE', TRUE),
(3, 'ICT2132', 'Database Management Systems', 3.0, 1, 'CORE', TRUE),
(4, 'ICT2142', 'Web Technologies', 3.0, 1, 'CORE', TRUE),
(5, 'ICT2152', 'Computer Networks', 2.0, 1, 'CORE', TRUE);

-- ------------------------------------------------------------
-- 5. COURSE OFFERINGS
-- ------------------------------------------------------------
INSERT IGNORE INTO course_offerings
(offering_id, course_id, semester_id, offering_group, status)
VALUES
(1, 1, 2, 'A', 'COMPLETED'),
(2, 3, 2, 'A', 'COMPLETED'),
(3, 2, 3, 'A', 'ACTIVE'),
(4, 3, 3, 'A', 'ACTIVE'),
(5, 4, 3, 'A', 'ACTIVE'),
(6, 5, 3, 'A', 'ACTIVE');

-- ------------------------------------------------------------
-- 6. LECTURER COURSE ASSIGNMENTS
-- ------------------------------------------------------------
INSERT IGNORE INTO lecturer_course_assignments
(assignment_id, offering_id, lecturer_id, assignment_type)
VALUES
(1, 1, 2, 'PRIMARY'),
(2, 2, 3, 'PRIMARY'),
(3, 3, 2, 'PRIMARY'),
(4, 4, 3, 'PRIMARY'),
(5, 5, 2, 'PRIMARY'),
(6, 6, 3, 'PRIMARY'),
(7, 3, 3, 'ASSISTANT');

-- ------------------------------------------------------------
-- 7. ENROLLMENTS
-- Past offerings: 1-12
-- Current OOP: 101-106
-- Current DBMS: 201-206
-- Current Web: 301-306
-- Current Networks: 401-406
-- ------------------------------------------------------------
INSERT IGNORE INTO enrollments
(enrollment_id, offering_id, student_id, attempt_no, status)
VALUES
(1,1,10,1,'COMPLETED'), (2,1,11,1,'COMPLETED'), (3,1,12,1,'COMPLETED'),
(4,1,13,1,'COMPLETED'), (5,1,14,1,'COMPLETED'), (6,1,15,1,'COMPLETED'),
(7,2,10,1,'COMPLETED'), (8,2,11,1,'COMPLETED'), (9,2,12,1,'COMPLETED'),
(10,2,13,1,'COMPLETED'),(11,2,14,1,'COMPLETED'),(12,2,15,1,'COMPLETED'),

(101,3,10,1,'ENROLLED'),(102,3,11,1,'ENROLLED'),(103,3,12,1,'ENROLLED'),
(104,3,13,1,'ENROLLED'),(105,3,14,1,'ENROLLED'),(106,3,15,1,'ENROLLED'),

(201,4,10,1,'ENROLLED'),(202,4,11,1,'ENROLLED'),(203,4,12,1,'ENROLLED'),
(204,4,13,1,'ENROLLED'),(205,4,14,1,'ENROLLED'),(206,4,15,1,'ENROLLED'),

(301,5,10,1,'ENROLLED'),(302,5,11,1,'ENROLLED'),(303,5,12,1,'ENROLLED'),
(304,5,13,1,'ENROLLED'),(305,5,14,1,'ENROLLED'),(306,5,15,1,'ENROLLED'),

(401,6,10,1,'ENROLLED'),(402,6,11,1,'ENROLLED'),(403,6,12,1,'ENROLLED'),
(404,6,13,1,'ENROLLED'),(405,6,14,1,'ENROLLED'),(406,6,15,1,'ENROLLED');

-- ------------------------------------------------------------
-- 8. COURSE MATERIALS
-- ------------------------------------------------------------
INSERT IGNORE INTO course_materials
(material_id, offering_id, title, description, material_type, resource_path, uploaded_by)
VALUES
(1,3,'OOP Introduction','Introduction to classes and objects','LECTURE_NOTE','materials/oop/intro.pdf',2),
(2,3,'Inheritance Practical','Practical sheet for inheritance','PRACTICAL','materials/oop/inheritance.pdf',2),
(3,3,'Assignment 01','Java OOP assignment brief','ASSIGNMENT','materials/oop/assignment01.pdf',2),
(4,4,'SQL Introduction','Basic SQL lecture note','LECTURE_NOTE','materials/db/sql-intro.pdf',3),
(5,4,'Normalization Practical','Normalization exercises','PRACTICAL','materials/db/normalization.pdf',3),
(6,5,'HTML and CSS','Web fundamentals','LECTURE_NOTE','materials/web/html-css.pdf',2),
(7,6,'Network Models','OSI and TCP/IP models','LECTURE_NOTE','materials/networks/models.pdf',3);

-- ------------------------------------------------------------
-- 9. ASSESSMENTS
-- OOP weights total = 100
-- DBMS/Web sample weights also total = 100
-- ------------------------------------------------------------
INSERT IGNORE INTO assessments
(assessment_id, offering_id, assessment_name, assessment_type, max_mark, weight_percentage, assessment_date, description, created_by)
VALUES
(101,3,'Quiz 01','QUIZ',100,10,'2026-10-10','OOP quiz',2),
(102,3,'Assignment 01','ASSIGNMENT',100,20,'2026-10-25','OOP assignment',2),
(103,3,'Mid Examination','MID',100,20,'2026-11-10','OOP mid examination',2),
(104,3,'Final Examination','FINAL_EXAM',100,50,'2027-01-15','OOP final examination',2),

(201,4,'Quiz 01','QUIZ',100,10,'2026-10-12','DBMS quiz',3),
(202,4,'Practical 01','PRACTICAL',100,20,'2026-11-02','SQL practical',3),
(203,4,'Mid Examination','MID',100,20,'2026-11-18','DBMS mid exam',3),
(204,4,'Final Examination','FINAL_EXAM',100,50,'2027-01-18','DBMS final exam',3),

(301,5,'Assignment 01','ASSIGNMENT',100,25,'2026-10-30','Web assignment',2),
(302,5,'Project','PROJECT',100,25,'2026-12-05','Web mini project',2),
(303,5,'Final Examination','FINAL_EXAM',100,50,'2027-01-20','Web final exam',2),

(401,6,'Quiz 01','QUIZ',100,20,'2026-10-20','Networks quiz',3),
(402,6,'Practical 01','PRACTICAL',100,30,'2026-11-25','Networks practical',3),
(403,6,'Final Examination','FINAL_EXAM',100,50,'2027-01-22','Networks final exam',3);

-- ------------------------------------------------------------
-- 10. MARKS
-- OOP: includes normal, absent and medical examples
-- ------------------------------------------------------------
INSERT IGNORE INTO marks
(mark_id, assessment_id, enrollment_id, marks_obtained, mark_status, entered_by)
VALUES
-- Quiz 01
(1001,101,101,78,'PRESENT',2),(1002,101,102,65,'PRESENT',2),(1003,101,103,92,'PRESENT',2),
(1004,101,104,55,'PRESENT',2),(1005,101,105,40,'PRESENT',2),(1006,101,106,NULL,'MEDICAL',2),
-- Assignment 01
(1011,102,101,82,'PRESENT',2),(1012,102,102,70,'PRESENT',2),(1013,102,103,95,'PRESENT',2),
(1014,102,104,60,'PRESENT',2),(1015,102,105,48,'PRESENT',2),(1016,102,106,75,'PRESENT',2),
-- Mid
(1021,103,101,74,'PRESENT',2),(1022,103,102,68,'PRESENT',2),(1023,103,103,88,'PRESENT',2),
(1024,103,104,NULL,'ABSENT',2),(1025,103,105,45,'PRESENT',2),(1026,103,106,72,'PRESENT',2),
-- Final
(1031,104,101,80,'PRESENT',2),(1032,104,102,66,'PRESENT',2),(1033,104,103,90,'PRESENT',2),
(1034,104,104,58,'PRESENT',2),(1035,104,105,42,'PRESENT',2),(1036,104,106,70,'PRESENT',2),

-- DBMS
(2001,201,201,75,'PRESENT',3),(2002,201,202,68,'PRESENT',3),(2003,201,203,84,'PRESENT',3),
(2004,201,204,60,'PRESENT',3),(2005,201,205,52,'PRESENT',3),(2006,201,206,77,'PRESENT',3),
(2011,202,201,80,'PRESENT',3),(2012,202,202,72,'PRESENT',3),(2013,202,203,90,'PRESENT',3),
(2014,202,204,63,'PRESENT',3),(2015,202,205,55,'PRESENT',3),(2016,202,206,79,'PRESENT',3),
(2021,203,201,71,'PRESENT',3),(2022,203,202,64,'PRESENT',3),(2023,203,203,86,'PRESENT',3),
(2024,203,204,59,'PRESENT',3),(2025,203,205,50,'PRESENT',3),(2026,203,206,73,'PRESENT',3);

-- ------------------------------------------------------------
-- 11. GRADE RULES
-- SAMPLE ONLY - replace with official faculty/SRS grading rules.
-- ------------------------------------------------------------
INSERT IGNORE INTO grade_rules
(grade_rule_id, grade_letter, min_mark, max_mark, grade_point, is_pass, is_active)
VALUES
(1,'A+',85,100,4.00,TRUE,TRUE),
(2,'A', 75,84.99,4.00,TRUE,TRUE),
(3,'A-',70,74.99,3.70,TRUE,TRUE),
(4,'B+',65,69.99,3.30,TRUE,TRUE),
(5,'B', 60,64.99,3.00,TRUE,TRUE),
(6,'B-',55,59.99,2.70,TRUE,TRUE),
(7,'C+',50,54.99,2.30,TRUE,TRUE),
(8,'C', 45,49.99,2.00,TRUE,TRUE),
(9,'C-',40,44.99,1.70,TRUE,TRUE),
(10,'D',35,39.99,1.00,FALSE,TRUE),
(11,'F',0,34.99,0.00,FALSE,TRUE);

-- ------------------------------------------------------------
-- 12. ATTENDANCE SESSIONS
-- ------------------------------------------------------------
INSERT IGNORE INTO attendance_sessions
(session_id, offering_id, session_date, start_time, end_time, session_type, topic, conducted_by)
VALUES
(1,3,'2026-09-10','09:00:00','11:00:00','THEORY','Classes and Objects',2),
(2,3,'2026-09-17','09:00:00','11:00:00','THEORY','Encapsulation',2),
(3,3,'2026-09-24','13:00:00','15:00:00','PRACTICAL','Java Classes Practical',2),
(4,3,'2026-10-01','09:00:00','11:00:00','THEORY','Inheritance',2),
(5,3,'2026-10-08','13:00:00','15:00:00','PRACTICAL','Inheritance Practical',2),
(6,3,'2026-10-15','09:00:00','11:00:00','THEORY','Polymorphism',2),

(11,4,'2026-09-11','10:00:00','12:00:00','THEORY','Database Concepts',3),
(12,4,'2026-09-18','10:00:00','12:00:00','THEORY','Relational Model',3),
(13,4,'2026-09-25','14:00:00','16:00:00','PRACTICAL','SQL Practical',3),
(14,4,'2026-10-02','10:00:00','12:00:00','THEORY','Normalization',3);

-- ------------------------------------------------------------
-- 13. ATTENDANCE RECORDS
-- ------------------------------------------------------------
INSERT IGNORE INTO attendance_records
(attendance_record_id, session_id, enrollment_id, attendance_status, marked_by)
VALUES
-- OOP Session 1
(1,1,101,'PRESENT',4),(2,1,102,'PRESENT',4),(3,1,103,'PRESENT',4),(4,1,104,'PRESENT',4),(5,1,105,'ABSENT',4),(6,1,106,'PRESENT',4),
-- OOP Session 2
(7,2,101,'PRESENT',4),(8,2,102,'PRESENT',4),(9,2,103,'PRESENT',4),(10,2,104,'ABSENT',4),(11,2,105,'PRESENT',4),(12,2,106,'PRESENT',4),
-- OOP Session 3
(13,3,101,'PRESENT',4),(14,3,102,'PRESENT',4),(15,3,103,'PRESENT',4),(16,3,104,'PRESENT',4),(17,3,105,'ABSENT',4),(18,3,106,'MEDICAL',4),
-- OOP Session 4
(19,4,101,'PRESENT',4),(20,4,102,'PRESENT',4),(21,4,103,'PRESENT',4),(22,4,104,'ABSENT',4),(23,4,105,'PRESENT',4),(24,4,106,'PRESENT',4),
-- OOP Session 5
(25,5,101,'PRESENT',4),(26,5,102,'PRESENT',4),(27,5,103,'PRESENT',4),(28,5,104,'PRESENT',4),(29,5,105,'ABSENT',4),(30,5,106,'PRESENT',4),
-- OOP Session 6
(31,6,101,'PRESENT',4),(32,6,102,'PRESENT',4),(33,6,103,'PRESENT',4),(34,6,104,'ABSENT',4),(35,6,105,'PRESENT',4),(36,6,106,'PRESENT',4),

-- DBMS Session 11
(101,11,201,'PRESENT',4),(102,11,202,'PRESENT',4),(103,11,203,'PRESENT',4),(104,11,204,'PRESENT',4),(105,11,205,'PRESENT',4),(106,11,206,'PRESENT',4),
-- DBMS Session 12
(107,12,201,'PRESENT',4),(108,12,202,'PRESENT',4),(109,12,203,'PRESENT',4),(110,12,204,'PRESENT',4),(111,12,205,'ABSENT',4),(112,12,206,'PRESENT',4),
-- DBMS Session 13
(113,13,201,'PRESENT',4),(114,13,202,'PRESENT',4),(115,13,203,'PRESENT',4),(116,13,204,'LATE',4),(117,13,205,'PRESENT',4),(118,13,206,'PRESENT',4),
-- DBMS Session 14
(119,14,201,'PRESENT',4),(120,14,202,'PRESENT',4),(121,14,203,'PRESENT',4),(122,14,204,'PRESENT',4),(123,14,205,'ABSENT',4),(124,14,206,'PRESENT',4);

-- ------------------------------------------------------------
-- 14. MEDICALS
-- ------------------------------------------------------------
INSERT IGNORE INTO medicals
(medical_id, student_id, offering_id, from_date, to_date, reason, document_path, status, reviewed_by, reviewed_at, review_note)
VALUES
(1,15,3,'2026-09-24','2026-09-24','Fever','medicals/tg2106-20260924.pdf','APPROVED',1,'2026-09-26 10:00:00','Approved for missed practical'),
(2,13,3,'2026-11-10','2026-11-10','Medical absence','medicals/tg2104-20261110.pdf','PENDING',NULL,NULL,NULL),
(3,14,4,'2026-10-02','2026-10-02','Clinic appointment','medicals/tg2105-20261002.pdf','REJECTED',1,'2026-10-05 09:30:00','Insufficient document');

-- ------------------------------------------------------------
-- 15. ELIGIBILITY RECORDS
-- DEMO snapshots only.
-- ------------------------------------------------------------
INSERT IGNORE INTO eligibility_records
(eligibility_id, enrollment_id, ca_score, ca_eligible, attendance_percentage, attendance_eligible, overall_eligible, reason, calculated_by)
VALUES
(1,101,77.40,TRUE,100.00,TRUE,TRUE,'Eligible',2),
(2,102,67.10,TRUE,100.00,TRUE,TRUE,'Eligible',2),
(3,103,91.10,TRUE,100.00,TRUE,TRUE,'Eligible',2),
(4,104,38.50,FALSE,50.00,FALSE,FALSE,'Low CA / attendance example',2),
(5,105,45.00,TRUE,50.00,FALSE,FALSE,'Attendance requirement not met',2),
(6,106,73.50,TRUE,100.00,TRUE,TRUE,'Approved medical considered',2);

-- ------------------------------------------------------------
-- 16. COURSE RESULTS
-- Past semester + OOP current demo
-- ------------------------------------------------------------
INSERT IGNORE INTO course_results
(result_id, enrollment_id, ca_mark, final_exam_mark, overall_mark, grade_rule_id, grade_letter, grade_point, result_status, published, published_at, calculated_by)
VALUES
-- Past Programming Fundamentals
(1,1,72,78,75,2,'A',4.00,'PASS',TRUE,'2026-07-15 10:00:00',2),
(2,2,65,66,65.5,4,'B+',3.30,'PASS',TRUE,'2026-07-15 10:00:00',2),
(3,3,84,90,87,1,'A+',4.00,'PASS',TRUE,'2026-07-15 10:00:00',2),
(4,4,58,61,59.5,6,'B-',2.70,'PASS',TRUE,'2026-07-15 10:00:00',2),
(5,5,48,50,49,8,'C',2.00,'PASS',TRUE,'2026-07-15 10:00:00',2),
(6,6,69,74,71.5,3,'A-',3.70,'PASS',TRUE,'2026-07-15 10:00:00',2),

-- Past DBMS
(7,7,75,82,78.5,2,'A',4.00,'PASS',TRUE,'2026-07-15 10:05:00',3),
(8,8,62,70,66,4,'B+',3.30,'PASS',TRUE,'2026-07-15 10:05:00',3),
(9,9,88,92,90,1,'A+',4.00,'PASS',TRUE,'2026-07-15 10:05:00',3),
(10,10,55,60,57.5,6,'B-',2.70,'PASS',TRUE,'2026-07-15 10:05:00',3),
(11,11,40,38,39,10,'D',1.00,'FAIL',TRUE,'2026-07-15 10:05:00',3),
(12,12,70,76,73,3,'A-',3.70,'PASS',TRUE,'2026-07-15 10:05:00',3),

-- Current OOP demo
(101,101,77.40,80,78.70,2,'A',4.00,'PASS',TRUE,'2027-01-30 10:00:00',2),
(102,102,67.10,66,66.55,4,'B+',3.30,'PASS',TRUE,'2027-01-30 10:00:00',2),
(103,103,91.10,90,90.55,1,'A+',4.00,'PASS',TRUE,'2027-01-30 10:00:00',2),
(104,104,38.50,NULL,NULL,NULL,NULL,NULL,'NOT_ELIGIBLE',TRUE,'2027-01-30 10:00:00',2),
(105,105,45.00,NULL,NULL,NULL,NULL,NULL,'NOT_ELIGIBLE',TRUE,'2027-01-30 10:00:00',2),
(106,106,73.50,70,71.75,3,'A-',3.70,'PASS',TRUE,'2027-01-30 10:00:00',2);

-- ------------------------------------------------------------
-- 17. SEMESTER RESULTS / GPA
-- Demo values for UI/report testing.
-- ------------------------------------------------------------
INSERT IGNORE INTO semester_results
(semester_result_id, student_id, semester_id, total_registered_credits, total_earned_credits, total_quality_points, sgpa, cgpa)
VALUES
(1,10,2,6,6,24.00,4.00,4.00),
(2,11,2,6,6,19.80,3.30,3.30),
(3,12,2,6,6,24.00,4.00,4.00),
(4,13,2,6,6,16.20,2.70,2.70),
(5,14,2,6,3,9.00,1.50,1.50),
(6,15,2,6,6,22.20,3.70,3.70),

(11,10,3,11,11,41.00,3.73,3.87),
(12,11,3,11,11,34.60,3.15,3.22),
(13,12,3,11,11,43.00,3.91,3.95),
(14,13,3,11,6,17.50,1.59,2.14),
(15,14,3,11,3,7.50,0.68,1.08),
(16,15,3,11,11,39.00,3.55,3.62);

-- ------------------------------------------------------------
-- 18. NOTICES
-- ------------------------------------------------------------
INSERT IGNORE INTO notices
(notice_id, title, content, audience, created_by, published_at, expires_at, is_active)
VALUES
(1,'Welcome to FTMS','Welcome to the Faculty Technology Management System.','ALL',1,'2026-09-01 08:00:00','2027-06-30 23:59:59',TRUE),
(2,'OOP Assignment 01','OOP Assignment 01 has been published. Check course materials.','STUDENTS',2,'2026-10-05 09:00:00','2026-10-30 23:59:59',TRUE),
(3,'Attendance Update','Please update attendance records after every scheduled session.','TECHNICAL_OFFICERS',1,'2026-09-05 08:30:00',NULL,TRUE),
(4,'Results Processing','Lecturers should verify marks before publishing final results.','LECTURERS',1,'2026-12-20 10:00:00','2027-02-01 23:59:59',TRUE);

-- ------------------------------------------------------------
-- 19. TIMETABLE
-- ------------------------------------------------------------
INSERT IGNORE INTO timetable_entries
(timetable_id, offering_id, day_of_week, start_time, end_time, room, session_type, lecturer_id, effective_from, effective_to)
VALUES
(1,3,'MONDAY','09:00:00','11:00:00','LT-01','LECTURE',2,'2026-09-01','2027-01-31'),
(2,3,'THURSDAY','13:00:00','15:00:00','LAB-01','PRACTICAL',2,'2026-09-01','2027-01-31'),
(3,4,'TUESDAY','10:00:00','12:00:00','LT-02','LECTURE',3,'2026-09-01','2027-01-31'),
(4,4,'FRIDAY','14:00:00','16:00:00','LAB-02','PRACTICAL',3,'2026-09-01','2027-01-31'),
(5,5,'WEDNESDAY','08:00:00','10:00:00','LT-03','LECTURE',2,'2026-09-01','2027-01-31'),
(6,6,'FRIDAY','10:00:00','12:00:00','LT-04','LECTURE',3,'2026-09-01','2027-01-31');

-- Foreign keys stay enabled throughout this import.

-- ------------------------------------------------------------
-- QUICK CHECKS
-- ------------------------------------------------------------
SELECT 'departments' AS table_name, COUNT(*) AS row_count FROM departments
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'students', COUNT(*) FROM students
UNION ALL SELECT 'courses', COUNT(*) FROM courses
UNION ALL SELECT 'course_offerings', COUNT(*) FROM course_offerings
UNION ALL SELECT 'enrollments', COUNT(*) FROM enrollments
UNION ALL SELECT 'assessments', COUNT(*) FROM assessments
UNION ALL SELECT 'marks', COUNT(*) FROM marks
UNION ALL SELECT 'attendance_sessions', COUNT(*) FROM attendance_sessions
UNION ALL SELECT 'attendance_records', COUNT(*) FROM attendance_records
UNION ALL SELECT 'medicals', COUNT(*) FROM medicals
UNION ALL SELECT 'eligibility_records', COUNT(*) FROM eligibility_records
UNION ALL SELECT 'course_results', COUNT(*) FROM course_results
UNION ALL SELECT 'semester_results', COUNT(*) FROM semester_results
UNION ALL SELECT 'notices', COUNT(*) FROM notices
UNION ALL SELECT 'timetable_entries', COUNT(*) FROM timetable_entries;

-- Check the three newly integrated features.
SHOW COLUMNS FROM users LIKE 'profile_picture';
SHOW COLUMNS FROM assessments LIKE 'is_published';
SHOW COLUMNS FROM assessments LIKE 'published_at';
SHOW TABLES LIKE 'attendance_medical_links';
