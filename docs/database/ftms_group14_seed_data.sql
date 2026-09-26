-- ============================================================
-- FTMS Group 14 - DEMO / TEST SEED DATA
-- Database: ftms_group14
--
-- Run this AFTER ftms_group14_schema.sql.
-- Intended for a fresh/empty development database.
--
-- DEMO PASSWORD FOR ALL SEEDED ACCOUNTS:
--   Ftms@123
--
-- Example usernames:
--   admin
--   lecturer1 ... lecturer5
--   to1 ... to4
--   student01 ... student20
--
-- IMPORTANT:
-- 1) These are sample/demo records, not official university data.
-- 2) Replace demo course/department data with confirmed real data later.
-- 3) grade_rules, course_results, semester_results are intentionally NOT
--    pre-populated because exact grade/GPA policies contain TBC items.
-- 4) 80% attendance values below are TEST SCENARIOS only, not an
--    eligibility cutoff.
-- ============================================================

USE ftms_group14;

START TRANSACTION;


-- ============================================================
-- 1. DEPARTMENTS (DEMO)
-- ============================================================
INSERT INTO departments
(department_id, department_code, department_name)
VALUES
(1, 'ICT', 'Information and Communication Technology'),
(2, 'ET',  'Engineering Technology');


-- ============================================================
-- 2. USERS
-- ============================================================
INSERT INTO users
(user_id, username, password_hash, full_name, contact_no, email, role, account_status, profile_picture)
VALUES

