package lk.ac.ruhuna.ftms.dao;
import java.sql.*;
import java.util.*;
import lk.ac.ruhuna.ftms.database.DBConnection;
import lk.ac.ruhuna.ftms.model.CourseMaterial;
public class CourseMaterialDAO {
    public List<CourseMaterial> findByOffering(int offering) throws SQLException {
        var rows=new ArrayList<CourseMaterial>();
        try(var c=DBConnection.getConnection();var p=c.prepareStatement("SELECT m.material_id,m.offering_id,m.title,m.description,m.material_type,m.resource_path,m.uploaded_by,m.uploaded_at,CONCAT_WS(' ',u.first_name,u.last_name) FROM course_materials m LEFT JOIN users u ON u.user_id=m.uploaded_by WHERE m.offering_id=? ORDER BY m.uploaded_at DESC,m.material_id DESC")) {
            p.setInt(1,offering);
            try(var r=p.executeQuery()) {while(r.next()) rows.add(new CourseMaterial(r.getInt(1),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getInt(7),r.getTimestamp(8)==null?null:r.getTimestamp(8).toLocalDateTime(),r.getString(9),"Not checked"));}
        }
        return rows;
    }
    public void save(CourseMaterial m,int lecturer) throws SQLException {
        var identity=lk.ac.ruhuna.ftms.util.SessionManager.requireSession();
        var user=lk.ac.ruhuna.ftms.util.SessionManager.getCurrentUser();
        if(identity.userId()!=lecturer || user==null || !user.isActive() || !"LECTURER".equals(user.getRole()))
            throw new SecurityException("Material updates require the authenticated lecturer.");
        String sql=m.materialId()==0 ? """
            INSERT INTO course_materials(offering_id,title,description,material_type,resource_path,uploaded_by)
            SELECT ?,?,?,?,?,? WHERE EXISTS(SELECT 1 FROM lecturer_course_assignments a
            JOIN users u ON u.user_id=a.lecturer_id WHERE a.offering_id=? AND a.lecturer_id=? AND u.role='LECTURER' AND u.is_active=TRUE)
            """ : """
            UPDATE course_materials SET title=?,description=?,material_type=?,resource_path=?
            WHERE material_id=? AND offering_id=? AND uploaded_by=? AND EXISTS(SELECT 1 FROM lecturer_course_assignments a
            JOIN users u ON u.user_id=a.lecturer_id WHERE a.offering_id=? AND a.lecturer_id=? AND u.role='LECTURER' AND u.is_active=TRUE)
            """;
        try(var c=DBConnection.getConnection();var p=c.prepareStatement(sql)) {
            if(m.materialId()==0) {p.setInt(1,m.offeringId());p.setString(2,m.title());p.setString(3,m.description());p.setString(4,m.materialType());p.setString(5,m.resourcePath());p.setInt(6,lecturer);}
            else {p.setString(1,m.title());p.setString(2,m.description());p.setString(3,m.materialType());p.setString(4,m.resourcePath());p.setInt(5,m.materialId());p.setInt(6,m.offeringId());}
            if(m.materialId()==0) {p.setInt(7,m.offeringId());p.setInt(8,lecturer);}
            else {p.setInt(7,lecturer);p.setInt(8,m.offeringId());p.setInt(9,lecturer);}
            lk.ac.ruhuna.ftms.util.SessionManager.requireSession(identity);
            if(p.executeUpdate()!=1) throw new SecurityException("Material or course assignment is no longer available.");
        }
    }
}
