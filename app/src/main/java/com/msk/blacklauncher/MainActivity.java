package com.msk.blacklauncher;

import android.app.WallpaperManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// import fragments
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.utils.FullScreenHelper;
import com.msk.blacklauncher.adapters.ViewPagerAdapter;
import com.msk.blacklauncher.fragments.AppsFragment;
import com.msk.blacklauncher.fragments.ChecklistAndNotesFragment;
import com.msk.blacklauncher.fragments.HomeFragment;
import com.msk.blacklauncher.fragments.WorkspaceFragment;
import com.msk.blacklauncher.model.AppModel;
import com.msk.blacklauncher.service.IdleModeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Field;

import androidx.annotation.NonNull;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final long HIDE_UI_DELAY_MS = 100; // 延迟隐藏UI的时间（毫秒）
    private static final int[] RECURSIVE_DELAYS = new int[]{100, 200, 300, 500, 800, 1500, 3000};
    
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideSystemUIRunnable = this::hideSystemUI;
    
    // 屏幕旋转内容观察者
    private ContentObserver rotationObserver;
    private int lastKnownOrientation = Configuration.ORIENTATION_UNDEFINED;
    
    // 记录当前主页面位置
    private int currentMainPage = 0;

    private TextView timeTextView, dateTextView;
    private ViewPager2 viewPager;
    private ViewPagerAdapter adapter;
    private AppWidgetHost mAppWidgetHost;
    private AppWidgetManager mAppWidgetManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 禁用系统字体缩放
        getResources().getConfiguration().fontScale = 1.0f;
        getResources().getDisplayMetrics().scaledDensity = getResources().getDisplayMetrics().density;
        
        // 记录当前方向
        lastKnownOrientation = getResources().getConfiguration().orientation;
        Log.d(TAG, "onCreate: 当前屏幕方向 = " + 
              (lastKnownOrientation == Configuration.ORIENTATION_LANDSCAPE ? "横屏" : "竖屏"));
        
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        FullScreenHelper.setFullScreen(this);

        // 设置全屏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // 设置全屏和透明状态栏
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        // 让系统壁纸显示在Activity背景
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);

        // 设置窗口背景为透明，以便能够显示系统壁纸
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        // 加载视图布局
        setContentView(R.layout.activity_main);
        
        // 获取壁纸并设置到根布局
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        Drawable wallpaperDrawable = wallpaperManager.getDrawable();
        
        // 获取Activity的根布局
        View decorView = getWindow().getDecorView();
        View rootView = decorView.findViewById(android.R.id.content);
        // 将壁纸设置为根布局的背景
        rootView.setBackground(wallpaperDrawable);
        
        // 获取ViewPager并设置其背景为透明
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        viewPager.setBackgroundColor(Color.TRANSPARENT);

        mAppWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
        mAppWidgetHost = new AppWidgetHost(getApplicationContext(), 0xfffff);
        //开始监听widget的变化
        mAppWidgetHost.startListening();

        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager(), getLifecycle());

        // Fetch installed apps and set adapter
        List<AppModel> appsList = getInstalledApps(getApplicationContext());
//        setupViewPager(viewPager, adapter, appsList);
        // Log the app names
        StringBuilder appNames = new StringBuilder("Installed Apps: ");
        for (AppModel app : appsList) {
            appNames.append(app.getAppName()).append(", ");
        }
        Log.i("AppsFragment", appNames.toString());

//        adapter.addFragment(new ChecklistAndNotesFragment()); // Add apps list fragment here
        adapter.addFragment(new HomeFragment()); // Add your home fragment here