(1, 'admin', '120000:rL4+H2c/XB4bEPUx6Sgvfg==:IKbTHbC2VJ8MHxsX5Ktp0BjP/oec4qcYTBPx4jH6SGU=', 'FTMS Administrator', '0770000001', 'admin@ftms.demo', 'ADMIN', 'ACTIVE', NULL),
(101, 'lecturer1', '120000:cZy9Gc3mhYjCQzM8q3L44g==:E6Yv5d8cR4capQ0YsbE8i6Et6Eta/QXUK8eVz3Obn1Y=', 'Lecturer 1', '0771000001', 'lecturer1@ftms.demo', 'LECTURER', 'ACTIVE', NULL),
(102, 'lecturer2', '120000:+C3+kAZfUdu06uT3KT4Aew==:1Zae9GgmbJ3KPHeFMgCnSiDG9i04E/BOiUi1Nb+bjAg=', 'Lecturer 2', '0771000002', 'lecturer2@ftms.demo', 'LECTURER', 'ACTIVE', NULL),
(103, 'lecturer3', '120000:DWZb7vgOsWM2g7kVz5FsaQ==:SZvqjk3qTSeNzaFS9iSUFi5QTxvua0LmNKdfOdVCva8=', 'Lecturer 3', '0771000003', 'lecturer3@ftms.demo', 'LECTURER', 'ACTIVE', NULL),
(104, 'lecturer4', '120000:Xu5RfMbZbnKimUNdnu4x+g==:bQSatQs44AOlnfCDCGc6wOTG41DajJcD9WQ/bbRDMfI=', 'Lecturer 4', '0771000004', 'lecturer4@ftms.demo', 'LECTURER', 'ACTIVE', NULL),
(105, 'lecturer5', '120000:WgNLoulvChGZVLydbzE6dA==:qzYd9+qBjthsJCrlew2+bFONJN/7l58CLHavXKD5hA8=', 'Lecturer 5', '0771000005', 'lecturer5@ftms.demo', 'LECTURER', 'ACTIVE', NULL),
(201, 'to1', '120000:v+UwVzUWlF1K81yoCPewSQ==:nlkuCUD6Pe15FQHT9nfDT/l5J4kZ2mDDF6IZK4RvdzM=', 'Technical Officer 1', '0772000001', 'to1@ftms.demo', 'TECHNICAL_OFFICER', 'ACTIVE', NULL),
(202, 'to2', '120000:1DzQYM0yIMGSvZskFl+XFA==:oskp8I867kv7o/MRcznXxWRPfaLEZaCpGeQNgRkuATw=', 'Technical Officer 2', '0772000002', 'to2@ftms.demo', 'TECHNICAL_OFFICER', 'ACTIVE', NULL),
(203, 'to3', '120000:8816G1oxaANxgv6raNfrkw==:uLj0QXE8kPVguR6OhAjpkTBQdaLbyOjCkWCBRTMcBag=', 'Technical Officer 3', '0772000003', 'to3@ftms.demo', 'TECHNICAL_OFFICER', 'ACTIVE', NULL),
(204, 'to4', '120000:bFSsTzwL7BNrmWAMLaA8Bg==:wCHWDbOlA9bZuqUfvBO8kcJuJvrd6JatnfgdOS93zdM=', 'Technical Officer 4', '0772000004', 'to4@ftms.demo', 'TECHNICAL_OFFICER', 'ACTIVE', NULL),
(1001, 'student01', '120000:OQZf1Mfgf3hXIDs901/2eQ==:exW9FknBFV7raWdlFcjt9aCG78OXoTQN8tD4DuCVBew=', 'Student 01', '0773000001', 'student01@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1002, 'student02', '120000:VEy5aRv2zKHPkdLCR8PQEw==:6coeknNtIqoih+jGdmGu/bnsgbhLd/M5pssqz+HS1AU=', 'Student 02', '0773000002', 'student02@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1003, 'student03', '120000:YT669i8nxWbGzJpzRA65iA==:j04ls/BAcJe1C07awzdyrngYm9NPGVUm76uPy9PHHso=', 'Student 03', '0773000003', 'student03@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1004, 'student04', '120000:AJGQzrc0OD2QsfXvOPwQzw==:EJD+4un/K2hSMqoiMekvgHK6jkY5tn1Ry06wqYmk0Lg=', 'Student 04', '0773000004', 'student04@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1005, 'student05', '120000:BC0XK4VAVotFMY7aC36OTg==:DIqsgQPrt4suWPT2/bh1ejhi/Fn35tZipLRIgK6TK4M=', 'Student 05', '0773000005', 'student05@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1006, 'student06', '120000:3j3iWJUqNuXWfKozMP+q+w==:T1ndbObrHkg85SWYMm+x1N9fHu7WSpH8WBoScG/frK8=', 'Student 06', '0773000006', 'student06@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1007, 'student07', '120000:TZmt1U3wr66ALFK3XY6xhQ==:6O69HOMTJGVEqG8N9E6fIlCqRPTXVlXrk+JNMvdQEeQ=', 'Student 07', '0773000007', 'student07@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1008, 'student08', '120000:q01PGoimBBP9cEn4cACqpg==:4HM4cvQnr+M0owYA3W0dbTwFElyeWnhQzyRkpQevXF8=', 'Student 08', '0773000008', 'student08@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1009, 'student09', '120000:BwaMpvciKpuFyhQHLq2eSA==:+4VMwDOwJpFiOUVIn3i/KDCSapbWj5cgdVBK6RYMyKE=', 'Student 09', '0773000009', 'student09@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1010, 'student10', '120000:1e2WbObDRwopOl0f46dISw==:IWGE92dgGPHLipraTgNjGIh0ZyGORHy2mgLy2zHYeyY=', 'Student 10', '0773000010', 'student10@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1011, 'student11', '120000:ZAxUV8Y7akROsVqoDy6W7Q==:dgUr65LJV/9/ZlWPeB4aiKbISE+WWWud4Gey7Lzdsgc=', 'Student 11', '0773000011', 'student11@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1012, 'student12', '120000:yWm9tZX5ox51UUbl6o5UQw==:ocmv5ByZbr9XEVjpF5ZO6amRlKLfkzN9/Xs1/dsKomY=', 'Student 12', '0773000012', 'student12@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1013, 'student13', '120000:8UD8RwfrayPRZqCMOkYO1Q==:9iBpjm5zdfgB0+CYEReRqnECJLYHKWHkSrXLGdQcavg=', 'Student 13', '0773000013', 'student13@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1014, 'student14', '120000:C/9ylmAoh6HORMnI542zyQ==:4CTXBrgI74uu+6pcvjvlmuji2Z1KtFfxROobaLF1Raw=', 'Student 14', '0773000014', 'student14@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1015, 'student15', '120000:FzvZqxi4Jq434BYoyRRl5w==:jHOb2J3z00285J9mJZQhF6IKc6f1un9+05/cWqbtihc=', 'Student 15', '0773000015', 'student15@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1016, 'student16', '120000:CffJyLfUoKnFK9BL6P9cfQ==:EcC1MzCPq4duEjXUg/MiQyIW6FxqpHpGE7fRtF6Yxck=', 'Student 16', '0773000016', 'student16@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1017, 'student17', '120000:WVdzr9OtlVUv//1O6JhoBQ==:qIbUZNwHRcfRIFx2r97cYkh7o2g+5sIqQ4VCzk8oKO0=', 'Student 17', '0773000017', 'student17@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1018, 'student18', '120000:9LypBTZIGd5NC0KrR+NZ0g==:AT0dwnvZvw3qYuzl/wOMvfhzd41YWqIAAxONkWVkc80=', 'Student 18', '0773000018', 'student18@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1019, 'student19', '120000:urhb5SZspKwvKXEVdrvq9g==:UfoK9MXJzE6RyNHb2L4nDU8o719gasbQ+n3OqJDacEw=', 'Student 19', '0773000019', 'student19@ftms.demo', 'STUDENT', 'ACTIVE', NULL),
(1020, 'student20', '120000:tEIeTnZCjVDMV7cmp440Xg==:ZhjicWbrhYo+pFp+kqOSPbpMH7kUcuAwAZjSNN3Y6xg=', 'Student 20', '0773000020', 'student20@ftms.demo', 'STUDENT', 'ACTIVE', NULL);


