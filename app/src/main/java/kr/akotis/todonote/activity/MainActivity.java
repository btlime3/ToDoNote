package kr.akotis.todonote.activity;

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
import android.widget.ImageView;
import android.widget.TextView;
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

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

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

import kr.akotis.todonote.R;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.fragment.CompletedToDoFragment;
import kr.akotis.todonote.fragment.MemoFragment;
import kr.akotis.todonote.fragment.MessageFragment;
import kr.akotis.todonote.fragment.NoteFragment;
import kr.akotis.todonote.fragment.ToDoFragment;
import kr.akotis.todonote.fragment.TrashFragment;

import com.google.firebase.messaging.FirebaseMessaging;
import android.view.Menu;
import android.view.MenuItem;
import android.content.ClipData;
import android.content.ClipboardManager;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    
    private ActivityResultLauncher<Intent> importDbLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    
    private MenuItem notificationMenuItem;
    private DatabaseHelper dbHelper;
    
    private static final String PREF_APP_LOCK = "app_lock_settings";
    private static final String KEY_APP_LOCK_ENABLED = "app_lock_enabled";
    private static final String KEY_FIRST_BIOMETRIC_AUTH = "first_biometric_auth";
    private static final String KEY_BACKUP_RESTORED = "backup_restored";
    private static final String KEY_TESTER_MODE_ENABLED = "tester_mode_enabled";
    private boolean isAppUnlocked = false;
    private int notificationTapCount = 0;
    private static final int TESTER_MODE_TAP_COUNT = 20;

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

        dbHelper = new DatabaseHelper(this);
        
        // 테스터 모드 터치 영역 설정
        setupTesterModeTouchArea();
        
        setupToolbar();
        setupDrawer();
        setupBottomNavigation();
        setupWindowInsets();
        setupImportLauncher();
        setupNotificationPermission();
        
        // 알림 권한 확인 및 요청
        checkAndRequestNotificationPermission();
        
        // 배터리 최적화 해제 요청
        checkAndRequestBatteryOptimization();
        
        // 알림 채널 미리 생성 (앱이 꺼진 상태에서도 알림이 작동하도록)
        createNotificationChannel();
        
        // 앱 잠금 확인
        if (isAppLockEnabled() && dbHelper.hasNotePassword() && !isAppUnlocked) {
            showAppLockAuthDialog();
            return; // 인증 전까지 화면 로드 중단
        }

        // Intent에서 fragment 파라미터 확인 (위젯에서 호출된 경우 또는 FCM 알림에서 호출된 경우)
        loadInitialFragment(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // 새로운 Intent 설정

        // 알림 클릭으로 인한 Fragment 이동 처리
        String fragmentName = intent.getStringExtra("fragment");
        if ("todo".equals(fragmentName)) {
            loadFragment(new ToDoFragment());
            bottomNav.setSelectedItemId(R.id.nav_todo);
        } else if ("message".equals(fragmentName)) {
            handleFcmNotificationIntent(intent);
            loadFragment(new MessageFragment());
            bottomNav.setSelectedItemId(-1);
        }
    }

    private void loadInitialFragment(Bundle savedInstanceState) {
        String fragmentName = getIntent().getStringExtra("fragment");
        if (fragmentName != null) {
            if (fragmentName.equals("todo")) {
                loadFragment(new ToDoFragment());
                bottomNav.setSelectedItemId(R.id.nav_todo);
            } else if (fragmentName.equals("message")) {
                // FCM 알림에서 왔는지 확인하고 메시지 저장
                handleFcmNotificationIntent(getIntent());
                loadFragment(new kr.akotis.todonote.fragment.MessageFragment());
                // 메시지 탭은 네비게이션 메뉴에 없으므로 선택 해제
                bottomNav.setSelectedItemId(-1);
            } else if (savedInstanceState == null) {
                loadFragment(new MemoFragment());
                bottomNav.setSelectedItemId(R.id.nav_memo);
            }
        } else if (savedInstanceState == null) {
            // 기본 화면을 메모 탭으로 설정
            loadFragment(new MemoFragment());
            bottomNav.setSelectedItemId(R.id.nav_memo);
        }
    }

    /**
     * FCM 알림 클릭으로 인한 Intent 처리
     * data payload만 사용하므로 onMessageReceived에서 이미 저장됨
     */
    private void handleFcmNotificationIntent(Intent intent) {
        // data payload만 사용하므로 onMessageReceived에서 이미 저장됨
        // 별도 처리 불필요
    }

