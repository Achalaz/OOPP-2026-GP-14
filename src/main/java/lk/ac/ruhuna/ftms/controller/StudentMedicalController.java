
package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;

import lk.ac.ruhuna.ftms.model.Medical;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.StudentMedicalService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Read-only medical history for the
 * currently logged-in undergraduate.
 */
public final class StudentMedicalController {

    @FXML private BorderPane studentMedicalRoot;

    @FXML private Label studentNameLabel;
    @FXML private Label summaryLabel;
    @FXML private Label statusLabel;
    @FXML private Label selectedMedicalLabel;
    @FXML private Label documentLabel;
    @FXML private Label reviewerLabel;

    @FXML private ComboBox<String> statusFilter;

    @FXML private Button refreshButton;
    @FXML private Button backButton;

    @FXML private TableView<Medical> medicalTable;

    @FXML private TableColumn<Medical, String> idColumn;
    @FXML private TableColumn<Medical, String> offeringColumn;
    @FXML private TableColumn<Medical, String> fromColumn;
    @FXML private TableColumn<Medical, String> toColumn;
    @FXML private TableColumn<Medical, String> statusColumn;
    @FXML private TableColumn<Medical, String> submittedColumn;

    @FXML private TextArea reasonArea;
    @FXML private TextArea reviewNoteArea;

    private final ObservableList<Medical> visibleMedicals =
            FXCollections.observableArrayList();

    private final SimpleBooleanProperty busy =
            new SimpleBooleanProperty(false);

    private List<Medical> allMedicals = List.of();