-- ============================================================
-- 3. USER SUBTYPES
-- ============================================================
INSERT INTO admins (user_id) VALUES (1);

INSERT INTO lecturers
(user_id, department_id, designation)
VALUES
(101, 1, 'Lecturer'),
(102, 1, 'Lecturer'),
(103, 1, 'Senior Lecturer'),
(104, 2, 'Lecturer'),
(105, 2, 'Senior Lecturer');

INSERT INTO technical_officers
(user_id, department_id)
VALUES
(201, 1),
(202, 1),
(203, 2),
(204, 2);

INSERT INTO undergraduates
(user_id, registration_no, batch, academic_year, student_status)
VALUES

(1001, 'TG/2024/2101', '2024', '2024/2025', 'REGULAR'),
(1002, 'TG/2024/2102', '2024', '2024/2025', 'REGULAR'),
(1003, 'TG/2024/2103', '2024', '2024/2025', 'REGULAR'),
(1004, 'TG/2024/2104', '2024', '2024/2025', 'REGULAR'),
(1005, 'TG/2024/2105', '2024', '2024/2025', 'REGULAR'),
(1006, 'TG/2024/2106', '2024', '2024/2025', 'REGULAR'),
(1007, 'TG/2024/2107', '2024', '2024/2025', 'REGULAR'),
(1008, 'TG/2024/2108', '2024', '2024/2025', 'REGULAR'),
(1009, 'TG/2024/2109', '2024', '2024/2025', 'REGULAR'),
(1010, 'TG/2024/2110', '2024', '2024/2025', 'REGULAR'),
(1011, 'TG/2024/2111', '2024', '2024/2025', 'REGULAR'),
(1012, 'TG/2024/2112', '2024', '2024/2025', 'REGULAR'),
(1013, 'TG/2024/2113', '2024', '2024/2025', 'REGULAR'),
(1014, 'TG/2024/2114', '2024', '2024/2025', 'REGULAR'),
(1015, 'TG/2024/2115', '2024', '2024/2025', 'REGULAR'),
(1016, 'TG/2024/2116', '2024', '2024/2025', 'REGULAR'),
(1017, 'TG/2024/2117', '2024', '2024/2025', 'REGULAR'),
(1018, 'TG/2024/2118', '2024', '2024/2025', 'REGULAR'),
(1019, 'TG/2023/2199', '2023', '2023/2024', 'REPEAT'),
(1020, 'TG/2023/2200', '2023', '2023/2024', 'BATCH_MISSED');


-- ============================================================
-- 4. COURSES + OFFERINGS
-- ICT2132 is the known project-course context.
-- DEMO1001/DEMO1002 are placeholders for development/testing.
-- Replace them with confirmed current-semester modules later.
-- ============================================================
INSERT INTO courses
(course_id, department_id, course_code, course_name, theory_credits, practical_credits, semester, status)
VALUES
(1, 1, 'ICT2132',  'Object Oriented Programming Practicum', 0.0, 2.0, 1, 'ACTIVE'),
(2, 1, 'DEMO1001', 'Database Systems - Demo',               2.0, 1.0, 1, 'ACTIVE'),
(3, 1, 'DEMO1002', 'Web Technologies - Demo',               2.0, 1.0, 1, 'ACTIVE');

INSERT INTO course_offerings
(offering_id, course_id, academic_year, semester, batch, status)
VALUES
(11, 1, '2026', 1, '2024', 'ACTIVE'),
(12, 2, '2026', 1, '2024', 'ACTIVE'),
(13, 3, '2026', 1, '2024', 'ACTIVE'),
(14, 1, '2026', 1, '2023', 'ACTIVE');


-- ============================================================
-- 5. LECTURER COURSE ASSIGNMENTS
-- ============================================================
INSERT INTO lecturer_course_assignments
(assignment_id, lecturer_id, offering_id)
VALUES
(1, 101, 11),
(2, 102, 12),
(3, 103, 13),
(4, 104, 14),
(5, 105, 11);