//        adapter.addFragment(new AppsFragment()); // Add apps list fragment here
        adapter.addFragment(new WorkspaceFragment());
        
        // 禁用ViewPager2的页面转换动画，使背景保持不动
        viewPager.setPageTransformer(null);
        
        // 消除页面之间的间距
        viewPager.setOffscreenPageLimit(2);
        
        // 应用自定义页面转换效果，保持背景不动
        viewPager.setPageTransformer((page, position) -> {
            // 不对页面应用任何变换效果
            page.setAlpha(1.0f);
        });
        
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(0);
        
        // 添加页面切换监听器
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                
                // 通知当前Fragment更新指示器
                Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + position);
                if (currentFragment instanceof HomeFragment) {
                    ((HomeFragment) currentFragment).updatePageIndicator(position);
                } else if (currentFragment instanceof WorkspaceFragment) {
                    ((WorkspaceFragment) currentFragment).updatePageIndicator(position);
                }
                
                // 记录当前页面位置，用于返回逻辑
                currentMainPage = position;
            }
        });
        
        // 更改ViewPager2的相关设置，提高滑动灵敏度
        try {
            Field recyclerViewField = ViewPager2.class.getDeclaredField("mRecyclerView");
            recyclerViewField.setAccessible(true);
            RecyclerView recyclerView = (RecyclerView) recyclerViewField.get(viewPager);
            
            // 移除默认的item装饰和divider
            if (recyclerView != null && recyclerView.getItemDecorationCount() > 0) {
                recyclerView.removeItemDecorationAt(0);
            }
            
            // 应用无分隔特效的简单动画
            recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            
            // 提高滑动灵敏度
            try {
                Field touchSlopField = RecyclerView.class.getDeclaredField("mTouchSlop");
                touchSlopField.setAccessible(true);
                int touchSlop = (int) touchSlopField.get(recyclerView);
                touchSlopField.set(recyclerView, touchSlop * 0.7f); // 降低滑动阈值，提高灵敏度
            } catch (Exception e) {
                Log.e("MainActivity", "无法调整滑动灵敏度: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e("MainActivity", "设置ViewPager2属性失败: " + e.getMessage());
        }

        // 注册 Home 键监听
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            // 当从其他应用返回时，确保回到首页
            if (viewPager != null) {
                viewPager.setCurrentItem(0, true);
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.viewPager), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // In MainActivity or the fragment manager for handling the swipe
//        viewPager.setCurrentItem(1); // Or use a gesture detector to trigger this fragment

        // Replace "custom_font.ttf" with the actual font file in the assets folder
//        FontOverride.setDefaultFont(this, "DEFAULT", "fonts/caveat.ttf");

        // 设置全局触摸监听，确保任何触摸都保持全屏
        setupTouchListener();
        
        // 强制全屏显示
        hideSystemUI();
        
        // 递归延迟多次设置全屏，确保应用一直保持全屏状态
        for (int delay : RECURSIVE_DELAYS) {
            uiHandler.postDelayed(this::hideSystemUI, delay);
        }

        // 启动空闲模式服务
        Intent idleModeServiceIntent = new Intent(this, IdleModeService.class);
        startService(idleModeServiceIntent);
        Log.d(TAG, "启动空闲模式服务");

        // 注册屏幕旋转内容观察者
        registerRotationObserver();
    }

    private void setupViewPager(ViewPager2 viewPager, ViewPagerAdapter adapter, List<AppModel> appsList) {
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList("apps_list", new ArrayList<>(appsList));

        ChecklistAndNotesFragment notesFragment = new ChecklistAndNotesFragment();
        HomeFragment homeFragment = new HomeFragment();
        AppsFragment appsFragment = new AppsFragment();
        appsFragment.setArguments(bundle);

        adapter.addFragment(notesFragment);
        adapter.addFragment(homeFragment);
        adapter.addFragment(appsFragment);

        viewPager.setAdapter(adapter);
    }

    private List<AppModel> getInstalledApps(Context context) {
        List<AppModel> appsList = new ArrayList<>();
        PackageManager packageManager = context.getPackageManager();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> hiddenAppsList= preferences.getStringSet("hiddenAppsList",null);

        // Get a list of all installed applications
        List<ApplicationInfo> applications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : applications) {
            // Filter for user-installed apps (non-system apps)
            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
                (appInfo.loadLabel(packageManager).toString().equals("Settings"))) {
                String appName = appInfo.loadLabel(packageManager).toString();
                Drawable appIcon = appInfo.loadIcon(packageManager);
                String packageName = appInfo.packageName;

                appsList.add(new AppModel(appName, appIcon, packageName));
            }
        }

        // Sort the apps list alphabetically by app name
        appsList.sort((app1, app2) -> app1.getAppName().compareToIgnoreCase(app2.getAppName()));

        return appsList;
    }
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    // 添加 onResume 方法来处理从后台返回
    @Override
    protected void onResume() {
        super.onResume();
        
        // 从后台返回时也切换到首页
        if (viewPager != null) {
            viewPager.setCurrentItem(0, true);
        }
        
        // 确保恢复时保持全屏
        hideSystemUI();
        
        // 启动定期检查全屏状态的任务
        startFullscreenChecker();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // 停止全屏检查任务
        stopFullscreenChecker();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 每次触摸事件都保持全屏
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            hideSystemUI();
        }
        return super.dispatchTouchEvent(ev);
    }
    
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int currentOrientation = newConfig.orientation;
        
        Log.d(TAG, "配置变更: " + (currentOrientation == Configuration.ORIENTATION_LANDSCAPE ? "横屏" : "竖屏"));
        
        // 确保保持全屏状态
        hideSystemUI();
        
        // 将配置变更事件传递给当前可见的Fragment
        if (viewPager != null) {
            int currentPosition = viewPager.getCurrentItem();
            Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + currentPosition);
            
            // 如果当前Fragment是HomeFragment，则通知它配置已经变更
            if (currentFragment instanceof HomeFragment) {
                Log.d(TAG, "将配置变更传递给HomeFragment");
                ((HomeFragment) currentFragment).onConfigurationChanged(newConfig);
                
                // 强制重新加载Fragment以应用新布局
                forceRefreshCurrentFragment();
            }
        }
    }
    
    /**
     * 强制刷新当前Fragment
     */
    private void forceRefreshCurrentFragment() {
        if (viewPager == null) return;
        
        try {
            int currentPosition = viewPager.getCurrentItem();
            Fragment currentFragment = getSupportFragmentManager().findFragmentByTag("f" + currentPosition);
            
            if (currentFragment != null) {
                Log.d(TAG, "强制刷新当前Fragment: " + currentFragment.getClass().getSimpleName());
                
                // 使用FragmentTransaction重新附加Fragment，触发重建
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.detach(currentFragment);
                ft.attach(currentFragment);
                ft.commitAllowingStateLoss();
            }
        } catch (Exception e) {
            Log.e(TAG, "强制刷新Fragment失败", e);
        }
    }

    /**
     * 强制隐藏系统UI，实现沉浸式全屏体验
     */
    private void hideSystemUI() {
        Log.d(TAG, "强制隐藏系统UI");
        
        // 设置窗口标志
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        
        // 清除非全屏标志
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        
        // 设置系统UI标志 - 使用最强全屏组合
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LOW_PROFILE
        );
    }
    
    /**
     * 设置全局触摸监听器
     */
    private void setupTouchListener() {
        View rootView = findViewById(android.R.id.content);
        rootView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hideSystemUI();
            }
            return false; // 不消费事件
        });
    }
    
    // 用于定期检查全屏状态的变量
    private boolean isFullscreenCheckerRunning = false;
    private static final long FULLSCREEN_CHECK_INTERVAL = 500; // 毫秒
    private final Runnable fullscreenCheckerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFullscreenCheckerRunning) return;
            
            // 检查当前系统UI状态并确保全屏
            View decorView = getWindow().getDecorView();
            int visibility = decorView.getSystemUiVisibility();
            boolean isFullscreen = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
            boolean isNavigationHidden = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) != 0;
            
            if (!isFullscreen || !isNavigationHidden) {
                Log.d(TAG, "检测到非全屏状态，重新设置全屏");
                hideSystemUI();
            }
            
            // 继续下一次检查
            uiHandler.postDelayed(this, FULLSCREEN_CHECK_INTERVAL);
        }
    };
    
    /**
     * 启动全屏状态定期检查任务
     */
    private void startFullscreenChecker() {
        if (!isFullscreenCheckerRunning) {
            isFullscreenCheckerRunning = true;
            uiHandler.post(fullscreenCheckerRunnable);
            Log.d(TAG, "启动全屏状态检查器");
        }
    }
    
    /**
     * 停止全屏状态检查任务
     */
    private void stopFullscreenChecker() {
        isFullscreenCheckerRunning = false;
        uiHandler.removeCallbacks(fullscreenCheckerRunnable);
        Log.d(TAG, "停止全屏状态检查器");
    }

    /**
     * 加载工作区Fragment
     */
    private void loadWorkspaceFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.viewPager, new WorkspaceFragment());
        transaction.commit();
    }

    /**
     * 注册屏幕旋转内容观察者，用于监测通过adb等方式旋转屏幕
     */
    private void registerRotationObserver() {
        rotationObserver = new ContentObserver(uiHandler) {
            @Override
            public void onChange(boolean selfChange) {
                try {
                    // 获取当前系统旋转设置
                    int userRotation = Settings.System.getInt(getContentResolver(), "user_rotation");
                    int currentOrientation = getResources().getConfiguration().orientation;
                    
                    Log.d(TAG, "系统旋转值变化：user_rotation = " + userRotation + 
                          ", 当前方向 = " + (currentOrientation == Configuration.ORIENTATION_LANDSCAPE ? 
                                           "横屏" : "竖屏"));
                    
                    // 如果设备当前配置的方向与上次不同，手动触发配置变更
                    if (currentOrientation != lastKnownOrientation) {
                        Log.d(TAG, "检测到屏幕方向变化，触发配置变更处理");
                        lastKnownOrientation = currentOrientation;
                        
                        // 延迟一点时间确保系统已完成旋转
                        uiHandler.postDelayed(() -> {
                            Configuration newConfig = new Configuration(getResources().getConfiguration());
                            onConfigurationChanged(newConfig);
                        }, 200);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "处理屏幕旋转变化时出错", e);
                }
            }
        };
        
        // 注册内容观察者，监听系统旋转设置变化
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor("user_rotation"), 
                false, rotationObserver);
        
        Log.d(TAG, "已注册屏幕旋转内容观察者");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 取消注册屏幕旋转观察者
        if (rotationObserver != null) {
            getContentResolver().unregisterContentObserver(rotationObserver);
            rotationObserver = null;
        }
    }
}