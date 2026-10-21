package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.util.PasswordUtil;

/** Only connection-local temporary tables. Production accounts and academic records are untouched. */
public final class StudentDatabaseFixture implements AutoCloseable {
    public final AcademicDatabaseFixture academic = new AcademicDatabaseFixture();
    public final String password = java.util.UUID.randomUUID().toString();

    public StudentDatabaseFixture() throws Exception {
        try {
        for (String table : java.util.List.of("medicals","notices")) {
            String ddl;
            try (var statement = academic.connection.createStatement();
                 var rows = statement.executeQuery("SHOW CREATE TABLE " + table)) {
                rows.next();
                ddl = rows.getString(2).replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE")
                        .replaceAll("(?m)^  CONSTRAINT .*FOREIGN KEY.*\\R", "")
                        .replaceAll(",\\s*\\) ENGINE", "\n) ENGINE");
            }
            academic.execute(ddl);
        }
        try (var statement = academic.connection.prepareStatement("UPDATE users SET password_hash=?,email='student@example.edu',phone='0123456789' WHERE user_id=3")) {
            statement.setString(1,PasswordUtil.hashPassword(password.toCharArray()));
            statement.executeUpdate();
        }
        academic.execute("INSERT INTO medicals(medical_id,student_id,offering_id,from_date,to_date,status,reason) VALUES "
                +"(1,3,10,'2026-01-01','2026-01-02','PENDING','Own pending'),"
                +"(2,3,10,'2026-02-01','2026-02-02','APPROVED','Own approved'),"
                +"(3,3,NULL,'2026-03-01','2026-03-02','REJECTED','Own rejected'),"
                +"(4,4,10,'2026-01-01','2026-01-02','APPROVED','Private other student')");
        academic.execute("INSERT INTO notices(title,content,audience,created_by,published_at,expires_at,is_active) VALUES "
                +"('All','Visible','ALL',5,DATE_SUB(NOW(),INTERVAL 1 DAY),NULL,TRUE),"
                +"('Students','Visible','STUDENTS',5,DATE_SUB(NOW(),INTERVAL 1 DAY),NULL,TRUE),"
                +"('Lecturers','Hidden','LECTURERS',5,DATE_SUB(NOW(),INTERVAL 1 DAY),NULL,TRUE),"
                +"('Inactive','Hidden','ALL',5,DATE_SUB(NOW(),INTERVAL 1 DAY),NULL,FALSE),"
                +"('Future','Hidden','STUDENTS',5,DATE_ADD(NOW(),INTERVAL 1 DAY),NULL,TRUE),"
                +"('Expired','Hidden','STUDENTS',5,DATE_SUB(NOW(),INTERVAL 2 DAY),DATE_SUB(NOW(),INTERVAL 1 DAY),TRUE)");
        } catch (Exception error) {
            academic.close();
            throw error;
        }
    }

    public void publishSamples() throws Exception {
        academic.login(1,"LECTURER");
        var marks = new MarkService();
        marks.saveMark(new lk.ac.ruhuna.ftms.model.Mark(100,10,40.0,1));
        marks.saveMark(new lk.ac.ruhuna.ftms.model.Mark(100,11,80.0,1));
        academic.confirmedFixtureEligibility(100);
        var results = new ResultService();
        results.publishResult(10,results.calculateAndSaveResult(100).publicationSnapshot());
        // A published result belonging to the other student must remain private.
        academic.execute("INSERT INTO course_results(enrollment_id,grade_letter,grade_point,result_status,published) VALUES (101,'B',3,'PASS',TRUE)");
        // An actual draft in a different semester must never become a student result row.
        academic.execute("INSERT INTO course_results(enrollment_id,grade_letter,grade_point,result_status,published) VALUES (200,'B',3,'PASS',FALSE)");
    }
    public void close() throws Exception { academic.close(); }
}