-- ============================================================
-- 6. ENROLLMENTS
-- Regular students 1-18 -> ICT2132/2024 offering
-- Repeat + batch-missed students -> 2023 offering
-- ============================================================
INSERT INTO enrollments
(enrollment_id, student_id, offering_id, attempt_no, status)
VALUES

(1, 1001, 11, 1, 'ACTIVE'),
(2, 1002, 11, 1, 'ACTIVE'),
(3, 1003, 11, 1, 'ACTIVE'),
(4, 1004, 11, 1, 'ACTIVE'),
(5, 1005, 11, 1, 'ACTIVE'),
(6, 1006, 11, 1, 'ACTIVE'),
(7, 1007, 11, 1, 'ACTIVE'),
(8, 1008, 11, 1, 'ACTIVE'),
(9, 1009, 11, 1, 'ACTIVE'),
(10, 1010, 11, 1, 'ACTIVE'),
(11, 1011, 11, 1, 'ACTIVE'),
(12, 1012, 11, 1, 'ACTIVE'),
(13, 1013, 11, 1, 'ACTIVE'),
(14, 1014, 11, 1, 'ACTIVE'),
(15, 1015, 11, 1, 'ACTIVE'),
(16, 1016, 11, 1, 'ACTIVE'),
(17, 1017, 11, 1, 'ACTIVE'),
(18, 1018, 11, 1, 'ACTIVE'),
(19, 1019, 14, 2, 'ACTIVE'),
(20, 1020, 14, 1, 'ACTIVE');


-- ============================================================
-- 7. COURSE MATERIALS
-- ============================================================
INSERT INTO course_materials
(material_id, offering_id, title, description, file_path_or_url, uploaded_by)
VALUES
(1, 11, 'Week 01 Introduction', 'Demo material record for ICT2132.', 'materials/ict2132/week01.pdf', 101),
(2, 11, 'OOP Concepts', 'Demo reference for inheritance, abstraction and polymorphism.', 'materials/ict2132/oop-concepts.pdf', 101);


-- ============================================================
-- 8. ASSESSMENTS
-- Demo criteria only; replace/confirm with course coordinator.
-- Total weight = 100.
-- ============================================================
INSERT INTO assessments
(assessment_id, offering_id, assessment_name, assessment_type, weight_percentage, maximum_mark, evaluation_criteria)
VALUES
(1, 11, 'Quiz 1',      'CA',    10.00, 100.00, 'Demo CA component'),
(2, 11, 'Assignment',  'CA',    20.00, 100.00, 'Demo CA component'),
(3, 11, 'Mid Exam',    'CA',    20.00, 100.00, 'Demo CA component'),
(4, 11, 'Final Exam',  'FINAL', 50.00, 100.00, 'Demo final component');


-- ============================================================
-- 9. SAMPLE MARKS FOR ACHALA MODULE
-- enrollment_id 1..6, assessments 1..4
-- ============================================================
INSERT INTO marks
(mark_id, enrollment_id, assessment_id, marks_obtained, attempt_no)
VALUES

(1, 1, 1, 72.00, 1),
(2, 1, 2, 80.00, 1),
(3, 1, 3, 65.00, 1),
(4, 1, 4, 70.00, 1),
(5, 2, 1, 55.00, 1),
(6, 2, 2, 60.00, 1),
(7, 2, 3, 58.00, 1),
(8, 2, 4, 62.00, 1),
(9, 3, 1, 40.00, 1),
(10, 3, 2, 40.00, 1),
(11, 3, 3, 40.00, 1),
(12, 3, 4, 50.00, 1),
(13, 4, 1, 85.00, 1),
(14, 4, 2, 78.00, 1),
(15, 4, 3, 90.00, 1),
(16, 4, 4, 88.00, 1),
(17, 5, 1, 30.00, 1),
(18, 5, 2, 35.00, 1),
(19, 5, 3, 32.00, 1),
(20, 5, 4, 45.00, 1),
(21, 6, 1, 95.00, 1),
(22, 6, 2, 92.00, 1),
(23, 6, 3, 90.00, 1),
(24, 6, 4, 94.00, 1);


-- ============================================================
-- 10. ATTENDANCE SESSIONS
-- 15 THEORY + 15 PRACTICAL, 2 hours each.
-- ============================================================
INSERT INTO attendance_sessions
(session_id, offering_id, session_type, session_no, session_date, duration_hours, topic)
VALUES

