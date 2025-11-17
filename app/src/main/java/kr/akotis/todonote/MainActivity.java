package kr.akotis.todonote;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import kr.akotis.todonote.database.DatabaseHelper;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    
    private ActivityResultLauncher<Intent> importDbLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // 상태 바 색상을 검정색으로 설정
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        
        // WindowInsetsControllerCompat를 사용하여 상태 바 색상 및 아이콘 색상 설정
        WindowInsetsControllerCompat windowInsetsController = ViewCompat.getWindowInsetsController(window.getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false); // 검정 배경이므로 아이콘을 밝게
        }
        
        setContentView(R.layout.activity_main);

        setupToolbar();
        setupDrawer();
        setupBottomNavigation();
        setupWindowInsets();
        setupImportLauncher();

        // Intent에서 fragment 파라미터 확인 (위젯에서 호출된 경우)
        String fragmentName = getIntent().getStringExtra("fragment");
        if (fragmentName != null && fragmentName.equals("todo")) {
            loadFragment(new ToDoFragment());
            bottomNav.setSelectedItemId(R.id.nav_todo);
        } else if (savedInstanceState == null) {
            // 기본 화면을 메모 탭으로 설정
            loadFragment(new MemoFragment());
            bottomNav.setSelectedItemId(R.id.nav_memo);
        }
    }
    
    private void setupImportLauncher() {
        importDbLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importDatabase(uri);
                    }
                }
            }
        );
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        Toolbar toolbar = findViewById(R.id.toolbar);

        drawerToggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(item -> {
            handleDrawerItemClick(item.getItemId());
            drawerLayout.closeDrawer(navigationView);
            return true;
        });
    }

    // 상단 NavBar 목록 메뉴들
    private void handleDrawerItemClick(int itemId) {
        if (itemId == R.id.nav_completed_todo) {
            loadFragment(new CompletedToDoFragment());
        } else if (itemId == R.id.nav_export) {
            exportDatabase();
        } else if (itemId == R.id.nav_import) {
            importDatabase();
        } else if (itemId == R.id.nav_trash) {
            loadFragment(new TrashFragment());
        } else if (itemId == R.id.nav_password) {
            showPasswordSettingsDialog();
        } else if (itemId == R.id.nav_about) {
            showAboutDialog();
        }
    }

    private void showAboutDialog() {
        String versionName = "1.0";
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("정보")
                .setMessage("ToDoNote\n버전: " + versionName + "\n\n할 일과 메모를 한 곳에서 관리하는 간편한 앱입니다.\n개발자 문의: github.com/btlime3/ToDoNote/issues")
                .setPositiveButton("확인", null)
                .create()
                .show();
    }

    // 하단 NavBar 생성
    private void setupBottomNavigation() {
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = getFragmentForItem(item.getItemId());
            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });
    }

    // 하단 NavBar 선택 작업
    private Fragment getFragmentForItem(int itemId) {
        if (itemId == R.id.nav_todo) {
            return new ToDoFragment();
        } else if (itemId == R.id.nav_memo) {
            return new MemoFragment();
        } else if (itemId == R.id.nav_note) {
            return new NoteFragment();
        }
        return null;
    }

    // Fragment 로딩
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    // UI 설정
    private void setupWindowInsets() {
        // AppBarLayout에 상단 inset 적용
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.app_bar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        // FragmentContainerView에 좌우 inset 적용
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, 0);
            return insets;
        });

        // 하단 네비게이션 바에 하단 inset 적용
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });
    }

    /**
     * 데이터베이스를 내보냅니다 (다운로드 폴더에 저장).
     */
    private void exportDatabase() {
        try {
            String dbPath = DatabaseHelper.getDatabasePath(this);
            File dbFile = new File(dbPath);
            
            if (!dbFile.exists()) {
                Toast.makeText(this, "데이터베이스 파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 파일명에 타임스탬프 추가
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            String fileName = "ToDoNote_backup_" + timestamp + ".db";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): MediaStore 사용
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.Downloads.MIME_TYPE, "application/x-sqlite3");
                contentValues.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                if (uri != null) {
                    try (InputStream in = new FileInputStream(dbFile);
                         OutputStream out = resolver.openOutputStream(uri)) {
                        if (out != null) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                            Toast.makeText(this, "다운로드 폴더에 백업이 저장되었습니다: " + fileName, Toast.LENGTH_LONG).show();
                        }
                    }
                } else {
                    Toast.makeText(this, "백업 저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Android 9 이하: 직접 파일 복사
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File backupFile = new File(downloadsDir, fileName);
                
                try (InputStream in = new FileInputStream(dbFile);
                     OutputStream out = new FileOutputStream(backupFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    Toast.makeText(this, "다운로드 폴더에 백업이 저장되었습니다: " + fileName, Toast.LENGTH_LONG).show();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "백업 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 데이터베이스 가져오기 파일 선택기를 엽니다.
     */
    private void importDatabase() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/x-sqlite3", "application/octet-stream"});
        importDbLauncher.launch(intent);
    }

    /**
     * 선택한 파일로 데이터베이스를 교체합니다.
     */
    private void importDatabase(Uri uri) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("데이터베이스 가져오기")
                .setMessage("현재 데이터베이스를 선택한 파일로 교체하시겠습니까? 현재 데이터는 백업 후 교체됩니다.")
                .setPositiveButton("가져오기", (dialog, which) -> {
                    try {
                        // 현재 DB 백업
                        String dbPath = DatabaseHelper.getDatabasePath(this);
                        File dbFile = new File(dbPath);
                        File backupFile = new File(dbPath + ".backup_" + System.currentTimeMillis());
                        
                        if (dbFile.exists()) {
                            try (InputStream in = new FileInputStream(dbFile);
                                 OutputStream out = new FileOutputStream(backupFile)) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, bytesRead);
                                }
                            }
                        }

                        // 선택한 파일을 현재 DB로 복사
                        try (InputStream in = getContentResolver().openInputStream(uri);
                             OutputStream out = new FileOutputStream(dbFile)) {
                            if (in != null && out != null) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, bytesRead);
                                }
                                
                                Toast.makeText(this, "데이터베이스가 성공적으로 가져와졌습니다. 앱을 재시작합니다.", Toast.LENGTH_LONG).show();
                                
                                // 앱 재시작
                                new android.os.Handler().postDelayed(() -> {
                                    Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                                    if (intent != null) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                        startActivity(intent);
                                        finish();
                                    }
                                }, 1000);
                            } else {
                                Toast.makeText(this, "파일을 읽을 수 없습니다.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "가져오기 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showPasswordSettingsDialog() {
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        boolean hasPassword = dbHelper.hasNotePassword();
        
        if (hasPassword) {
            // 암호가 이미 설정되어 있는 경우: 기존 암호 확인 먼저
            showPasswordVerificationDialog(dbHelper);
        } else {
            // 암호가 설정되지 않은 경우: 바로 설정 다이얼로그 표시
            showPasswordSetupDialog(dbHelper);
        }
        dbHelper.close();
    }

    private void showPasswordVerificationDialog(DatabaseHelper dbHelper) {
        // 지문 인증 사용 가능 여부 확인
        BiometricManager biometricManager = BiometricManager.from(this);
        boolean canUseBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        
        if (canUseBiometric) {
            // 지문 인증이 가능하면 먼저 지문 인증 시도
            authenticateWithBiometricForSettings(dbHelper, true);
        } else {
            // 지문 인증이 불가능하면 암호 입력 다이얼로그 표시
            showPasswordInputDialogForSettings(dbHelper);
        }
    }

    private void showPasswordInputDialogForSettings(DatabaseHelper dbHelper) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        MaterialButton btnFingerprint = dialogView.findViewById(R.id.btn_fingerprint);
        
        passwordLayout.setHint("기존 암호");

        // 지문 인증 사용 가능 여부 확인
        BiometricManager biometricManager = BiometricManager.from(this);
        boolean canUseBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        
        if (!canUseBiometric) {
            btnFingerprint.setVisibility(View.GONE);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("암호 확인")
                .setMessage("암호 설정을 변경하려면 기존 암호를 입력해주세요.")
                .setView(dialogView)
                .setPositiveButton("확인", (d, which) -> {
                    String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
                    if (dbHelper.verifyNotePassword(password)) {
                        // 암호가 맞으면 설정/변경/제거 다이얼로그 표시
                        showPasswordChangeDialog(dbHelper);
                    } else {
                        Toast.makeText(this, "암호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .create();

        // 지문 인증 버튼 클릭
        btnFingerprint.setOnClickListener(v -> {
            dialog.dismiss();
            authenticateWithBiometricForSettings(dbHelper, false);
        });

        dialog.show();
    }

    private void authenticateWithBiometricForSettings(DatabaseHelper dbHelper, boolean showPasswordOnCancel) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("지문 인증")
                .setSubtitle("지문을 인식하여 암호 설정에 접근하세요")
                .setNegativeButtonText(showPasswordOnCancel ? "암호 입력" : "취소")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        // 지문 인증 성공 시 암호 변경 다이얼로그 표시
                        showPasswordChangeDialog(dbHelper);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            // 사용자가 취소하거나 "암호 입력" 버튼을 누른 경우
                            if (showPasswordOnCancel) {
                                showPasswordInputDialogForSettings(dbHelper);
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "지문 인증 실패: " + errString, Toast.LENGTH_SHORT).show();
                            if (showPasswordOnCancel) {
                                // 오류 발생 시에도 암호 입력 다이얼로그 표시
                                showPasswordInputDialogForSettings(dbHelper);
                            }
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(MainActivity.this, "지문 인증에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        biometricPrompt.authenticate(promptInfo);
    }

    private void showPasswordSetupDialog(DatabaseHelper dbHelper) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_settings, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputLayout confirmPasswordLayout = dialogView.findViewById(R.id.confirm_password_layout);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        TextInputEditText etConfirmPassword = dialogView.findViewById(R.id.et_confirm_password);

        passwordLayout.setHint("암호");
        confirmPasswordLayout.setHint("암호 확인");

        new MaterialAlertDialogBuilder(this)
                .setTitle("노트 암호 설정")
                .setView(dialogView)
                .setPositiveButton("설정", (dialog, which) -> {
                    String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
                    String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

                    if (password.isEmpty()) {
                        Toast.makeText(this, "암호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!password.equals(confirmPassword)) {
                        Toast.makeText(this, "암호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    dbHelper.setNotePassword(password);
                    Toast.makeText(this, "암호가 설정되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showPasswordChangeDialog(DatabaseHelper dbHelper) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_settings, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputLayout confirmPasswordLayout = dialogView.findViewById(R.id.confirm_password_layout);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        TextInputEditText etConfirmPassword = dialogView.findViewById(R.id.et_confirm_password);

        passwordLayout.setHint("새 암호");
        confirmPasswordLayout.setHint("새 암호 확인");

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("노트 암호 변경")
                .setView(dialogView)
                .setPositiveButton("변경", (dialog, which) -> {
                    String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
                    String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

                    if (password.isEmpty()) {
                        Toast.makeText(this, "암호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!password.equals(confirmPassword)) {
                        Toast.makeText(this, "암호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    dbHelper.setNotePassword(password);
                    Toast.makeText(this, "암호가 변경되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .setNeutralButton("암호 제거", (dialog, which) -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("암호 제거")
                            .setMessage("노트 암호를 제거하시겠습니까? 모든 잠금된 노트는 잠금 해제됩니다.")
                            .setPositiveButton("제거", (d, w) -> {
                                // 모든 잠금된 노트를 먼저 복호화 (암호 제거 전에 복호화해야 함)
                                List<kr.akotis.todonote.model.NoteItem> notes = dbHelper.getAllNotes();
                                for (kr.akotis.todonote.model.NoteItem note : notes) {
                                    if (note.isLocked()) {
                                        // 잠금 해제하면서 복호화된 내용으로 저장
                                        dbHelper.toggleNoteLock(note.getId(), false);
                                    }
                                }
                                
                                // 데이터베이스에서 암호 해시 제거
                                dbHelper.removeNotePassword();
                                
                                Toast.makeText(this, "암호가 제거되었습니다.", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("취소", null)
                            .show();
                });

        builder.show();
    }
}