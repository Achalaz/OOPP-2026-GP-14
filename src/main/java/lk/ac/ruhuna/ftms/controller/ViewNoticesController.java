package lk.ac.ruhuna.ftms.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import lk.ac.ruhuna.ftms.dao.NoticeDAO;
import lk.ac.ruhuna.ftms.model.Notice;
import lk.ac.ruhuna.ftms.service.NoticeService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class ViewNoticesController {
    @FXML private TableView<Notice> noticeTable;
    @FXML private TableColumn<Notice, String> titleColumn;
    @FXML private TableColumn<Notice, String> publishedAtColumn;
    @FXML private Label selectedTitleLabel;
    @FXML private Label selectedDateLabel;
    @FXML private Label statusLabel;
    @FXML private TextArea contentArea;

    private final ObservableList<Notice> noticeList = FXCollections.observableArrayList();
    private final NoticeService noticeService;

    public ViewNoticesController() {
        this(new NoticeService(new NoticeDAO()));
    }

    public ViewNoticesController(NoticeService noticeService) {
        this.noticeService = Objects.requireNonNull(noticeService);
    }

    @FXML public void initialize() {
        noticeService.requireViewerAccess();
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        publishedAtColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatDate(cell.getValue().getPublishedAt())));
        noticeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        noticeTable.setPlaceholder(new Label("No published notices yet. Use Refresh Notices to check again."));
        noticeTable.setItems(noticeList);
        noticeTable.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            clearDetails();
            if (selected != null) {
                try {
                    noticeService.requireViewerAccess();
                    selectedTitleLabel.setText(selected.getTitle());
                    selectedDateLabel.setText("Published from: " + formatDate(selected.getPublishedAt())
                            + "  |  Expires: " + (selected.getExpiresAt() == null ? "No expiry" : formatDate(selected.getExpiresAt())));
                    contentArea.setText(selected.getContent());
                    contentArea.positionCaret(0);
                } catch (SecurityException error) {
                    statusLabel.setText(error.getMessage());
                }
            }
        });
        loadPublishedNotices();
    }

    @FXML private void handleRefresh() { loadPublishedNotices(); }

    @FXML private void handleBack() {
        try {
            noticeService.requireViewerAccess();
            NavigationUtil.navigate(noticeTable, NavigationUtil.dashboardResource(), "FTMS - Dashboard");
        } catch (IOException | SecurityException error) {
            if (error instanceof SecurityException) {
                noticeList.clear();
                clearDetails();
            }
            statusLabel.setText("Could not open dashboard: " + error.getMessage());
        }
    }

    private void loadPublishedNotices() {
        Notice selected = noticeTable.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? 0 : selected.getNoticeId();
        noticeTable.getSelectionModel().clearSelection();
        noticeList.clear();
        clearDetails();
        try {
            // Always use the published-only service; never retrieve drafts here.
            noticeList.setAll(noticeService.getPublishedNotices());
            statusLabel.setText(noticeList.isEmpty()
                    ? "No published notices yet. Use Refresh Notices to check again."
                    : noticeList.size() + " published notice(s). Select a title to read the full notice.");
            noticeList.stream().filter(notice -> notice.getNoticeId() == selectedId).findFirst()
                    .ifPresent(notice -> noticeTable.getSelectionModel().select(notice));
        } catch (SQLException error) {
            statusLabel.setText("Unable to load notices. Please try Refresh Notices again or contact an administrator.");
        } catch (SecurityException error) {
            statusLabel.setText(error.getMessage());
        }
    }

    private void clearDetails() {
        selectedTitleLabel.setText("Select a notice");
        selectedDateLabel.setText("");
        contentArea.clear();
    }

    private static String formatDate(LocalDateTime date) {
        return date == null ? "Not recorded" : date.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
    }
}
