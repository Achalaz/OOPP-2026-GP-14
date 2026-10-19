package lk.ac.ruhuna.ftms.model;
public record CourseMaterial(int materialId,int offeringId,String title,String description,
                             String materialType,String resourcePath,int uploadedBy) {
    @Override public String toString() { return title + " (" + materialType + ")"; }
}
