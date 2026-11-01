package kr.akotis.todonote.util;

import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import kr.akotis.todonote.R;
import kr.akotis.todonote.database.DatabaseHelper;

/** 잠긴 노트를 열기 전에 지문 또는 암호로 인증한다. */
public final class NoteUnlocker {
    private final Fragment fragment;
    private final DatabaseHelper dbHelper;
    private final Runnable onUnlocked;

    private NoteUnlocker(Fragment fragment, DatabaseHelper dbHelper, Runnable onUnlocked) {
        this.fragment = fragment;
        this.dbHelper = dbHelper;
        this.onUnlocked = onUnlocked;
    }

    public static void unlock(Fragment fragment, DatabaseHelper dbHelper, Runnable onUnlocked) {
        new NoteUnlocker(fragment, dbHelper, onUnlocked).start();
    }

    private void start() {
        if (!dbHelper.hasNotePassword()) {
            // 암호가 설정되지 않은 경우: 암호 설정 안내
            new MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle("암호 미설정")
                    .setMessage("노트 암호가 설정되지 않았습니다. 왼쪽 메뉴에서 '암호 설정'을 통해 암호를 설정해주세요.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        if (canUseBiometric()) {
            authenticateWithBiometric(true);
        } else {
            showPasswordDialog();
        }
    }

    private boolean canUseBiometric() {
        return BiometricManager.from(fragment.requireContext())
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void showPasswordDialog() {
        View dialogView = fragment.getLayoutInflater().inflate(R.layout.dialog_password, null);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        MaterialButton btnFingerprint = dialogView.findViewById(R.id.btn_fingerprint);

        String hint = dbHelper.getNotePasswordHint();
        String message = hint != null && !hint.isEmpty() ? "힌트: " + hint : null;

        if (!canUseBiometric()) {
            btnFingerprint.setVisibility(View.GONE);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle("잠금 해제")
                .setMessage(message)
                .setView(dialogView)
                .setPositiveButton("확인", (d, which) -> {
                    String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
                    if (password.isEmpty()) {
                        toast("암호를 입력해주세요.");
                    } else if (dbHelper.verifyNotePassword(password)) {
                        onUnlocked.run();
                    } else {
                        toast("암호가 올바르지 않습니다.");
                    }
                })
                .setNegativeButton("취소", null)
                .create();

        btnFingerprint.setOnClickListener(v -> {
            dialog.dismiss();
            authenticateWithBiometric(false);
        });

        dialog.show();
    }

    private void authenticateWithBiometric(boolean showPasswordOnCancel) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("지문 인증")
                .setSubtitle("지문을 인식하여 노트 잠금을 해제하세요")
                .setNegativeButtonText(showPasswordOnCancel ? "암호 입력" : "취소")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(
                fragment,
                ContextCompat.getMainExecutor(fragment.requireContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        if (fragment.isAdded()) {
                            onUnlocked.run();
                        }
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (!fragment.isAdded()) {
                            return;
                        }
                        boolean userChose = errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON;
                        if (!userChose) {
                            toast("지문 인증 실패: " + errString);
                        }
                        // 취소하거나 "암호 입력"을 누른 경우, 또는 오류가 난 경우 암호로 인증할 수 있게 한다.
                        if (showPasswordOnCancel) {
                            showPasswordDialog();
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        if (fragment.isAdded()) {
                            toast("지문 인증에 실패했습니다.");
                        }
                    }
                }
        );

        biometricPrompt.authenticate(promptInfo);
    }

    private void toast(String message) {
        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
