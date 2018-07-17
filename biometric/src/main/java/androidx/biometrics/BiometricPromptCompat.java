/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.biometrics;

import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import java.security.Signature;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.Mac;

/**
 * A class that manages a system-provided biometric prompt. On devices running P and above, this
 * will show a system-provided authentication prompt, using a device's supported biometric
 * (fingerprint, iris, face, etc). On devices before P, this will show a dialog prompting for
 * fingerprint authentication. The prompt will persist across orientation changes unless explicitly
 * canceled by the client. For security reasons, the prompt will automatically dismiss when the
 * activity is no longer in the foreground.
 */
public class BiometricPromptCompat implements BiometricConstants {

    private static final String TAG = "BiometricPromptCompat";
    private static final boolean DEBUG = false;

    static final String DIALOG_FRAGMENT_TAG = "FingerprintDialogFragment";
    static final String FINGERPRINT_HELPER_FRAGMENT_TAG = "FingerprintHelperFragment";
    static final String BIOMETRIC_FRAGMENT_TAG = "BiometricFragment";
    static final String KEY_TITLE = "title";
    static final String KEY_SUBTITLE = "subtitle";
    static final String KEY_DESCRIPTION = "description";
    static final String KEY_NEGATIVE_TEXT = "negative_text";

    /**
     * A wrapper class for the crypto objects supported by BiometricPrompt. Currently the
     * framework supports {@link Signature}, {@link Cipher}, and {@link Mac} objects.
     */
    public static class CryptoObject {
        private final Signature mSignature;
        private final Cipher mCipher;
        private final Mac mMac;

        public CryptoObject(@NonNull Signature signature) {
            mSignature = signature;
            mCipher = null;
            mMac = null;
        }

        public CryptoObject(@NonNull Cipher cipher) {
            mCipher = cipher;
            mSignature = null;
            mMac = null;
        }

        public CryptoObject(@NonNull Mac mac) {
            mMac = mac;
            mCipher = null;
            mSignature = null;
        }

        /**
         * Get {@link Signature} object.
         * @return {@link Signature} object or null if this doesn't contain one.
         */
        @Nullable
        public Signature getSignature() {
            return mSignature;
        }

        /**
         * Get {@link Cipher} object.
         * @return {@link Cipher} object or null if this doesn't contain one.
         */
        @Nullable
        public Cipher getCipher() {
            return mCipher;
        }

        /**
         * Get {@link Mac} object.
         * @return {@link Mac} object or null if this doesn't contain one.
         */
        @Nullable
        public Mac getMac() {
            return mMac;
        }
    }

    /**
     * Container for callback data from {@link #authenticate(PromptInfo)} and
     * {@link #authenticate(PromptInfo, CryptoObject)}.
     */
    public static class AuthenticationResult {
        private final CryptoObject mCryptoObject;

        /**
         * @param crypto
         */
        AuthenticationResult(CryptoObject crypto) {
            mCryptoObject = crypto;
        }

        /**
         * Obtain the crypto object associated with this transaction
         * @return crypto object provided to {@link #authenticate(PromptInfo, CryptoObject)}.
         */
        public CryptoObject getCryptoObject() {
            return mCryptoObject;
        }
    }

    /**
     * Callback structure provided to {@link BiometricPromptCompat}. Users of {@link
     * BiometricPromptCompat} must provide an implementation of this for listening to
     * fingerprint events.
     */
    public abstract static class AuthenticationCallback {
        /**
         * Called when an unrecoverable error has been encountered and the operation is complete.
         * No further actions will be made on this object.
         * @param errorCode An integer identifying the error message. The error message will usually
         *                  be one of the BIOMETRIC_ERROR constants.
         * @param errString A human-readable error string that can be shown on an UI
         */
        public void onAuthenticationError(int errorCode, CharSequence errString) {}

        /**
         * Called when a biometric is recognized.
         * @param result An object containing authentication-related data
         */
        public void onAuthenticationSucceeded(AuthenticationResult result) {}

        /**
         * Called when a biometric is valid but not recognized.
         */

        public void onAuthenticationFailed() {}
    }

    /**
     * A class that contains a builder which returns the {@link PromptInfo} to be used in
     * {@link #authenticate(PromptInfo, CryptoObject)} and {@link #authenticate(PromptInfo)}.
     */
    public static class PromptInfo {

        /**
         * A builder that collects arguments to be shown on the system-provided biometric dialog.
         */
        public static class Builder {
            private final Bundle mBundle = new Bundle();

            /**
             * Required: Set the title to display.
             */
            public Builder setTitle(@NonNull CharSequence title) {
                mBundle.putCharSequence(KEY_TITLE, title);
                return this;
            }

            /**
             * Optional: Set the subtitle to display.
             */
            public Builder setSubtitle(@NonNull CharSequence subtitle) {
                mBundle.putCharSequence(KEY_SUBTITLE, subtitle);
                return this;
            }

