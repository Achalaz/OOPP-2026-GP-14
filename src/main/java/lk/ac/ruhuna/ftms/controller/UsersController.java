package lk.ac.ruhuna.ftms.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import lk.ac.ruhuna.ftms.dao.UserDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.service.UserService;
import lk.ac.ruhuna.ftms.util.NavigationUtil;
import lk.ac.ruhuna.ftms.util.PasswordUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

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
    @FXML private ComboBox<UserDAO.DepartmentOption> departmentComboBox;
    @FXML private TextField staffNumberField;

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

        roleComboBox.setItems(
                FXCollections.observableArrayList(
                        "ADMIN",
                        "LECTURER",
                        "TECHNICAL_OFFICER",
                        "STUDENT"
                )
        );
        try {
            departmentComboBox.setItems(
                    FXCollections.observableArrayList(userDAO.getDepartments()));
        } catch (SQLException exception) {
            showError("Cannot load departments: " + exception.getMessage());
        }
        departmentComboBox.disableProperty().bind(
                roleComboBox.valueProperty().isNotEqualTo("TECHNICAL_OFFICER"));
        staffNumberField.disableProperty().bind(
                roleComboBox.valueProperty().isNotEqualTo("TECHNICAL_OFFICER"));

        usersTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldUser, newUser) -> {

                    selectedUser = newUser;

                    if (newUser != null) {
                        usernameField.setText(
                                newUser.getUsername());

                        nameField.setText(
                                newUser.getFullName());

                        emailField.setText(
                                newUser.getEmail());

                        roleComboBox.setValue(
                                newUser.getRole());

                        passwordField.clear();
                    }
                });

        loadUsers();
    }

    @FXML
    private void handleBackToDashboard() {
        try {
            NavigationUtil.navigate(
                    usersTable,
                    "/fxml/admin_dashboard.fxml",
                    "FTMS - Admin Dashboard"
            );
        } catch (IOException | RuntimeException e) {
            showError("Cannot open admin dashboard: " + e.getMessage());
        }
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

    // ADD USER WITH PASSWORD HASHING
    @FXML
    private void handleAdd() {

        char[] passwordChars = null;

        try {

            // Step 1: Validate user details
            userService.validateUser(
                    usernameField.getText(),
                    nameField.getText(),
                    emailField.getText(),
                    roleComboBox.getValue()
            );

            // Step 2: Validate password
            userService.validatePassword(
                    passwordField.getText()
            );

            // Step 3: Convert password to char array
            passwordChars =
                    passwordField.getText().toCharArray();

            // Step 4: Generate secure password hash
            String passwordHash =
                    PasswordUtil.hashPassword(passwordChars);

            // Step 5: Create User object
            User newUser = new User(
                    0,
                    usernameField.getText().trim(),
                    nameField.getText().trim(),
                    emailField.getText().trim(),
                    roleComboBox.getValue(),
                    true
            );

            // Step 6: Save into MySQL
            if ("TECHNICAL_OFFICER".equals(newUser.getRole())) {
                UserDAO.DepartmentOption department = departmentComboBox.getValue();
                if (department == null) {
                    throw new IllegalArgumentException("Please select a department.");
                }
                userDAO.addTechnicalOfficer(newUser, passwordHash,
                        department.departmentId(), staffNumberField.getText());
            } else {
                userDAO.addUser(newUser, passwordHash);
            }

            // Step 7: Clear and refresh
            handleClear();
            loadUsers();

            showInfo("User added successfully.");

        } catch (IllegalArgumentException e) {

            showError(e.getMessage());

        } catch (SQLException e) {

            showError("Cannot add user: " + e.getMessage());
            e.printStackTrace();

        } finally {

            // Remove password data from char array
            if (passwordChars != null) {
                Arrays.fill(passwordChars, '\0');
            }

            passwordField.clear();
            departmentComboBox.setValue(null);
            staffNumberField.clear();
        }
    }

    @FXML
    private void handleUpdate() {
        try {
            if (selectedUser == null) {
                throw new IllegalArgumentException("Please select a user first.");
            }
            // Keep the displayed row unchanged until the whole update has committed.
            User updated = new User(selectedUser.getUserId(), usernameField.getText(),
                    nameField.getText(), emailField.getText(), roleComboBox.getValue(), selectedUser.isActive());
            boolean passwordChanged = userService.updateUser(updated, passwordField.getText());

            handleClear();
            loadUsers();

            showInfo(passwordChanged
                    ? "User details and password updated successfully. Account activation is unchanged."
                    : "User details updated successfully. Password and account activation are unchanged.");

        } catch (IllegalArgumentException | SecurityException e) {
            passwordField.clear();
            showError(e.getMessage());
        } catch (SQLException e) {
            passwordField.clear();
            // Database errors can contain bound values; never display or log credential details.
            showError("Unable to save the user update. Refresh and try again.");
        } catch (IllegalStateException e) {
            passwordField.clear();
            showError("Unable to secure the new password. No changes were saved.");
        } finally {
            passwordField.clear();
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

            userDAO.deactivateUser(
                    selectedUser.getUserId()
            );

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

    void showError(String message) {

        new Alert(
                Alert.AlertType.ERROR,
                message
        ).showAndWait();
    }

    void showInfo(String message) {

        new Alert(
                Alert.AlertType.INFORMATION,
                message
        ).showAndWait();
    }
}
