package lk.ac.ruhuna.ftms.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import lk.ac.ruhuna.ftms.model.Notice;
import lk.ac.ruhuna.ftms.service.NoticeService;

import java.sql.SQLException;
import java.util.Optional;
import lk.ac.ruhuna.ftms.dao.NoticeDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.NoticeService;
import lk.ac.ruhuna.ftms.util.SessionManager;

public class NoticeController {

    @FXML private TextField searchField;
    @FXML private TextField titleField;
    @FXML private TextArea contentField;
    @FXML private CheckBox publishedCheckBox;

    @FXML private TableView<Notice> noticeTable;

    @FXML private TableColumn<Notice, Long> idColumn;
    @FXML private TableColumn<Notice, String> titleColumn;
    @FXML private TableColumn<Notice, String> contentColumn;
    @FXML private TableColumn<Notice, Boolean> publishedColumn;
    @FXML private TableColumn<Notice, Object> createdAtColumn;

    private final ObservableList<Notice> noticeList =
            FXCollections.observableArrayList();

    private NoticeService noticeService;

    @FXML
    public void initialize() {

        idColumn.setCellValueFactory(
                new PropertyValueFactory<>("noticeId"));

        titleColumn.setCellValueFactory(
                new PropertyValueFactory<>("title"));

        contentColumn.setCellValueFactory(
                new PropertyValueFactory<>("content"));

        publishedColumn.setCellValueFactory(
                new PropertyValueFactory<>("published"));

        createdAtColumn.setCellValueFactory(
                new PropertyValueFactory<>("createdAt"));

        noticeTable.setItems(noticeList);

        noticeTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldNotice, selectedNotice) -> {
                    if (selectedNotice != null) {
                        populateForm(selectedNotice);
                    }
                });
        noticeService = new NoticeService(
                new NoticeDAO(),
                this::isActiveAdmin
        );

        refreshNotices();
    }

    // Called by the navigation code after loading the FXML.
    public void setNoticeService(NoticeService noticeService) {
        this.noticeService = noticeService;
        refreshNotices();
    }

    @FXML
    private void handleSearch() {
        if (!ensureServiceConfigured()) {
            return;
        }

        try {
            noticeList.setAll(
                    noticeService.searchNotices(searchField.getText())
            );
        } catch (SQLException | SecurityException e) {
            showError("Could not search notices", e);
        }
    }

    @FXML
    private void handleShowAll() {
        searchField.clear();
        refreshNotices();
    }

    @FXML
    private void handleSave() {
        if (!ensureServiceConfigured()) {
            return;
        }

        String title = titleField.getText();
        String content = contentField.getText();

        if (title == null || title.isBlank()) {
            showWarning("Please enter a notice title.");
            titleField.requestFocus();
            return;
        }

        if (content == null || content.isBlank()) {
            showWarning("Please enter notice content.");
            contentField.requestFocus();
            return;
        }

        Notice selected = noticeTable.getSelectionModel()
                .getSelectedItem();

        Notice notice = new Notice();

        if (selected != null) {
            notice.setNoticeId(selected.getNoticeId());
        }

        notice.setTitle(title.trim());
        notice.setContent(content.trim());
        notice.setPublished(publishedCheckBox.isSelected());

        try {
            boolean saved = noticeService.saveNotice(notice);

            if (saved) {
                showInformation("Notice saved successfully.");
                handleClear();
                refreshNotices();
            } else {
                showWarning("The notice was not saved.");
            }

        } catch (SQLException | IllegalArgumentException
                 | SecurityException e) {
            showError("Could not save notice", e);
        }
    }

    @FXML
    private void handleClear() {
        noticeTable.getSelectionModel().clearSelection();

        titleField.clear();
        contentField.clear();
        publishedCheckBox.setSelected(false);
    }

    @FXML
    private void handlePublishToggle() {
        if (!ensureServiceConfigured()) {
            return;
        }

        Notice selected = noticeTable.getSelectionModel()
                .getSelectedItem();

        if (selected == null) {
            showWarning("Please select a notice first.");
            return;
        }

        boolean newStatus = !selected.isPublished();

        try {
            boolean updated = noticeService.setPublished(
                    selected.getNoticeId(), newStatus);

            if (updated) {
                showInformation(
                        newStatus
                                ? "Notice published successfully."
                                : "Notice unpublished successfully."
                );

                refreshNotices();
                handleClear();
            } else {
                showWarning("The notice status was not changed.");
            }

        } catch (SQLException | IllegalArgumentException
                 | SecurityException e) {
            showError("Could not change notice status", e);
        }
    }

    @FXML
    private void handleDelete() {
        if (!ensureServiceConfigured()) {
            return;
        }

        Notice selected = noticeTable.getSelectionModel()
                .getSelectedItem();

        if (selected == null) {
            showWarning("Please select a notice to delete.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Notice");
        confirmation.setHeaderText("Delete this notice?");
        confirmation.setContentText(
                "Are you sure you want to delete \""
                        + selected.getTitle()
                        + "\"? This action cannot be undone."
        );

        Optional<ButtonType> result = confirmation.showAndWait();

        if (result.isEmpty()
                || result.get() != ButtonType.OK) {
            return;
        }

        try {
            boolean deleted = noticeService.deleteNotice(
                    selected.getNoticeId());

            if (deleted) {
                showInformation("Notice deleted successfully.");
                handleClear();
                refreshNotices();
            } else {
                showWarning("The notice could not be deleted.");
            }

        } catch (SQLException | IllegalArgumentException
                 | SecurityException e) {
            showError("Could not delete notice", e);
        }
    }

    private void refreshNotices() {
        if (noticeService == null) {
            return;
        }

        try {
            noticeList.setAll(noticeService.getAllNotices());
        } catch (SQLException | SecurityException e) {
            showError("Could not load notices", e);
        }
    }

    private void populateForm(Notice notice) {
        titleField.setText(notice.getTitle());
        contentField.setText(notice.getContent());
        publishedCheckBox.setSelected(notice.isPublished());
    }

    private boolean ensureServiceConfigured() {
        if (noticeService == null) {
            showWarning(
                    "Notice service is not configured. "
                            + "Please connect it through the Admin dashboard."
            );
            return false;
        }

        return true;
    }

    private void showInformation(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Notice Management");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Notice Management");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String heading, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Notice Management");
        alert.setHeaderText(heading);

        String message = exception.getMessage();

        alert.setContentText(
                message == null || message.isBlank()
                        ? "An unexpected error occurred."
                        : message
        );

        alert.showAndWait();
    }
    private boolean isActiveAdmin() {
        User user = SessionManager.getCurrentUser();

        return user != null
                && user.isActive()
                && "ADMIN".equals(user.getRole());
    }
}