            /**
             * Optional: Set the description to display.
             */
            public Builder setDescription(@NonNull CharSequence description) {
                mBundle.putCharSequence(KEY_DESCRIPTION, description);
                return this;
            }

            /**
             * Required: Set the text for the negative button. This would typically be used as a
             * "Cancel" button, but may be also used to show an alternative method for
             * authentication, such as screen that asks for a backup password.
             * @param text
             * @return
             */
            public Builder setNegativeButtonText(@NonNull CharSequence text) {
                mBundle.putCharSequence(KEY_NEGATIVE_TEXT, text);
                return this;
            }

            /**
             * Creates a {@link BiometricPromptCompat}.
             * @return a {@link BiometricPromptCompat}
             * @throws IllegalArgumentException if any of the required fields are not set.
             */
            public PromptInfo build() {
                final CharSequence title = mBundle.getCharSequence(KEY_TITLE);
                final CharSequence negative = mBundle.getCharSequence(KEY_NEGATIVE_TEXT);

                if (TextUtils.isEmpty(title)) {
                    throw new IllegalArgumentException("Title must be set and non-empty");
                } else if (TextUtils.isEmpty(negative)) {
                    throw new IllegalArgumentException("Negative button text must be set and "
                            + "non-empty");
                }
                return new PromptInfo(mBundle);
            }
        }

        private Bundle mBundle;

        PromptInfo(Bundle bundle) {
            mBundle = bundle;
        }

        Bundle getBundle() {
            return mBundle;
        }
    }

    // Passed in from the client.
    final FragmentActivity mFragmentActivity;
    final Executor mExecutor;
    final AuthenticationCallback mAuthenticationCallback;

    // Created internally for devices before P.
    FingerprintDialogFragment mFingerprintDialogFragment;
    FingerprintHelperFragment mFingerprintHelperFragment;

    // Created internally for devices P and above.
    BiometricFragment mBiometricFragment;

