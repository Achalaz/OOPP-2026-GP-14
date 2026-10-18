package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lk.ac.ruhuna.ftms.dao.NoticeDAO;
import lk.ac.ruhuna.ftms.model.Notice;
import lk.ac.ruhuna.ftms.model.Notice.Audience;
import lk.ac.ruhuna.ftms.service.NoticeService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Objects;

public class NoticeController {
    @FXML private TextField searchField;
    @FXML private TextField titleField;
    @FXML private TextArea contentField;
    @FXML private CheckBox publishedCheckBox;
    @FXML private ComboBox<Audience> audienceCombo;
    @FXML private TextField publishedAtField;
    @FXML private TextField expiresAtField;
    @FXML private Label formHeading;
    @FXML private Label statusLabel;
    @FXML private Button saveButton;
    @FXML private Button editButton;
    @FXML private Button publishButton;
    @FXML private Button deleteButton;
    @FXML private TableView<Notice> noticeTable;
    @FXML private TableColumn<Notice, Long> idColumn;
    @FXML private TableColumn<Notice, String> titleColumn;
    @FXML private TableColumn<Notice, String> contentColumn;
    @FXML private TableColumn<Notice, String> publishedColumn;
    @FXML private TableColumn<Notice, String> publishedAtColumn;
    @FXML private TableColumn<Notice, Audience> audienceColumn;

    private static final DateTimeFormatter DATE_INPUT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    private final ObservableList<Notice> noticeList = FXCollections.observableArrayList();
    private final NoticeService noticeService;
    private long editingNoticeId;

    public NoticeController() {
        this(new NoticeService(new NoticeDAO()));
    }

    public NoticeController(NoticeService noticeService) {
        this.noticeService = Objects.requireNonNull(noticeService);
    }

