package com.example.myquotes.drive;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.myquotes.BuildConfig;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.Collections;

/**
 * Public facade for Google Drive auth plumbing. All other modules talk to this class only; the
 * Credential Manager sign-in call, the Authorization API drive.file consent request, and the
 * connected-account/enabled SharedPreferences are internal details. Does not perform any Drive
 * reads or writes itself - that is later slices' job.
 */
public final class DriveAuth {
    private static final String TAG = "DriveAuth";

    private static final String PREFS_NAME = "DriveAuthPrefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_ACCOUNT_EMAIL = "account_email";

    public static final String DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file";

    public interface AccountCallback {
        void onSuccess(String accountEmail);
        void onCancelled();
        void onFailed(Exception e);
    }

    public interface AuthorizationCallback {
        void onGranted();
        void onResolutionRequired(android.app.PendingIntent pendingIntent);
        void onFailed(Exception e);
    }

    private DriveAuth() {}

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static String getConnectedAccountEmail(Context context) {
        return prefs(context).getString(KEY_ACCOUNT_EMAIL, null);
    }

    /** Step 1: shows the Credential Manager account chooser for Google sign-in. */
    public static void signIn(Activity activity, AccountCallback callback) {
        CredentialManager credentialManager = CredentialManager.create(activity);

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setServerClientId(BuildConfig.DRIVE_OAUTH_CLIENT_ID)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        String email = extractEmail(result.getCredential());
                        if (email == null) {
                            callback.onFailed(new IllegalStateException("Unexpected credential type"));
                            return;
                        }
                        callback.onSuccess(email);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.w(TAG, "Sign-in failed", e);
                        if (e instanceof GetCredentialCancellationException) {
                            callback.onCancelled();
                        } else {
                            callback.onFailed(e);
                        }
                    }
                });
    }

    private static String extractEmail(Credential credential) {
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            try {
                GoogleIdTokenCredential googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
                return googleIdTokenCredential.getEmail();
            } catch (Exception e) {
                Log.w(TAG, "Failed to parse Google ID token credential", e);
            }
        }
        return null;
    }

    /**
     * Step 2: requests the drive.file scope for the signed-in account via the Authorization API.
     * If consent UI is required, {@link AuthorizationCallback#onResolutionRequired} fires with a
     * PendingIntent the caller must launch; feed the result back through
     * {@link #completeAuthorizationResult(Context, Intent)}.
     */
    public static void authorizeDriveAccess(Activity activity, AuthorizationCallback callback) {
        AuthorizationRequest authorizationRequest = AuthorizationRequest.builder()
                .setRequestedScopes(Collections.singletonList(new Scope(DRIVE_FILE_SCOPE)))
                .build();

        Identity.getAuthorizationClient(activity)
                .authorize(authorizationRequest)
                .addOnSuccessListener(authorizationResult -> {
                    if (authorizationResult.hasResolution()) {
                        callback.onResolutionRequired(authorizationResult.getPendingIntent());
                    } else {
                        callback.onGranted();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Drive authorization failed", e);
                    callback.onFailed(e);
                });
    }

    /** Finishes step 2 after the consent UI returns successfully. Throws if consent was denied. */
    public static AuthorizationResult completeAuthorizationResult(Context context, Intent data) throws ApiException {
        return Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data);
    }

    /** Persists the connected account and turns the feature on. Call once authorization succeeds. */
    public static void markConnected(Context context, String accountEmail) {
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_ACCOUNT_EMAIL, accountEmail)
                .apply();
    }

    /** Signs out, clears the connected account, and turns the feature off. */
    public static void disconnect(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, false)
                .remove(KEY_ACCOUNT_EMAIL)
                .apply();

        CredentialManager credentialManager = CredentialManager.create(context);
        credentialManager.clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                new CancellationSignal(),
                ContextCompat.getMainExecutor(context),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override
                    public void onResult(Void result) {
                        Log.d(TAG, "Cleared credential state");
                    }

                    @Override
                    public void onError(ClearCredentialException e) {
                        Log.w(TAG, "Failed to clear credential state", e);
                    }
                });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
