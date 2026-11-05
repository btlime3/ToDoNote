package kr.akotis.todonote.activity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
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
import androidx.fragment.app.FragmentManager;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
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
import kr.akotis.todonote.database.DamagedDatabaseKeeper;
import kr.akotis.todonote.database.DatabaseHelper;
import kr.akotis.todonote.fragment.MemoFragment;
import kr.akotis.todonote.fragment.MessageFragment;
import kr.akotis.todonote.fragment.NoteFragment;
import kr.akotis.todonote.fragment.SavedFragment;
import kr.akotis.todonote.service.MyFirebaseMessagingService;
import kr.akotis.todonote.fragment.ToDoFragment;
import kr.akotis.todonote.fragment.TodayFragment;
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
    /** 앱을 보고 있는 동안 도착한 알림도 숫자와 알림 화면에 바로 반영한다. */
    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (current instanceof MessageFragment) {
                ((MessageFragment) current).refresh();
            } else {
                updateNotificationBadge();
            }
        }
    };
    /** 잠금 해제를 기다리는 동안 받은 알림 Intent가 있는지. 해제 뒤 해당 화면을 연다. */
    private boolean notificationIntentPending;
    /** 손상 안내를 이번 실행에서 이미 보여 주었는지. 화면으로 돌아올 때마다 반복하지 않는다. */
    private boolean databaseDamageChecked;
    
    private static final String PREF_APP_LOCK = "app_lock_settings";
    private static final String KEY_APP_LOCK_ENABLED = "app_lock_enabled";
    private static final String KEY_FIRST_BIOMETRIC_AUTH = "first_biometric_auth";
    private static final String KEY_BACKUP_RESTORED = "backup_restored";
    private static final String KEY_TESTER_MODE_ENABLED = "tester_mode_enabled";
    private static final String STATE_APP_UNLOCKED = "app_unlocked";
    private boolean isAppUnlocked = false;
    private int notificationTapCount = 0;
    private static final int TESTER_MODE_TAP_COUNT = 20;
    private static final String TEMP_IMPORT_PREFIX = "temp_import_db_";
    private static final String IMPORTING_SUFFIX = ".importing";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        isAppUnlocked = savedInstanceState != null && savedInstanceState.getBoolean(STATE_APP_UNLOCKED);

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
        cleanUpLeftoverImportFiles();

        // 테스터 모드 터치 영역 설정
        setupTesterModeTouchArea();
        
        setupToolbar();
        setupDrawer();
        setupBottomNavigation();
        setupWindowInsets();
        setupBackNavigation();
        setupImportLauncher();
        setupNotificationPermission();
        
        // 알림 권한 확인 및 요청
        checkAndRequestNotificationPermission();
        
        // 배터리 최적화 해제 요청
        checkAndRequestBatteryOptimization();
        
        // 알림 채널 미리 생성 (앱이 꺼진 상태에서도 알림이 작동하도록)
        createNotificationChannel();
        
        // 앱 잠금 확인
        if (isAppLockRequired()) {
            // 인증 전에는 복원된 화면의 내용이 보이지 않게 가린다.
            findViewById(R.id.fragment_container).setVisibility(View.INVISIBLE);
            showAppLockAuthDialog();
            return; // 인증 전까지 화면 로드 중단
        }

        loadInitialFragment();
    }

    private boolean isAppLockRequired() {
        return isAppLockEnabled() && dbHelper.hasNotePassword() && !isAppUnlocked;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 화면 회전처럼 계속 사용 중일 때만 잠금 해제 상태를 이어 간다. 앱이 종료되었다면 다시 인증한다.
        outState.putBoolean(STATE_APP_UNLOCKED, isAppUnlocked && isChangingConfigurations());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, messageReceiver,
                new IntentFilter(MyFirebaseMessagingService.ACTION_MESSAGE_RECEIVED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(messageReceiver);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // 새로운 Intent 설정

        // 잠금 해제 전이면 인증 뒤 loadInitialFragment가 이 Intent를 처리한다.
        if (isAppLockRequired()) {
            notificationIntentPending = true;
            return;
        }
        routeNotificationIntent(intent);
    }

    /** 알림 클릭으로 인한 Fragment 이동 처리 */
    private void routeNotificationIntent(Intent intent) {
        String fragmentName = intent.getStringExtra("fragment");
        boolean toToDo = "todo".equals(fragmentName);
        if (!toToDo && !"message".equals(fragmentName)) {
            return;
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            // 노트 편집이나 검색 중이면 그 위에 열어 뒤로가기로 돌아갈 수 있게 한다.
            fragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, toToDo ? new ToDoFragment() : new MessageFragment())
                    .addToBackStack(null)
                    .commit();
        } else if (toToDo) {
            loadFragment(new ToDoFragment());
        } else {
            showMessages();
        }
    }

    private void loadInitialFragment() {
        findViewById(R.id.fragment_container).setVisibility(View.VISIBLE);
        // 시스템이 복원한 화면이 있으면 그대로 둔다. 시작 Intent를 다시 적용하면 보던 화면을 잃는다.
        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) != null) {
            if (notificationIntentPending) {
                notificationIntentPending = false;
                routeNotificationIntent(getIntent());
            }
            return;
        }
        // 아래에서 getIntent()를 처리하므로 다시 처리하지 않는다.
        notificationIntentPending = false;
        String fragmentName = getIntent().getStringExtra("fragment");
        if ("todo".equals(fragmentName)) {
            // 뒤로 가면 메모 탭으로 돌아가도록 탭 표시만 맞춘다.
            selectTabWithoutLoading(R.id.nav_memo);
            loadFragment(new ToDoFragment());
        } else if ("message".equals(fragmentName)) {
            // 알림 화면에서 뒤로 가면 메모 탭으로 돌아가도록 탭 표시만 맞춘다.
            selectTabWithoutLoading(R.id.nav_memo);
            showMessages();
        } else {
            // 기본 화면을 메모 탭으로 설정 (탭 선택 리스너가 화면을 불러온다)
            bottomNav.setSelectedItemId(R.id.nav_memo);
        }
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
        // 방금 설정한 사람이 사용 중이므로 회전만으로 다시 묻지 않는다.
        isAppUnlocked = enabled;
    }

    private boolean canUseBiometric() {
        return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }
    
    private void showAppLockAuthDialog() {
        // 백업 복원 여부 확인
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_APP_LOCK, MODE_PRIVATE);
        boolean isBackupRestored = prefs.getBoolean(KEY_BACKUP_RESTORED, false);
        boolean isFirstBiometricAuth = prefs.getBoolean(KEY_FIRST_BIOMETRIC_AUTH, true);

        // 백업 복원 후에는 암호 입력으로만 인증
        if (isBackupRestored) {
            showPasswordInputForAppUnlock();
            return;
        }

        boolean canUseBiometric = canUseBiometric();

        if (canUseBiometric && !isFirstBiometricAuth) {
            // 일반 지문 인증
            authenticateForAppUnlock();
        } else if (canUseBiometric && isFirstBiometricAuth) {
            // 첫 지문 인증 시: 지문 인증 후 암호 확인
            authenticateForFirstBiometricAuth();
        } else {
            // 지문 불가능 시: 암호 입력
            showPasswordInputForAppUnlock();
        }
    }
    
    private void authenticateForAppUnlock() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("앱 잠금 해제")
                .setSubtitle("지문을 인식하여 앱을 잠금 해제하세요")
                .setNegativeButtonText("암호 입력")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        isAppUnlocked = true;
                        loadInitialFragment();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            Toast.makeText(MainActivity.this, "지문 인증 실패: " + errString, Toast.LENGTH_SHORT).show();
                        }
                        showPasswordInputForAppUnlock();
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
                            // 인증 수단 없이 남으면 잠금을 거치지 않고 탭과 메뉴를 쓸 수 있게 된다.
                            if (!isFinishing() && !isDestroyed()) {
                                showPasswordInputForBiometricVerification();
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

    private void showPasswordInputForBiometricVerification() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        MaterialButton btnFingerprint = dialogView.findViewById(R.id.btn_fingerprint);

        passwordLayout.setHint("암호 확인");

        // 힌트 표시
        String message = buildPasswordMessage("지문 인증을 사용하려면 암호를 확인해주세요.");

        if (!canUseBiometric()) {
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
                    loadInitialFragment();
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

        if (!canUseBiometric()) {
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
                    // 복원한 백업의 암호를 확인했으므로 다음부터 지문 등록 절차로 돌아간다.
                    getSharedPreferences(PREF_APP_LOCK, MODE_PRIVATE).edit()
                            .putBoolean(KEY_BACKUP_RESTORED, false)
                            .apply();
                    isAppUnlocked = true;
                    dialog.dismiss();
                    loadInitialFragment();
                } else {
                    Toast.makeText(this, "암호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 지문 인증 버튼 클릭
        btnFingerprint.setOnClickListener(v -> {
            dialog.dismiss();
            authenticateForAppUnlock();
        });

        dialog.show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        notificationMenuItem = menu.findItem(R.id.action_notifications);
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

    private void showMessages() {
        loadFragment(new MessageFragment());
    }

    private void selectTabWithoutLoading(int tab) {
        bottomNav.setOnItemSelectedListener(null);
        bottomNav.setSelectedItemId(tab);
        bottomNav.setOnItemSelectedListener(this::onBottomNavItemSelected);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 손상은 DB를 처음 열 때 발견된다. 오늘 화면처럼 메인 DB를 쓰지 않는 화면도 있으므로 여기서 먼저 연다.
        if (!databaseDamageChecked) {
            databaseDamageChecked = true;
            dbHelper.getReadableDatabase();
            notifyIfDatabaseWasDamaged();
        }
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
        
        Toast.makeText(this, "FCM 토큰 메뉴를 표시합니다.", Toast.LENGTH_LONG).show();

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
    
    public void updateNotificationBadge() {
        if (notificationMenuItem != null && dbHelper != null) {
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
            TextView badgeView = actionView != null ? actionView.findViewById(R.id.badge) : null;
            
            // 항상 기본 알림 아이콘 표시 (색상 변경 없음)
            if (iconView != null) {
                iconView.setImageResource(R.drawable.ic_notification);
                iconView.clearColorFilter();
            }
            
            // 읽지 않은 알림 개수 표시
            if (badgeView != null) {
                badgeView.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
    
    private void createNotificationChannel() {
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
    
    private void checkAndRequestBatteryOptimization() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager == null || powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            return;
        }
        // SharedPreferences로 한 번만 표시하도록 체크
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("battery_opt_dialog_shown", false)) {
            return;
        }
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
                            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
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
        if (itemId == R.id.nav_saved) {
            loadFragment(new SavedFragment());
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
                    // 토큰을 받는 동안 화면이 닫혔으면 다이얼로그를 띄울 수 없다.
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    String token = task.isSuccessful() ? task.getResult() : "";
                    String messageText = task.isSuccessful()
                            ? "알림 수신을 위해 해당 FCM 토큰 값을 관리자에게 전달해 주세요!\n\n" + token
                            : "토큰을 가져올 수 없습니다.";

                    new MaterialAlertDialogBuilder(this)
                            .setTitle("FCM 토큰 확인")
                            .setMessage(messageText)
                            .setPositiveButton("복사", (dialog, which) -> {
                                if (!token.isEmpty()) {
                                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    clipboard.setPrimaryClip(ClipData.newPlainText("FCM Token", token));
                                    Toast.makeText(this, "FCM 토큰이 복사되었습니다.", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("닫기", null)
                            .show();
                });
    }

    private void showAboutDialog() {
        String versionName = "1.0";
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            android.util.Log.w("MainActivity", "Could not read version", e);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("정보")
                .setMessage("ToDoNote\n버전: " + versionName + "\n\n메모, 노트, 할 일, 알림을 한 곳에서 관리하는 앱입니다.\n\n개발자 문의: github.com/btlime3/ToDoNote/issues")
                .setPositiveButton("확인", null)
                .show();
    }

    // 하단 NavBar 생성
    private void setupBottomNavigation() {
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(this::onBottomNavItemSelected);
    }

    private boolean onBottomNavItemSelected(@NonNull MenuItem item) {
        Fragment fragment = getFragmentForItem(item.getItemId());
        if (fragment != null) {
            loadFragment(fragment);
            return true;
        }
        return false;
    }

    /** 드로어에서 연 화면은 뒤로가기로 앱을 나가지 않고 현재 탭으로 돌아간다. */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fragmentManager = getSupportFragmentManager();
                Fragment current = fragmentManager.findFragmentById(R.id.fragment_container);
                boolean openedFromMenu = current instanceof TrashFragment
                        || current instanceof MessageFragment
                        || current instanceof ToDoFragment
                        || current instanceof SavedFragment;
                if (openedFromMenu && fragmentManager.getBackStackEntryCount() == 0) {
                    bottomNav.setSelectedItemId(bottomNav.getSelectedItemId());
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    // 하단 NavBar 선택 작업
    private Fragment getFragmentForItem(int itemId) {
        if (itemId == R.id.nav_today) {
            return new TodayFragment();
        } else if (itemId == R.id.nav_memo) {
            return new MemoFragment();
        } else if (itemId == R.id.nav_note) {
            return new NoteFragment();
        }
        return null;
    }

    // Fragment 로딩
    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        // 쌓여 있는 편집 화면을 정리해 뒤로가기가 이미 떠난 화면으로 돌아가지 않게 한다.
        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
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
        File snapshot = null;
        try {
            snapshot = dbHelper.createCompactBackup(getCacheDir());
            String fileName = "ToDoNote_backup_" + fileTimestamp() + ".db";
            saveToDownloads(snapshot, fileName);
            Toast.makeText(this, "다운로드 폴더에 백업이 저장되었습니다: " + fileName, Toast.LENGTH_LONG).show();
        } catch (IOException | android.database.sqlite.SQLiteException e) {
            android.util.Log.e("MainActivity", "Export failed", e);
            Toast.makeText(this, "백업 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (snapshot != null) {
                snapshot.delete();
            }
        }
    }

    private static String fileTimestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    }

    /** 쓰기가 끝날 때까지 파일을 숨겨 두어, 실패해도 잘린 파일이 백업처럼 남지 않게 한다. */
    private void saveToDownloads(File source, String fileName) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/x-sqlite3");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("다운로드 폴더에 파일을 만들 수 없습니다.");
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) {
                throw new IOException("다운로드 폴더의 파일을 열 수 없습니다.");
            }
            copyStream(in, out);
        } catch (IOException | RuntimeException e) {
            resolver.delete(uri, null, null);
            throw e;
        }
        ContentValues finished = new ContentValues();
        finished.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, finished, null, null);
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
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
     * 선택한 파일을 임시 사본으로 옮겨 검사한 뒤, 필요하면 암호를 확인하고 교체 여부를 묻습니다.
     */
    private void importDatabase(Uri uri) {
        File tempDbFile = new File(getCacheDir(), TEMP_IMPORT_PREFIX + System.currentTimeMillis() + ".db");
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tempDbFile)) {
            if (in == null) {
                throw new IOException("파일을 열 수 없습니다.");
            }
            copyStream(in, out);
        } catch (IOException e) {
            tempDbFile.delete();
            Toast.makeText(this, "파일을 읽는 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseHelper.ImportCheck check = DatabaseHelper.checkImportFile(tempDbFile);
        if (check == DatabaseHelper.ImportCheck.OK && !DatabaseHelper.canOpenImportedFile(this, tempDbFile)) {
            check = DatabaseHelper.ImportCheck.NOT_BACKUP;
        }
        if (check != DatabaseHelper.ImportCheck.OK) {
            tempDbFile.delete();
            Toast.makeText(this, importCheckMessage(check), Toast.LENGTH_LONG).show();
            return;
        }

        if (DatabaseHelper.getPasswordHashFromFile(tempDbFile.getAbsolutePath()) != null) {
            // 비밀번호가 설정된 DB: 인증 필요
            showImportPasswordDialog(tempDbFile);
        } else {
            // 비밀번호가 없는 DB: 바로 진행
            showImportConfirmDialog(tempDbFile);
        }
    }

    private static String importCheckMessage(DatabaseHelper.ImportCheck check) {
        switch (check) {
            case TOO_NEW:
                return "더 새로운 버전의 앱에서 만든 백업입니다. 앱을 업데이트한 뒤 가져와주세요.";
            case DAMAGED:
                return "손상된 백업 파일이라 가져올 수 없습니다. 현재 데이터는 그대로입니다.";
            default:
                return "ToDoNote 백업 파일이 아닙니다. 현재 데이터는 그대로입니다.";
        }
    }

    /**
     * 가져오기 비밀번호 인증 다이얼로그를 표시합니다.
     */
    private void showImportPasswordDialog(File tempDbFile) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        MaterialButton btnFingerprint = dialogView.findViewById(R.id.btn_fingerprint);

        passwordLayout.setHint("백업 파일 암호");

        // 지문 인증 버튼 숨김 (기기 종속적이므로 다른 기기에서 사용 불가)
        btnFingerprint.setVisibility(View.GONE);

        // 힌트 표시
        String hint = DatabaseHelper.getPasswordHintFromFile(tempDbFile.getAbsolutePath());
        String message = "백업 파일을 만들 때 설정한 암호를 입력해주세요.";
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
                    showImportConfirmDialog(tempDbFile);
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
    private void showImportConfirmDialog(File tempDbFile) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("데이터베이스 가져오기")
                .setMessage("현재 데이터를 선택한 파일로 교체하시겠습니까?\n\n"
                        + "교체 전 데이터는 다운로드 폴더에 ToDoNote_before_import 파일로 저장되며, "
                        + "이 파일을 다시 가져오면 되돌릴 수 있습니다.")
                .setPositiveButton("가져오기", (dialog, which) -> performDatabaseImport(tempDbFile))
                .setNegativeButton("취소", (dialog, which) -> tempDbFile.delete())
                .setOnCancelListener(dialog -> tempDbFile.delete())
                .show();
    }

    /**
     * 실제 데이터베이스 교체를 수행합니다.
     * 완성된 새 파일을 같은 폴더에 만든 뒤 한 번에 바꾸므로, 중간에 실패해도 현재 데이터는 그대로입니다.
     */
    private void performDatabaseImport(File tempDbFile) {
        File dbFile = new File(DatabaseHelper.getDatabasePath(this));
        File replacement = new File(dbFile.getPath() + IMPORTING_SUFFIX);
        File snapshot = null;
        try {
            DatabaseHelper.prepareImportedDatabase(tempDbFile);

            // 사용자가 직접 되돌릴 수 있도록 현재 데이터를 먼저 저장한다. 실패하면 교체하지 않는다.
            if (dbFile.exists()) {
                snapshot = dbHelper.createCompactBackup(getCacheDir());
                saveToDownloads(snapshot, "ToDoNote_before_import_" + fileTimestamp() + ".db");
            }

            try (InputStream in = new FileInputStream(tempDbFile);
                 FileOutputStream out = new FileOutputStream(replacement)) {
                copyStream(in, out);
                out.flush();
                out.getFD().sync();
            }

            // 열린 연결과 옛 WAL 파일이 남으면 새 파일에 옛 내용이 섞여 손상될 수 있다.
            dbHelper.close();
            for (String suffix : new String[]{"-wal", "-shm", "-journal"}) {
                new File(dbFile.getPath() + suffix).delete();
            }
            java.nio.file.Files.move(replacement.toPath(), dbFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // 이전 버전이 남긴 앱 내부 사본은 이제 쓰이지 않는다.
            new File(getNoBackupFilesDir(), "ToDoNote_before_import.db").delete();

            // 백업 복원 플래그 설정 (보안을 위해 지문 인증 비활성화)
            getSharedPreferences(PREF_APP_LOCK, MODE_PRIVATE).edit()
                    .putBoolean(KEY_BACKUP_RESTORED, true)
                    .putBoolean(KEY_FIRST_BIOMETRIC_AUTH, true)  // 다시 지문 등록 필요
                    .commit();

            Toast.makeText(this, "데이터베이스를 가져왔습니다. 앱을 재시작합니다.", Toast.LENGTH_LONG).show();
            restartAppAfterImport();
        } catch (IOException | RuntimeException e) {
            android.util.Log.e("MainActivity", "Import failed", e);
            replacement.delete();
            Toast.makeText(this, "가져오기 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            tempDbFile.delete();
            if (snapshot != null) {
                snapshot.delete();
            }
        }
    }

    /** 프로세스를 새로 시작해 이전 파일을 가리키는 연결이나 화면이 남지 않게 한다. */
    private void restartAppAfterImport() {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            startActivity(Intent.makeRestartActivityTask(new ComponentName(this, MainActivity.class)));
            Runtime.getRuntime().exit(0);
        }, 1000);
    }

    /** 가져오기가 중간에 끝나 남은 임시 파일을 지운다. 이 파일에는 노트 내용이 평문으로 들어 있다. */
    private void cleanUpLeftoverImportFiles() {
        File[] leftovers = getCacheDir().listFiles((dir, name) ->
                name.startsWith(TEMP_IMPORT_PREFIX) || name.startsWith("todonote_backup_"));
        if (leftovers != null) {
            for (File leftover : leftovers) {
                leftover.delete();
            }
        }
        new File(DatabaseHelper.getDatabasePath(this) + IMPORTING_SUFFIX).delete();
    }

    /** 손상으로 데이터베이스가 다시 만들어졌다면 사용자가 찾을 수 있는 곳에 사본을 두고 알린다. */
    private void notifyIfDatabaseWasDamaged() {
        List<File> copies = DamagedDatabaseKeeper.pendingCopies(this);
        if (copies.isEmpty()) {
            return;
        }
        String message;
        try {
            for (File copy : copies) {
                saveToDownloads(copy, copy.getName());
            }
            // 사용자가 찾을 수 있는 곳에 저장한 뒤에만 완료로 표시한다.
            DamagedDatabaseKeeper.markNotified(this);
            message = "손상된 파일 사본을 다운로드 폴더에 저장했습니다: " + copies.get(0).getName();
        } catch (IOException | RuntimeException e) {
            android.util.Log.e("MainActivity", "Could not export damaged copy", e);
            message = "손상된 파일 사본은 앱 내부에 보관되어 있으며, 다음 실행 때 다시 저장합니다.";
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("데이터 손상 감지")
                .setMessage("저장된 데이터에서 손상이 발견되어 새 데이터베이스로 시작했습니다.\n\n" + message
                        + "\n\n최근 백업이 있다면 메뉴의 '가져오기'로 복원할 수 있습니다.")
                .setPositiveButton("확인", null)
                .show();
    }

    private void showPasswordSettingsDialog() {
        if (!dbHelper.hasNotePassword()) {
            // 암호가 설정되지 않은 경우: 바로 설정 다이얼로그 표시
            showPasswordSetupDialog();
        } else if (canUseBiometric()) {
            // 지문 인증이 가능하면 먼저 지문 인증 시도
            authenticateWithBiometricForSettings(true);
        } else {
            // 지문 인증이 불가능하면 암호 입력 다이얼로그 표시
            showPasswordInputDialogForSettings();
        }
    }

    /** 입력이 틀려도 입력한 내용이 사라지지 않도록, 확인 버튼은 성공할 때만 다이얼로그를 닫는다. */
    private void keepOpenUntilSuccess(androidx.appcompat.app.AlertDialog dialog, java.util.function.BooleanSupplier onConfirm) {
        dialog.setOnShowListener(shown ->
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    if (onConfirm.getAsBoolean()) {
                        dialog.dismiss();
                    }
                }));
    }

    private void showPasswordInputDialogForSettings() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputEditText etPassword = dialogView.findViewById(R.id.et_password);
        MaterialButton btnFingerprint = dialogView.findViewById(R.id.btn_fingerprint);

        passwordLayout.setHint("기존 암호");

        // 힌트 표시
        String message = buildPasswordMessage("암호 설정을 변경하려면 기존 암호를 입력해주세요.");

        if (!canUseBiometric()) {
            btnFingerprint.setVisibility(View.GONE);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("암호 확인")
                .setMessage(message)
                .setView(dialogView)
                .setPositiveButton("확인", null)
                .setNegativeButton("취소", null)
                .create();

        keepOpenUntilSuccess(dialog, () -> {
            String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
            if (dbHelper.verifyNotePassword(password)) {
                // 암호가 맞으면 설정/변경/제거 다이얼로그 표시
                showPasswordChangeDialog();
                return true;
            }
            Toast.makeText(this, "암호가 올바르지 않습니다.", Toast.LENGTH_SHORT).show();
            return false;
        });

        // 지문 인증 버튼 클릭
        btnFingerprint.setOnClickListener(v -> {
            dialog.dismiss();
            authenticateWithBiometricForSettings(false);
        });

        dialog.show();
    }

    private void authenticateWithBiometricForSettings(boolean showPasswordOnCancel) {
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
                        showPasswordChangeDialog();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            Toast.makeText(MainActivity.this, "지문 인증 실패: " + errString, Toast.LENGTH_SHORT).show();
                        }
                        if (showPasswordOnCancel) {
                            showPasswordInputDialogForSettings();
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

    private void showPasswordSetupDialog() {
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

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("암호 설정")
                .setView(dialogView)
                .setPositiveButton("설정", null)
                .setNegativeButton("취소", null)
                .create();

        keepOpenUntilSuccess(dialog, () -> {
            String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
            String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";
            String hint = etHint.getText() != null ? etHint.getText().toString().trim() : "";

            if (password.isEmpty()) {
                Toast.makeText(this, "암호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "암호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                return false;
            }

            dbHelper.setNotePassword(password, hint.isEmpty() ? null : hint);
            setAppLockEnabled(cbAppLock.isChecked());
            Toast.makeText(this, "암호가 설정되었습니다.", Toast.LENGTH_SHORT).show();
            return true;
        });

        dialog.show();
    }

    private void showPasswordChangeDialog() {
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

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("암호 설정")
                .setView(dialogView)
                .setPositiveButton("저장", null)
                .setNegativeButton("취소", null)
                .setNeutralButton("암호 제거", (d, which) -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("암호 제거")
                            .setMessage("암호를 제거하시겠습니까?\n\n• 모든 잠금된 노트는 잠금 해제됩니다.\n• 앱 실행 시 잠금도 해제됩니다.")
                            .setPositiveButton("제거", (confirm, w) -> {
                                // 암호 해시를 지우고 휴지통을 포함한 모든 노트의 잠금을 해제
                                dbHelper.removeNotePassword();

                                // 앱 잠금 설정도 해제
                                setAppLockEnabled(false);

                                Toast.makeText(this, "암호가 제거되었습니다.", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("취소", null)
                            .show();
                })
                .create();

        keepOpenUntilSuccess(dialog, () -> {
            String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
            String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";
            String hint = etHint.getText() != null ? etHint.getText().toString().trim() : "";

            if (!password.isEmpty()) {
                if (!password.equals(confirmPassword)) {
                    Toast.makeText(this, "암호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                dbHelper.setNotePassword(password, null);
            }
            // 비워 둔 힌트도 그대로 반영해 이전 힌트가 남지 않게 한다.
            dbHelper.setNotePasswordHint(hint.isEmpty() ? null : hint);

            // 앱 잠금 설정 저장
            setAppLockEnabled(cbAppLock.isChecked());
            Toast.makeText(this, "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show();
            return true;
        });

        dialog.show();
    }
}
