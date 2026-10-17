package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.util.SessionManager;
import java.util.*;

/** Explicit read-only integration runner. Never seeds accounts or persists marks/materials. */
public final class LecturerAccessSmokeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SessionManager.logout();
        denied(() -> new LecturerViewService());
        denied(() -> new MarkService().getAllMarks());
        denied(() -> new MarkService().saveMark(new Mark(1,1,10.0,1)));
        var users = new UserDAO().getAllUsers();
        for (var user : users) {
            if (!user.isActive()) continue;
            SessionManager.startSession(user);
            if (!"LECTURER".equals(user.getRole())) {
                denied(() -> new LecturerViewService().offerings());
                continue;
            }
            var service = new LecturerViewService();
            denied(() -> new EligibilityService().evaluateAndSaveEligibility(1,100,true,user.getUserId()));
            denied(() -> new AttendanceService().saveSession(null));
            denied(() -> new AttendanceService().saveAttendance(null));
            denied(() -> new MedicalService().approveMedical(1,user.getUserId(),""));
            denied(() -> new MedicalService().getAllMedicals());
            denied(() -> new GPAService().saveSemesterResult(null));
            denied(() -> new ResultService().calculateAndSaveResult(1,user.getUserId()));
            denied(() -> new ReportService().getStudentReport(1));
            var materials = new CourseMaterialService();
            var offerings = service.offerings();
            for (var other : new EligibilityDAO().findOfferings()) {
                if (offerings.stream().noneMatch(o -> o.offeringId()==other.offeringId())) {
                    for (String view : List.of("students","marks","eligibility","results","gpa","attendance","medical"))
                        denied(() -> service.load(view,other.offeringId()));
                    denied(() -> materials.list(other.offeringId()));
                }
            }
            for (var offering : offerings) {
                check(new LecturerCourseAssignmentDAO().isAssigned(user.getUserId(),offering.offeringId()),"assigned offering");
                for (String view : List.of("students","marks","eligibility","results","gpa","attendance","medical")) {
                    var table=service.load(view,offering.offeringId());
                    check(!table.columns().isEmpty(),view+" columns");
                    for (var row:table.rows()) check(row.size()==table.columns().size(),view+" row mapping");
                    if (view.equals("medical")) check(table.columns().equals(List.of("Registration","From_date","To_date","Status")),"medical privacy");
                }
                materials.list(offering.offeringId());
                var marks=service.load("marks",offering.offeringId());
                if(!marks.rows().isEmpty()) {
                    var r=marks.rows().getFirst();
                    for(double invalid: new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.parseDouble(r.get(5))+1}) {
                        invalid(() -> service.saveMark(offering.offeringId(),new Mark(Integer.parseInt(r.get(0)),Integer.parseInt(r.get(2)),invalid,Integer.parseInt(r.get(6)))));
                    }
                }
                invalid(() -> materials.save(offering.offeringId(),0,"","","OTHER",null));
                invalid(() -> materials.save(offering.offeringId(),0,"Title","","OTHER",null));
            }
            denied(() -> service.load("students",Integer.MAX_VALUE));
            denied(() -> materials.list(Integer.MAX_VALUE));
            denied(() -> new MarkService().deleteMark(1));
            denied(() -> new MarkService().getAllMarks());
            // Re-login as the same user must invalidate services created in the earlier session.
            SessionManager.startSession(user);
            denied(service::offerings);
            checks++;
        }
        SessionManager.logout();
        System.out.println("Lecturer read-only integration: "+checks+" checks passed; no database writes.");
    }
    private interface Work { void run() throws Exception; }
    private static void denied(Work work) throws Exception {
        try {work.run();throw new AssertionError("Expected access denial");}
        catch(SecurityException expected){checks++;}
    }
    private static void invalid(Work work) throws Exception {
        try {work.run();throw new AssertionError("Expected validation failure");}
        catch(IllegalArgumentException expected){checks++;}
        catch(lk.ac.ruhuna.ftms.exception.InvalidMarkException expected){checks++;}
    }
    private static void check(boolean condition,String description) {
        if(!condition)throw new AssertionError(description);checks++;
    }
}
