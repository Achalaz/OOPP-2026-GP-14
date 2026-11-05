package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.TimetableDAO.TimetableInput;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Explicit runner: uses real MySQL transactions, but writes temporary fixture tables only. */
public final class TimetableIntegrationTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        String before = TimetableDatabaseFixture.productionFingerprint();
        try (var f = new TimetableDatabaseFixture()) {
            login(3,"STUDENT");
            var emptySchedule = f.student().getMyTimetableData();
            check(emptySchedule.enrolledOfferingCount()==2 && emptySchedule.entries().isEmpty(),
                    "enrolled student without scheduled entries is distinguishable from missing enrollment");
            login(1,"ADMIN"); var admin = f.admin();
            check(admin.getAllTimetable().isEmpty(), "empty timetable");
            check(admin.getAllOfferings().size()==5, "all offerings including historical");
            check(admin.getSchedulableOfferings().size()==4, "cancelled offering excluded from scheduling");
            check(admin.getAssignedLecturers().size()==3, "actual assignments deduplicated across assignment types");
            int id = admin.createTimetableEntry(input(10,"MONDAY","09:00","10:00","LT-1",4,null,null));
            check(id>0 && f.count()==1, "create committed and generated ID returned");
            check(f.admin().getOfferingTimetable(10).getFirst().timetableId()==id, "committed create reloaded by fresh service");
            check(admin.getAllTimetable().getFirst().lecturerName().equals("Timetable Fixture"), "lecturer name joins users");
            conflict(() -> admin.createTimetableEntry(input(10,"MONDAY","09:15","09:45",null,null,null,null)), "same course offering");
            conflict(() -> admin.createTimetableEntry(input(13,"MONDAY","09:00","10:00",null,4,null,null)), "lecturer #4");
            conflict(() -> admin.createTimetableEntry(input(20,"MONDAY","09:00","10:00"," lt-1 ",5,null,null)), "room lt-1");
            check(f.count()==1, "course, lecturer and cross-department room conflicts rollback");
            int adjacent = admin.createTimetableEntry(input(10,"MONDAY","10:00","11:00","LT-1",4,null,null));
            check(f.count()==2, "adjacent times do not conflict");
            admin.updateTimetableEntry(id,input(10,"MONDAY","08:00","09:00","LT-2",4,null,null));
            check(f.admin().getOfferingTimetable(10).getFirst().startTime().equals(LocalTime.of(8,0)), "update committed and reloaded");
            admin.updateTimetableEntry(id,input(10,"MONDAY","08:00","09:00","LT-2",4,null,null));
            check(f.count()==2, "unchanged update excludes own entry");
            invalid(() -> admin.updateTimetableEntry(id,input(10,"MONDAY","10:00","11:00",null,null,null,null)));
            check(admin.getOfferingTimetable(10).getFirst().room().equals("LT-2"), "failed update preserves saved data");
            invalid(() -> admin.createTimetableEntry(input(12,"TUESDAY","09:00","10:00",null,null,null,null)));
            invalid(() -> admin.createTimetableEntry(input(10,"TUESDAY","09:00","10:00",null,5,null,null)));
            f.execute("UPDATE users SET is_active=0 WHERE user_id=4");
            invalid(() -> admin.createTimetableEntry(input(10,"TUESDAY","09:00","10:00",null,4,null,null)));
            f.execute("UPDATE users SET is_active=1 WHERE user_id=4");
            invalid(() -> admin.createTimetableEntry(input(10,"TUESDAY","10:00","09:00",null,null,null,null)));
            invalid(() -> admin.createTimetableEntry(input(10,"BAD","09:00","10:00",null,null,null,null)));
            LocalDate jan=LocalDate.of(2026,1,1), feb=LocalDate.of(2026,2,1);
            invalid(() -> admin.createTimetableEntry(input(10,"TUESDAY","09:00","10:00",null,null,feb,jan)));
            int dated = admin.createTimetableEntry(input(10,"TUESDAY","09:00","10:00",null,null,jan,jan.plusDays(10)));
            admin.createTimetableEntry(input(10,"TUESDAY","09:00","10:00",null,null,feb,null));
            invalid(() -> admin.createTimetableEntry(input(10,"TUESDAY","09:00","10:00",null,null,jan.plusDays(10),feb)));
            invalid(() -> admin.createTimetableEntry(input(10,"TUESDAY","09:00","10:00",null,null,null,null)));
            admin.createTimetableEntry(input(10,"TUESDAY","09:00","10:00",null,null,null,jan.minusDays(1)));
            check(f.count()==5, "disjoint dates and open date bounds handled; inclusive endpoints conflict");
            admin.deleteTimetableEntry(adjacent); admin.deleteTimetableEntry(dated);
            check(f.count()==3, "delete committed");
            invalid(() -> admin.deleteTimetableEntry(adjacent));
            // Seed historical cancelled entries directly into the temporary table, never the production table.
            f.execute("INSERT INTO timetable_entries (offering_id,day_of_week,start_time,end_time) VALUES (11,'FRIDAY','08:00','09:00'),(12,'FRIDAY','09:00','10:00'),(13,'FRIDAY','10:00','11:00'),(20,'FRIDAY','11:00','12:00')");
            var beforeStudentAccess = admin.getAllTimetable();
            login(2,"TECHNICAL_OFFICER"); var officer=f.officer();
            check(officer.getAuthorizedOfferings().size()==4, "officer dropdown includes all department statuses");
            check(officer.getDepartmentTimetable().stream().noneMatch(e -> e.offeringId()==20), "officer department isolation");
            for (int offering : List.of(10,11,12,13)) check(!officer.getOfferingTimetable(offering).isEmpty(), "filter offering "+offering);
            denied(() -> officer.getOfferingTimetable(20));
            f.execute("UPDATE users SET department_id=2 WHERE user_id=2");
            check(officer.getAuthorizedOfferings().size()==1 && officer.getDepartmentTimetable().size()==1, "department resolved on every request");
            f.execute("UPDATE users SET is_active=0 WHERE user_id=2"); denied(officer::getDepartmentTimetable);
            login(3,"STUDENT"); var student=f.student();
            var studentIdentity = SessionManager.requireSession();
            denied(f::admin);
            denied(() -> admin.createTimetableEntry(input(10,"SUNDAY","09:00","10:00",null,null,null,null)));
            denied(() -> admin.updateTimetableEntry(id,input(10,"MONDAY","08:00","09:00","STUDENT-EDIT",4,null,null)));
            denied(() -> admin.deleteTimetableEntry(id));
            check(SessionManager.isCurrentSession(studentIdentity), "denied writes preserve student session");
            var firstStudentData = student.getMyTimetableData();
            check(firstStudentData.enrolledOfferingCount()==2,"enrollment count deduplicates repeat attempts");
            check(student.getMyTimetable().size()==4, "enrolled/repeat only, no duplicated sessions for repeat attempts");
            check(student.getMyTimetable().stream().allMatch(e -> e.offeringId()==10 || e.offeringId()==11), "withdrawn/completed enrollments and other students excluded");
            login(6,"STUDENT");
            var secondStudentData = f.student().getMyTimetableData();
            check(secondStudentData.enrolledOfferingCount()==1, "second student's enrollment count is isolated");
            check(secondStudentData.entries().size()==1 && secondStudentData.entries().getFirst().offeringId()==20,
                    "second student cannot read the first student's enrolled timetable");
            denied(student::getMyTimetable);
            denied(student::getMyTimetableData);
            login(3,"STUDENT");
            check(f.student().getMyTimetableData().equals(firstStudentData), "returning student reloads only their own timetable");
            login(1,"ADMIN");
            check(f.admin().getAllTimetable().equals(beforeStudentAccess), "student reads and denied writes leave every timetable entry unchanged");
            login(7,"STUDENT"); check(f.student().getMyTimetable().isEmpty(), "student without enrollments");
            check(f.student().getMyTimetableData().enrolledOfferingCount()==0,"empty enrollment is reported accurately");
            denied(student::getMyTimetable);
            f.execute("UPDATE users SET is_active=0 WHERE user_id=7"); denied(f.student()::getMyTimetable);
            login(1,"ADMIN"); var finalAdmin=f.admin(); int count=f.count();
            f.afterNextWrite(SessionManager::logout);
            denied(() -> finalAdmin.createTimetableEntry(input(10,"SUNDAY","09:00","10:00",null,null,null,null)));
            check(f.count()==count, "logout before commit rolls back write");
            login(1,"ADMIN"); var cancelled=f.admin();
            f.afterNextWrite(() -> Thread.currentThread().interrupt());
            try { cancelled.createTimetableEntry(input(10,"SUNDAY","09:00","10:00",null,null,null,null)); throw new AssertionError("Cancellation ignored"); }
            catch (java.util.concurrent.CancellationException expected) { checks++; }
            finally { Thread.interrupted(); }
            check(f.count()==count, "cancelled write rolls back");
            f.execute("UPDATE users SET is_active=0 WHERE user_id=1"); denied(cancelled::getAllTimetable);
        } finally { SessionManager.logout(); }
        check(before.equals(TimetableDatabaseFixture.productionFingerprint()), "all production table contents unchanged");
        System.out.println("Timetable MySQL integration: " + checks + " checks passed; production data unchanged.");
    }
    private static void login(int id,String role) { TimetableDatabaseFixture.login(id,role); }
    private static TimetableInput input(int offering,String day,String start,String end,String room,Integer lecturer,LocalDate from,LocalDate to) {
        return new TimetableInput(offering,day,LocalTime.parse(start),LocalTime.parse(end),room,"LECTURE",lecturer,from,to);
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); checks++; }
    private interface Action { void run(); }
    private static void invalid(Action action) { try { action.run(); throw new AssertionError("Expected validation failure"); } catch(IllegalArgumentException expected) { checks++; } }
    private static void conflict(Action action,String reason) {
        try { action.run(); throw new AssertionError("Expected schedule conflict"); }
        catch(IllegalArgumentException expected) { check(expected.getMessage().contains(reason)
                && expected.getMessage().contains("MONDAY") && expected.getMessage().contains("Choose a different time"), "conflict explains "+reason); }
    }
    private static void denied(Action action) { try { action.run(); throw new AssertionError("Expected access denial"); } catch(SecurityException expected) { checks++; } }
}
