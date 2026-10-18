package lk.ac.ruhuna.ftms.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;

import lk.ac.ruhuna.ftms.dao.NoticeDAO;
import lk.ac.ruhuna.ftms.model.Notice;
import lk.ac.ruhuna.ftms.service.NoticeService;

import java.sql.SQLException;

public class ViewNoticesController {

    @FXML
    private TableView<Notice> noticeTable;

    @FXML
    private TableColumn<Notice, String> titleColumn;

    @FXML
    private TableColumn<Notice, Object> createdAtColumn;

    @FXML
    private Label selectedTitleLabel;

    @FXML
    private TextArea contentArea;

    private final ObservableList<Notice> noticeList =
            FXCollections.observableArrayList();

    private NoticeService noticeService;

    @FXML
    public void initialize() {

        titleColumn.setCellValueFactory(
                new PropertyValueFactory<>("title")
        );

        createdAtColumn.setCellValueFactory(
                new PropertyValueFactory<>("createdAt")
        );

        noticeTable.setItems(noticeList);

        noticeTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldNotice, selectedNotice) -> {

                    if (selectedNotice != null) {
                        selectedTitleLabel.setText(
                                selectedNotice.getTitle()
                        );

                        contentArea.setText(
                                selectedNotice.getContent()
                        );
                    } else {
                        clearDetails();
                    }
                });

        noticeService = new NoticeService(new NoticeDAO());

        loadPublishedNotices();
    }

    @FXML
    private void handleRefresh() {
        loadPublishedNotices();
    }

    private void loadPublishedNotices() {

        try {
            noticeList.setAll(
                    noticeService.getPublishedNotices()
            );

            clearDetails();

        } catch (SQLException | SecurityException e) {
            showError(
                    "Unable to load notices",
                    e.getMessage()
            );
        }
    }

    private void clearDetails() {
        selectedTitleLabel.setText("Select a notice");
        contentArea.clear();
    }

    private void showError(String title, String message) {

        Alert alert = new Alert(Alert.AlertType.ERROR);

        alert.setTitle("Notice Viewer");
        alert.setHeaderText(title);
        alert.setContentText(
                message == null
                        ? "An unexpected error occurred."
                        : message
        );

        alert.showAndWait();
    }
}