    /**
     *  A shim to interface with the framework API and simplify the support library's API.
     *  The support library sends onAuthenticationError when the negative button is pressed.
     *  Conveniently, the {@link FingerprintDialogFragment} also uses the
     *  {@DialogInterface.OnClickListener} for its buttons ;)
     */
    final DialogInterface.OnClickListener mNegativeButtonListener =
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                CharSequence errorText =
                                        mBiometricFragment.getNegativeButtonText();
                                mAuthenticationCallback.onAuthenticationError(
                                        BIOMETRIC_ERROR_NEGATIVE_BUTTON,
                                        errorText);
                                mFragmentActivity.getSupportFragmentManager().beginTransaction()
                                        .remove(mBiometricFragment).commit();
                            } else {
                                CharSequence errorText =
                                        mFingerprintDialogFragment.getNegativeButtonText();
                                mAuthenticationCallback.onAuthenticationError(
                                        BIOMETRIC_ERROR_NEGATIVE_BUTTON,
                                        errorText);
                                mFingerprintHelperFragment.cancel(
                                        FingerprintHelperFragment
                                                .USER_CANCELED_FROM_NEGATIVE_BUTTON);
                            }
                        }
                    });
                }
            };

    /**
     * Observe the client's lifecycle. Keep authenticating across configuration changes, but
     * dismiss the prompt if the client goes into the background.
     */
    private final LifecycleObserver mLifecycleObserver = new LifecycleObserver() {
        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        void onPause() {
            if (!mFragmentActivity.isChangingConfigurations()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (mBiometricFragment != null) {
                        mBiometricFragment.cancel();
                    }
                } else {
                    // May be null if no authentication is occurring.
                    if (mFingerprintDialogFragment != null) {
                        mFingerprintDialogFragment.dismiss();
                    }
                    if (mFingerprintHelperFragment != null) {
                        mFingerprintHelperFragment.cancel(
                                FingerprintHelperFragment.USER_CANCELED_FROM_NONE);
                    }
                }
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        void onResume() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mBiometricFragment = (BiometricFragment) mFragmentActivity
                        .getSupportFragmentManager().findFragmentByTag(BIOMETRIC_FRAGMENT_TAG);
                if (DEBUG) Log.v(TAG, "BiometricFragment: " + mBiometricFragment);
                if (mBiometricFragment != null) {
                    mBiometricFragment.setCallbacks(mExecutor, mNegativeButtonListener,
                            mAuthenticationCallback);
                }
            } else {
                mFingerprintDialogFragment = (FingerprintDialogFragment) mFragmentActivity
                        .getSupportFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG);
                mFingerprintHelperFragment = (FingerprintHelperFragment) mFragmentActivity
                        .getSupportFragmentManager().findFragmentByTag(
                                FINGERPRINT_HELPER_FRAGMENT_TAG);

                if (DEBUG) Log.v(TAG, "FingerprintDialogFragment: " + mFingerprintDialogFragment);
                if (mFingerprintDialogFragment != null && mFingerprintHelperFragment != null) {
                    mFingerprintDialogFragment.setNegativeButtonListener(mNegativeButtonListener);
                    mFingerprintHelperFragment.setCallback(mExecutor, mAuthenticationCallback);
                    mFingerprintHelperFragment.setHandler(mFingerprintDialogFragment.getHandler());
                }
            }
        }
    };

    /**
     * Constructs a {@link BiometricPromptCompat} which can be used to prompt the user for
     * authentication. The authenticaton prompt created by
     * {@link BiometricPromptCompat#authenticate(PromptInfo, CryptoObject)} and
     * {@link BiometricPromptCompat#authenticate(PromptInfo)} will persist across device
     * configuration changes by default. If authentication is in progress, re-creating
     * the {@link BiometricPromptCompat} can be used to update the {@link Executor} and
     * {@link AuthenticationCallback}. This should be used to update the
     * {@link AuthenticationCallback} after configuration changes.
     * such as {@link FragmentActivity#onCreate(Bundle)}.
     *
     * @param fragmentActivity A reference to the client's activity.
     * @param executor An executor to handle callback events.
     * @param callback An object to receive authentication events.
     */
    public BiometricPromptCompat(@NonNull FragmentActivity fragmentActivity,
            @NonNull Executor executor, @NonNull AuthenticationCallback callback) {
        if (fragmentActivity == null) {
            throw new IllegalArgumentException("FragmentActivity must not be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Executor must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("AuthenticationCallback must not be null");
        }
        mFragmentActivity = fragmentActivity;
        mExecutor = executor;
        mAuthenticationCallback = callback;

        mFragmentActivity.getLifecycle().addObserver(mLifecycleObserver);
    }

    /**
     * Shows the biometric prompt. The prompt survives lifecycle changes by default. To cancel the
     * authentication, use {@link #cancelAuthentication()}.
     * @param info The information that will be displayed on the prompt. Create this object using
     *             {@link BiometricPromptCompat.PromptInfo.Builder}.
     * @param crypto The crypto object associated with the authentication.
     */
    public void authenticate(@NonNull PromptInfo info, @NonNull CryptoObject crypto) {
        if (info == null) {
            throw new IllegalArgumentException("PromptInfo can not be null");
        } else if (crypto == null) {
            throw new IllegalArgumentException("CryptoObject can not be null");
        }
        authenticateInternal(info, crypto);
    }

    /**
     * Shows the biometric prompt. The prompt survives lifecycle changes by default. To cancel the
     * authentication, use {@link #cancelAuthentication()}.
     * @param info The information that will be displayed on the prompt. Create this object using
     *             {@link BiometricPromptCompat.PromptInfo.Builder}.
     */
    public void authenticate(@NonNull PromptInfo info) {
        if (info == null) {
            throw new IllegalArgumentException("PromptInfo can not be null");
        }
        authenticateInternal(info, null /* crypto */);
    }

    private void authenticateInternal(@NonNull PromptInfo info, @Nullable CryptoObject crypto) {
        final Bundle bundle = info.getBundle();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (mBiometricFragment == null) {
                // Create the fragment that wraps BiometricPrompt
                mBiometricFragment = BiometricFragment.newInstance(bundle);
                mBiometricFragment.setCryptoObject(crypto);
                mBiometricFragment.setCallbacks(mExecutor, mNegativeButtonListener,
                        mAuthenticationCallback);
            }
            mFragmentActivity.getSupportFragmentManager().beginTransaction()
                    .add(mBiometricFragment, BIOMETRIC_FRAGMENT_TAG).commit();
        } else {
            // Create the UI
            if (mFingerprintDialogFragment == null) {
                mFingerprintDialogFragment = FingerprintDialogFragment.newInstance(bundle);
                mFingerprintDialogFragment.setNegativeButtonListener(mNegativeButtonListener);
            }
            mFingerprintDialogFragment.show(mFragmentActivity.getSupportFragmentManager(),
                    DIALOG_FRAGMENT_TAG);

            // Create the connection to FingerprintManager
            if (mFingerprintHelperFragment == null) {
                mFingerprintHelperFragment = FingerprintHelperFragment.newInstance();
                mFingerprintHelperFragment.setCryptoObject(crypto);
                mFingerprintHelperFragment.setCallback(mExecutor, mAuthenticationCallback);
                mFingerprintHelperFragment.setHandler(mFingerprintDialogFragment.getHandler());
            }
            mFragmentActivity.getSupportFragmentManager().beginTransaction()
                    .add(mFingerprintHelperFragment, FINGERPRINT_HELPER_FRAGMENT_TAG).commit();
        }
    }

    /**
     * Cancels the biometric authentication, and dismisses the dialog upon confirmation from the
     * biometric service.
     */
    public void cancelAuthentication() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (mBiometricFragment != null) {
                mBiometricFragment.cancel();
            }
        } else {
            if (mFingerprintHelperFragment != null && mFingerprintDialogFragment != null) {
                mFingerprintHelperFragment.cancel(
                        FingerprintHelperFragment.USER_CANCELED_FROM_NONE);
                mFingerprintDialogFragment.dismiss();
            }
        }
    }
}