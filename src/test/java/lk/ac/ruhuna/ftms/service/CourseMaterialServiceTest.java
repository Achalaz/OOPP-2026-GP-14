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
        SessionManager.startSession(new User(7,"test","Lecturer","l@example.org","LECTURER",true));
        var identity=SessionManager.requireSession();
        var access=new LecturerAccessService(){
            @Override public int requireLecturer(){SessionManager.requireSession(identity);return 7;}
            @Override public void requireOffering(int id){requireLecturer();if(id!=12)throw new SecurityException("Not assigned");}
        };
        var dao=new MemoryDAO();
        Path temp=Files.createTempDirectory(Path.of("target"),"materials-test-").toAbsolutePath();
        try {
            Path source=temp.resolve("note.txt");Files.writeString(source,"Course note");
            Path store=temp.resolve("store");
            var service=new CourseMaterialService(access,dao,store);
            service.save(12,0,"  Notes  ","Week one","LECTURE_NOTE",source);
            check(dao.saved.title().equals("Notes"),"trimmed title");
            check(!dao.saved.resourcePath().contains("note"),"managed resource name");
            check(dao.saved.uploadedBy()==7,"authenticated uploader");
            check(new String(service.download(12,1)).equals("Course note"),"download matches upload");
            check(Files.readString(source).equals("Course note"),"source preserved");
            service.save(12,1,"Edited","New description","PRACTICAL",null);
            check(new String(service.download(12,1)).equals("Course note"),"metadata edit preserves resource");
            Files.writeString(source,"Replacement");service.save(12,1,"Edited","","PRACTICAL",source);
            check(new String(service.download(12,1)).equals("Replacement"),"file replacement");
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
            for(String unsafe:List.of("../note.txt",source.toString(),"https://example.org/note.pdf")) {
                dao.saved=new CourseMaterial(1,12,"Unsafe","","OTHER",unsafe,7);
                reject(()->service.download(12,1));
            }
            SessionManager.logout();reject(()->service.list(12));
            System.out.println("Course material service/file checks passed: "+checks);
        } finally {
            SessionManager.logout();
            try(var paths=Files.walk(temp)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
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