(1, 11, 'THEORY', 1, '2026-01-05', 2.00, 'Theory Session 1'),
(2, 11, 'THEORY', 2, '2026-01-12', 2.00, 'Theory Session 2'),
(3, 11, 'THEORY', 3, '2026-01-19', 2.00, 'Theory Session 3'),
(4, 11, 'THEORY', 4, '2026-01-26', 2.00, 'Theory Session 4'),
(5, 11, 'THEORY', 5, '2026-02-02', 2.00, 'Theory Session 5'),
(6, 11, 'THEORY', 6, '2026-02-09', 2.00, 'Theory Session 6'),
(7, 11, 'THEORY', 7, '2026-02-16', 2.00, 'Theory Session 7'),
(8, 11, 'THEORY', 8, '2026-02-23', 2.00, 'Theory Session 8'),
(9, 11, 'THEORY', 9, '2026-03-02', 2.00, 'Theory Session 9'),
(10, 11, 'THEORY', 10, '2026-03-09', 2.00, 'Theory Session 10'),
(11, 11, 'THEORY', 11, '2026-03-16', 2.00, 'Theory Session 11'),
(12, 11, 'THEORY', 12, '2026-03-23', 2.00, 'Theory Session 12'),
(13, 11, 'THEORY', 13, '2026-03-30', 2.00, 'Theory Session 13'),
(14, 11, 'THEORY', 14, '2026-04-06', 2.00, 'Theory Session 14'),
(15, 11, 'THEORY', 15, '2026-04-13', 2.00, 'Theory Session 15'),
(16, 11, 'PRACTICAL', 1, '2026-01-07', 2.00, 'Practical Session 1'),
(17, 11, 'PRACTICAL', 2, '2026-01-14', 2.00, 'Practical Session 2'),
(18, 11, 'PRACTICAL', 3, '2026-01-21', 2.00, 'Practical Session 3'),
(19, 11, 'PRACTICAL', 4, '2026-01-28', 2.00, 'Practical Session 4'),
(20, 11, 'PRACTICAL', 5, '2026-02-04', 2.00, 'Practical Session 5'),
(21, 11, 'PRACTICAL', 6, '2026-02-11', 2.00, 'Practical Session 6'),
(22, 11, 'PRACTICAL', 7, '2026-02-18', 2.00, 'Practical Session 7'),
(23, 11, 'PRACTICAL', 8, '2026-02-25', 2.00, 'Practical Session 8'),
(24, 11, 'PRACTICAL', 9, '2026-03-04', 2.00, 'Practical Session 9'),
(25, 11, 'PRACTICAL', 10, '2026-03-11', 2.00, 'Practical Session 10'),
(26, 11, 'PRACTICAL', 11, '2026-03-18', 2.00, 'Practical Session 11'),
(27, 11, 'PRACTICAL', 12, '2026-03-25', 2.00, 'Practical Session 12'),
(28, 11, 'PRACTICAL', 13, '2026-04-01', 2.00, 'Practical Session 13'),
(29, 11, 'PRACTICAL', 14, '2026-04-08', 2.00, 'Practical Session 14'),
(30, 11, 'PRACTICAL', 15, '2026-04-15', 2.00, 'Practical Session 15');


-- ============================================================
-- 11. MEDICALS
-- Includes APPROVED, PENDING and REJECTED test records.
-- ============================================================
INSERT INTO medicals
(medical_id, student_id, medical_date, document_reference, status, recorded_by_user_id, remarks)
VALUES
(1, 1004, '2026-03-18', 'medicals/student04-approved.pdf', 'APPROVED', 201, 'Approved demo medical'),
(2, 1005, '2026-03-04', 'medicals/student05-pending.pdf',  'PENDING',  201, 'Pending demo medical'),
(3, 1006, '2026-03-09', 'medicals/student06-rejected.pdf', 'REJECTED', 201, 'Rejected demo medical');


-- ============================================================
-- 12. ATTENDANCE RECORDS
-- Mandatory test scenarios around 80%.
-- IMPORTANT: These percentages are demo/test bands only.
-- ============================================================
INSERT INTO attendance_records
(attendance_record_id, session_id, enrollment_id, attendance_status, medical_id, recorded_by_user_id)
VALUES