    private SessionIdentity loginIdentity;
    private StudentMedicalService medicalService;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd",
                    Locale.ROOT
            );

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:mm",
                    Locale.ROOT
            );

    // =====================================
    // INITIALIZE
    // =====================================

    @FXML
    public void initialize() {

        loginIdentity = SessionManager.requireSession();

        User student = requireStudent();

        medicalService = new StudentMedicalService();

        String name = student.getFullName();

        studentNameLabel.setText(
                "My Medical Records - "
                        + (name == null ? "Student" : name)
        );

        idColumn.setCellValueFactory(
                c -> value(
                        Integer.toString(
                                c.getValue().getMedicalId()
                        )
                )
        );

        offeringColumn.setCellValueFactory(
                c -> value(
                        c.getValue().getOfferingId() == null
                                ? "General"
                                : "#" + c.getValue().getOfferingId()
                )
        );

        fromColumn.setCellValueFactory(
                c -> value(
                        formatDate(
                                c.getValue().getFromDate()
                        )
                )
        );

        toColumn.setCellValueFactory(
                c -> value(
                        formatDate(
                                c.getValue().getToDate()
                        )
                )
        );

        statusColumn.setCellValueFactory(
                c -> value(
                        safe(c.getValue().getStatus())
                )
        );

        submittedColumn.setCellValueFactory(
                c -> value(
                        formatDateTime(
                                c.getValue().getSubmittedAt()
                        )
                )
        );

        medicalTable.setItems(visibleMedicals);

        medicalTable.setPlaceholder(
                new Label("No medical records to display.")
        );

        medicalTable.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (obs, oldValue, selected) ->
                                showDetails(selected)
                );

        statusFilter.getItems().setAll(
                "ALL",
                "PENDING",
                "APPROVED",
                "REJECTED"
        );

        statusFilter.setValue("ALL");

        statusFilter.valueProperty()
                .addListener(
                        (obs, oldValue, selected) ->
                                applyFilter()
                );

        refreshButton.disableProperty().bind(busy);
        backButton.disableProperty().bind(busy);
        statusFilter.disableProperty().bind(busy);
        medicalTable.disableProperty().bind(busy);

        reasonArea.setEditable(false);
        reviewNoteArea.setEditable(false);

        showDetails(null);

        handleRefresh();
    }

    // =====================================
    // STUDENT AUTHORIZATION
    // =====================================

    private User requireStudent() {

        SessionManager.requireSession(loginIdentity);

        User user = SessionManager.getCurrentUser();

        if (user == null
                || !user.isActive()
                || !"STUDENT".equals(user.getRole())
                || user.getUserId()
                != loginIdentity.userId()) {

            throw new SecurityException(
                    "Active student login required."
            );
        }

        return user;
    }

    // =====================================
    // LOAD MEDICAL RECORDS
    // =====================================

    @FXML
    private void handleRefresh() {

        if (busy.get()) {
            return;
        }

        try {

            requireStudent();

        } catch (SecurityException error) {

            handleSessionError(error);
            return;
        }

        busy.set(true);

        statusLabel.setText(
                "Loading your medical records..."
        );

        Task<List<Medical>> task = new Task<>() {

            @Override
            protected List<Medical> call() throws Exception {

                return medicalService.getMyMedicals();
            }
        };

        task.setOnSucceeded(event -> {

            busy.set(false);

            try {

                requireStudent();

                allMedicals = task.getValue();

                updateSummary();
                applyFilter();

                statusLabel.setText(
                        "Loaded "
                                + allMedicals.size()
                                + " medical record(s)."
                );

            } catch (SecurityException error) {

                handleSessionError(error);
            }
        });

        task.setOnFailed(event -> {

            busy.set(false);

            Throwable error = task.getException();

            if (error != null) {
                error.printStackTrace();
            }

            if (error instanceof SecurityException) {

                handleSessionError(
                        (SecurityException) error
                );

            } else {

                showError(
                        "Unable to load your medical records. "
                                + "Check the database connection."
                );
            }
        });

        Thread worker = new Thread(
                task,
                "ftms-student-medical"
        );

        worker.setDaemon(true);
        worker.start();
    }

    // =====================================
    // STATUS FILTER
    // =====================================

    private void applyFilter() {

        String filter = statusFilter.getValue();

        medicalTable.getSelectionModel()
                .clearSelection();

        if (filter == null || "ALL".equals(filter)) {

            visibleMedicals.setAll(allMedicals);

        } else {

            visibleMedicals.setAll(
                    allMedicals.stream()
                            .filter(
                                    m -> filter.equals(
                                            m.getStatus()
                                    )
                            )
                            .toList()
            );
        }

        statusLabel.setText(
                "Showing "
                        + visibleMedicals.size()
                        + " record(s) - "
                        + (filter == null ? "ALL" : filter)
        );
    }

    // =====================================
    // MEDICAL STATUS SUMMARY
    // =====================================

    private void updateSummary() {

        long pending = allMedicals.stream()
                .filter(
                        m -> "PENDING".equals(
                                m.getStatus()
                        )
                )
                .count();

        long approved = allMedicals.stream()
                .filter(
                        m -> "APPROVED".equals(
                                m.getStatus()
                        )
                )
                .count();

        long rejected = allMedicals.stream()
                .filter(
                        m -> "REJECTED".equals(
                                m.getStatus()
                        )
                )
                .count();

        summaryLabel.setText(
                "Total: " + allMedicals.size()
                        + "   |   Pending: " + pending
                        + "   |   Approved: " + approved
                        + "   |   Rejected: " + rejected
        );
    }

    // =====================================
    // VIEW SELECTED MEDICAL DETAILS
    // =====================================

    private void showDetails(Medical medical) {

        if (medical == null) {

            selectedMedicalLabel.setText(
                    "Select a medical record to view details."
            );

            reasonArea.clear();
            reviewNoteArea.clear();

            reviewerLabel.setText("Review: -");
            documentLabel.setText("Document: -");

            return;
        }

        selectedMedicalLabel.setText(
                "Medical #"
                        + medical.getMedicalId()
                        + "  |  "
                        + safe(medical.getStatus())
        );

        reasonArea.setText(
                safe(medical.getReason())
        );

        reviewNoteArea.setText(
                safe(medical.getReviewNote())
        );

        reviewerLabel.setText(
                "Reviewed: "
                        + formatDateTime(
                        medical.getReviewedAt()
                )
        );

        // Local file path is not a file upload.
        // Do not expose server-side file paths.

        documentLabel.setText(
                medical.getDocumentPath() == null
                        || medical.getDocumentPath().isBlank()
                        ? "Document: None recorded"
                        : "Document: Reference recorded "
                        + "(not downloadable here)"
        );
    }

    // =====================================
    // BACK TO STUDENT DASHBOARD
    // =====================================

    @FXML
    private void handleBack() {

        try {

            requireStudent();

            NavigationUtil.navigate(
                    studentMedicalRoot,
                    "/fxml/student_dashboard.fxml",
                    "FTMS - Student Dashboard"
            );

        } catch (SecurityException error) {

            handleSessionError(error);

        } catch (IOException | RuntimeException error) {

            error.printStackTrace();

            showError(
                    "Unable to return to the Student Dashboard."
            );
        }
    }

    // =====================================
    // SESSION ERROR
    // =====================================

    private void handleSessionError(
            SecurityException error) {

        allMedicals = List.of();

        visibleMedicals.clear();

        showDetails(null);

        showError(
                error.getMessage()
                        + " Please sign in again."
        );
    }

    // =====================================
    // ERROR MESSAGE
    // =====================================

    private void showError(String message) {

        statusLabel.setText(message);

        Alert alert = new Alert(
                Alert.AlertType.ERROR,
                message,
                ButtonType.OK
        );

        alert.setTitle(
                "FTMS - Student Medical"
        );

        alert.setHeaderText(null);

        if (studentMedicalRoot.getScene() != null
                && studentMedicalRoot.getScene()
                .getWindow() != null) {

            alert.initOwner(
                    studentMedicalRoot
                            .getScene()
                            .getWindow()
            );
        }

        alert.showAndWait();
    }

    // =====================================
    // HELPERS
    // =====================================

    private static ReadOnlyStringWrapper value(
            String text) {

        return new ReadOnlyStringWrapper(
                text == null ? "" : text
        );
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String formatDate(LocalDate date) {

        return date == null
                ? "-"
                : date.format(DATE_FORMAT);
    }

    private static String formatDateTime(
            LocalDateTime dateTime) {

        return dateTime == null
                ? "-"
                : dateTime.format(DATE_TIME_FORMAT);
    }
}
