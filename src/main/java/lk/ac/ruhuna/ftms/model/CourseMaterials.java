package lk.ac.ruhuna.ftms.model;

import java.time.LocalDateTime;

public class CourseMaterials {

    private int materialId;
    private int offeringId;
    private String title;
    private String description;
    private String materialType;
    private String resourcePath;
    private int uploadedBy;
    private LocalDateTime uploadedAt;

    public CourseMaterials() {
    }

    public CourseMaterials(
            int offeringId,
            String title,
            String description,
            String materialType,
            String resourcePath,
            int uploadedBy) {

        this.offeringId = offeringId;
        this.title = title;
        this.description = description;
        this.materialType = materialType;
        this.resourcePath = resourcePath;
        this.uploadedBy = uploadedBy;
    }

    public int getMaterialId() {
        return materialId;
    }

    public void setMaterialId(int materialId) {
        this.materialId = materialId;
    }

    public int getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(int offeringId) {
        this.offeringId = offeringId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMaterialType() {
        return materialType;
    }

    public void setMaterialType(String materialType) {
        this.materialType = materialType;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public int getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(int uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
