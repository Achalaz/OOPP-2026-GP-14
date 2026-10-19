package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.dao.ReportDAO.*;
import lk.ac.ruhuna.ftms.model.*;
import java.sql.*;
import java.util.*;

public class ReportService {
    private final ReportDAO dao=new ReportDAO();
    private final AcademicAccessDAO access=new AcademicAccessDAO();
    private int lecturer(User actor) {
        if("LECTURER".equals(actor.getRole()))return actor.getUserId();
        if("STUDENT".equals(actor.getRole()))return 0;
        throw new SecurityException("Academic reports require an active lecturer or student.");
    }
    public List<StudentOption> getStudents()throws SQLException {
        return AcademicTransaction.run((c,id)->{
            var actor=access.actor(c,id);int l=lecturer(actor);return dao.students(c,l,l==0?id.userId():0);
        });
    }
    public List<OfferingOption> getCourseOfferings()throws SQLException {
        return AcademicTransaction.run((c,id)->{
            int l=lecturer(access.actor(c,id));return l==0?List.of():dao.offerings(c,l);
        });
    }
    public List<Integer> getBatchYears()throws SQLException {
        return AcademicTransaction.run((c,id)->{
            int l=lecturer(access.actor(c,id));return l==0?List.of():dao.batches(c,l);
        });
    }
    public List<ResultReportRow> getStudentReport(int student)throws SQLException {
        if(student<=0)throw new IllegalArgumentException("Select a student.");
        return AcademicTransaction.run((c,id)->{
            int l=lecturer(access.actor(c,id));
            if(l==0 && student!=id.userId())throw new SecurityException("Students may view only their own results.");
            return dao.report(c,student,0,0,l);
        });
    }
    public List<ResultReportRow> getOfferingReport(int offering)throws SQLException {
        return AcademicTransaction.run((c,id)->{access.offering(c,id,offering);return dao.report(c,0,offering,0,id.userId());});
    }
    public List<ResultReportRow> getBatchReport(int intake)throws SQLException {
        if(intake<=0)throw new IllegalArgumentException("Select an intake year.");
        return AcademicTransaction.run((c,id)->{
            int l=lecturer(access.actor(c,id));if(l==0)throw new SecurityException("Students cannot view batch reports.");
            return dao.report(c,0,0,intake,l);
        });
    }
    public List<MarkDetail> getMarkDetails(int enrollment)throws SQLException {
        return AcademicTransaction.run((c,id)->{
            int l=lecturer(access.actor(c,id));return dao.details(c,enrollment,l,l==0?id.userId():0);
        });
    }
    public int countTotalResults(List<ResultReportRow> rows){return rows==null?0:rows.size();}
    public int countPassed(List<ResultReportRow> rows){return count(rows,"PASS");}
    public int countFailed(List<ResultReportRow> rows){return count(rows,"FAIL");}
    public int countPending(List<ResultReportRow> rows){
        return rows==null?0:(int)rows.stream().filter(r->r.getResultStatus()==null
            || List.of("PENDING","INCOMPLETE").contains(r.getResultStatus())).count();
    }
    private int count(List<ResultReportRow> rows,String status){
        return rows==null?0:(int)rows.stream().filter(r->status.equals(r.getResultStatus())).count();
    }
    public Double calculateAverageOverallMark(List<ResultReportRow> rows){
        if(rows==null)return null;
        var values=rows.stream().filter(r->List.of("PASS","FAIL").contains(Objects.toString(r.getResultStatus(),"")))
            .map(ResultReportRow::getOverallMark).filter(Objects::nonNull).toList();
        if(values.isEmpty())return null;
        java.math.BigDecimal sum=java.math.BigDecimal.ZERO;
        for(double value:values)sum=sum.add(java.math.BigDecimal.valueOf(value));
        return sum.divide(java.math.BigDecimal.valueOf(values.size()),2,java.math.RoundingMode.HALF_UP).doubleValue();
    }
}