    @FXML public void initialize() {
        // Reject direct FXMLLoader/controller access before loading any drafts.
        noticeService.requireAdminAccess();
        idColumn.setCellValueFactory(new PropertyValueFactory<>("noticeId"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        contentColumn.setCellValueFactory(new PropertyValueFactory<>("content"));
        audienceColumn.setCellValueFactory(new PropertyValueFactory<>("audience"));
        audienceCombo.getItems().setAll(Audience.values());
        publishedColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().publicationStatus(LocalDateTime.now())));
        publishedAtColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getPublishedAt() == null ? "Not recorded" :
                        cell.getValue().getPublishedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))));
        noticeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        noticeTable.setPlaceholder(new Label("No notices found. Create a notice or change your search."));
        noticeTable.setItems(noticeList);
        noticeTable.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            if (selected == null) clearForm();
            else populateForm(selected);
        });
        clearForm();
        refreshNotices();
    }

    @FXML private void handleSearch() { refreshNotices(); }

    @FXML private void handleShowAll() {
        searchField.clear();
        refreshNotices();
    }

    @FXML private void handleSave() {
        if (!ensureAdmin()) return;
        try {
            Notice notice = new Notice();
            notice.setNoticeId(editingNoticeId);
            notice.setTitle(titleField.getText());
            notice.setContent(contentField.getText());
            notice.setActive(publishedCheckBox.isSelected());
            notice.setAudience(audienceCombo.getValue());
            notice.setPublishedAt(parseDate(publishedAtField.getText(), "Publication start"));
            notice.setExpiresAt(parseDate(expiresAtField.getText(), "Expiry"));
            if (noticeService.saveNotice(notice)) {
                // Show the saved row even if the previous search no longer matches it.
                searchField.clear();
                if (refreshNotices()) statusLabel.setText("Notice #" + notice.getNoticeId() + " saved successfully.");
            } else {
                statusLabel.setText("The notice was not saved. Refresh to check whether it still exists.");
            }
        } catch (SQLException | IllegalArgumentException | SecurityException error) {
            showError("Could not save notice", error);
        }
    }

    @FXML private void handleEdit() {
        if (!ensureAdmin()) return;
        Notice selected = noticeTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            populateForm(selected);
            titleField.requestFocus();
        }
    }

    @FXML private void handleClear() {
        noticeTable.getSelectionModel().clearSelection();
        clearForm();
    }

    @FXML private void handlePublishToggle() {
        if (!ensureAdmin()) return;
        Notice selected = noticeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a notice first.");
            return;
        }
        boolean published = !selected.isActive();
        try {
            if (noticeService.setPublished(selected.getNoticeId(), published)) {
                if (refreshNotices()) statusLabel.setText(published
                        ? "Publication enabled. The audience, publication start and expiry still apply."
                        : "Notice unpublished (inactive).");
            } else {
                statusLabel.setText("The notice no longer exists. Refresh the list.");
            }
        } catch (SQLException | IllegalArgumentException | SecurityException error) {
            showError("Could not change notice status", error);
        }
    }

    @FXML private void handleDelete() {
        if (!ensureAdmin()) return;
        Notice selected = noticeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Please select a notice to delete.");
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Notice");
        confirmation.setHeaderText("Delete this notice?");
        confirmation.setContentText("Delete \"" + selected.getTitle() + "\"? This action cannot be undone.");
        confirmation.initOwner(noticeTable.getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            if (noticeService.deleteNotice(selected.getNoticeId())) {
                if (refreshNotices()) statusLabel.setText("Notice deleted.");
            } else {
                statusLabel.setText("The notice no longer exists. Refresh the list.");
            }
        } catch (SQLException | IllegalArgumentException | SecurityException error) {
            showError("Could not delete notice", error);
        }
    }

    @FXML private void handleBack() {
        try {
            noticeService.requireAdminAccess();
            NavigationUtil.navigate(noticeTable, NavigationUtil.dashboardResource(), "FTMS - Admin Dashboard");
        } catch (IOException | SecurityException error) {
            showError("Could not open dashboard", error);
        }
    }

    private boolean refreshNotices() {
        handleClear();
        noticeList.clear();
        try {
            List<Notice> notices = searchField.getText() == null || searchField.getText().isBlank()
                    ? noticeService.getAllNotices() : noticeService.searchNotices(searchField.getText());
            noticeList.setAll(notices);
            statusLabel.setText(notices.isEmpty() ? "No notices found. Create a notice or change your search."
                    : notices.size() + " notice(s). Select one to edit or manage it.");
            return true;
        } catch (SQLException | SecurityException error) {
            showError("Could not load notices", error);
            return false;
        }
    }

    private void populateForm(Notice notice) {
        if (!ensureAdmin()) return;
        editingNoticeId = notice.getNoticeId();
        titleField.setText(notice.getTitle());
        contentField.setText(notice.getContent());
        publishedCheckBox.setSelected(notice.isActive());
        audienceCombo.setValue(notice.getAudience());
        publishedAtField.setText(notice.getPublishedAt() == null ? "" : notice.getPublishedAt().format(DATE_INPUT));
        expiresAtField.setText(notice.getExpiresAt() == null ? "" : notice.getExpiresAt().format(DATE_INPUT));
        formHeading.setText("Editing notice #" + editingNoticeId);
        saveButton.setText("Save Changes");
        editButton.setDisable(false);
        publishButton.setDisable(false);
        publishButton.setText(notice.isActive() ? "Unpublish" : "Publish");
        deleteButton.setDisable(false);
    }

    private void clearForm() {
        editingNoticeId = 0;
        titleField.clear();
        contentField.clear();
        publishedCheckBox.setSelected(false);
        audienceCombo.setValue(Audience.ALL);
        publishedAtField.clear();
        expiresAtField.clear();
        formHeading.setText("Create a notice");
        saveButton.setText("Save Notice");
        editButton.setDisable(true);
        publishButton.setDisable(true);
        publishButton.setText("Publish / Unpublish");
        deleteButton.setDisable(true);
    }

    private boolean ensureAdmin() {
        try {
            noticeService.requireAdminAccess();
            return true;
        } catch (SecurityException error) {
            showError("Access denied", error);
            return false;
        }
    }

    private LocalDateTime parseDate(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.trim(), DATE_INPUT);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException(label + " must use yyyy-MM-dd HH:mm:ss (for example, 2026-12-20 10:00:00).");
        }
    }

    private void showError(String heading, Exception error) {
        if (error instanceof SecurityException) {
            handleClear();
            noticeList.clear();
        }
        String message = error instanceof SQLException
                ? "Check the database connection and notices schema, then refresh."
                : error.getMessage();
        statusLabel.setText(heading + ": " + message);
    }
}