(1, 1, 1, 'PRESENT', NULL, 201),
(2, 2, 1, 'PRESENT', NULL, 201),
(3, 3, 1, 'PRESENT', NULL, 201),
(4, 4, 1, 'PRESENT', NULL, 201),
(5, 5, 1, 'PRESENT', NULL, 201),
(6, 6, 1, 'PRESENT', NULL, 201),
(7, 7, 1, 'PRESENT', NULL, 201),
(8, 8, 1, 'PRESENT', NULL, 201),
(9, 9, 1, 'PRESENT', NULL, 201),
(10, 10, 1, 'PRESENT', NULL, 201),
(11, 11, 1, 'PRESENT', NULL, 201),
(12, 12, 1, 'PRESENT', NULL, 201),
(13, 13, 1, 'PRESENT', NULL, 201),
(14, 14, 1, 'PRESENT', NULL, 201),
(15, 15, 1, 'PRESENT', NULL, 201),
(16, 16, 1, 'PRESENT', NULL, 201),
(17, 17, 1, 'PRESENT', NULL, 201),
(18, 18, 1, 'PRESENT', NULL, 201),
(19, 19, 1, 'PRESENT', NULL, 201),
(20, 20, 1, 'PRESENT', NULL, 201),
(21, 21, 1, 'PRESENT', NULL, 201),
(22, 22, 1, 'PRESENT', NULL, 201),
(23, 23, 1, 'PRESENT', NULL, 201),
(24, 24, 1, 'PRESENT', NULL, 201),
(25, 25, 1, 'PRESENT', NULL, 201),
(26, 26, 1, 'ABSENT', NULL, 201),
(27, 27, 1, 'ABSENT', NULL, 201),
(28, 28, 1, 'ABSENT', NULL, 201),
(29, 29, 1, 'ABSENT', NULL, 201),
(30, 30, 1, 'ABSENT', NULL, 201),
(31, 1, 2, 'PRESENT', NULL, 201),
(32, 2, 2, 'PRESENT', NULL, 201),
(33, 3, 2, 'PRESENT', NULL, 201),
(34, 4, 2, 'PRESENT', NULL, 201),
(35, 5, 2, 'PRESENT', NULL, 201),
(36, 6, 2, 'PRESENT', NULL, 201),
(37, 7, 2, 'PRESENT', NULL, 201),
(38, 8, 2, 'PRESENT', NULL, 201),
(39, 9, 2, 'PRESENT', NULL, 201),
(40, 10, 2, 'PRESENT', NULL, 201),
(41, 11, 2, 'PRESENT', NULL, 201),
(42, 12, 2, 'PRESENT', NULL, 201),
(43, 13, 2, 'PRESENT', NULL, 201),
(44, 14, 2, 'PRESENT', NULL, 201),
(45, 15, 2, 'PRESENT', NULL, 201),
(46, 16, 2, 'PRESENT', NULL, 201),
(47, 17, 2, 'PRESENT', NULL, 201),
(48, 18, 2, 'PRESENT', NULL, 201),
(49, 19, 2, 'PRESENT', NULL, 201),
(50, 20, 2, 'PRESENT', NULL, 201),
(51, 21, 2, 'PRESENT', NULL, 201),
(52, 22, 2, 'PRESENT', NULL, 201),
(53, 23, 2, 'PRESENT', NULL, 201),
(54, 24, 2, 'PRESENT', NULL, 201),
(55, 25, 2, 'ABSENT', NULL, 201),
(56, 26, 2, 'ABSENT', NULL, 201),
(57, 27, 2, 'ABSENT', NULL, 201),
(58, 28, 2, 'ABSENT', NULL, 201),
(59, 29, 2, 'ABSENT', NULL, 201),
(60, 30, 2, 'ABSENT', NULL, 201),
(61, 1, 3, 'PRESENT', NULL, 201),
(62, 2, 3, 'PRESENT', NULL, 201),
(63, 3, 3, 'PRESENT', NULL, 201),
(64, 4, 3, 'PRESENT', NULL, 201),
(65, 5, 3, 'PRESENT', NULL, 201),
(66, 6, 3, 'PRESENT', NULL, 201),
(67, 7, 3, 'PRESENT', NULL, 201),
(68, 8, 3, 'PRESENT', NULL, 201),
(69, 9, 3, 'PRESENT', NULL, 201),
(70, 10, 3, 'PRESENT', NULL, 201),
(71, 11, 3, 'PRESENT', NULL, 201),
(72, 12, 3, 'PRESENT', NULL, 201),
(73, 13, 3, 'PRESENT', NULL, 201),
(74, 14, 3, 'PRESENT', NULL, 201),
(75, 15, 3, 'PRESENT', NULL, 201),
(76, 16, 3, 'PRESENT', NULL, 201),
(77, 17, 3, 'PRESENT', NULL, 201),
(78, 18, 3, 'PRESENT', NULL, 201),
(79, 19, 3, 'PRESENT', NULL, 201),
(80, 20, 3, 'PRESENT', NULL, 201),
(81, 21, 3, 'PRESENT', NULL, 201),
(82, 22, 3, 'PRESENT', NULL, 201),
(83, 23, 3, 'PRESENT', NULL, 201),
(84, 24, 3, 'ABSENT', NULL, 201),
(85, 25, 3, 'ABSENT', NULL, 201),
(86, 26, 3, 'ABSENT', NULL, 201),
(87, 27, 3, 'ABSENT', NULL, 201),
(88, 28, 3, 'ABSENT', NULL, 201),
(89, 29, 3, 'ABSENT', NULL, 201),
(90, 30, 3, 'ABSENT', NULL, 201),
(91, 1, 4, 'PRESENT', NULL, 201),
(92, 2, 4, 'PRESENT', NULL, 201),
(93, 3, 4, 'PRESENT', NULL, 201),
(94, 4, 4, 'PRESENT', NULL, 201),
(95, 5, 4, 'PRESENT', NULL, 201),
(96, 6, 4, 'PRESENT', NULL, 201),
(97, 7, 4, 'PRESENT', NULL, 201),
(98, 8, 4, 'PRESENT', NULL, 201),
(99, 9, 4, 'PRESENT', NULL, 201),
(100, 10, 4, 'PRESENT', NULL, 201),
(101, 11, 4, 'PRESENT', NULL, 201),
(102, 12, 4, 'PRESENT', NULL, 201),
(103, 13, 4, 'PRESENT', NULL, 201),
(104, 14, 4, 'PRESENT', NULL, 201),
(105, 15, 4, 'PRESENT', NULL, 201),
(106, 16, 4, 'PRESENT', NULL, 201),
(107, 17, 4, 'PRESENT', NULL, 201),
(108, 18, 4, 'PRESENT', NULL, 201),
(109, 19, 4, 'PRESENT', NULL, 201),
(110, 20, 4, 'PRESENT', NULL, 201),
(111, 21, 4, 'PRESENT', NULL, 201),
(112, 22, 4, 'PRESENT', NULL, 201),
(113, 23, 4, 'PRESENT', NULL, 201),
(114, 24, 4, 'PRESENT', NULL, 201),
(115, 25, 4, 'PRESENT', NULL, 201),
(116, 26, 4, 'ABSENT', 1, 201),
(117, 27, 4, 'ABSENT', NULL, 201),
(118, 28, 4, 'ABSENT', NULL, 201),
(119, 29, 4, 'ABSENT', NULL, 201),
(120, 30, 4, 'ABSENT', NULL, 201),
(121, 1, 5, 'PRESENT', NULL, 201),
(122, 2, 5, 'PRESENT', NULL, 201),
(123, 3, 5, 'PRESENT', NULL, 201),
(124, 4, 5, 'PRESENT', NULL, 201),
(125, 5, 5, 'PRESENT', NULL, 201),
(126, 6, 5, 'PRESENT', NULL, 201),
(127, 7, 5, 'PRESENT', NULL, 201),
(128, 8, 5, 'PRESENT', NULL, 201),
(129, 9, 5, 'PRESENT', NULL, 201),
(130, 10, 5, 'PRESENT', NULL, 201),
(131, 11, 5, 'PRESENT', NULL, 201),
(132, 12, 5, 'PRESENT', NULL, 201),
(133, 13, 5, 'PRESENT', NULL, 201),
(134, 14, 5, 'PRESENT', NULL, 201),
(135, 15, 5, 'PRESENT', NULL, 201),
(136, 16, 5, 'PRESENT', NULL, 201),
(137, 17, 5, 'PRESENT', NULL, 201),
(138, 18, 5, 'PRESENT', NULL, 201),
(139, 19, 5, 'PRESENT', NULL, 201),
(140, 20, 5, 'PRESENT', NULL, 201),
(141, 21, 5, 'PRESENT', NULL, 201),
(142, 22, 5, 'PRESENT', NULL, 201),
(143, 23, 5, 'PRESENT', NULL, 201),
(144, 24, 5, 'ABSENT', 2, 201),
(145, 25, 5, 'ABSENT', NULL, 201),
(146, 26, 5, 'ABSENT', NULL, 201),
(147, 27, 5, 'ABSENT', NULL, 201),
(148, 28, 5, 'ABSENT', NULL, 201),
(149, 29, 5, 'ABSENT', NULL, 201),
(150, 30, 5, 'ABSENT', NULL, 201),
(151, 1, 6, 'PRESENT', NULL, 201),
(152, 2, 6, 'PRESENT', NULL, 201),
(153, 3, 6, 'PRESENT', NULL, 201),
(154, 4, 6, 'PRESENT', NULL, 201),
(155, 5, 6, 'PRESENT', NULL, 201),
(156, 6, 6, 'PRESENT', NULL, 201),
(157, 7, 6, 'PRESENT', NULL, 201),
(158, 8, 6, 'PRESENT', NULL, 201),
(159, 9, 6, 'PRESENT', NULL, 201),
(160, 10, 6, 'ABSENT', 3, 201),
(161, 11, 6, 'PRESENT', NULL, 201),
(162, 12, 6, 'PRESENT', NULL, 201),
(163, 13, 6, 'PRESENT', NULL, 201),
(164, 14, 6, 'PRESENT', NULL, 201),
(165, 15, 6, 'PRESENT', NULL, 201),
(166, 16, 6, 'PRESENT', NULL, 201),
(167, 17, 6, 'PRESENT', NULL, 201),
(168, 18, 6, 'PRESENT', NULL, 201),
(169, 19, 6, 'PRESENT', NULL, 201),
(170, 20, 6, 'PRESENT', NULL, 201),
(171, 21, 6, 'PRESENT', NULL, 201),
(172, 22, 6, 'PRESENT', NULL, 201),
(173, 23, 6, 'PRESENT', NULL, 201),
(174, 24, 6, 'PRESENT', NULL, 201),
(175, 25, 6, 'PRESENT', NULL, 201),
(176, 26, 6, 'PRESENT', NULL, 201),
(177, 27, 6, 'PRESENT', NULL, 201),
(178, 28, 6, 'ABSENT', NULL, 201),
(179, 29, 6, 'ABSENT', NULL, 201),
(180, 30, 6, 'PRESENT', NULL, 201);


