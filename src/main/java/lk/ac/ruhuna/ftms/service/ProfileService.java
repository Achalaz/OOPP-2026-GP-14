package lk.ac.ruhuna.ftms.service;

import lk.ac.ruhuna.ftms.dao.ProfileDAO;
import lk.ac.ruhuna.ftms.model.User;
import lk.ac.ruhuna.ftms.util.SessionManager;
import lk.ac.ruhuna.ftms.util.SessionManager.SessionIdentity;
import lk.ac.ruhuna.ftms.util.ProfilePictureStore;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** A profile service belongs to one authenticated login, not to a caller-supplied user ID. */
public class ProfileService {
    // Verified against the existing users table: first_name/last_name VARCHAR(80), email VARCHAR(150).
    private static final int NAME_PART_LIMIT = 80;
    private static final int EMAIL_LIMIT = 150;
    private static final Set<String> ROLES = Set.of("ADMIN", "LECTURER", "TECHNICAL_OFFICER", "STUDENT");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9_%+\\-]+(?:\\.[A-Za-z0-9_%+\\-]+)*@"
                    + "[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,63}");

    private final ProfileDAO profileDAO;
    private final SessionIdentity identity;
    private final ProfilePictureStore pictureStore;
    private final UserService userService = new UserService();

    public ProfileService() {
        this(new ProfileDAO());
    }

    public ProfileService(ProfileDAO profileDAO) {
        this(profileDAO, new ProfilePictureStore());
    }

    public ProfileService(ProfileDAO profileDAO, ProfilePictureStore pictureStore) {
        this.profileDAO = Objects.requireNonNull(profileDAO);
        this.pictureStore = Objects.requireNonNull(pictureStore);
        identity = SessionManager.requireSession();
    }

    public User getMyProfile() throws SQLException {
        requireSession();
        User profile = profileDAO.getProfile(identity);
        requireSession();
        requireAvailable(profile);
        SessionManager.updateProfile(identity, profile.getFullName(), profile.getEmail());
        SessionManager.updateProfilePicture(identity, profile.getProfilePicture());
        return profile;
    }

    public byte[] loadMyProfilePicture(String filename) throws IOException {
        requireSession();
        return pictureStore.read(identity.userId(), filename);
    }

    public User changeMyProfilePicture(Path source) throws SQLException, IOException {
        requireSession();
        User profile = profileDAO.getProfile(identity);
        requireAvailable(profile);
        requireSession();
        String filename = pictureStore.save(identity.userId(), source);
        boolean retainFile = false;
        try {
            requireSession();
            if (!profileDAO.updateProfilePicture(identity, profile.getProfilePicture(), filename)) {
                throw new IllegalStateException("Your profile changed or is no longer available. Refresh and try again.");
            }
            retainFile = true;
            deletePictureQuietly(profile.getProfilePicture());
            SessionManager.updateProfilePicture(identity, filename);
            profile.setProfilePicture(filename);
            return profile;
        } catch (SQLException error) {
            // The server may have committed before a connection was lost. Never delete a
            // potentially referenced image unless a fresh read proves it was not stored.
            retainFile = true;
            try {
                User current = profileDAO.getProfile(identity);
                if (current != null) { retainFile = filename.equals(current.getProfilePicture()); }
            } catch (SQLException | SecurityException verificationFailure) {
                error.addSuppressed(verificationFailure);
            }
            throw error;
        } finally {
            if (!retainFile) { deletePictureQuietly(filename); }
        }
    }

    public User removeMyProfilePicture() throws SQLException {
        requireSession();
        User profile = profileDAO.getProfile(identity);
        requireAvailable(profile);
        requireSession();
        String previous = profile.getProfilePicture();
        if (previous != null && !profileDAO.updateProfilePicture(identity, previous, null)) {
            throw new IllegalStateException("Your profile changed or is no longer available. Refresh and try again.");
        }
        deletePictureQuietly(previous);
        SessionManager.updateProfilePicture(identity, null);
        profile.setProfilePicture(null);
        return profile;
    }

    private void deletePictureQuietly(String filename) {
        try { pictureStore.delete(identity.userId(), filename); }
        catch (IOException error) {
            System.getLogger(ProfileService.class.getName()).log(System.Logger.Level.WARNING,
                    "Profile saved, but an unused managed picture could not be removed.", error);
        }
    }

    public User updateMyProfile(String fullName, String email) throws SQLException {
        requireSession();
        if ("STUDENT".equals(SessionManager.getCurrentUser().getRole())) {
            User profile = profileDAO.getProfile(identity);
            requireAvailable(profile);
            return updateMyStudentProfile(fullName, email, profile.getPhone());
        }
        if (fullName != null && fullName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Full name cannot contain control characters or line breaks.");
        }
        String name = fullName == null ? "" : fullName.strip().replaceAll("(?U)\\s+", " ");
        String address = email == null ? "" : email.strip();
        User current = SessionManager.getCurrentUser();
        requireSession();
        userService.validateUser(current.getUsername(), name, address, current.getRole());
        if (name.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException("Full name must contain at least one letter.");
        }
        String[] names = name.split(" ", 2);
        if (length(names[0]) > NAME_PART_LIMIT || (names.length > 1 && length(names[1]) > NAME_PART_LIMIT)) {
            throw new IllegalArgumentException("First name and remaining names must each be 80 characters or fewer.");
        }
        if (address.length() > EMAIL_LIMIT || !EMAIL.matcher(address).matches()) {
            throw new IllegalArgumentException("Enter a valid email address of 150 characters or fewer.");
        }

        // Read the current database account, so deleted/deactivated accounts cannot save.
        User profile = profileDAO.getProfile(identity);
        requireAvailable(profile);
        requireSession();
        if (!profileDAO.updateProfile(identity, name, address)) {
            throw new SecurityException("Your account is no longer available. Please sign in again.");
        }
        // A completed operation from an old login must never refresh a new login's session.
        SessionManager.updateProfile(identity, name, address);
        profile.setFullName(name);
        profile.setEmail(address);
        return profile;
    }

    public User updateMyStudentProfile(String fullName, String email, String phone) throws SQLException {
        requireSession();
        if (!"STUDENT".equals(SessionManager.getCurrentUser().getRole()))
            throw new SecurityException("A student session is required.");
        User profile = profileDAO.getProfile(identity);
        requireAvailable(profile);
        if (!Objects.equals(fullName, profile.getFullName()))
            throw new SecurityException("Your full name is managed by your administrator.");
        String address = email == null ? "" : email.strip();
        if (address.length() > EMAIL_LIMIT || !EMAIL.matcher(address).matches())
            throw new IllegalArgumentException("Enter a valid email address of 150 characters or fewer.");
        String contact = phone == null || phone.isBlank() ? null : phone.strip();
        if (contact != null && (length(contact) > 30 || contact.codePoints().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Phone must be 30 characters or fewer without control characters.");
        requireSession();
        if (!profileDAO.updateStudentContact(identity, profile.getFullName(), address, contact))
            throw new SecurityException("Your profile has changed or is no longer available. Refresh and try again.");
        SessionManager.updateProfile(identity, profile.getFullName(), address);
        profile.setEmail(address);
        profile.setPhone(contact);
        return profile;
    }

    private void requireSession() {
        SessionManager.requireSession(identity);
        User user = SessionManager.getCurrentUser();
        if (user == null || !user.isActive() || user.getRole() == null || !ROLES.contains(user.getRole())) {
            throw new SecurityException("Your account cannot access My Profile. Please sign in again.");
        }
    }

    private void requireAvailable(User profile) {
        requireSession();
        if (profile == null || !profile.isActive() || profile.getUserId() != identity.userId()
                || !ROLES.contains(profile.getRole())
                || !Objects.equals(profile.getRole(), SessionManager.getCurrentUser().getRole())) {
            throw new SecurityException("Your account is no longer available. Please sign in again.");
        }
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
