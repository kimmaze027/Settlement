package com.kimmiro.app;

import android.os.Handler;
import android.os.Looper;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.concurrent.Executor;

/**
 * Local app-lock biometric prompt. Gates re-entry to an already-authenticated
 * session: the server session cookie is unaffected; this only proves the device
 * owner is present. PIN is the always-available fallback (handled in JS).
 *
 * Uses androidx.biometric so the system BiometricPrompt works across API 24+
 * (the platform android.hardware.biometrics.BiometricPrompt is API 28+ only).
 * BridgeActivity is a FragmentActivity, which BiometricPrompt requires.
 */
@CapacitorPlugin(name = "Biometric")
public class BiometricPlugin extends Plugin {

    @PluginMethod
    public void isAvailable(PluginCall call) {
        BiometricManager bm = BiometricManager.from(getContext());
        int can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        JSObject ret = new JSObject();
        ret.put("available", can == BiometricManager.BIOMETRIC_SUCCESS);
        ret.put("code", can);
        call.resolve(ret);
    }

    @PluginMethod
    public void authenticate(PluginCall call) {
        final String title = call.getString("title", "인증");
        final String subtitle = call.getString("subtitle", "");
        final FragmentActivity activity = (FragmentActivity) getActivity();
        // BiometricPrompt must be constructed and authenticate() invoked on the UI thread.
        getActivity().runOnUiThread(() -> {
            Executor executor = new Handler(Looper.getMainLooper())::post;
            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText("취소")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .setConfirmationRequired(false)
                    .build();
            BiometricPrompt prompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    call.resolve(new JSObject());
                }

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    // Error codes: 5 = user canceled, 10 = canceled via negative button, 13 = lockout.
                    // Reject in all cases; the JS side treats any rejection as "stay on PIN entry".
                    call.reject(errString != null ? errString.toString() : ("auth error " + errorCode), String.valueOf(errorCode));
                }
                // onAuthenticationFailed() (single bad attempt) is intentionally NOT handled:
                // the system prompt lets the user retry, and we only resolve/reject on
                // success or a terminal error.
            });
            prompt.authenticate(info);
        });
    }
}