-- ============================================================
-- 13. NOTICES
-- ============================================================
INSERT INTO notices
(notice_id, title, body, posted_by, target_role, published)
VALUES
(1, 'Welcome to FTMS', 'This is a demo notice for all FTMS users.', 1, 'ALL', TRUE),
(2, 'ICT2132 Update', 'Please check the latest course material and timetable.', 1, 'STUDENT', TRUE),
(3, 'Staff Notice', 'Demo notice for lecturer accounts.', 1, 'LECTURER', TRUE);


-- ============================================================
-- 14. TIMETABLE
-- ============================================================
INSERT INTO timetables
(timetable_id, department_id, offering_id, day_of_week, start_time, end_time, location, academic_year, semester)
VALUES
(1, 1, 11, 'MONDAY',    '08:00:00', '10:00:00', 'ICT Lab 01', '2026', 1),
(2, 1, 12, 'TUESDAY',   '10:00:00', '12:00:00', 'Lecture Hall A', '2026', 1),
(3, 1, 13, 'THURSDAY',  '13:00:00', '15:00:00', 'ICT Lab 02', '2026', 1),
(4, 1, 11, 'WEDNESDAY', '08:00:00', '10:00:00', 'ICT Lab 01', '2026', 1);


-- ============================================================
-- 15. INTENTIONALLY EMPTY TABLES
-- ============================================================
-- grade_rules:
--   Keep empty until exact UGC Circular No. 12-2024 boundaries
--   and grade points are confirmed.
--
-- course_results:
--   Generate through Achala ResultService after marks, attendance
--   policy and GradeRule configuration are ready.
--
-- semester_results:
--   Generate through GPAService after repeat/fail/incomplete
--   policy is confirmed.

