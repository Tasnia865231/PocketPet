package com.example.pocketpet.firebase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

/**
 * FIREBASE AUTHENTICATION: the ONLY place in the app that talks to Firebase Auth.
 * Other classes (LoginActivity, Settings...) call these methods and never touch FirebaseAuth directly.
 *
 * What Auth gives us:
 *   - register / log in with email + password
 *   - a UID: a unique, never-changing id for each player (use it to name their Firestore document later)
 *   - the login session is saved by Firebase on the phone, so the player stays logged in after the app closes
 */
public final class FirebaseAuthManager {
    private FirebaseAuthManager() {}

    /** Answer for register/login. Exactly one of the two methods is called, always on the main (UI) thread. */
    public interface Callback {
        void onSuccess(@NonNull FirebaseUser user);
        void onError(@NonNull String friendlyMessage);
    }

    private static FirebaseAuth auth() { return FirebaseAuth.getInstance(); }

    // ---- who is logged in? ----

    /** The logged-in player, or null when nobody is logged in. */
    @Nullable public static FirebaseUser currentUser() { return auth().getCurrentUser(); }

    public static boolean isLoggedIn() { return currentUser() != null; }

    /** The player's unique id (UID), or null when logged out. */
    @Nullable public static String uid() {
        FirebaseUser u = currentUser();
        return u == null ? null : u.getUid();
    }

    /** The name other players see (set at registration), or "Player" if missing. */
    @NonNull public static String displayName() {
        FirebaseUser u = currentUser();
        if (u == null || u.getDisplayName() == null || u.getDisplayName().trim().isEmpty()) return "Player";
        return u.getDisplayName();
    }

    // ---- register / log in / log out ----

    /**
     * Creates a NEW account. Firebase refuses an email that already has an account, so a player can never
     * get two accounts (or two profiles) with the same email. After creating the account we save the display name.
     */
    public static void register(String email, String password, final String displayName, final Callback cb) {
        auth().createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (!task.isSuccessful() || auth().getCurrentUser() == null) {
                cb.onError(friendlyError(task));
                return;
            }
            final FirebaseUser user = auth().getCurrentUser();
            UserProfileChangeRequest nameChange = new UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName).build();
            user.updateProfile(nameChange).addOnCompleteListener(t -> cb.onSuccess(user));
            // Even if saving the name fails, the account exists, so we still report success.
        });
    }

    /** Logs an existing player in. Does NOT create anything: wrong email/password gives an error. */
    public static void login(String email, String password, final Callback cb) {
        auth().signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            FirebaseUser user = auth().getCurrentUser();
            if (task.isSuccessful() && user != null) cb.onSuccess(user);
            else cb.onError(friendlyError(task));
        });
    }

    /** Logs out. The player's saved data stays safe in the cloud. */
    public static void logout() { auth().signOut(); }

    // ---- turning Firebase errors into kind messages ----

    private static String friendlyError(Task<?> task) {
        Exception e = task.getException();
        if (e instanceof FirebaseNetworkException) return "No internet. Check your connection and try again.";
        if (e instanceof FirebaseAuthWeakPasswordException) return "That password is too easy. Use at least 6 characters.";
        if (e instanceof FirebaseAuthUserCollisionException) return "This email already has an account. Try logging in.";
        if (e instanceof FirebaseAuthInvalidUserException) return "No account found, or it was disabled.";
        if (e instanceof FirebaseAuthInvalidCredentialsException) return "Wrong email or password. Please check them.";
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_TOO_MANY_REQUESTS".equals(code)) return "Too many tries. Please wait a bit and try again.";
            if ("ERROR_INVALID_EMAIL".equals(code)) return "That email does not look right.";
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) return "Email login is not turned on in Firebase.";
        }
        return "Something went wrong. Please try again.";
    }
}
