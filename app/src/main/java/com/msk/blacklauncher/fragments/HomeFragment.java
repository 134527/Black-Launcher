package com.msk.blacklauncher.fragments;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.SettingsActivity;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private CardView settingsCardView;
    private CardView officeCardView;
    private CardView appsCardView;

    private HorizontalScrollView basicToolsGrid;
    private GridLayout settingsGrid;
    private GridLayout officeGrid;
    private GridLayout appsGrid;
    private AppUpdateReceiver appUpdateReceiver;
    private PackageManager pm;
    private static final int PICK_IMAGE = 100;
    private View view;
    private View settingsOverlay;
    private View officeOverlay;
    private View appsOverlay;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);
//        appFolder2 = view.findViewById(R.id.appFolder2);

        // Initialize views
        timeTextView = view.findViewById(R.id.timerText);
        dateTextView = view.findViewById(R.id.dateText);
        initViews(view);


        // 初始化分类
        for (AppCategory category : AppCategory.values()) {
            categorizedApps.put(category, new ArrayList<>());
        }
        // Update the date and time
        updateDateTime();
        // 加载并分类应用
  /*      loadAndCategorizeApps();

        // 显示分类应用
        displayCategorizedApps();*/
        // Add long-press listener to open settings
        pm = requireActivity().getPackageManager();
        appUpdateReceiver = new AppUpdateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        requireActivity().registerReceiver(appUpdateReceiver, filter);


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
        settingsCardView = view.findViewById(R.id.settingsCardView);
        officeCardView = view.findViewById(R.id.officeCardView);
        appsCardView = view.findViewById(R.id.appsCardView);

        settingsOverlay = view.findViewById(R.id.settingsCardOverlay);
        officeOverlay = view.findViewById(R.id.officeCardOverlay);
        appsOverlay = view.findViewById(R.id.appsCardOverlay);

        // 设置遮罩层不拦截触摸事件
        View.OnTouchListener touchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                handleCardClick(v.getParent().getParent() instanceof CardView ?
                                (CardView)v.getParent().getParent() : null,
                        () -> showAppsDialog(getDialogTitle(v.getId()), getCategoryByViewId(v.getId())));
            }
            return false; // 返回 false 表示不拦截事件
        };

        categorizeAndDisplayApps();
        setupClickListeners(); // 初始化点击监听器
