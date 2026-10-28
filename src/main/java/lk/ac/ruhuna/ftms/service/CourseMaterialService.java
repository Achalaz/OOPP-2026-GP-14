package lk.ac.ruhuna.ftms.service;
import java.io.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import lk.ac.ruhuna.ftms.dao.CourseMaterialDAO;
import lk.ac.ruhuna.ftms.model.CourseMaterial;
public class CourseMaterialService {
    private final LecturerAccessService access;
    private final CourseMaterialDAO dao;
    private final MaterialStorage storage;
    public CourseMaterialService() {
        this(new LecturerAccessService(), new CourseMaterialDAO(), MaterialStorage.configured());
    }
    public CourseMaterialService(LecturerAccessService access, CourseMaterialDAO dao, Path root) {
        this(access, dao, new MaterialStorage(root));
    }
    private CourseMaterialService(LecturerAccessService access, CourseMaterialDAO dao, MaterialStorage storage) {
        this.access=Objects.requireNonNull(access);
        this.dao=Objects.requireNonNull(dao);
        this.storage=storage;
    }
    public List<CourseMaterial> list(int offering) throws SQLException {
        access.requireOffering(offering);
        var rows=dao.findByOffering(offering).stream().map(m->m.withAvailability(storage.availability(m.resourcePath()))).toList();
        access.requireOffering(offering);return rows;
    }
    public void save(int offering,int id,String title,String description,String type,Path source) throws SQLException,IOException {
        access.requireOffering(offering);
        if(title==null || title.isBlank() || title.strip().length()>180) throw new IllegalArgumentException("Enter a title of 1–180 characters.");
        if(description!=null && description.length()>10000) throw new IllegalArgumentException("Description is too long (maximum 10000 characters).");
        if(type==null || !Set.of("LECTURE_NOTE","PRACTICAL","ASSIGNMENT","OTHER").contains(type)) throw new IllegalArgumentException("Select a file material type.");
        if(id<0) throw new IllegalArgumentException("Invalid material ID.");
        CourseMaterial existing=id==0?null:find(offering,id);
        if(existing!=null && existing.uploadedBy()!=access.requireLecturer())
            throw new SecurityException("Only the original uploader may edit this material.");
        if(source==null && existing==null) throw new IllegalArgumentException("Choose a file to upload.");
        String stored=existing==null?null:existing.resourcePath();
        if(source!=null) stored=storage.store(source);
        access.requireOffering(offering);
        // Retain staged files on uncertain database outcomes; never delete a possibly committed resource.
        dao.save(new CourseMaterial(id,offering,title.strip(),description,type,stored,access.requireLecturer()),access.requireLecturer());
    }
    public byte[] download(int offering,int id) throws SQLException,IOException {
        CourseMaterial material=find(offering,id);
        byte[] bytes=storage.read(material.resourcePath());
        access.requireOffering(offering);
        return bytes;
    }
    public void export(int offering,int id,Path destination) throws SQLException,IOException {
        byte[] bytes=download(offering,id);
        storage.export(bytes,destination,()->access.requireOffering(offering));
    }
    private CourseMaterial find(int offering,int id) throws SQLException {
        return list(offering).stream().filter(m->m.materialId()==id).findFirst()
                .orElseThrow(()->new SecurityException("Material is not available in this offering."));
    }
}
