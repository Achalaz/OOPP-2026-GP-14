-- SELECT-only diagnostics. Run while connected to ftms_group14.
-- The application resolves student_id from the authenticated session; there is no UI ID parameter.
SELECT VERSION() AS mysql_version, DATABASE() AS database_name;

SELECT table_name,column_name,column_type,is_nullable,column_key
FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN
 ('users','students','enrollments','course_offerings','courses','semesters','academic_years',
  'course_results','semester_results','grade_rules')
ORDER BY table_name,ordinal_position;

SELECT u.user_id,u.username,u.is_active,s.registration_no,
 COUNT(e.enrollment_id) AS enrollments,
 COALESCE(SUM(e.status<>'WITHDRAWN' AND o.status<>'CANCELLED'),0) AS valid_enrollments,
 COUNT(r.result_id) AS saved_results,
 COALESCE(SUM(r.published=TRUE),0) AS published_results,
 COALESCE(SUM(r.published=TRUE AND e.status<>'WITHDRAWN' AND o.status<>'CANCELLED'),0) AS visible_results
FROM users u LEFT JOIN students s ON s.student_id=u.user_id
LEFT JOIN enrollments e ON e.student_id=s.student_id
LEFT JOIN course_offerings o ON o.offering_id=e.offering_id
LEFT JOIN course_results r ON r.enrollment_id=e.enrollment_id
WHERE u.role='STUDENT'
GROUP BY u.user_id,u.username,u.is_active,s.registration_no ORDER BY u.user_id;

-- Missing parents/mappings or incomplete published snapshots need investigation, never automatic repair.
SELECT r.result_id,e.enrollment_id,e.student_id,c.course_code,e.status,o.status,
 s.semester_id,ay.academic_year_name,r.grade_letter,r.grade_point,r.result_status,r.published_at
FROM course_results r LEFT JOIN enrollments e ON e.enrollment_id=r.enrollment_id
LEFT JOIN course_offerings o ON o.offering_id=e.offering_id
LEFT JOIN courses c ON c.course_id=o.course_id LEFT JOIN semesters s ON s.semester_id=o.semester_id
LEFT JOIN academic_years ay ON ay.academic_year_id=s.academic_year_id
WHERE r.published=TRUE AND (e.enrollment_id IS NULL OR c.course_id IS NULL OR ay.academic_year_id IS NULL
 OR r.grade_letter IS NULL OR r.grade_point IS NULL OR r.published_at IS NULL
 OR r.result_status NOT IN ('PASS','FAIL') OR e.status='WITHDRAWN' OR o.status='CANCELLED');

SELECT * FROM grade_rules ORDER BY min_mark;
SELECT student_id,semester_id,total_registered_credits,total_earned_credits,total_quality_points,sgpa,cgpa,calculated_at
FROM semester_results ORDER BY student_id,semester_id;

-- Independent ordinary-attempt SGPA check; this is not a repeat replacement policy or a GPA save.
SELECT e.student_id,o.semester_id,SUM(c.credits) AS included_credits,
 SUM(c.credits*r.grade_point) AS quality_points,
 ROUND(SUM(c.credits*r.grade_point)/NULLIF(SUM(c.credits),0),2) AS published_sgpa
FROM enrollments e JOIN course_offerings o ON o.offering_id=e.offering_id
JOIN courses c ON c.course_id=o.course_id JOIN course_results r ON r.enrollment_id=e.enrollment_id
WHERE r.published=TRUE AND r.result_status IN ('PASS','FAIL') AND r.grade_point IS NOT NULL
 AND e.attempt_no=1 AND e.status NOT IN ('WITHDRAWN','REPEAT') AND o.status<>'CANCELLED'
GROUP BY e.student_id,o.semester_id ORDER BY e.student_id,o.semester_id;
