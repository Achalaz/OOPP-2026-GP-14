package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;

/** Actual MySQL SQL against connection-local tables copied from the live schema. No production writes. */
public final class AcademicDatabaseFixture implements AutoCloseable {
    private static final List<String> TABLES=List.of("users","students","lecturers","academic_years","semesters","courses",
        "course_offerings","lecturer_course_assignments","enrollments","assessments","marks","eligibility_records",
        "grade_rules","course_results","semester_results","attendance_sessions","attendance_records");
    final Connection connection;
    private final List<Driver> originalDrivers;
    private final Driver driver;
    private String failSql;
    private Runnable afterWrite;
    private int connectionsOpened;
    public AcademicDatabaseFixture()throws Exception {
        connection=DBConnection.getConnection();
        for(String table:TABLES) {
            String ddl;
            try(var s=connection.createStatement();var r=s.executeQuery("SHOW CREATE TABLE "+table)){
                r.next();ddl=r.getString(2).replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE")
                    .replaceAll("(?m)^  CONSTRAINT .*FOREIGN KEY.*\\R","").replaceAll(",\\s*\\) ENGINE","\n) ENGINE");
            }
            try(var s=connection.createStatement()){s.execute(ddl);}
        }
        // Exercise the feature before deployment without altering the live assessments table.
        // Every table above is connection-local; this migration targets only its temporary copy.
        boolean publicationColumns;
        try(var s=connection.createStatement();var r=s.executeQuery("SHOW COLUMNS FROM assessments LIKE 'is_published'")) {
            publicationColumns=r.next();
        }
        if(!publicationColumns) {
            String migration=java.nio.file.Files.readString(java.nio.file.Path.of("docs/database/add_assessment_publication.sql"));
            try(var s=connection.createStatement()){s.execute(migration);}
        }
        seed();
        Connection borrowed=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("close")){
                if(!connection.getAutoCommit()){connection.rollback();connection.setAutoCommit(true);}return null;
            }
            try {
                Object result=method.invoke(connection,args);
                if(method.getName().equals("prepareStatement")){
                    String sql=args[0].toString();var statement=(PreparedStatement)result;
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,operation,parameters)->{
                        try {
                            Object value=operation.invoke(statement,parameters);
                            if(operation.getName().equals("executeUpdate")){
                                if(afterWrite!=null){var callback=afterWrite;afterWrite=null;callback.run();}
                                if(failSql!=null && sql.contains(failSql)){failSql=null;throw new SQLException("Injected failure after database write");}
                            }return value;
                        }catch(InvocationTargetException error){throw error.getCause();}
                    });
                }return result;
            }catch(InvocationTargetException error){throw error.getCause();}
        });
        originalDrivers=Collections.list(DriverManager.getDrivers());
        for(var d:originalDrivers)DriverManager.deregisterDriver(d);
        driver=new Driver(){
            public Connection connect(String url,Properties p){
                if (!url.startsWith("jdbc:mysql:")) return null;
                connectionsOpened++;
                return borrowed;
            }
            public boolean acceptsURL(String url){return url.startsWith("jdbc:mysql:");}
            public DriverPropertyInfo[] getPropertyInfo(String u,Properties p){return new DriverPropertyInfo[0];}
            public int getMajorVersion(){return 1;}public int getMinorVersion(){return 0;}
            public boolean jdbcCompliant(){return false;}public Logger getParentLogger(){return Logger.getGlobal();}
        };
        DriverManager.registerDriver(driver);
    }
    public void login(int id,String role){SessionManager.startSession(new User(id,"fixture","Fixture",null,role,true));}
    public void failAfterWrite(String fragment){failSql=fragment;}
    public void afterWrite(Runnable action){afterWrite=action;}
    public int connectionsOpened(){return connectionsOpened;}
    public void execute(String sql)throws SQLException{
        try(var s=connection.createStatement()){s.executeUpdate(sql);}
        if(!connection.getAutoCommit())connection.commit();
    }
    public Object scalar(String sql)throws SQLException{
        try(var s=connection.createStatement();var r=s.executeQuery(sql)){r.next();return r.getObject(1);}
    }
    public void confirmedFixtureEligibility(int enrollment)throws SQLException {
        execute("INSERT INTO eligibility_records(enrollment_id,ca_score,ca_eligible,attendance_percentage,attendance_eligible,overall_eligible,calculated_by) "+
            "VALUES("+enrollment+",80,TRUE,100,TRUE,TRUE,1) ON DUPLICATE KEY UPDATE ca_score=80,ca_eligible=TRUE,attendance_percentage=100,"+
            "attendance_eligible=TRUE,overall_eligible=TRUE,calculated_at=CURRENT_TIMESTAMP,calculated_by=1");
    }
    private void seed()throws SQLException{
        for(int id=1;id<=6;id++){
            String role=id==1||id==2?"LECTURER":id==3||id==4?"STUDENT":id==5?"ADMIN":"TECHNICAL_OFFICER";
            try(var p=connection.prepareStatement("INSERT INTO users(user_id,username,password_hash,role,first_name,last_name,is_active) VALUES(?,?,?,?,'Test','Fixture',TRUE)")){
                p.setInt(1,id);p.setString(2,"academic-test-"+id);p.setString(3,UUID.randomUUID().toString());p.setString(4,role);p.executeUpdate();
            }
        }
        execute("INSERT INTO students(student_id,registration_no,intake_year) VALUES(3,'TEST/3',2024),(4,'TEST/4',2025)");
        execute("INSERT INTO lecturers(lecturer_id,staff_no) VALUES(1,'TEST/L1'),(2,'TEST/L2')");
        execute("INSERT INTO academic_years(academic_year_id,academic_year_name,start_date) VALUES(9,'2024/25','2024-01-01'),(1,'2025/26','2025-01-01')");
        execute("INSERT INTO semesters(semester_id,academic_year_id,semester_no) VALUES(9,9,1),(1,1,1)");
        execute("INSERT INTO courses(course_id,course_code,course_name,credits,department_id) VALUES(1,'TEST-A','Test A',3,1),(2,'TEST-B','Test B',2,1),(3,'TEST-C','Test C',1,1)");
        execute("INSERT INTO course_offerings(offering_id,course_id,semester_id,status) VALUES(10,1,9,'ACTIVE'),(20,2,1,'ACTIVE'),(30,3,1,'ACTIVE')");
        execute("INSERT INTO lecturer_course_assignments(offering_id,lecturer_id,assignment_type) VALUES(10,1,'PRIMARY'),(10,1,'ASSISTANT'),(20,1,'PRACTICAL'),(30,2,'PRIMARY')");
        execute("INSERT INTO enrollments(enrollment_id,offering_id,student_id) VALUES(100,10,3),(101,10,4),(200,20,3),(300,30,4)");
        execute("INSERT INTO assessments(assessment_id,offering_id,assessment_name,assessment_type,max_mark,weight_percentage) VALUES(10,10,'CA','QUIZ',50,40),(11,10,'Exam','FINAL_EXAM',100,60),(20,20,'Exam','FINAL_EXAM',100,100),(30,30,'Exam','FINAL_EXAM',100,100)");
        execute("INSERT INTO grade_rules(grade_rule_id,grade_letter,min_mark,max_mark,grade_point,is_pass) VALUES(1,'F',0,49.99,0,FALSE),(2,'B',50,79.99,3,TRUE),(3,'A',80,100,4,TRUE)");
        execute("INSERT INTO attendance_sessions(session_id,offering_id,session_date,session_type) VALUES(1,10,'2026-01-01','THEORY'),(2,10,'2026-01-02','PRACTICAL'),(3,20,'2026-01-01','THEORY')");
        execute("INSERT INTO attendance_records(session_id,enrollment_id,attendance_status) VALUES(1,100,'PRESENT'),(2,100,'PRESENT'),(1,101,'ABSENT'),(2,101,'ABSENT'),(3,200,'PRESENT')");
    }
    public void close()throws SQLException{
        SessionManager.logout();DriverManager.deregisterDriver(driver);
        for(var d:originalDrivers)DriverManager.registerDriver(d);
        connection.close();
    }
}
