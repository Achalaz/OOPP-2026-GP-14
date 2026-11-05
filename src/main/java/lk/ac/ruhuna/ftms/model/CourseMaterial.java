package lk.ac.ruhuna.ftms.model;
public record CourseMaterial(int materialId,int offeringId,String title,String description,
                             String materialType,String resourcePath,int uploadedBy,
                             java.time.LocalDateTime uploadedAt,String uploaderName,String availability) {
    public CourseMaterial(int id,int offering,String title,String description,String type,String path,int uploader) {
        this(id,offering,title,description,type,path,uploader,null,null,"Not checked");
    }
    public CourseMaterial withAvailability(String status) {
        return new CourseMaterial(materialId,offeringId,title,description,materialType,resourcePath,
                uploadedBy,uploadedAt,uploaderName,status);
    }
    @Override public String toString() { return title + " (" + materialType + ")"; }
}
