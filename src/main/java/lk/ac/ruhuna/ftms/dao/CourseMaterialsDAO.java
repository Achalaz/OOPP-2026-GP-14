package lk.ac.ruhuna.ftms.dao;

import java.sql.SQLException;
import java.util.List;
import lk.ac.ruhuna.ftms.model.CourseMaterials;
import lk.ac.ruhuna.ftms.service.CourseMaterialsService;
import lk.ac.ruhuna.ftms.service.LecturerAccessService;

/** Retained compatibility facade; no independent path-based writes or unscoped reads. */
public class CourseMaterialsDAO {
    private CourseMaterialsService service(int uploader) throws SQLException {
        if(new LecturerAccessService().requireLecturer()!=uploader)
            throw new SecurityException("Use the authenticated lecturer identity.");
        return new CourseMaterialsService(this,new LecturerCourseAssignmentDAO());
    }
    public List<CourseMaterials> getByUploader(int uploader) throws SQLException {
        return service(uploader).getMyMaterials();
    }
    public List<CourseMaterials> searchByUploader(int uploader,String keyword) throws SQLException {
        return service(uploader).searchMyMaterials(keyword);
    }
    public boolean save(CourseMaterials material) throws SQLException {
        if(material==null) throw new IllegalArgumentException("Material is required.");
        return service(material.getUploadedBy()).save(material);
    }
    public boolean delete(int materialId,int uploader) throws SQLException {
        return service(uploader).delete(materialId);
    }
}
