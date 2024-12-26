package com.msk.blacklauncher.fragments;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.SettingsActivity;
import com.msk.blacklauncher.adapters.AppGridPagerAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private TextView timeTextView, dateTextView;
    private Handler handler = new Handler();
    private GridLayout appFolder2;
    private TextClock timeText;
    private TextClock dateText;
    private ImageButton searchButton;
    private CardView tvModeCard;
    private CardView themeCard;
    private CardView screensaverCard;
    private CardView whiteboardCard;
    private CardView toolsCard;
    private CardView appsCard;
    private HorizontalScrollView basicToolsGrid;
    private GridLayout settingsGrid;
    private GridLayout officeGrid;
    private GridLayout appsGrid;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
//        appFolder2 = view.findViewById(R.id.appFolder2);

        // Initialize views
        timeTextView = view.findViewById(R.id.timerText);
        dateTextView = view.findViewById(R.id.dateText);
        initViews(view);


        // 初始化分类
     /*   for (AppCategory category : AppCategory.values()) {
            categorizedApps.put(category, new ArrayList<>());
        }*/
        // Update the date and time
        updateDateTime();
        // 加载并分类应用
  /*      loadAndCategorizeApps();

        // 显示分类应用
        displayCategorizedApps();*/
        // Add long-press listener to open settings
        timeTextView.setOnLongClickListener(v -> {
            openSettings();
            return true;
        });
        return view;
    }
    private void initViews(View view) {
        basicToolsGrid = view.findViewById(R.id.basicToolsContainer);
        settingsGrid = view.findViewById(R.id.basicSettingGrid);
        officeGrid = view.findViewById(R.id.officeLearningGrid);
        appsGrid = view.findViewById(R.id.appsGrid);

        // 初始化搜索按钮
        searchButton = view.findViewById(R.id.searchButton);

        // 初始化所有卡片
        tvModeCard = view.findViewById(R.id.tvModeCard);
        themeCard = view.findViewById(R.id.themeCard);
        screensaverCard = view.findViewById(R.id.screensaverCard);
        whiteboardCard = view.findViewById(R.id.whiteboardCard);
        toolsCard = view.findViewById(R.id.toolsCard);
        categorizeAndDisplayApps();
        setupClickListeners(); // 初始化点击监听器
//        appsCard = view.findViewById(R.id.appsCard);
    }

    private void setupClickListeners() {
        // 设置搜索按钮点击事件
        searchButton.setOnClickListener(v -> {
            // 实现搜索功能
        });

        tvModeCard.setOnClickListener(v -> handleCardClick(v, this::launchTVModeDialog));
        themeCard.setOnClickListener(v -> handleCardClick(v, this::openThemeSettings));
        screensaverCard.setOnClickListener(v -> handleCardClick(v, this::openScreensaver));
        whiteboardCard.setOnClickListener(v -> handleCardClick(v, this::openWhiteboard));
        toolsCard.setOnClickListener(v -> openToolsGrid());

        // 如果需要设置长按事件，确保对应的视图已经初始化
        if (timeText != null) {
            timeText.setOnLongClickListener(v -> {
                // 处理长按事件
                return true;
            });
        }

        if (dateText != null) {
            dateText.setOnLongClickListener(v -> {
                // 处理长按事件
                return true;
            });
        }
    }


    private void handleCardClick(View view, Runnable action) {
        // 创建缩放动画
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f, 1f);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY);
        animatorSet.setDuration(200);
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                // 动画开始时
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // 动画结束时执行点击事件
                action.run();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // 动画取消时
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                // 动画重复时
            }
        });
        animatorSet.start();
    }

    private void launchTVModeDialog() {
        new AlertDialog.Builder(getContext(), R.style.CustomAlertDialog)
                .setTitle("启用TV模式")
                .setMessage("是否启用TV模式？")
                .setPositiveButton("是", (dialog, which) -> launchTVMode())
                .setNegativeButton("否", null)
                .show();
    }


    // 修改应用分类枚举
    private enum AppCategory {
        BASIC_TOOLS("基础工具", R.id.basicToolsContainer),
        SETTINGS("基础设置", R.id.basicSettingGrid),
        OFFICE("办公学习", R.id.officeLearningGrid),
        APPS("应用宝", R.id.appsGrid);

        private String displayName;
        private int gridId;

        AppCategory(String name, int gridId) {
            this.displayName = name;
            this.gridId = gridId;
        }
    }

    // 存储分类后的应用
    private Map<AppCategory, List<ApplicationInfo>> categorizedApps = new HashMap<>();

    private void updateDateTime() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // Format date and time
                SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

                // Get current date and time
                Date now = new Date();
                String currentDate = dateFormat.format(now);
                String currentTime = timeFormat.format(now);

                // Update TextViews
                dateTextView.setText(currentDate);
                timeTextView.setText(currentTime);

                // Update every second
                handler.postDelayed(this, 1000);
            }
        });
    }
    private void categorizeAndDisplayApps() {
        PackageManager pm = requireActivity().getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        Map<AppCategory, List<ApplicationInfo>> categorizedApps = new HashMap<>();

        // 初始化分类列表
        for (AppCategory category : AppCategory.values()) {
            categorizedApps.put(category, new ArrayList<>());
        }

        // 分类应用
        for (ResolveInfo resolveInfo : apps) {
            ApplicationInfo appInfo;
            try {
                appInfo = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0);
                AppCategory category = categorizeApp(appInfo, pm);
                categorizedApps.get(category).add(appInfo);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        // 显示分类后的应用
        for (AppCategory category : AppCategory.values()) {
            ViewGroup targetGrid = getGridForCategory(category);
            if (targetGrid != null) {
                displayAppsInGrid(categorizedApps.get(category), targetGrid);
            }
        }
    }

    private ViewGroup getGridForCategory(AppCategory category) {
        switch (category) {
            case BASIC_TOOLS:
                return basicToolsGrid;
            case SETTINGS:
                return settingsGrid;
            case OFFICE:
                return officeGrid;
            case APPS:
                return appsGrid;
            default:
                return null;
        }
    }

    private void displayAppsInGrid(List<ApplicationInfo> apps, ViewGroup container) {
        if (container.getId() == R.id.basicToolsContainer) {
            // 获取HorizontalScrollView中的LinearLayout
            LinearLayout basicToolsLayout = (LinearLayout) ((HorizontalScrollView) container).getChildAt(0);
            basicToolsLayout.removeAllViews();
            PackageManager pm = requireActivity().getPackageManager();

            for (ApplicationInfo app : apps) {
                // 创建垂直布局容器
                LinearLayout appContainer = new LinearLayout(requireContext());
                appContainer.setOrientation(LinearLayout.VERTICAL);
                appContainer.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                containerParams.setMargins(16, 8, 16, 8);
                appContainer.setLayoutParams(containerParams);

                // 创建图标
                ImageView appIcon = new ImageView(requireContext());
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(180, 180);
                appIcon.setLayoutParams(iconParams);
                appIcon.setImageDrawable(app.loadIcon(pm));
                appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

                // 创建应用名称
                TextView appName = new TextView(requireContext());
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                appName.setLayoutParams(nameParams);
                appName.setText(app.loadLabel(pm));
                appName.setTextColor(Color.WHITE);
                appName.setTextSize(12);
                appName.setGravity(Gravity.CENTER);
                appName.setMaxLines(1);
                appName.setEllipsize(TextUtils.TruncateAt.END);

                // 添加到容器
                appContainer.addView(appIcon);
                appContainer.addView(appName);

                // 设置点击事件
                appContainer.setOnClickListener(v -> {
                    Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    }
                });

                basicToolsLayout.addView(appContainer);
            }
        } else {
            // 其他网格的垂直滚动处理
            GridLayout grid = (GridLayout) container;
            grid.removeAllViews();
            PackageManager pm = requireActivity().getPackageManager();

            for (int i = 0; i < apps.size(); i++) {
                ApplicationInfo app = apps.get(i);

                // 创建应用图标容器
                LinearLayout appContainer = new LinearLayout(requireContext());
                appContainer.setOrientation(LinearLayout.VERTICAL);
                appContainer.setGravity(Gravity.CENTER);

                // 设置图标容器的布局参数
                GridLayout.LayoutParams containerParams = new GridLayout.LayoutParams();
                containerParams.width = GridLayout.LayoutParams.WRAP_CONTENT;
                containerParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
                containerParams.setMargins(8, 8, 8, 8);
                appContainer.setLayoutParams(containerParams);

                // 创建图标
                ImageView appIcon = new ImageView(requireContext());
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(120, 120);
                appIcon.setLayoutParams(iconParams);
                appIcon.setImageDrawable(app.loadIcon(pm));
                appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

                // 创建应用名称
                TextView appName = new TextView(requireContext());
                appName.setText(app.loadLabel(pm));
                appName.setTextColor(Color.WHITE);
                appName.setTextSize(12);
                appName.setGravity(Gravity.CENTER);
                appName.setMaxLines(1);
                appName.setEllipsize(TextUtils.TruncateAt.END);

                // 添加到容器
                appContainer.addView(appIcon);
                appContainer.addView(appName);

                // 设置点击事件
                appContainer.setOnClickListener(v -> {
                    Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    }
                });

                grid.addView(appContainer);
            }
        }

    }

    private AppCategory categorizeApp(ApplicationInfo app, PackageManager pm) {
        String packageName = app.packageName.toLowerCase();
        String appName = app.loadLabel(pm).toString().toLowerCase();

        // 基础工具类应用
        if (packageName.contains("calculator") || packageName.contains("calendar") ||
                packageName.contains("clock") || appName.contains("计算器") ||
                appName.contains("日历") || appName.contains("时钟")) {
            return AppCategory.BASIC_TOOLS;
        }
        // 设置类应用
        else if (packageName.contains("settings") || packageName.contains("setup") ||
                appName.contains("设置") || appName.contains("系统")) {
            return AppCategory.SETTINGS;
        }
        // 办公学习类应用
        else if (packageName.contains("office") || packageName.contains("word") ||
                packageName.contains("excel") || appName.contains("办公") ||
                appName.contains("学习") || appName.contains("教育")) {
            return AppCategory.OFFICE;
        }
        // 其他应用放入应用宝
        else {
            return AppCategory.APPS;
        }
    }

    private void openSettings() {
        Intent intent = new Intent(getContext(), SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }
    private void launchTVMode() {
        new AlertDialog.Builder(getContext(), R.style.CustomAlertDialog) .setMessage("TV模式正在开发？").show();
    }

    private void openThemeSettings() {
        // 实现主题设置逻辑
    }

    private void openScreensaver() {
        // 实现屏保设置逻辑
    }

    private void openWhiteboard() {
        // 实现白板功能逻辑
    }

    private void openToolsGrid() {
        // 实现工具网格逻辑
    }

    private void openAppsGrid() {
        // 实现应用网格逻辑
    }
}