package lk.ac.ruhuna.ftms.model;
public record CourseMaterial(int materialId,int offeringId,String title,String description,
                             String materialType,String resourcePath,int uploadedBy,
                             java.time.LocalDateTime uploadedAt,String uploaderName,String availability,
                             java.time.LocalDate availableFrom,java.time.LocalDate expiresOn) {
    public CourseMaterial(int id,int offering,String title,String description,String type,String path,int uploader,
                          java.time.LocalDateTime uploadedAt,String uploaderName,String availability) {
        this(id,offering,title,description,type,path,uploader,uploadedAt,uploaderName,availability,null,null);
    }
    public CourseMaterial(int id,int offering,String title,String description,String type,String path,int uploader) {
        this(id,offering,title,description,type,path,uploader,null,null,"Not checked",null,null);
    }
    public CourseMaterial withAvailability(String status) {
        return new CourseMaterial(materialId,offeringId,title,description,materialType,resourcePath,
                uploadedBy,uploadedAt,uploaderName,status,availableFrom,expiresOn);
    }
    public boolean isAvailableOn(java.time.LocalDate date) {
        return (availableFrom==null || !date.isBefore(availableFrom))
                && (expiresOn==null || !date.isAfter(expiresOn));
    }
    @Override public String toString() { return title + " (" + materialType + ")"; }
}
