package kr.akotis.todonote;

import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        setupToolbar();
        setupDrawer();
        setupBottomNavigation();
        setupWindowInsets();

        // 기본 화면을 메모 탭으로 설정
        if (savedInstanceState == null) {
            loadFragment(new MemoFragment());
            bottomNav.setSelectedItemId(R.id.nav_memo);
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
        if (itemId == R.id.nav_export) {
            // TODO: 내보내기 기능 구현
        } else if (itemId == R.id.nav_import) {
            // TODO: 가져오기 기능 구현
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
}