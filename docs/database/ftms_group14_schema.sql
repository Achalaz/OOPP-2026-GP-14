-- ============================================================
-- FTMS Group 14 - Consolidated MySQL Schema
-- ICT2132 Object Oriented Programming Practicum
-- Database: ftms_group14
--
-- Design choice:
--   Base users table + subtype tables (admins, lecturers,
--   technical_officers, undergraduates) to match the current
--   Group 14 class/ER model.
--
-- Important TBC rules intentionally NOT hard-coded:
--   1) Attendance eligibility cutoff
--   2) Effect of approved medicals on attendance/eligibility
--   3) Exact UGC Circular 12-2024 grade boundaries/points
--   4) Repeat/fail/incomplete GPA treatment
-- ============================================================

CREATE DATABASE IF NOT EXISTS ftms_group14
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ftms_group14;

-- ============================================================
-- 1. DEPARTMENTS
-- ============================================================
CREATE TABLE IF NOT EXISTS departments (
    department_id INT PRIMARY KEY AUTO_INCREMENT,
    department_code VARCHAR(30) NOT NULL UNIQUE,
    department_name VARCHAR(120) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- ============================================================
-- 2. USERS (BASE TABLE)
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(60) NOT NULL UNIQUE,
    password_hash VARCHAR(512) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    contact_no VARCHAR(30),
    email VARCHAR(120),
    role ENUM('ADMIN','LECTURER','TECHNICAL_OFFICER','STUDENT') NOT NULL,
    account_status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    profile_picture VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================
-- 3. ADMINS
-- ============================================================
CREATE TABLE IF NOT EXISTS admins (
    user_id INT PRIMARY KEY,
    CONSTRAINT fk_admin_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 4. LECTURERS
-- ============================================================
CREATE TABLE IF NOT EXISTS lecturers (
    user_id INT PRIMARY KEY,
    department_id INT NOT NULL,
    designation VARCHAR(100),

    CONSTRAINT fk_lecturer_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_lecturer_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 5. TECHNICAL OFFICERS
-- ============================================================
CREATE TABLE IF NOT EXISTS technical_officers (
    user_id INT PRIMARY KEY,
    department_id INT NOT NULL,

    CONSTRAINT fk_to_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_to_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 6. UNDERGRADUATES
-- ============================================================
CREATE TABLE IF NOT EXISTS undergraduates (
    user_id INT PRIMARY KEY,
    registration_no VARCHAR(50) NOT NULL UNIQUE,
    batch VARCHAR(30) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    student_status ENUM('REGULAR','REPEAT','BATCH_MISSED') NOT NULL DEFAULT 'REGULAR',

    CONSTRAINT fk_undergraduate_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 7. COURSES
-- ============================================================
CREATE TABLE IF NOT EXISTS courses (
    course_id INT PRIMARY KEY AUTO_INCREMENT,
    department_id INT NOT NULL,
    course_code VARCHAR(30) NOT NULL UNIQUE,
    course_name VARCHAR(150) NOT NULL,
    theory_credits DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    practical_credits DECIMAL(4,1) NOT NULL DEFAULT 0.0,
    semester INT NOT NULL,
    status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT chk_course_theory_credits
        CHECK (theory_credits >= 0),

    CONSTRAINT chk_course_practical_credits
        CHECK (practical_credits >= 0),

    CONSTRAINT chk_course_semester
        CHECK (semester > 0),

    CONSTRAINT fk_course_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 8. COURSE OFFERINGS
-- ============================================================
CREATE TABLE IF NOT EXISTS course_offerings (
    offering_id INT PRIMARY KEY AUTO_INCREMENT,
    course_id INT NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    semester INT NOT NULL,
    batch VARCHAR(30) NOT NULL,
    status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT uq_course_offering
        UNIQUE (course_id, academic_year, semester, batch),

    CONSTRAINT chk_offering_semester
        CHECK (semester > 0),

    CONSTRAINT fk_offering_course
        FOREIGN KEY (course_id)
        REFERENCES courses(course_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 9. LECTURER COURSE ASSIGNMENTS
-- ============================================================
CREATE TABLE IF NOT EXISTS lecturer_course_assignments (
    assignment_id INT PRIMARY KEY AUTO_INCREMENT,
    lecturer_id INT NOT NULL,
    offering_id INT NOT NULL,

    CONSTRAINT uq_lecturer_offering
        UNIQUE (lecturer_id, offering_id),

    CONSTRAINT fk_assignment_lecturer
        FOREIGN KEY (lecturer_id)
        REFERENCES lecturers(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_assignment_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 10. ENROLLMENTS
-- Actor who creates/maintains enrollment is still TBC.
-- ============================================================
CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id INT PRIMARY KEY AUTO_INCREMENT,
    student_id INT NOT NULL,
    offering_id INT NOT NULL,
    attempt_no INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_enrollment_attempt
        UNIQUE (student_id, offering_id, attempt_no),

    CONSTRAINT chk_enrollment_attempt
        CHECK (attempt_no > 0),

    CONSTRAINT fk_enrollment_student
        FOREIGN KEY (student_id)
        REFERENCES undergraduates(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_enrollment_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 11. COURSE MATERIALS
-- ============================================================
CREATE TABLE IF NOT EXISTS course_materials (
    material_id INT PRIMARY KEY AUTO_INCREMENT,
    offering_id INT NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    file_path_or_url VARCHAR(700) NOT NULL,
    uploaded_by INT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_material_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_material_lecturer
        FOREIGN KEY (uploaded_by)
        REFERENCES lecturers(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 12. ASSESSMENTS
-- Marks/components are out of 100.
-- Actual criteria/weights come from course coordinators.
-- ============================================================
CREATE TABLE IF NOT EXISTS assessments (
    assessment_id INT PRIMARY KEY AUTO_INCREMENT,
    offering_id INT NOT NULL,
    assessment_name VARCHAR(100) NOT NULL,
    assessment_type VARCHAR(30) NOT NULL,
    weight_percentage DECIMAL(6,2) NOT NULL,
    maximum_mark DECIMAL(6,2) NOT NULL DEFAULT 100.00,
    evaluation_criteria VARCHAR(255),

    CONSTRAINT uq_assessment_name_per_offering
        UNIQUE (offering_id, assessment_name),

    CONSTRAINT chk_assessment_weight
        CHECK (weight_percentage > 0 AND weight_percentage <= 100),

    CONSTRAINT chk_assessment_maximum_mark
        CHECK (maximum_mark = 100),

    CONSTRAINT fk_assessment_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 13. MARKS
-- Missing mark = NULL, not numeric 0.
-- ============================================================
CREATE TABLE IF NOT EXISTS marks (
    mark_id INT PRIMARY KEY AUTO_INCREMENT,
    enrollment_id INT NOT NULL,
    assessment_id INT NOT NULL,
    marks_obtained DECIMAL(6,2) NULL,
    attempt_no INT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_mark
        UNIQUE (enrollment_id, assessment_id, attempt_no),

    CONSTRAINT chk_mark_attempt
        CHECK (attempt_no > 0),

    CONSTRAINT chk_mark_range
        CHECK (
            marks_obtained IS NULL
            OR (marks_obtained >= 0 AND marks_obtained <= 100)
        ),

    CONSTRAINT fk_mark_enrollment
        FOREIGN KEY (enrollment_id)
        REFERENCES enrollments(enrollment_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_mark_assessment
        FOREIGN KEY (assessment_id)
        REFERENCES assessments(assessment_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 14. ATTENDANCE SESSIONS
-- 15 THEORY + 15 PRACTICAL; each session = 2 hours.
-- ============================================================
CREATE TABLE IF NOT EXISTS attendance_sessions (
    session_id INT PRIMARY KEY AUTO_INCREMENT,
    offering_id INT NOT NULL,
    session_type ENUM('THEORY','PRACTICAL') NOT NULL,
    session_no INT NOT NULL,
    session_date DATE NOT NULL,
    duration_hours DECIMAL(4,2) NOT NULL DEFAULT 2.00,
    topic VARCHAR(255),

    CONSTRAINT uq_attendance_session
        UNIQUE (offering_id, session_type, session_no),

    CONSTRAINT chk_attendance_session_no
        CHECK (session_no BETWEEN 1 AND 15),

    CONSTRAINT chk_attendance_duration
        CHECK (duration_hours > 0),

    CONSTRAINT fk_attendance_session_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 15. MEDICALS
-- Medical effect on attendance/eligibility is intentionally TBC.
-- ============================================================
CREATE TABLE IF NOT EXISTS medicals (
    medical_id INT PRIMARY KEY AUTO_INCREMENT,
    student_id INT NOT NULL,
    medical_date DATE NOT NULL,
    document_reference VARCHAR(500),
    status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    recorded_by_user_id INT NOT NULL,
    remarks VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_medical_student
        FOREIGN KEY (student_id)
        REFERENCES undergraduates(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_medical_recorded_by
        FOREIGN KEY (recorded_by_user_id)
        REFERENCES technical_officers(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 16. ATTENDANCE RECORDS
-- ============================================================
CREATE TABLE IF NOT EXISTS attendance_records (
    attendance_record_id INT PRIMARY KEY AUTO_INCREMENT,
    session_id INT NOT NULL,
    enrollment_id INT NOT NULL,
    attendance_status ENUM('PRESENT','ABSENT') NOT NULL,
    medical_id INT NULL,
    recorded_by_user_id INT NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_attendance_record
        UNIQUE (session_id, enrollment_id),

    CONSTRAINT fk_attendance_record_session
        FOREIGN KEY (session_id)
        REFERENCES attendance_sessions(session_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_attendance_record_enrollment
        FOREIGN KEY (enrollment_id)
        REFERENCES enrollments(enrollment_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_attendance_record_medical
        FOREIGN KEY (medical_id)
        REFERENCES medicals(medical_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    CONSTRAINT fk_attendance_record_to
        FOREIGN KEY (recorded_by_user_id)
        REFERENCES technical_officers(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 17. GRADE RULES
-- DO NOT seed guessed grade boundaries or grade points.
-- Load only confirmed UGC Circular No. 12-2024 rules.
-- ============================================================
CREATE TABLE IF NOT EXISTS grade_rules (
    grade_rule_id INT PRIMARY KEY AUTO_INCREMENT,
    min_mark DECIMAL(6,2) NOT NULL,
    max_mark DECIMAL(6,2) NOT NULL,
    grade_letter VARCHAR(10) NOT NULL,
    grade_point DECIMAL(4,2) NOT NULL,

    CONSTRAINT chk_grade_min
        CHECK (min_mark >= 0 AND min_mark <= 100),

    CONSTRAINT chk_grade_max
        CHECK (max_mark >= 0 AND max_mark <= 100),

    CONSTRAINT chk_grade_range
        CHECK (min_mark <= max_mark),

    CONSTRAINT chk_grade_point
        CHECK (grade_point >= 0)
) ENGINE=InnoDB;

-- ============================================================
-- 18. COURSE RESULTS
-- attendance_eligible may remain NULL while policy is TBC.
-- ============================================================
CREATE TABLE IF NOT EXISTS course_results (
    result_id INT PRIMARY KEY AUTO_INCREMENT,
    enrollment_id INT NOT NULL UNIQUE,
    ca_mark DECIMAL(6,2) NULL,
    final_exam_mark DECIMAL(6,2) NULL,
    overall_mark DECIMAL(6,2) NULL,
    ca_eligible BOOLEAN NULL,
    attendance_eligible BOOLEAN NULL,
    overall_eligible BOOLEAN NULL,
    grade_letter VARCHAR(10) NULL,
    grade_point DECIMAL(4,2) NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_result_ca_mark
        CHECK (ca_mark IS NULL OR (ca_mark >= 0 AND ca_mark <= 100)),

    CONSTRAINT chk_result_final_exam
        CHECK (final_exam_mark IS NULL OR (final_exam_mark >= 0 AND final_exam_mark <= 100)),

    CONSTRAINT chk_result_overall_mark
        CHECK (overall_mark IS NULL OR (overall_mark >= 0 AND overall_mark <= 100)),

    CONSTRAINT chk_result_grade_point
        CHECK (grade_point IS NULL OR grade_point >= 0),

    CONSTRAINT fk_course_result_enrollment
        FOREIGN KEY (enrollment_id)
        REFERENCES enrollments(enrollment_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 19. SEMESTER RESULTS
-- CGPA can be derived dynamically; only SGPA is stored here.
-- Repeat/fail/incomplete GPA policy is TBC.
-- ============================================================
CREATE TABLE IF NOT EXISTS semester_results (
    semester_result_id INT PRIMARY KEY AUTO_INCREMENT,
    student_id INT NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    semester INT NOT NULL,
    sgpa DECIMAL(5,2) NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uq_semester_result
        UNIQUE (student_id, academic_year, semester),

    CONSTRAINT chk_semester_result_semester
        CHECK (semester > 0),

    CONSTRAINT fk_semester_result_student
        FOREIGN KEY (student_id)
        REFERENCES undergraduates(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 20. NOTICES
-- Admin creates/maintains notices; others view published notices.
-- target_role is a project design convenience, not a mandatory brief rule.
-- ============================================================
CREATE TABLE IF NOT EXISTS notices (
    notice_id INT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    posted_by INT NOT NULL,
    date_posted TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    target_role VARCHAR(30) NOT NULL DEFAULT 'ALL',
    published BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_notice_admin
        FOREIGN KEY (posted_by)
        REFERENCES admins(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- 21. TIMETABLES
-- Admin maintains; TO sees own department; Student sees enrolled courses.
-- ============================================================
CREATE TABLE IF NOT EXISTS timetables (
    timetable_id INT PRIMARY KEY AUTO_INCREMENT,
    department_id INT NOT NULL,
    offering_id INT NOT NULL,
    day_of_week VARCHAR(15) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    location VARCHAR(100),
    academic_year VARCHAR(20),
    semester INT,

    CONSTRAINT uq_timetable_entry
        UNIQUE (offering_id, day_of_week, start_time),

    CONSTRAINT chk_timetable_time
        CHECK (start_time < end_time),

    CONSTRAINT chk_timetable_semester
        CHECK (semester IS NULL OR semester > 0),

    CONSTRAINT fk_timetable_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,

    CONSTRAINT fk_timetable_offering
        FOREIGN KEY (offering_id)
        REFERENCES course_offerings(offering_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ============================================================
-- USEFUL INDEXES
-- ============================================================
CREATE INDEX idx_users_role
    ON users(role);

CREATE INDEX idx_lecturers_department
    ON lecturers(department_id);

CREATE INDEX idx_to_department
    ON technical_officers(department_id);

CREATE INDEX idx_offerings_course
    ON course_offerings(course_id);

CREATE INDEX idx_enrollments_student
    ON enrollments(student_id);

CREATE INDEX idx_enrollments_offering
    ON enrollments(offering_id);

CREATE INDEX idx_assessments_offering
    ON assessments(offering_id);

CREATE INDEX idx_marks_enrollment
    ON marks(enrollment_id);

CREATE INDEX idx_marks_assessment
    ON marks(assessment_id);

CREATE INDEX idx_attendance_sessions_offering
    ON attendance_sessions(offering_id);

CREATE INDEX idx_attendance_records_enrollment
    ON attendance_records(enrollment_id);

CREATE INDEX idx_medicals_student
    ON medicals(student_id);

CREATE INDEX idx_timetables_department
    ON timetables(department_id);

CREATE INDEX idx_notices_published
    ON notices(published);

-- ============================================================
-- END OF SCHEMA
-- ============================================================