//        appsCard = view.findViewById(R.id.appsCard);
    }

    private void setupClickListeners() {
        // 设置搜索按钮点击事件
        searchButton.setOnClickListener(v -> {
            // 创建启动搜索的 Intent
            Intent intent = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                // 尝试调用系统搜索
                startActivity(intent);
            } catch (Exception e) {
                // 如果系统搜索不可用，尝试使用 Google 搜索
                try {
                    intent = new Intent("com.google.android.googlequicksearchbox.GOOGLE_SEARCH");
                    intent.setPackage("com.google.android.googlequicksearchbox");
                    startActivity(intent);
                } catch (Exception e2) {
                    // 如果 Google 搜索也不可用，尝试使用全局搜索
                    try {
                        intent = new Intent(Intent.ACTION_ASSIST);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e3) {
                        Toast.makeText(requireContext(), "未找到可用的搜索应用", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        tvModeCard.setOnClickListener(v -> handleCardClick(v, this::launchTVModeDialog));
        themeCard.setOnClickListener(v -> handleCardClick(v, this::openThemeSettings));
        screensaverCard.setOnClickListener(v -> handleCardClick(v, this::openWallpaperChooser));
        whiteboardCard.setOnClickListener(v -> handleCardClick(v, this::openWhiteboard));
        toolsCard.setOnClickListener(v -> openToolsGrid());

        // 设置遮罩层的点击事件
        settingsOverlay.setOnClickListener(v ->
                handleCardClick(settingsCardView, () -> showAppsDialog("基础设置", AppCategory.SETTINGS)));

        officeOverlay.setOnClickListener(v ->
                handleCardClick(officeCardView, () -> showAppsDialog("办公学习", AppCategory.OFFICE)));

        appsOverlay.setOnClickListener(v ->
                handleCardClick(appsCardView, () -> showAppsDialog("应用宝", AppCategory.APPS)));

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
        // 创建弹出动画
        view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .withEndAction(action)
                            .start();
                })
                .start();
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
                SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, yyyy年MMMdd日", Locale.getDefault());
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm ", Locale.getDefault());

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
    public void categorizeAndDisplayApps() {
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
            // 基础工具栏的水平滚动布局
            LinearLayout basicToolsLayout = (LinearLayout) ((HorizontalScrollView) container).getChildAt(0);
            basicToolsLayout.removeAllViews();
            PackageManager pm = requireActivity().getPackageManager();

            for (ApplicationInfo app : apps) {
                LinearLayout appContainer = new LinearLayout(requireContext());
                appContainer.setOrientation(LinearLayout.VERTICAL);
                appContainer.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                containerParams.setMargins(24, 8, 24, 8); // 增加水平间距
                appContainer.setLayoutParams(containerParams);

                // 修改基础工具栏图标大小
                ImageView appIcon = new ImageView(requireContext());
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(180, 200); // 调整为更小的尺寸
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
            // 其他网格布局
            GridLayout grid = (GridLayout) container;
            grid.removeAllViews();
            PackageManager pm = requireActivity().getPackageManager();

            for (int i = 0; i < apps.size(); i++) {
                ApplicationInfo app = apps.get(i);
                LinearLayout appContainer = new LinearLayout(requireContext());
                appContainer.setOrientation(LinearLayout.VERTICAL);
                appContainer.setGravity(Gravity.CENTER);

                GridLayout.LayoutParams containerParams = new GridLayout.LayoutParams();
                containerParams.width = GridLayout.LayoutParams.WRAP_CONTENT;
                containerParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
                containerParams.setMargins(16, 16, 16, 16); // 增加间距
                appContainer.setLayoutParams(containerParams);

                // 修改网格布局图标大小
                ImageView appIcon = new ImageView(requireContext());
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(80, 80); // 调整为更小的尺寸
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
        if (packageName.contains("calculator") || packageName.contains("calendar") || packageName.contains("search") ||
                packageName.contains("clock") || appName.contains("计算器") ||
                appName.contains("日历") || appName.contains("相机") || appName.contains("搜索") || appName.contains("时钟")) {
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
        // 注销广播接收器
        if (appUpdateReceiver != null) {
            requireActivity().unregisterReceiver(appUpdateReceiver);
        }
    }

    private class AppUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction()) ||
                    Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()))) {
                // 应用安装或卸载后，刷新应用列表
                categorizeAndDisplayApps();
            }
        }
    }

    private void openWallpaperChooser() {
        // 使用系统壁纸选择器
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
        startActivity(Intent.createChooser(intent, "选择壁纸"));
    }



    private void launchTVMode() {
        new AlertDialog.Builder(getContext(), R.style.CustomAlertDialog) .setMessage("TV模式正在开发？").show();
    }

    private void openThemeSettings() {
        // 实现主题设置逻辑
    }



    private void openWhiteboard() {

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo resolveInfo : apps) {
            if (resolveInfo.activityInfo.packageName.toLowerCase().endsWith("whiteboard")) {
                Intent launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    return; // 找到并启动应用后直接返回
                }
            }
        }
        // 如果没有找到������的应用，���示一个提示
        Toast.makeText(getContext(), "未找到电子白板应用", Toast.LENGTH_SHORT).show();
    }


    private void openToolsGrid() {
        // 实现工具网格逻辑
    }

    private void openAppsGrid() {
        // 实现应用网格逻辑
    }
 private void showAppsDialog(String title, AppCategory category) {
        Dialog dialog = new Dialog(requireContext(), R.style.BlurDialogTheme);
        dialog.setContentView(R.layout.dialog_apps_grid);

        Window window = dialog.getWindow();
     if (window != null) {
         window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

         // 设置对话框大小和位置
         WindowManager.LayoutParams params = window.getAttributes();
         params.width = 1440; // 固定宽度
         params.height =810; // 固定高度
         params.gravity = Gravity.CENTER; // 居中显示
         window.setAttributes(params);
     }
     TextView titleView = dialog.findViewById(R.id.dialogTitle);
     GridLayout dialogAppsGrid = dialog.findViewById(R.id.dialogAppsGrid);
     Button addButton = dialog.findViewById(R.id.addAppButton);

     titleView.setText(title);

     // 根据类别获取对应的原始网格
     GridLayout sourceGrid = null;
     switch (category) {
         case SETTINGS:
             sourceGrid = settingsGrid;
             break;
         case OFFICE:
             sourceGrid = officeGrid;
             break;
         case APPS:
             sourceGrid = appsGrid;
             break;
     }

     // 从原始网格复制应用图标到对话框网格
     if (sourceGrid != null) {
         for (int i = 0; i < sourceGrid.getChildCount(); i++) {
             View child = sourceGrid.getChildAt(i);
             if (child instanceof LinearLayout) {
                 LinearLayout originalContainer = (LinearLayout) child;

                 // 创建新的应用容器
                 LinearLayout newContainer = new LinearLayout(requireContext());
                 newContainer.setOrientation(LinearLayout.VERTICAL);
                 newContainer.setGravity(Gravity.CENTER);

                 GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                 params.width = GridLayout.LayoutParams.WRAP_CONTENT;
                 params.height = GridLayout.LayoutParams.WRAP_CONTENT;
                 params.setMargins(16, 16, 16, 16);
                 newContainer.setLayoutParams(params);

                 // 复制图标
                 ImageView originalIcon = (ImageView) originalContainer.getChildAt(0);
                 ImageView newIcon = new ImageView(requireContext());
                 newIcon.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
                 newIcon.setImageDrawable(originalIcon.getDrawable());
                 newIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

                 // 复制文本
                 TextView originalText = (TextView) originalContainer.getChildAt(1);
                 TextView newText = new TextView(requireContext());
                 newText.setText(originalText.getText());
                 newText.setTextColor(Color.BLACK);
                 newText.setTextSize(12);
                 newText.setGravity(Gravity.CENTER);
                 newText.setMaxLines(1);
                 newText.setEllipsize(TextUtils.TruncateAt.END);

                 // 添加到新容器
                 newContainer.addView(newIcon);
                 newContainer.addView(newText);

                 // 设置点击事件
                 newContainer.setOnClickListener(v -> {
                     if (originalContainer.hasOnClickListeners()) {
                         originalContainer.performClick();
                     }
                     dialog.dismiss();
                 });


                 dialogAppsGrid.addView(newContainer);
             }
         }
     }

     addButton.setOnClickListener(v -> {
         showAppPickerDialog(category, dialog);
     });
     dialog.show();
 }
 private void showAppPickerDialog(AppCategory category, Dialog parentDialog) {
        Dialog dialog = new Dialog(requireContext(), R.style.BlurDialogTheme);
        dialog.setContentView(R.layout.dialog_apps_grid);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = 1440;
            params.height = 810;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }

        TextView titleView = dialog.findViewById(R.id.dialogTitle);
        GridLayout dialogAppsGrid = dialog.findViewById(R.id.dialogAppsGrid);
        Button addButton = dialog.findViewById(R.id.addAppButton);

        titleView.setText("选择应用");
        addButton.setVisibility(View.GONE);

        // 确保 categorizedApps 已初始化
        if (categorizedApps == null) {
            categorizedApps = new HashMap<>();
        }
        if (!categorizedApps.containsKey(category)) {
            categorizedApps.put(category, new ArrayList<>());
        }

        // 获取所有已安装的应用
        PackageManager pm = requireActivity().getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        List<ApplicationInfo> allApps = new ArrayList<>();

        for (ResolveInfo resolveInfo : resolveInfos) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0);
                allApps.add(appInfo);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        // 移除已经在当前分类中的应用
        List<ApplicationInfo> currentApps = categorizedApps.get(category);
        if (currentApps != null) {
            allApps.removeAll(currentApps);
        }

        // 显示所有可用的应用
        for (ApplicationInfo app : allApps) {
            LinearLayout appContainer = new LinearLayout(requireContext());
            appContainer.setOrientation(LinearLayout.VERTICAL);
            appContainer.setGravity(Gravity.CENTER);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = GridLayout.LayoutParams.WRAP_CONTENT;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(16, 16, 16, 16);
            appContainer.setLayoutParams(params);

            ImageView appIcon = new ImageView(requireContext());
            appIcon.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
            appIcon.setImageDrawable(app.loadIcon(pm));
            appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

            TextView appName = new TextView(requireContext());
            appName.setText(app.loadLabel(pm));
            appName.setTextColor(Color.BLACK);
            appName.setTextSize(12);
            appName.setGravity(Gravity.CENTER);
            appName.setMaxLines(1);
            appName.setEllipsize(TextUtils.TruncateAt.END);

            appContainer.addView(appIcon);
            appContainer.addView(appName);

            appContainer.setOnClickListener(v -> {
                try {
                    // 添加应用到对应分类
                    categorizedApps.get(category).add(app);

                    // 更新对应的网格
                    GridLayout targetGrid = null;
                    switch (category) {
                        case SETTINGS:
                            targetGrid = settingsGrid;
                            break;
                        case OFFICE:
                            targetGrid = officeGrid;
                            break;
                        case APPS:
                            targetGrid = appsGrid;
                            break;
                    }

                    if (targetGrid != null) {
                        targetGrid.removeAllViews();
                        displayAppsInGrid(categorizedApps.get(category), targetGrid);
                    }

                    dialog.dismiss();
                    parentDialog.dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(requireContext(), "添加应用失败", Toast.LENGTH_SHORT).show();
                }
            });

            dialogAppsGrid.addView(appContainer);
        }

        dialog.show();
    }

    private List<ApplicationInfo> getAllInstalledApps() {
        List<ApplicationInfo> apps = new ArrayList<>();

        PackageManager pm = requireActivity().getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo resolveInfo : resolveInfos) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0);
                apps.add(appInfo);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        return apps;
    }
    private void updateGridByCategory(AppCategory category) {
        GridLayout targetGrid = null;
        switch (category) {
            case SETTINGS:
                targetGrid = settingsGrid;
                break;
            case OFFICE:
                targetGrid = officeGrid;
                break;
            case APPS:
                targetGrid = appsGrid;
                break;
        }

        if (targetGrid != null) {
            displayAppsInGrid(categorizedApps.get(category), targetGrid);
        }
    }


    private String getDialogTitle(int viewId) {
        if (viewId == R.id.settingsCardOverlay) return "基础设置";
        if (viewId == R.id.officeCardOverlay) return "办公学习";
        if (viewId == R.id.appsCardOverlay) return "应用宝";
        return "";
    }

    private AppCategory getCategoryByViewId(int viewId) {
        if (viewId == R.id.settingsCardOverlay) return AppCategory.SETTINGS;
        if (viewId == R.id.officeCardOverlay) return AppCategory.OFFICE;
        if (viewId == R.id.appsCardOverlay) return AppCategory.APPS;
        return AppCategory.SETTINGS; // 默认返回
    }
}