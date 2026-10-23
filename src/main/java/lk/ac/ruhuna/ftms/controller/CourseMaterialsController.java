
package lk.ac.ruhuna.ftms.controller;

import lk.ac.ruhuna.ftms.dao.CourseMaterialsDAO;
import lk.ac.ruhuna.ftms.dao.LecturerCourseAssignmentDAO;
import lk.ac.ruhuna.ftms.model.CourseMaterials;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.CourseMaterialsService;
import lk.ac.ruhuna.ftms.util.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CourseMaterialsController {

    @FXML
    private TableView<CourseMaterials> materialsTable;

    @FXML
    private TableColumn<CourseMaterials, Integer> offeringColumn;

    @FXML
    private TableColumn<CourseMaterials, String> titleColumn;

    @FXML
    private TableColumn<CourseMaterials, String> typeColumn;

    @FXML
    private TableColumn<CourseMaterials, String> descriptionColumn;

    @FXML
    private TableColumn<CourseMaterials, String> resourcePathColumn;

    @FXML
    private TableColumn<CourseMaterials, Object> uploadedAtColumn;

    @FXML
    private ComboBox<OfferingOption> offeringCombo;

    @FXML
    private TextField titleField;

    @FXML
    private ComboBox<String> materialTypeCombo;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private TextField resourcePathField;

    @FXML
    private TextField searchField;

    @FXML
    private Button saveButton;

    private final CourseMaterialsDAO materialsDAO =
            new CourseMaterialsDAO();

    private final LecturerCourseAssignmentDAO assignmentDAO =
            new LecturerCourseAssignmentDAO();

    private final CourseMaterialsService materialsService =
            new CourseMaterialsService(
                    materialsDAO,
                    assignmentDAO
            );

    private int selectedMaterialId = 0;

    @FXML
    public void initialize() {

        configureTable();

        materialTypeCombo.getItems().setAll(
                "LECTURE_NOTE",
                "PRACTICAL",
                "ASSIGNMENT",
                "LINK",
                "OTHER"
        );

        materialTypeCombo.setValue("OTHER");

        try {
            loadAssignedOfferings();
            loadMaterials(null);
        } catch (Exception e) {
            showError("Unable to initialize Course Materials.", e);
        }
    }

    private void configureTable() {

        offeringColumn.setCellValueFactory(
                new PropertyValueFactory<>("offeringId"));

        titleColumn.setCellValueFactory(
                new PropertyValueFactory<>("title"));

        typeColumn.setCellValueFactory(
                new PropertyValueFactory<>("materialType"));

        descriptionColumn.setCellValueFactory(
                new PropertyValueFactory<>("description"));

        resourcePathColumn.setCellValueFactory(
                new PropertyValueFactory<>("resourcePath"));

        uploadedAtColumn.setCellValueFactory(
                new PropertyValueFactory<>("uploadedAt"));

        materialsTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldMaterial, newMaterial) -> {
                    if (newMaterial != null) {
                        populateForm(newMaterial);
                    }
                });
    }

    private int requireLecturerId() {

        SessionManager.SessionIdentity identity =
                SessionManager.requireSession();

        User user = SessionManager.getCurrentUser();

        SessionManager.requireSession(identity);

        if (user == null
                || user.getUserId() != identity.userId()
                || !user.isActive()
                || !"LECTURER".equalsIgnoreCase(user.getRole())) {

            throw new SecurityException(
                    "Access denied. Please log in as an active lecturer.");
        }

        return identity.userId();
    }

    private void loadAssignedOfferings()
            throws SQLException {

        int lecturerId = requireLecturerId();

        List<Integer> offeringIds =
                assignmentDAO.getAssignedOfferingIds(lecturerId);

        offeringCombo.getItems().clear();

        for (Integer id : offeringIds) {
            offeringCombo.getItems().add(
                    new OfferingOption(id));
        }
    }

    private void loadMaterials(String keyword)
            throws SQLException {

        List<CourseMaterials> materials;

        if (keyword == null || keyword.isBlank()) {
            materials = materialsService.getMyMaterials();
        } else {
            materials = materialsService.searchMyMaterials(
                    keyword.trim());
        }

        materialsTable.getItems().setAll(materials);
    }

    @FXML
    private void handleSearch() {

        try {
            loadMaterials(searchField.getText());
        } catch (Exception e) {
            showError("Could not search materials.", e);
        }
    }

    @FXML
    private void handleRefresh() {

        searchField.clear();

        try {
            loadAssignedOfferings();
            loadMaterials(null);
        } catch (Exception e) {
            showError("Could not refresh materials.", e);
        }
    }

    @FXML
    private void handleSave() {

        try {
            OfferingOption offering = offeringCombo.getValue();

            if (offering == null) {
                showError(
                        "Please select an assigned course offering.",
                        null);
                return;
            }

            String title = titleField.getText();

            if (title == null || title.isBlank()) {
                showError("Please enter a material title.", null);
                return;
            }

            String materialType = materialTypeCombo.getValue();

            if (materialType == null || materialType.isBlank()) {
                showError("Please select a material type.", null);
                return;
            }

            String resourcePath = resourcePathField.getText();

            if (resourcePath == null || resourcePath.isBlank()) {
                showError(
                        "Please enter a resource path or URL.",
                        null);
                return;
            }

            CourseMaterials material = new CourseMaterials();

            material.setMaterialId(selectedMaterialId);
            material.setOfferingId(offering.getOfferingId());
            material.setTitle(title.trim());
            material.setDescription(descriptionArea.getText());
            material.setMaterialType(
                    materialType.toUpperCase(Locale.ROOT));
            material.setResourcePath(resourcePath.trim());

            // Service assigns the uploader from the active session.
            boolean saved = materialsService.save(material);

            if (!saved) {
                showError(
                        "Material was not saved. Check whether you own it.",
                        null);
                return;
            }

            showInfo(
                    selectedMaterialId == 0
                            ? "Material uploaded successfully."
                            : "Material updated successfully."
            );

            clearForm();
            loadMaterials(searchField.getText());

        } catch (Exception e) {
            showError("Could not save the material.", e);
        }
    }

    @FXML
    private void handleDelete() {

        CourseMaterials selected =
                materialsTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showError(
                    "Please select a material to delete.",
                    null);
            return;
        }

        Alert confirmation =
                new Alert(Alert.AlertType.CONFIRMATION);

        confirmation.setTitle("Delete Material");
        confirmation.setHeaderText("Delete selected material?");
        confirmation.setContentText(selected.getTitle());

        Optional<ButtonType> result = confirmation.showAndWait();

        if (result.isEmpty()
                || result.get() != ButtonType.OK) {
            return;
        }

        try {
            boolean deleted = materialsService.delete(
                    selected.getMaterialId());

            if (deleted) {
                showInfo("Material deleted successfully.");
                clearForm();
                loadMaterials(searchField.getText());
            } else {
                showError(
                        "Material was not deleted. It may no longer exist "
                                + "or may not belong to your account.",
                        null);
            }

        } catch (Exception e) {
            showError("Could not delete the material.", e);
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
    }

    private void populateForm(CourseMaterials material) {

        selectedMaterialId = material.getMaterialId();

        offeringCombo.getItems().stream()
                .filter(option ->
                        option.getOfferingId()
                                == material.getOfferingId())
                .findFirst()
                .ifPresentOrElse(
                        offeringCombo::setValue,
                        () -> offeringCombo.setValue(null)
                );

        titleField.setText(material.getTitle());

        materialTypeCombo.setValue(
                material.getMaterialType());

        descriptionArea.setText(
                material.getDescription() == null
                        ? ""
                        : material.getDescription());

        resourcePathField.setText(
                material.getResourcePath() == null
                        ? ""
                        : material.getResourcePath());

        saveButton.setText("Update Material");
    }

    private void clearForm() {

        selectedMaterialId = 0;

        materialsTable.getSelectionModel().clearSelection();

        offeringCombo.setValue(null);
        titleField.clear();
        descriptionArea.clear();
        resourcePathField.clear();
        materialTypeCombo.setValue("OTHER");

        saveButton.setText("Save Material");
    }

    private void showInfo(String message) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Course Materials");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message, Exception exception) {

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Course Materials Error");
        alert.setHeaderText(message);

        if (exception != null) {
            alert.setContentText(
                    exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage());
        }

        alert.showAndWait();
    }

    // Displays the offering ID in the dropdown.
    public static class OfferingOption {

        private final int offeringId;

        public OfferingOption(int offeringId) {
            this.offeringId = offeringId;
        }

        public int getOfferingId() {
            return offeringId;
        }

        @Override
        public String toString() {
            return "Offering #" + offeringId;
        }
    }
}