/**
     * 암호 힌트를 포함한 메시지를 생성합니다.
     */
    private String buildPasswordMessage(String baseMessage) {
        String hint = dbHelper.getNotePasswordHint();
        String message = baseMessage;
        if (hint != null && !hint.isEmpty()) {
            message += "\n\n힌트: " + hint;
        }
        return message;
    }

    private boolean isAppLockEnabled() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_APP_LOCK, MODE_PRIVATE);
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false);
    }

    private void setAppLockEnabled(boolean enabled) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_APP_LOCK, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply();
    }
    
    private void showAppLockAuthDialog() {
        // 백업 복원 여부 확인
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_APP_LOCK, MODE_PRIVATE);
        boolean isBackupRestored = prefs.getBoolean(KEY_BACKUP_RESTORED, false);
        boolean isFirstBiometricAuth = prefs.getBoolean(KEY_FIRST_BIOMETRIC_AUTH, true);

        // 백업 복원 시 또는 첫 지문 인증 시에는 암호 입력으로만 인증
        if (isBackupRestored) {
            showPasswordInputForAppUnlock();
            return;
        }

        // 지문 인증 사용 가능 여부 확인
        BiometricManager biometricManager = BiometricManager.from(this);
        boolean canUseBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;

        if (canUseBiometric && !isFirstBiometricAuth) {
            // 일반 지문 인증
            authenticateForAppUnlock(true);
        } else if (canUseBiometric && isFirstBiometricAuth) {
            // 첫 지문 인증 시: 지문 인증 후 암호 확인
            authenticateForFirstBiometricAuth();
        } else {
            // 지문 불가능 시: 암호 입력
            showPasswordInputForAppUnlock();
        }
    }
    
    private void authenticateForAppUnlock(boolean showPasswordOnCancel) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("앱 잠금 해제")
                .setSubtitle("지문을 인식하여 앱을 잠금 해제하세요")
                .setNegativeButtonText(showPasswordOnCancel ? "암호 입력" : "취소")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        isAppUnlocked = true;
                        loadInitialFragment(null);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            if (showPasswordOnCancel) {
                                showPasswordInputForAppUnlock();
                            } else {
                                finish(); // 앱 종료
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "지문 인증 실패: " + errString, Toast.LENGTH_SHORT).show();
                            if (showPasswordOnCancel) {
                                showPasswordInputForAppUnlock();
                            } else {
                                finish();
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

    private void authenticateForFirstBiometricAuth() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("지문 인증 설정")
                .setSubtitle("지문을 등록하려면 먼저 암호를 확인해주세요")
                .setNegativeButtonText("암호 입력")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        // 지문 인증 성공 시 암호 확인 다이얼로그 표시
                        showPasswordInputForBiometricVerification();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            // 암호 입력으로 전환
                            showPasswordInputForBiometricVerification();
                        } else {
                            Toast.makeText(MainActivity.this, "지문 인증 실패: " + errString, Toast.LENGTH_SHORT).show();
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

    private void showPasswordInputForBiometricVerification() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        MaterialButton btnFingerprint = dialogView.findViewById(R.id.btn_fingerprint);

        passwordLayout.setHint("암호 확인");

        // 힌트 표시
        String message = buildPasswordMessage("지문 인증을 사용하려면 암호를 확인해주세요.");

        // 지문 인증 사용 가능 여부 확인
        BiometricManager biometricManager = BiometricManager.from(this);
        boolean canUseBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;

        if (!canUseBiometric) {
            btnFingerprint.setVisibility(View.GONE);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("지문 인증 설정")
                .setMessage(message)
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("확인", null)
                .setNegativeButton("취소", (d, which) -> finish())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
                if (dbHelper.verifyNotePassword(password)) {
                    // 암호 확인 성공: 지문 인증 활성화
                    android.content.SharedPreferences prefs = getSharedPreferences(PREF_APP_LOCK, MODE_PRIVATE);
                    prefs.edit().putBoolean(KEY_FIRST_BIOMETRIC_AUTH, false).apply();

                    isAppUnlocked = true;
                    dialog.dismiss();
                    loadInitialFragment(null);
                } else {
                    Toast.makeText(this, "암호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 지문 인증 버튼 클릭
        btnFingerprint.setOnClickListener(v -> {
            dialog.dismiss();
            authenticateForFirstBiometricAuth();
        });

        dialog.show();
    }

    private void showPasswordInputForAppUnlock() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        MaterialButton btnFingerprint = dialogView.findViewById(R.id.btn_fingerprint);

        passwordLayout.setHint("암호");

        // 힌트 표시
        String message = buildPasswordMessage("앱을 사용하려면 암호를 입력해주세요.");

        // 지문 인증 사용 가능 여부 확인
        BiometricManager biometricManager = BiometricManager.from(this);
        boolean canUseBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;

        if (!canUseBiometric) {
            btnFingerprint.setVisibility(View.GONE);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("앱 잠금 해제")
                .setMessage(message)
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("확인", null) // 후에 설정
                .setNegativeButton("종료", (d, which) -> finish())
                .create();
        
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
                if (dbHelper.verifyNotePassword(password)) {
                    isAppUnlocked = true;
                    dialog.dismiss();
                    loadInitialFragment(null);
                } else {
                    Toast.makeText(this, "암호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 지문 인증 버튼 클릭
        btnFingerprint.setOnClickListener(v -> {
            dialog.dismiss();
            authenticateForAppUnlock(true);
        });

        dialog.show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        notificationMenuItem = menu.findItem(R.id.action_notifications);
        
        // 테스터 모드가 아니면 알림 메뉴 아이템 숨기기
        if (!isTesterModeEnabled()) {
            notificationMenuItem.setVisible(false);
        }
        
        updateNotificationBadge();
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_notifications) {
            loadFragment(new MessageFragment());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationBadge();
        updateFcmTokenMenuVisibility();
    }
    
    private boolean isTesterModeEnabled() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_APP_LOCK, MODE_PRIVATE);
        return prefs.getBoolean(KEY_TESTER_MODE_ENABLED, false);
    }
    
    private void enableTesterMode() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_APP_LOCK, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_TESTER_MODE_ENABLED, true).apply();
        
        Toast.makeText(this, "테스터용 알리미 기능 활성화", Toast.LENGTH_LONG).show();
        
        // 알림 메뉴 아이템 표시
        if (notificationMenuItem != null) {
            notificationMenuItem.setVisible(true);
        }
        
        // FCM 토큰 메뉴 표시
        updateFcmTokenMenuVisibility();
    }
    
    private void updateFcmTokenMenuVisibility() {
        NavigationView navigationView = findViewById(R.id.nav_view);
        if (navigationView != null) {
            Menu navMenu = navigationView.getMenu();
            MenuItem fcmMenuItem = navMenu.findItem(R.id.nav_fcm_token);
            if (fcmMenuItem != null) {
                fcmMenuItem.setVisible(isTesterModeEnabled());
            }
        }
    }
    
    private void setupTesterModeTouchArea() {
        View touchArea = findViewById(R.id.tester_mode_touch_area);
        if (touchArea != null) {
            // 테스터 모드가 활성화되어 있으면 터치 영역 숨기기
            if (isTesterModeEnabled()) {
                touchArea.setVisibility(View.GONE);
            } else {
                touchArea.setOnClickListener(v -> handleNotificationAreaTap());
            }
        }
    }
    
    private void handleNotificationAreaTap() {
        notificationTapCount++;
        if (notificationTapCount >= TESTER_MODE_TAP_COUNT) {
            if (!isTesterModeEnabled()) {
                enableTesterMode();
                // 터치 영역 숨기기
                View touchArea = findViewById(R.id.tester_mode_touch_area);
                if (touchArea != null) {
                    touchArea.setVisibility(View.GONE);
                }
            }
            notificationTapCount = 0;
        }
    }
    
    private void updateNotificationBadge() {
        if (notificationMenuItem != null && dbHelper != null) {
            // 테스터 모드가 아니면 아이콘 숨기기
            if (!isTesterModeEnabled()) {
                notificationMenuItem.setVisible(false);
                return;
            }
            
            int unreadCount = dbHelper.getUnreadMessageCount();
            
            // ActionView가 없으면 생성
            View actionView = notificationMenuItem.getActionView();
            if (actionView == null) {
                androidx.core.view.MenuItemCompat.setActionView(notificationMenuItem, 
                    android.view.LayoutInflater.from(this).inflate(R.layout.notification_badge, null));
                actionView = notificationMenuItem.getActionView();
                
                // 클릭 이벤트 설정
                if (actionView != null) {
                    actionView.setOnClickListener(v -> {
                        loadFragment(new MessageFragment());
                    });
                }
            }
            
            ImageView iconView = actionView != null ? actionView.findViewById(R.id.icon) : null;
            View badgeView = actionView != null ? actionView.findViewById(R.id.badge) : null;
            
            // 항상 기본 알림 아이콘 표시 (색상 변경 없음)
            if (iconView != null) {
                iconView.setImageResource(R.drawable.ic_notification);
                iconView.clearColorFilter();
            }
            
            // 읽지 않은 알림이 있으면 빨간 원형 인디케이터 표시
            if (badgeView != null) {
                badgeView.setVisibility(unreadCount > 0 ? View.VISIBLE : View.GONE);
            }
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
    
    private void setupNotificationPermission() {
        notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, "알림을 받으려면 설정에서 알림 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
                }
            }
        );
    }
    
    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "todo_notification_channel";
            CharSequence name = "ToDo 알림";
            String description = "할 일 알림 채널";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);
            channel.enableLights(true);
            channel.enableVibration(true);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    private void checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                // SharedPreferences로 한 번만 표시하도록 체크
                SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                boolean batteryOptShown = prefs.getBoolean("battery_opt_dialog_shown", false);
                
                if (!batteryOptShown) {
                    // 배터리 최적화 해제 요청 다이얼로그 표시
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("배터리 최적화 해제")
                            .setMessage("알림이 정상적으로 작동하려면 배터리 최적화를 해제해야 합니다.\n설정으로 이동하시겠습니까?")
                            .setPositiveButton("설정으로 이동", (dialog, which) -> {
                                try {
                                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                    intent.setData(Uri.parse("package:" + getPackageName()));
                                    startActivity(intent);
                                } catch (Exception e) {
                                    // 일부 기기에서는 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS가 지원되지 않을 수 있음
                                    // 대신 일반 설정 화면으로 이동
                                    try {
                                        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                        startActivity(intent);
                                    } catch (Exception ex) {
                                android.util.Log.e("MainActivity", "배터리 최적화 설정 화면을 열 수 없습니다.", ex);
                            }
                        }
                        // 다이얼로그 표시 여부 저장
                        prefs.edit().putBoolean("battery_opt_dialog_shown", true).apply();
                    })
                    .setNegativeButton("나중에", (dialog, which) -> {
                        // 나중에 선택해도 다이얼로그 표시 여부 저장
                        prefs.edit().putBoolean("battery_opt_dialog_shown", true).apply();
                    })
                    .setCancelable(false)
                    .show();
                }
            }
        }
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
        } else if (itemId == R.id.nav_fcm_token) {
            showFcmTokenDialog();
        } else if (itemId == R.id.nav_about) {
            showAboutDialog();
        }
    }
    
    private void showFcmTokenDialog() {
        // FCM 토큰 가져오기
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    String tokenText;
                    String messageText;
                    
                    if (task.isSuccessful()) {
                        String token = task.getResult();
                        tokenText = token;
                        messageText = "알림 수신을 위해 해당 FCM 토큰 값을 관리자에게 전달해 주세요!\n\n" + token;
                    } else {
                        tokenText = "";
                        messageText = "토큰을 가져올 수 없습니다.";
                    }
                    
                    final String finalToken = tokenText;
                    
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("FCM 토큰 확인")
                            .setMessage(messageText)
                            .setPositiveButton("복사", (dialog, which) -> {
                                if (!finalToken.isEmpty()) {
                                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    ClipData clip = ClipData.newPlainText("FCM Token", finalToken);
                                    clipboard.setPrimaryClip(clip);
                                    Toast.makeText(this, "FCM 토큰이 복사되었습니다.", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("확인", null)
                            .create()
                            .show();
                });
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
                .setMessage("ToDoNote\n버전: " + versionName + "\n\n할 일과 메모를 한 곳에서 관리하는 간편한 앱입니다.\n\n개발자 문의: github.com/btlime3/ToDoNote/issues")
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
     * 가져올 DB에 비밀번호가 설정되어 있으면 인증 후 진행합니다.
     */
    private void importDatabase(Uri uri) {
        try {
            // 임시 파일에 선택한 DB 복사 (비밀번호 확인용)
            File tempDir = getCacheDir();
            File tempDbFile = new File(tempDir, "temp_import_db_" + System.currentTimeMillis() + ".db");
            
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tempDbFile)) {
                if (in == null) {
                    Toast.makeText(this, "파일을 읽을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            // 가져올 DB에서 비밀번호 해시 확인
            String passwordHash = DatabaseHelper.getPasswordHashFromFile(tempDbFile.getAbsolutePath());
            
            if (passwordHash != null) {
                // 비밀번호가 설정된 DB: 인증 필요
                showImportPasswordDialog(tempDbFile, uri);
            } else {
                // 비밀번호가 없는 DB: 바로 진행
                showImportConfirmDialog(tempDbFile, uri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "파일을 읽는 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 가져오기 비밀번호 인증 다이얼로그를 표시합니다.
     */
    private void showImportPasswordDialog(File tempDbFile, Uri originalUri) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        MaterialButton btnFingerprint = dialogView.findViewById(R.id.btn_fingerprint);

        passwordLayout.setHint("백업 파일 암호");
        
        // 지문 인증 버튼 숨김 (기기 종속적이므로 다른 기기에서 사용 불가)
        btnFingerprint.setVisibility(View.GONE);

        // 힌트 표시
        String hint = DatabaseHelper.getPasswordHintFromFile(tempDbFile.getAbsolutePath());
        String message = "다른 기기에서 내보낸 백업 파일입니다.\n해당 백업의 암호를 입력해주세요.";
        if (hint != null && !hint.isEmpty()) {
            message += "\n\n힌트: " + hint;
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("백업 파일 인증")
                .setMessage(message)
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("확인", null) // 후에 설정
                .setNegativeButton("취소", (d, which) -> {
                    // 임시 파일 삭제
                    tempDbFile.delete();
                })
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
                
                if (password.isEmpty()) {
                    Toast.makeText(this, "암호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 비밀번호 검증
                if (DatabaseHelper.verifyPasswordForFile(tempDbFile.getAbsolutePath(), password)) {
                    dialog.dismiss();
                    // 인증 성공: 가져오기 확인 다이얼로그 표시
                    showImportConfirmDialog(tempDbFile, originalUri);
                } else {
                    Toast.makeText(this, "암호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    /**
     * 가져오기 확인 다이얼로그를 표시하고 실제 교체를 수행합니다.
     */
    private void showImportConfirmDialog(File tempDbFile, Uri originalUri) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("데이터베이스 가져오기")
                .setMessage("현재 데이터베이스를 선택한 파일로 교체하시겠습니까? 현재 데이터는 백업 후 교체됩니다.")
                .setPositiveButton("가져오기", (dialog, which) -> {
                    performDatabaseImport(tempDbFile);
                })
                .setNegativeButton("취소", (dialog, which) -> {
                    // 임시 파일 삭제
                    tempDbFile.delete();
                })
                .show();
    }

    /**
     * 실제 데이터베이스 교체를 수행합니다.
     */
    private void performDatabaseImport(File tempDbFile) {
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

            // 임시 파일을 현재 DB로 복사
            try (InputStream in = new FileInputStream(tempDbFile);
                 OutputStream out = new FileOutputStream(dbFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                
                // 임시 파일 삭제
                tempDbFile.delete();
                
                // 백업 복원 플래그 설정 (보안을 위해 지문 인증 비활성화)
                android.content.SharedPreferences prefs = getSharedPreferences(PREF_APP_LOCK, MODE_PRIVATE);
                prefs.edit()
                    .putBoolean(KEY_BACKUP_RESTORED, true)
                    .putBoolean(KEY_FIRST_BIOMETRIC_AUTH, true)  // 다시 지문 등록 필요
                    .apply();

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
            }
        } catch (IOException e) {
            e.printStackTrace();
            // 임시 파일 삭제
            tempDbFile.delete();
            Toast.makeText(this, "가져오기 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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

        // 힌트 표시
        String message = buildPasswordMessage("암호 설정을 변경하려면 기존 암호를 입력해주세요.");

        // 지문 인증 사용 가능 여부 확인
        BiometricManager biometricManager = BiometricManager.from(this);
        boolean canUseBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;

        if (!canUseBiometric) {
            btnFingerprint.setVisibility(View.GONE);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("암호 확인")
                .setMessage(message)
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
        android.widget.CheckBox cbAppLock = dialogView.findViewById(R.id.cb_app_lock);
        TextInputEditText etHint = dialogView.findViewById(R.id.et_hint);

        passwordLayout.setHint("암호");
        confirmPasswordLayout.setHint("암호 확인");
        cbAppLock.setChecked(isAppLockEnabled());

        new MaterialAlertDialogBuilder(this)
                .setTitle("암호 설정")
                .setView(dialogView)
                .setPositiveButton("설정", (dialog, which) -> {
                    String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
                    String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";
                    String hint = etHint.getText() != null ? etHint.getText().toString().trim() : "";

                    if (password.isEmpty()) {
                        Toast.makeText(this, "암호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!password.equals(confirmPassword)) {
                        Toast.makeText(this, "암호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    dbHelper.setNotePassword(password, hint.isEmpty() ? null : hint);
                    setAppLockEnabled(cbAppLock.isChecked());
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
        android.widget.CheckBox cbAppLock = dialogView.findViewById(R.id.cb_app_lock);
        TextInputEditText etHint = dialogView.findViewById(R.id.et_hint);

        passwordLayout.setHint("새 암호 (변경 시에만 입력)");
        confirmPasswordLayout.setHint("새 암호 확인");
        cbAppLock.setChecked(isAppLockEnabled());

        // 기존 힌트 표시
        String existingHint = dbHelper.getNotePasswordHint();
        if (existingHint != null) {
            etHint.setText(existingHint);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("암호 설정")
                .setView(dialogView)
                .setPositiveButton("저장", (dialog, which) -> {
                    String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
                    String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";
                    String hint = etHint.getText() != null ? etHint.getText().toString().trim() : "";

                    // 암호 변경 (비어있지 않은 경우에만)
                    if (!password.isEmpty()) {
                        if (!password.equals(confirmPassword)) {
                            Toast.makeText(this, "암호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        dbHelper.setNotePassword(password, hint.isEmpty() ? null : hint);
                    } else if (!hint.isEmpty() || (existingHint != null && !existingHint.equals(hint))) {
                        // 암호는 변경하지 않고 힌트만 변경
                        dbHelper.setNotePasswordHint(hint);
                    }

                    // 앱 잠금 설정 저장
                    setAppLockEnabled(cbAppLock.isChecked());
                    Toast.makeText(this, "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", null)
                .setNeutralButton("암호 제거", (dialog, which) -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("암호 제거")
                            .setMessage("암호를 제거하시겠습니까?\n\n• 모든 잠금된 노트는 잠금 해제됩니다.\n• 앱 실행 시 잠금도 해제됩니다.")
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

                                // 앱 잠금 설정도 해제
                                setAppLockEnabled(false);

                                Toast.makeText(this, "암호가 제거되었습니다.", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("취소", null)
                            .show();
                });

        builder.show();
    }
}

