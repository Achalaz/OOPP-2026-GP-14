-- SELECT-only Admin/developer diagnostics for ftms_group14. No grants or academic changes.
-- SessionManager stores the logged-in application user in memory, not in MySQL.
SELECT u.user_id,u.username,u.is_active,l.staff_no,a.offering_id,a.assignment_type,
       c.course_code,o.offering_group,o.status,ay.academic_year_name,s.semester_name
FROM users u LEFT JOIN lecturers l ON l.lecturer_id=u.user_id
LEFT JOIN lecturer_course_assignments a ON a.lecturer_id=u.user_id
LEFT JOIN course_offerings o ON o.offering_id=a.offering_id LEFT JOIN courses c ON c.course_id=o.course_id
LEFT JOIN semesters s ON s.semester_id=o.semester_id LEFT JOIN academic_years ay ON ay.academic_year_id=s.academic_year_id
WHERE u.role='LECTURER' ORDER BY u.user_id,o.offering_id;

-- One row per lecturer/student, including exactly which assignments block a full transcript.
-- This administrator diagnostic includes unrelated students; the application UI does not.
SELECT u.user_id AS lecturer_id,u.username,s.student_id,s.registration_no,
 COUNT(DISTINCT o.offering_id) AS required_offerings,
 COUNT(DISTINCT CASE WHEN a.assignment_id IS NOT NULL THEN o.offering_id END) AS assigned_offerings,
 GROUP_CONCAT(DISTINCT CASE WHEN a.assignment_id IS NULL THEN CONCAT(o.offering_id,':',c.course_code) END
              ORDER BY o.offering_id SEPARATOR ', ') AS missing_assignments,
 COUNT(DISTINCT CASE WHEN r.published=TRUE THEN r.result_id END) AS published_results,
 (SELECT COUNT(*) FROM semester_results sr WHERE sr.student_id=s.student_id) AS saved_semesters
FROM users u CROSS JOIN students s JOIN enrollments e ON e.student_id=s.student_id
JOIN course_offerings o ON o.offering_id=e.offering_id JOIN courses c ON c.course_id=o.course_id
LEFT JOIN lecturer_course_assignments a ON a.offering_id=o.offering_id AND a.lecturer_id=u.user_id
 AND a.assignment_type IN ('PRIMARY','ASSISTANT','PRACTICAL')
LEFT JOIN course_results r ON r.enrollment_id=e.enrollment_id
WHERE u.role='LECTURER' AND u.is_active=TRUE AND e.status<>'WITHDRAWN' AND o.status<>'CANCELLED'
GROUP BY u.user_id,u.username,s.student_id,s.registration_no ORDER BY u.user_id,s.registration_no;

SELECT o.offering_id,c.course_code,o.offering_group,o.status,
 COUNT(e.enrollment_id) AS enrollments,COUNT(r.result_id) AS saved_results,
 COALESCE(SUM(r.published=TRUE),0) AS published_results
FROM course_offerings o JOIN courses c ON c.course_id=o.course_id
LEFT JOIN enrollments e ON e.offering_id=o.offering_id LEFT JOIN course_results r ON r.enrollment_id=e.enrollment_id
GROUP BY o.offering_id,c.course_code,o.offering_group,o.status ORDER BY o.offering_id;