COMMIT;

-- ============================================================
-- QUICK CHECKS
-- ============================================================
SELECT COUNT(*) AS departments_count FROM departments;
SELECT COUNT(*) AS users_count FROM users;
SELECT COUNT(*) AS lecturers_count FROM lecturers;
SELECT COUNT(*) AS technical_officers_count FROM technical_officers;
SELECT COUNT(*) AS students_count FROM undergraduates;
SELECT COUNT(*) AS courses_count FROM courses;
SELECT COUNT(*) AS enrollments_count FROM enrollments;
SELECT COUNT(*) AS assessments_count FROM assessments;
SELECT COUNT(*) AS marks_count FROM marks;
SELECT COUNT(*) AS attendance_sessions_count FROM attendance_sessions;
SELECT COUNT(*) AS attendance_records_count FROM attendance_records;
SELECT COUNT(*) AS medicals_count FROM medicals;
SELECT COUNT(*) AS notices_count FROM notices;
SELECT COUNT(*) AS timetables_count FROM timetables;

-- Expected key counts:
-- users = 30
-- lecturers = 5
-- technical_officers = 4
-- students = 20
-- attendance_sessions = 30
-- attendance_records = 180
--
-- Login demo:
-- username: admin
-- password: Ftms@123
-- ============================================================
