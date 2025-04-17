package com.msk.blacklauncher;

import android.app.WallpaperManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// import fragments
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.Utils.FullScreenHelper;
import com.msk.blacklauncher.adapters.ViewPagerAdapter;
import com.msk.blacklauncher.fragments.AppsFragment;
import com.msk.blacklauncher.fragments.ChecklistAndNotesFragment;
import com.msk.blacklauncher.fragments.HomeFragment;
import com.msk.blacklauncher.fragments.WorkspaceFragment;
import com.msk.blacklauncher.model.AppModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Field;

public class MainActivity extends AppCompatActivity {

    private TextView timeTextView, dateTextView;
    private ViewPager2 viewPager;
    private ViewPagerAdapter adapter;
    private Handler handler = new Handler();
    private AppWidgetHost mAppWidgetHost;
    private AppWidgetManager mAppWidgetManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        FullScreenHelper.setFullScreen(this);

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
            }
        });
        
        // 更改ViewPager2的相关设置，移除默认的滑动效果
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
        // 保持全屏模式
        FullScreenHelper.maintainFullScreen(this, hasFocus);
    }

    // 添加 onResume 方法来处理从后台返回
    @Override
    protected void onResume() {
        super.onResume();
        // 从后台返回时也切换到首页
        if (viewPager != null) {
            viewPager.setCurrentItem(0, true);
        }
    }
}