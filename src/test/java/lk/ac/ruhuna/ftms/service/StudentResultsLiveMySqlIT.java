package lk.ac.ruhuna.ftms.service;

import junit.framework.TestCase;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;

import java.util.ArrayList;
import java.util.List;

/** Opt-in SELECT-only verification against existing accounts; never creates or publishes results. */
public class StudentResultsLiveMySqlIT extends TestCase {
    public void testExistingStudentResultsAndDemoGpaWithoutWrites() throws Exception {
        var before = snapshot();
        int students = 0, visible = 0, empty = 0;
        boolean demoVerified = false;
        try (var connection = DBConnection.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT u.user_id,u.username FROM users u JOIN students s ON s.student_id=u.user_id
                     WHERE u.role='STUDENT' AND u.is_active=TRUE ORDER BY u.user_id
                     """); var accounts = statement.executeQuery()) {
            while (accounts.next()) {
                int student = accounts.getInt(1);
                SessionManager.startSession(new User(student,accounts.getString(2),"Read-only verification",null,"STUDENT",true));
                var identity = SessionManager.requireSession();
                var records = new ResultService().getMyPublishedRecords(identity);
                try (var expected = connection.prepareStatement("""
                        SELECT c.course_code,r.grade_letter,r.grade_point,c.credits,e.attempt_no
                        FROM enrollments e JOIN course_results r ON r.enrollment_id=e.enrollment_id
                        JOIN course_offerings o ON o.offering_id=e.offering_id
                        JOIN courses c ON c.course_id=o.course_id
                        JOIN semesters s ON s.semester_id=o.semester_id
                        JOIN academic_years ay ON ay.academic_year_id=s.academic_year_id
                        WHERE e.student_id=? AND r.published=TRUE
                        AND e.status<>'WITHDRAWN' AND o.status<>'CANCELLED'
                        ORDER BY ay.start_date,ay.academic_year_name,s.semester_no,s.semester_id,c.course_code,e.attempt_no
                        """)) {
                    expected.setInt(1,student);
                    try (var rows = expected.executeQuery()) {
                        int index = 0;
                        while (rows.next()) {
                            assertTrue(index < records.grades().size());
                            var grade = records.grades().get(index++);
                            assertEquals(rows.getString(1),grade.courseCode());
                            assertEquals(rows.getString(2),grade.grade());
                            assertEquals(rows.getBigDecimal(3),grade.gradePoint());
                            assertEquals(rows.getBigDecimal(4),grade.credits());
                            assertEquals(rows.getInt(5),grade.attempt());
                        }
                        assertEquals(index,records.grades().size());
                    }
                }
                assertEquals(records.grades(),new ResultService().getMyPublishedRecords(identity).grades());
                students++;
                visible += records.grades().size();
                if (records.grades().isEmpty()) {
                    empty++;
                    assertTrue(records.semesters().isEmpty());
                }
                if ("demo_stu01".equals(accounts.getString(2))) {
                    // Existing 3-credit B+ and 3-credit B: (3*3.30 + 3*3.00) / 6 = 3.15.
                    assertEquals(2,records.grades().size());
                    assertEquals(3.15,records.semesters().getFirst().result().getSgpa(),0.00001);
                    assertEquals(3.15,records.semesters().getFirst().result().getCgpa(),0.00001);
                    demoVerified = true;
                }
                SessionManager.logout();
                StudentAccessTest.denied(() -> new ResultService().getMyPublishedRecords(identity));
            }
            assertTrue("Existing students required for this opt-in check",students > 0);
            assertTrue("Existing published demo results required",visible > 0);
            assertTrue("Existing demo GPA verified",demoVerified);
            System.out.println("Live SELECT-only results: " + students + " active students, " + visible
                    + " visible published results, " + empty + " empty student(s); demo SGPA/CGPA 3.15.");
        } finally {
            SessionManager.logout();
            assertEquals("Student reads must preserve all academic records",before,snapshot());
        }
    }

    private List<List<String>> snapshot() throws Exception {
        var values = new ArrayList<List<String>>();
        try (var connection = DBConnection.getConnection()) {
            for (String table : List.of("enrollments","course_results","semester_results","grade_rules")) {
                try (var statement = connection.prepareStatement("SELECT * FROM " + table + " ORDER BY 1");
                     var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        var value = new ArrayList<String>();
                        value.add(table);
                        for (int i=1;i<=rows.getMetaData().getColumnCount();i++) value.add(rows.getString(i));
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }
}
