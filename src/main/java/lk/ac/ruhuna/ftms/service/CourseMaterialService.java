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
    private static final long LIMIT=20L*1024*1024;
    private final Path root;
    public CourseMaterialService() {
        this(new LecturerAccessService(), new CourseMaterialDAO(), Path.of(System.getProperty("ftms.materials.dir",
                Path.of(System.getProperty("user.home"),".ftms","course-materials").toString())));
    }
    public CourseMaterialService(LecturerAccessService access, CourseMaterialDAO dao, Path root) {
        this.access=Objects.requireNonNull(access);
        this.dao=Objects.requireNonNull(dao);
        this.root=Objects.requireNonNull(root).toAbsolutePath().normalize();
    }
    public List<CourseMaterial> list(int offering) throws SQLException {
        access.requireOffering(offering);var rows=dao.findByOffering(offering);access.requireOffering(offering);return rows;
    }
    public void save(int offering,int id,String title,String description,String type,Path source) throws SQLException,IOException {
        access.requireOffering(offering);
        if(title==null || title.isBlank() || title.strip().length()>180) throw new IllegalArgumentException("Enter a title of 1–180 characters.");
        if(description!=null && description.length()>10000) throw new IllegalArgumentException("Description is too long (maximum 10000 characters).");
        if(type==null || !Set.of("LECTURE_NOTE","PRACTICAL","ASSIGNMENT","OTHER").contains(type)) throw new IllegalArgumentException("Select a file material type.");
        CourseMaterial existing=id==0?null:find(offering,id);
        if(source==null && existing==null) throw new IllegalArgumentException("Choose a file to upload.");
        String stored=existing==null?null:existing.resourcePath();
        if(source!=null) {
            String original=source.getFileName().toString();int dot=original.lastIndexOf('.');
            String ext=dot<0?"":original.substring(dot).toLowerCase(Locale.ROOT);
            if(!Set.of(".pdf",".txt",".png",".jpg",".jpeg",".pptx",".docx",".xlsx",".zip").contains(ext)) throw new IllegalArgumentException("Supported files: PDF, TXT, images, Office documents and ZIP.");
            if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS) || Files.size(source)>LIMIT) throw new IllegalArgumentException("Choose a regular file up to 20 MiB.");
            byte[] bytes;try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes((int)LIMIT+1);}
            if(bytes.length==0 || bytes.length>LIMIT) throw new IllegalArgumentException("File must be nonempty and at most 20 MiB.");
            Files.createDirectories(root);checkRoot();
            stored=UUID.randomUUID()+ext;
            Files.write(root.resolve(stored),bytes,StandardOpenOption.CREATE_NEW);
        }
        access.requireOffering(offering);
        // Retain staged files on uncertain database outcomes; never delete a possibly committed resource.
        dao.save(new CourseMaterial(id,offering,title.strip(),description,type,stored,access.requireLecturer()),access.requireLecturer());
    }
    public byte[] download(int offering,int id) throws SQLException,IOException {
        CourseMaterial material=find(offering,id);String name=material.resourcePath();
        if(name==null || !name.matches("[0-9a-f-]{36}\\.[a-z0-9]+")) throw new IOException("Legacy/external resource is not in managed storage. Ask its uploader to upload the file.");
        checkRoot();Path path=root.resolve(name);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>LIMIT) throw new IOException("Material file is unavailable.");
        byte[] bytes;try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes((int)LIMIT+1);}
        if(bytes.length>LIMIT) throw new IOException("Material exceeds download limit.");
        access.requireOffering(offering);return bytes;
    }
    public void export(int offering,int id,Path destination) throws SQLException,IOException {
        byte[] bytes=download(offering,id);
        access.requireOffering(offering);
        Files.write(destination,bytes,LinkOption.NOFOLLOW_LINKS,StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE);
    }
    private CourseMaterial find(int offering,int id) throws SQLException {
        return list(offering).stream().filter(m->m.materialId()==id).findFirst().orElseThrow(()->new SecurityException("Material is not available in this offering."));
    }
    private void checkRoot() throws IOException {
        if(!root.toRealPath().equals(root)) throw new IOException("Material storage must not use redirected directories.");
    }
}
