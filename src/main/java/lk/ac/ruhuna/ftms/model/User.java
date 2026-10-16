
package lk.ac.ruhuna.ftms.model;

public class User {

    private int userId;
    private String username;
    private String fullName;
    private String email;
    private String role;
    private boolean active;
    private String profilePicture;

    public User() {
    }

    public User(int userId, String username,
                String fullName, String email,
                String role, boolean active) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.active = active;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** A generated relative filename in the managed picture directory, or null. */
    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    @Override
    public String toString() {
        return fullName + " (" + username + ")";
    }
}
