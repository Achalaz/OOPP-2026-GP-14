package lk.ac.ruhuna.ftms.service;
import java.nio.file.*;
import java.util.*;
import lk.ac.ruhuna.ftms.dao.CourseMaterialDAO;
import lk.ac.ruhuna.ftms.model.*;
import lk.ac.ruhuna.ftms.util.SessionManager;

/** Uses real files in a temporary workspace and an in-memory DAO. No production writes. */
public final class CourseMaterialServiceTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        if(Arrays.asList(args).contains("--mysql-read")) { mysqlReadOnly();return; }
        SessionManager.startSession(new User(7,"test","Lecturer","l@example.org","LECTURER",true));
        var identity=SessionManager.requireSession();
        var access=new LecturerAccessService(){
            @Override public int requireLecturer(){SessionManager.requireSession(identity);return 7;}
            @Override public void requireOffering(int id){requireLecturer();if(id!=12)throw new SecurityException("Not assigned");}
        };
        var dao=new MemoryDAO();
        Path temp=Files.createTempDirectory(Path.of("target"),"materials-test-").toAbsolutePath();
        try {
            Path source=temp.resolve("note.pdf");Files.writeString(source,"%PDF-1.4\nCourse note\n%%EOF");
            byte[] original=Files.readAllBytes(source);
            Path store=temp.resolve("store");
            var service=new CourseMaterialService(access,dao,store);
            service.save(12,0,"  Notes  ","Week one","LECTURE_NOTE",source);
            check(dao.saved.title().equals("Notes"),"trimmed title");
            check(!dao.saved.resourcePath().contains("note"),"managed resource name");
            check(dao.saved.uploadedBy()==7,"authenticated uploader");
            check(Arrays.equals(service.download(12,1),original),"PDF download matches upload");
            check(Arrays.equals(Files.readAllBytes(source),original),"source preserved");
            check(Files.isRegularFile(store.resolve(dao.saved.resourcePath())),"managed file exists");
            check(service.list(12).getFirst().availability().equals("Available"),"file availability");
            Path exported=temp.resolve("export.pdf");Files.writeString(exported,"Previous destination");
            service.export(12,1,exported);
            check(Arrays.equals(Files.readAllBytes(exported),original),"export bytes match PDF");
            reject(()->service.export(12,1,store.resolve(dao.saved.resourcePath())));
            service.save(12,1,"Edited","New description","PRACTICAL",null);
            check(Arrays.equals(service.download(12,1),original),"metadata edit preserves resource");
            check(dao.saved.materialId()==1 && dao.saved.description().equals("New description")
                    && dao.saved.materialType().equals("PRACTICAL"),"edit preserves ID and saves metadata");
            Files.writeString(source,"Replacement");service.save(12,1,"Edited","","PRACTICAL",source);
            check(new String(service.download(12,1)).equals("Replacement"),"file replacement");
            var own=dao.saved;
            dao.saved=new CourseMaterial(1,12,"Other uploader","","OTHER",own.resourcePath(),8);
            check(new String(service.download(12,1)).equals("Replacement"),"assigned co-lecturer may download");
            reject(()->service.save(12,1,"Stolen","","OTHER",null));
            reject(()->service.save(12,1,"Stolen","","OTHER",source));
            check(dao.saved.uploadedBy()==8,"ownership remains intact");
            dao.saved=own;
            reject(()->service.download(12,999));
            reject(()->service.save(12,999,"Missing","","OTHER",null));
            reject(()->service.save(12,1,"Title","x".repeat(10001),"OTHER",null));
            reject(()->service.list(13));
            reject(()->service.download(13,1));
            reject(()->service.save(13,0,"Title","","OTHER",source));
            reject(()->service.save(12,1,"","","OTHER",null));
            reject(()->service.save(12,1,"a".repeat(181),"","OTHER",null));
            reject(()->service.save(12,1,"Title","",null,null));
            reject(()->service.save(12,0,"Title","","OTHER",null));
            Path executable=temp.resolve("bad.exe");Files.writeString(executable,"bad");
            reject(()->service.save(12,0,"Title","","OTHER",executable));
            Path empty=temp.resolve("empty.txt");Files.createFile(empty);
            reject(()->service.save(12,0,"Title","","OTHER",empty));
            Path oversized=temp.resolve("large.txt");
            try(var file=new java.io.RandomAccessFile(oversized.toFile(),"rw")){file.setLength(20L*1024*1024+1);}
            reject(()->service.save(12,0,"Title","","OTHER",oversized));
            for(String unsafe:List.of("../note.txt",source.toString(),"https://example.org/note.pdf","materials/oop/intro.pdf")) {
                dao.saved=new CourseMaterial(1,12,"Unsafe","","OTHER",unsafe,7);
                reject(()->service.download(12,1));
                check(service.list(12).getFirst().availability().startsWith("Legacy"),"legacy status");
            }
            dao.saved=new CourseMaterial(1,12,"Missing","","OTHER",UUID.randomUUID()+".pdf",7);
            reject(()->service.download(12,1));
            reject(()->service.export(12,1,exported));
            check(Arrays.equals(Files.readAllBytes(exported),original),"failed export preserves destination");
            dao.saved=own;
            SessionManager.logout();reject(()->service.list(12));
            reject(()->service.download(12,1));
            reject(()->service.save(12,1,"Expired","","OTHER",null));
            reject(()->service.export(12,1,exported));
            SessionManager.startSession(new User(7,"test","Lecturer","l@example.org","LECTURER",true));
            reject(()->service.list(12));
            System.out.println("Course material service/file checks passed: "+checks);
        } finally {
            SessionManager.logout();
            try(var paths=Files.walk(temp)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
    /** Explicit opt-in: SELECT statements only, including a before/after row snapshot. */
    private static void mysqlReadOnly() throws Exception {
        var before=databaseSnapshot();
        try {
            for(var user:new lk.ac.ruhuna.ftms.dao.UserDAO().getAllUsers()) {
                if(!user.isActive() || !"LECTURER".equals(user.getRole()))continue;
                SessionManager.startSession(user);
                var service=new CourseMaterialService();
                var legacy=new CourseMaterialsService(new lk.ac.ruhuna.ftms.dao.CourseMaterialsDAO(),
                        new lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO());
                var offerings=new LecturerViewService().offerings();
                for(var offering:offerings) {
                    for(var material:service.list(offering.offeringId())) {
                        check(material.offeringId()==offering.offeringId(),"live offering scope");
                        check(material.uploadedAt()!=null,"live upload timestamp");
                        if(material.uploadedBy()!=user.getUserId())
                            reject(()->service.save(offering.offeringId(),material.materialId(),"Denied","","OTHER",null));
                        if(material.availability().startsWith("Legacy"))
                            reject(()->service.download(offering.offeringId(),material.materialId()));
                    }
                }
                reject(()->service.list(Integer.MAX_VALUE));
                reject(()->service.save(Integer.MAX_VALUE,0,"Denied","","OTHER",null));
                for(var material:legacy.getMyMaterials()) {
                    check(material.getUploadedBy()==user.getUserId(),"legacy authenticated uploader scope");
                    check(offerings.stream().anyMatch(o->o.offeringId()==material.getOfferingId()),"legacy assigned offering scope");
                }
                SessionManager.logout();reject(()->service.list(1));
                reject(legacy::getMyMaterials);
            }
            check(before.equals(databaseSnapshot()),"all live material records unchanged");
            System.out.println("MySQL read-only materials checks passed: "+checks+"; rows preserved: "+before.size());
        } finally {SessionManager.logout();}
    }
    private static List<List<String>> databaseSnapshot() throws Exception {
        var snapshot=new ArrayList<List<String>>();
        try(var c=lk.ac.ruhuna.ftms.database.DBConnection.getConnection();
            var p=c.prepareStatement("SELECT material_id,offering_id,title,description,material_type,resource_path,uploaded_by,uploaded_at FROM course_materials ORDER BY material_id");
            var r=p.executeQuery()) {
            while(r.next()) {
                var row=new ArrayList<String>();
                for(int i=1;i<=8;i++)row.add(r.getString(i));snapshot.add(row);
            }
        }
        return snapshot;
    }
    private static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);checks++;}
    private interface Work {void run() throws Exception;}
    private static void reject(Work work)throws Exception{
        try{work.run();throw new AssertionError("Expected rejection");}
        catch(SecurityException|IllegalArgumentException|java.io.IOException expected){checks++;}
    }
    private static final class MemoryDAO extends CourseMaterialDAO {
        CourseMaterial saved;
        @Override public List<CourseMaterial> findByOffering(int id){return saved==null?List.of():List.of(saved);}
        @Override public void save(CourseMaterial m,int lecturer){saved=new CourseMaterial(1,m.offeringId(),m.title(),m.description(),m.materialType(),m.resourcePath(),lecturer);}
    }
}
