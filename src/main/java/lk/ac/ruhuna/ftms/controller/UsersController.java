package lk.ac.ruhuna.ftms.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.UserService;

import java.sql.SQLException;

public class UsersController {

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Integer> idColumn;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> nameColumn;
    @FXML private TableColumn<User, String> emailColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, Boolean> activeColumn;

    @FXML private TextField usernameField;
    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleComboBox;

    private final UserDAO userDAO = new UserDAO();
    private final UserService userService = new UserService();

    private User selectedUser;

    @FXML
    private void initialize() {
        idColumn.setCellValueFactory(
                new PropertyValueFactory<>("userId"));
        usernameColumn.setCellValueFactory(
                new PropertyValueFactory<>("username"));
        nameColumn.setCellValueFactory(
                new PropertyValueFactory<>("fullName"));
        emailColumn.setCellValueFactory(
                new PropertyValueFactory<>("email"));
        roleColumn.setCellValueFactory(
                new PropertyValueFactory<>("role"));
        activeColumn.setCellValueFactory(
                new PropertyValueFactory<>("active"));

        roleComboBox.setItems(FXCollections.observableArrayList(
                "ADMIN", "LECTURER", "TECHNICAL_OFFICER", "STUDENT"
        ));

        usersTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldUser, newUser) -> {
                    selectedUser = newUser;

                    if (newUser != null) {
                        usernameField.setText(newUser.getUsername());
                        nameField.setText(newUser.getFullName());
                        emailField.setText(newUser.getEmail());
                        roleComboBox.setValue(newUser.getRole());
                        passwordField.clear();
                    }
                });

        loadUsers();
    }

    @FXML
    private void handleRefresh() {
        loadUsers();
    }

    private void loadUsers() {
        try {
            usersTable.setItems(
                    FXCollections.observableArrayList(
                            userDAO.getAllUsers()
                    )
            );
        } catch (SQLException e) {
            showError("Cannot load users: " + e.getMessage());
        }
    }

    @FXML
    private void handleAdd() {
        try {
            userService.validateUser(
                    usernameField.getText(),
                    nameField.getText(),
                    emailField.getText(),
                    roleComboBox.getValue()
            );

            userService.validatePassword(passwordField.getText());

            // TODO: Replace with a secure password hash
            // created by PasswordUtil or AuthService.
            showError("Password hashing is not connected yet. "
                    + "User was not created.");

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleUpdate() {
        if (selectedUser == null) {
            showError("Please select a user first.");
            return;
        }

        try {
            userService.validateUser(
                    usernameField.getText(),
                    nameField.getText(),
                    emailField.getText(),
                    roleComboBox.getValue()
            );

            selectedUser.setUsername(usernameField.getText().trim());
            selectedUser.setFullName(nameField.getText().trim());
            selectedUser.setEmail(emailField.getText().trim());
            selectedUser.setRole(roleComboBox.getValue());

            userDAO.updateUser(selectedUser);
            loadUsers();
            showInfo("User updated successfully.");

        } catch (SQLException | IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleDeactivate() {
        if (selectedUser == null) {
            showError("Please select a user first.");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Deactivate selected user?",
                ButtonType.YES,
                ButtonType.NO
        );

        if (confirm.showAndWait().orElse(ButtonType.NO)
                != ButtonType.YES) {
            return;
        }

        try {
            userDAO.deactivateUser(selectedUser.getUserId());
            handleClear();
            loadUsers();
            showInfo("User deactivated successfully.");
        } catch (SQLException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleClear() {
        usersTable.getSelectionModel().clearSelection();
        selectedUser = null;
        usernameField.clear();
        nameField.clear();
        emailField.clear();
        passwordField.clear();
        roleComboBox.setValue(null);
    }

    private void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }

    private void showInfo(String message) {
        new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
    }
}