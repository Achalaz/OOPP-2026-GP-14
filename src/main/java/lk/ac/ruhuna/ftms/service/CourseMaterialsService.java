package lk.ac.ruhuna.ftms.service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import lk.ac.ruhuna.ftms.dao.*;
import lk.ac.ruhuna.ftms.model.CourseMaterials;

/** Compatibility API. All reads and metadata edits use the managed-file authorization policy. */
public class CourseMaterialsService {
    private final LecturerAccessService access = new LecturerAccessService();
    private final CourseMaterialService primary = new CourseMaterialService();
    private final LecturerCourseAssignmentDAO assignments;
    public CourseMaterialsService(CourseMaterialsDAO ignored, LecturerCourseAssignmentDAO assignments) {
        Objects.requireNonNull(ignored);
        this.assignments=Objects.requireNonNull(assignments);
    }
    public List<CourseMaterials> getMyMaterials() throws SQLException {
        int lecturer=access.requireLecturer();
        var rows=new ArrayList<CourseMaterials>();
        for(var offering:assignments.findAssigned(lecturer)) {
            for(var m:primary.list(offering.offeringId())) {
                if(m.uploadedBy()!=lecturer) continue;
                var row=new CourseMaterials(m.offeringId(),m.title(),m.description(),m.materialType(),m.resourcePath(),m.uploadedBy());
                row.setMaterialId(m.materialId());row.setUploadedAt(m.uploadedAt());rows.add(row);
            }
        }
        access.requireLecturer();return rows;
    }
    public List<CourseMaterials> searchMyMaterials(String keyword) throws SQLException {
        String query=Objects.toString(keyword, "").strip().toLowerCase(Locale.ROOT);
        return getMyMaterials().stream().filter(m->(m.getTitle()+" "+Objects.toString(m.getDescription(), ""))
                .toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    public boolean save(CourseMaterials material) throws SQLException {
        access.requireLecturer();
        if(material==null || material.getMaterialId()<=0)
            throw new IllegalArgumentException("Use Choose File in Course Materials to upload a new material.");
        var existing=primary.list(material.getOfferingId()).stream()
                .filter(m->m.materialId()==material.getMaterialId()).findFirst()
                .orElseThrow(()->new SecurityException("Material is unavailable in this offering."));
        if(!Objects.equals(existing.resourcePath(),material.getResourcePath()))
            throw new IllegalArgumentException("Use Choose File to replace a managed file.");
        try {
            primary.save(material.getOfferingId(),material.getMaterialId(),material.getTitle(),
                    material.getDescription(),material.getMaterialType(),null);
            return true;
        } catch(IOException error) { throw new SQLException("Unable to save material metadata.",error); }
    }
    public boolean delete(int materialId) throws SQLException {
        access.requireLecturer();
        throw new SecurityException("Material deletion is not supported; existing records are retained.");
    }
}
