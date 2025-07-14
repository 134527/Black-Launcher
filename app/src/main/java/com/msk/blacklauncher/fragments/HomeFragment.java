package com.msk.blacklauncher.fragments;


import static android.content.ContentValues.TAG;

import com.msk.blacklauncher.activities.screensaver.ScreensaverActivity;
import  com.msk.blacklauncher.utils.FullScreenHelper;
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
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.SettingsActivity;
import com.msk.blacklauncher.utils.AppLayoutManager;
import com.msk.blacklauncher.utils.IconUtils;
import com.msk.blacklauncher.utils.PinyinUtils;
import com.msk.blacklauncher.model.AppModel;
import com.msk.blacklauncher.view.CardTouchInterceptor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import eightbitlab.com.blurview.BlurView;

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
    private com.msk.blacklauncher.view.PageIndicator pageIndicator;
    private ViewPager2 mainViewPager;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;
    private int currentOrientation = Configuration.ORIENTATION_UNDEFINED;

    private LinearLayout basicToolsGrid;
    private GridLayout settingsGrid;
    private GridLayout officeGrid;
    private GridLayout appsGrid;
    private AppUpdateReceiver appUpdateReceiver;
    private PackageManager pm;
    private static final int PICK_IMAGE = 100;
    private View view;
    private CardTouchInterceptor settingsOverlay;
    private CardTouchInterceptor officeOverlay;
    private CardTouchInterceptor appsOverlay;

    // 定义对话框变量为类成员，方便全局管理
    private Dialog currentAppDialog = null;
    private Dialog currentBlurBackgroundDialog = null;
    private Dialog currentAppPickerDialog = null;
    private Dialog currentPickerBlurDialog = null;
    private Dialog currentAppDrawerDialog = null;
    private Dialog currentAppDrawerBlurDialog = null;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置Fragment在配置变更时保留实例，避免重新创建
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 禁用系统字体大小和显示大小缩放
        Context contextThemeWrapper = new ContextThemeWrapper(getActivity(), R.style.Theme_BlackLauncher);
        contextThemeWrapper.getResources().getConfiguration().fontScale = 1.0f;
        LayoutInflater localInflater = inflater.cloneInContext(contextThemeWrapper);
        
        // 检测当前屏幕方向并加载相应布局
        currentOrientation = getResources().getConfiguration().orientation;
        int layoutResId = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) ? 
                         R.layout.fragment_home : 
                         R.layout.fragment_home_portrait;
        
        view = localInflater.inflate(layoutResId, container, false);
        
        // 初始化ViewPager2
        mainViewPager = requireActivity().findViewById(R.id.viewPager);
        
        // 初始化页面指示器
        pageIndicator = view.findViewById(R.id.home_page_indicator);
        initPageIndicator();

        // Initialize views
        timeTextView = view.findViewById(R.id.timerText);
        dateTextView = view.findViewById(R.id.dateText);
        initViews(view);

        if (isFirstLaunch()) {
            Log.d("HomeFragment", "检测到首次启动，加载默认应用");
            loadDefaultApps();
        } else {
            // 加载已保存的应用
            loadSavedAppsToGrids();
        }
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
        // 从持久化存储加载应用布局
        loadSavedAppsToGrids();
        fixMissingTags();
        timeTextView.setOnLongClickListener(v -> {
            openSettings();
            return true;
        });
        return view;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // 只有当方向发生变化时才重新加载UI
        if (currentOrientation != newConfig.orientation) {
            currentOrientation = newConfig.orientation;
            Log.d("HomeFragment", "屏幕方向改变，重新加载UI: " + 
                  (currentOrientation == Configuration.ORIENTATION_LANDSCAPE ? "横屏" : "竖屏"));
            
            // 关闭所有打开的对话框
            closeAllDialogs();
            
            // 直接替换根视图而不是重新加载整个Fragment
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null && getActivity() != null) {
                int index = parent.indexOfChild(view);
                parent.removeView(view);
                
                // 创建新的视图
                Context contextThemeWrapper = new ContextThemeWrapper(getActivity(), R.style.Theme_BlackLauncher);
                contextThemeWrapper.getResources().getConfiguration().fontScale = 1.0f;
                LayoutInflater localInflater = LayoutInflater.from(getActivity()).cloneInContext(contextThemeWrapper);
                
                // 根据当前方向选择布局
                int layoutResId = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) ? 
                                 R.layout.fragment_home : 
                                 R.layout.fragment_home_portrait;
                
                View newView = localInflater.inflate(layoutResId, parent, false);
                parent.addView(newView, index);
                view = newView;
                
                // 重新初始化视图和组件
                timeTextView = view.findViewById(R.id.timerText);
                dateTextView = view.findViewById(R.id.dateText);
                initViews(view);
                loadSavedAppsToGrids();
                fixMissingTags();
                updateDateTime();
                
                // 更新页面指示器
                pageIndicator = view.findViewById(R.id.home_page_indicator);
                initPageIndicator();
                
                Log.d("HomeFragment", "视图已重新加载，适应" + 
                      (currentOrientation == Configuration.ORIENTATION_LANDSCAPE ? "横屏" : "竖屏") + "布局");
            } else {
                Log.e("HomeFragment", "无法重新加载视图，父视图为null或Activity已销毁");
                // 尝试使用Fragment事务重新加载
                reloadUI();
            }
        }
    }
    
    private void reloadUI() {
        if (!isAdded() || getActivity() == null) return;
        
        try {
            // 保存当前状态
            Bundle state = new Bundle();
            // 这里可以保存任何需要在重建UI后恢复的状态
            
            // 使用FragmentTransaction替换当前Fragment
            getParentFragmentManager().beginTransaction()
                .detach(this)
                .attach(this)
                .commit();
            
            Log.d("HomeFragment", "UI已重新加载，适应新的屏幕方向");
        } catch (Exception e) {
            Log.e("HomeFragment", "重新加载UI失败: " + e.getMessage());
        }
    }

    private void fixMissingTags() {
        fixMissingTagsInGrid(settingsGrid, "设置");
        fixMissingTagsInGrid(officeGrid, "办公");
        fixMissingTagsInGrid(appsGrid, "应用");
    }

    private void fixMissingTagsInGrid(GridLayout grid, String gridName) {
        PackageManager pm = requireContext().getPackageManager();

        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child.getTag() == null && child instanceof LinearLayout) {
                LinearLayout container = (LinearLayout) child;

                // 获取应用名称
                if (container.getChildCount() > 1 && container.getChildAt(1) instanceof TextView) {
                    TextView labelView = (TextView) container.getChildAt(1);
                    String appName = labelView.getText().toString();

                    // 查找对应的包名
                    String packageName = findPackageNameByAppName(appName);
                    if (packageName != null) {
                        container.setTag(packageName);
                        Log.d("HomeFragment", "修复" + gridName + "网格中应用标签: " + appName + " -> " + packageName);
                    }
                }
            }
        }
    }

    private boolean isFirstLaunch() {
        SharedPreferences prefs = requireContext().getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("is_first_launch", true);
    }

    private void setFirstLaunchCompleted() {
        SharedPreferences prefs = requireContext().getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("is_first_launch", false).apply();
    }

    private void loadDefaultApps() {
        Log.d("HomeFragment", "正在加载默认应用...");

        // 设置卡片默认应用
        List<String> defaultSettingsApps = new ArrayList<>();
        defaultSettingsApps.add("com.android.settings"); // 设置
        defaultSettingsApps.add("com.android.deskclock"); // 时钟
        defaultSettingsApps.add("com.android.calculator2"); // 计算器

        // 办公卡片默认应用
        List<String> defaultOfficeApps = new ArrayList<>();
        defaultOfficeApps.add("com.android.contacts"); // 联系人
        defaultOfficeApps.add("com.android.calendar"); // 日历
        defaultOfficeApps.add("com.android.email"); // 邮件

        // 应用卡片默认应用
        List<String> defaultApps = new ArrayList<>();
        defaultApps.add("com.android.vending"); // Play商店
        defaultApps.add("com.android.chrome"); // Chrome浏览器
        defaultApps.add("com.google.android.gm"); // Gmail

        // 保存默认应用到持久化存储
        AppLayoutManager.saveAppsForCard(requireContext(), AppLayoutManager.getSettingsCardKey(), defaultSettingsApps);
        AppLayoutManager.saveAppsForCard(requireContext(), AppLayoutManager.getOfficeCardKey(), defaultOfficeApps);
        AppLayoutManager.saveAppsForCard(requireContext(), AppLayoutManager.getAppsCardKey(), defaultApps);

        // 标记首次启动完成
        setFirstLaunchCompleted();

        // 刷新网格显示默认应用
        loadSavedAppsToGrids();
    }

    private String findPackageNameByAppName(String appName) {
        PackageManager pm = requireContext().getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo resolveInfo : resolveInfos) {
            String label = resolveInfo.loadLabel(pm).toString();
            if (label.equals(appName)) {
                return resolveInfo.activityInfo.packageName;
            }
        }

        return null;
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
        
        // 初始化所有卡片的模糊效果
        initAllBlurViews(view);

        settingsOverlay = view.findViewById(R.id.settingsCardOverlay);
        officeOverlay = view.findViewById(R.id.officeCardOverlay);
        appsOverlay = view.findViewById(R.id.appsCardOverlay);

        // 设置空白区域点击监听器
        settingsOverlay.setOnClickListener(v -> showAppsDialog(getDialogTitle(v.getId()),getCategoryByViewId(v.getId())));
        officeOverlay.setOnClickListener(v -> showAppsDialog(getDialogTitle(v.getId()),getCategoryByViewId(v.getId())));
        appsOverlay.setOnClickListener(v -> showAppsDialog(getDialogTitle(v.getId()),getCategoryByViewId(v.getId())));

        categorizeAndDisplayApps();
        setupClickListeners(); // 初始化点击监听器

        // 设置屏保卡片点击事件
        screensaverCard.setOnClickListener(v -> {
            Log.d(TAG, "点击屏保卡片");
            
            // 启动屏保活动
            Intent intent = new Intent(getActivity(), ScreensaverActivity.class);
            startActivity(intent);
        });
    }

    // 初始化所有卡片的模糊效果
    private void initAllBlurViews(View view) {
        // 获取Activity的根视图作为要模糊的目标，而不是Fragment的根视图
        ViewGroup rootView = (ViewGroup) requireActivity().getWindow().getDecorView().findViewById(android.R.id.content);
        
        // 修复：模糊半径必须在0-25之间，设置为有效值
        float radius = 20f; // 设置为有效的模糊半径值
        
        // 初始化电子白板的模糊效果
        initBlurView(view, rootView, R.id.whiteboard_blur_view, radius);
        
        // 初始化TV模式卡片的模糊效果
        initBlurView(view, rootView, R.id.tv_card_blur_view, radius);
        
        // 初始化基础工具卡片的模糊效果
        initBlurView(view, rootView, R.id.tools_card_blur_view, radius);
        initBlurView(view, rootView, R.id.screensaverCard_portrait, radius);
        initBlurView(view, rootView, R.id.themeCard_portrait, radius);
        
        // 初始化基础设置卡片的模糊效果
        initBlurView(view, rootView, R.id.settings_card_blur_view, radius);
        
        // 初始化办公学习卡片的模糊效果
        initBlurView(view, rootView, R.id.office_card_blur_view, radius);
        
        // 初始化应用宝卡片的模糊效果
        initBlurView(view, rootView, R.id.apps_card_blur_view, radius);
    }
    
    // 初始化单个BlurView的通用方法
    private void initBlurView(View view, ViewGroup rootView, int blurViewId, float radius) {
        BlurView blurView = view.findViewById(blurViewId);
        if (blurView != null) {
            // 确保模糊半径在有效范围内(0 < r <= 25)
            float validRadius = Math.min(Math.max(0.1f, radius), 25f);
            
            // 使用RenderScriptBlur算法，对API 31+会自动使用RenderEffect
            blurView.setupWith(rootView)
                .setBlurRadius(validRadius)
                .setBlurAutoUpdate(true) // 自动更新模糊效果
                .setOverlayColor(Color.parseColor("#33FFFFFF")); // 半透明白色覆盖层
        }
    }

    // 原来的电子白板模糊效果初始化方法现在由initAllBlurViews调用initBlurView替代
    private void initWhiteboardBlur(View view) {
        // 方法保留但内容由initAllBlurViews替代
        // 这个方法的调用在initViews中已经被替换为initAllBlurViews
    }

    // 修改此方法，使用传入的view参数而非requireView()
    private void loadSavedAppsToGrids() {
        // 加载设置类应用
        String settingsKey = AppLayoutManager.getSettingsCardKey();
        List<String> settingsApps = AppLayoutManager.getAppsForCard(requireContext(), settingsKey);
        Log.d("HomeFragment", "加载设置应用，数量: " + settingsApps.size());
        loadAppsToGrid(settingsApps, settingsGrid, settingsKey);

        // 加载办公类应用
        String officeKey = AppLayoutManager.getOfficeCardKey();
        List<String> officeApps = AppLayoutManager.getAppsForCard(requireContext(), officeKey);
        Log.d("HomeFragment", "加载办公应用，数量: " + officeApps.size());
        loadAppsToGrid(officeApps, officeGrid, officeKey);

        // 加载应用类应用
        String appsKey = AppLayoutManager.getAppsCardKey();
        List<String> apps = AppLayoutManager.getAppsForCard(requireContext(), appsKey);
        Log.d("HomeFragment", "加载通用应用，数量: " + apps.size());
        loadAppsToGrid(apps, appsGrid, appsKey);
    }


    private void loadAppsToGrid(List<String> packageNames, GridLayout grid, String cardKey) {
        if (packageNames.isEmpty()) {
            Log.d("HomeFragment", "没有保存的应用，跳过加载");
            return;
        }

        PackageManager pm = requireContext().getPackageManager();

        // 清空现有网格内容
        grid.removeAllViews();

        for (String packageName : packageNames) {
            try {
                // 尝试获取应用信息
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);

                // 创建应用模型
                AppModel appModel = new AppModel(
                        appInfo.loadLabel(pm).toString(),
                        appInfo.loadIcon(pm),
                        packageName
                );

                // 添加到网格
                addAppToGrid(appModel, grid, cardKey);
                Log.d("HomeFragment", "成功加载应用: " + appModel.getAppName() + ", 包名: " + packageName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e("HomeFragment", "找不到应用: " + packageName + ", 将从存储中移除");
                // 从存储中移除已卸载的应用
                AppLayoutManager.removeAppFromCard(requireContext(), cardKey, packageName);
            } catch (Exception e) {
                Log.e("HomeFragment", "加载应用失败: " + packageName + ", 错误: " + e.getMessage());
            }
        }
    }
    private void refreshAllGrids() {
        // 清空所有网格
        settingsGrid.removeAllViews();
        officeGrid.removeAllViews();
        appsGrid.removeAllViews();

        // 重新加载所有应用
        loadSavedAppsToGrids();

        // 记录日志
        Log.d("HomeFragment", "已刷新所有网格");
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAllGrids(); // 每次回到前台都刷新一次
        updateAppDrawerIfShowing(); // 如果应用抽屉正在显示，刷新其内容
    }

    // 从包名获取应用模型
    private AppModel getAppModelFromPackageName(String packageName) {
        try {
            android.content.pm.PackageManager pm = requireContext().getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            String appName = pm.getApplicationLabel(appInfo).toString();
            Drawable appIcon = pm.getApplicationIcon(appInfo);

            return new AppModel(appName, appIcon, packageName);
        } catch (Exception e) {
            Log.e("HomeFragment", "获取应用信息失败: " + packageName, e);
            return null;
        }
    }

    
    // 添加应用到网格，并设置长按删除功能
    private void addAppToGrid(AppModel app, GridLayout grid, String cardKey) {
        View appView = createAppIconView(app);

        // 设置点击启动应用
        appView.setOnClickListener(v -> {
            try {
                Intent launchIntent = requireContext().getPackageManager()
                        .getLaunchIntentForPackage(app.getPackageName());
                if (launchIntent != null) {
                    startActivity(launchIntent);
                }
            } catch (Exception e) {
                Toast.makeText(requireContext(), "无法启动应用", Toast.LENGTH_SHORT).show();
            }
        });

        // 设置长按删除功能
        appView.setOnLongClickListener(v -> {
            showDeleteConfirmDialog(app, grid, cardKey);
            return true;
        });

        // 设置布局参数
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = GridLayout.LayoutParams.WRAP_CONTENT;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;

        params.setMargins(16, 13, 13, 10);

        // 添加到网格
        appView.setLayoutParams(params);
        grid.addView(appView);
    }

    private View createAppIconView(AppModel app) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        // 创建图标
        ImageView iconView = new ImageView(requireContext());
        iconView.setLayoutParams(new LinearLayout.LayoutParams(70,70));
//        iconView.setImageDrawable(app.getAppIcon());
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        IconUtils.setRoundedIcon(requireContext(), iconView, app.getAppIcon(), 20f); // 20dp圆角，更圆润


        // 创建文本
        TextView labelView = new TextView(requireContext());
        labelView.setText(app.getAppName());
        labelView.setTextColor(Color.WHITE);
        labelView.setPadding(0,0,0,0); // 移除顶部padding
        labelView.setTextSize(16); // 减小文字大小
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(1);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setVisibility(View.GONE);

        // 添加到容器
        container.addView(iconView);
        container.addView(labelView);

        // 关键修复：明确设置包名作为标签
        String packageName = app.getPackageName();
        container.setTag(packageName);
        Log.d("HomeFragment", "创建应用图标，设置标签: " + packageName);

        return container;
    }
    private void showDeleteConfirmDialog(AppModel app, GridLayout grid, String cardKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("移除应用图标")
                .setMessage("是否要从此卡片中移除 " + app.getAppName() + "?")
                .setPositiveButton("确定", (dialog, which) -> {
                    String packageName = app.getPackageName();

                    // 输出调试信息
                    Log.d("HomeFragment", "开始删除应用 " + app.getAppName() + " (包名: " + packageName + ")");
                    Log.d("HomeFragment", "删除前网格子视图数量: " + grid.getChildCount());

                    // 从卡片视图中移除
                    boolean removed = removeAppFromCardView(grid, packageName);

                    // 从持久化存储中移除
                    AppLayoutManager.removeAppFromCard(requireContext(), cardKey, packageName);

                    // 从 categorizedApps 中移除
                    if (categorizedApps != null) {
                        AppCategory category = getCategoryFromCardKey(cardKey);
                        if (category != null && categorizedApps.containsKey(category)) {
                            List<ApplicationInfo> appList = categorizedApps.get(category);
                            if (appList != null) {
                                // 移除包名匹配的应用
                                Iterator<ApplicationInfo> iterator = appList.iterator();
                                while (iterator.hasNext()) {
                                    ApplicationInfo info = iterator.next();
                                    if (info.packageName.equals(packageName)) {
                                        iterator.remove();
                                        Log.d("HomeFragment", "已从 categorizedApps 中移除应用: " + packageName);
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    // 如果直接删除失败，则尝试重新加载整个网格
                    if (!removed) {
                        Log.d("HomeFragment", "直接删除失败，尝试重新加载网格");
                        reloadGridFromStorage(grid, cardKey);
                    }

                    Log.d("HomeFragment", "删除后网格子视图数量: " + grid.getChildCount());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private AppCategory getCategoryFromCardKey(String cardKey) {
        if (cardKey.equals(AppLayoutManager.getSettingsCardKey())) {
            return AppCategory.SETTINGS;
        } else if (cardKey.equals(AppLayoutManager.getOfficeCardKey())) {
            return AppCategory.OFFICE;
        } else if (cardKey.equals(AppLayoutManager.getAppsCardKey())) {
            return AppCategory.APPS;
        }
        return null;
    }

    private void reloadGridFromStorage(GridLayout grid, String cardKey) {
        // 清空网格
        grid.removeAllViews();

        // 从存储加载应用列表
        List<String> packageNames = AppLayoutManager.getAppsForCard(requireContext(), cardKey);
        Log.d("HomeFragment", "从存储重新加载网格, 应用数量: " + packageNames.size());

        // 重新加载应用到网格
        for (String packageName : packageNames) {
            try {
                AppModel app = getAppModelFromPackageName(packageName);
                if (app != null) {
                    addAppToGrid(app, grid, cardKey);
                }
            } catch (Exception e) {
                Log.e("HomeFragment", "重新加载应用失败: " + packageName, e);
            }
        }
    }


    private void setupClickListeners() {
        // 设置搜索按钮点击事件
        searchButton.setOnClickListener(v -> {
            showAppDrawerDialog();
        });

        tvModeCard.setOnClickListener(v -> handleCardClick(v, this::launchTVModeDialog));
        themeCard.setOnClickListener(v -> handleCardClick(v, this::openWallpaperChooser));
        screensaverCard.setOnClickListener(v -> handleCardClick(v, this::openThemeSettings));
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
        new AlertDialog.Builder(getContext(), R.style.AppDrawer_CustomAlertDialog)
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
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM月dd日    EE", Locale.getDefault());
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
            // 基础工具栏现在是固定的LinearLayout
            LinearLayout basicToolsLayout = (LinearLayout) container;
            basicToolsLayout.removeAllViews();
            PackageManager pm = requireActivity().getPackageManager();

            // 固定四个基础工具应用
            String[] defaultTools = {
                "com.android.server.telecom",  // 计算器
                "com.android.deskclock",      // 时钟
                "com.android.camera2",        // 相机
                "com.android.settings"        // 设置
            };


            
            // 设置均等间距
            basicToolsLayout.setWeightSum(defaultTools.length);

            
            // 确保LinearLayout有足够高度显示图标和文字
            ViewGroup.LayoutParams layoutParams = basicToolsLayout.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                basicToolsLayout.setLayoutParams(layoutParams);
            }

            for (int i = 0; i < defaultTools.length; i++) {
                String packageName = defaultTools[i];
                try {
                    ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
                    LinearLayout appContainer = new LinearLayout(requireContext());
                    appContainer.setOrientation(LinearLayout.VERTICAL);
                    appContainer.setGravity(Gravity.CENTER);
                    
                    // 使用weight均匀分布，但保证宽度足够
                    LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                            0, 
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    containerParams.weight = 1;
                    containerParams.gravity = 1;

                    containerParams.leftMargin = 8;
                    containerParams.rightMargin = 8;


                    
                    appContainer.setLayoutParams(containerParams);

                    // 设置图标大小
                    ImageView appIcon = new ImageView(requireContext());
                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(120, 120);
                    iconParams.gravity = Gravity.CENTER_HORIZONTAL;
                    appIcon.setLayoutParams(iconParams);
                    appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    IconUtils.setRoundedIcon(requireContext(), appIcon, app.loadIcon(pm), 32f);

                    // 添加到容器
                    appContainer.addView(appIcon);

                    // 添加应用名称文本
                    TextView appLabel = new TextView(requireContext());
                    appLabel.setText(app.loadLabel(pm));
                    appLabel.setTextColor(Color.WHITE);
                    appLabel.setPadding(0, 6, 0, 8); // 增加上下padding确保文字显示完整
                    appLabel.setTextSize(16); // 减小文字大小
                    appLabel.setGravity(Gravity.CENTER);
                    appLabel.setMaxLines(1);
                    appLabel.setEllipsize(TextUtils.TruncateAt.END);
                    

                    
                    appContainer.addView(appLabel);

                    // 设置点击事件
                    appContainer.setOnClickListener(v -> {
                        Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                        if (launchIntent != null) {
                            startActivity(launchIntent);
                        }
                    });
                    appContainer.setTag(packageName);

                    basicToolsLayout.addView(appContainer);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e("HomeFragment", "找不到应用: " + packageName, e);
                }
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
                containerParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                containerParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
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
                appContainer.setTag(app.packageName);
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
        // 注销ViewPager2回调
        if (mainViewPager != null && pageChangeCallback != null) {
            mainViewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        
        // 清理所有对话框
        closeAllDialogs();
    }

    private class AppUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction()) ||
                    Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) ||
                    Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction()) ||
                    Intent.ACTION_PACKAGE_CHANGED.equals(intent.getAction()))) {
                
                // 应用安装或卸载后，刷新应用列表
                categorizeAndDisplayApps();
                
                // 如果应用抽屉正在显示，更新其内容
                updateAppDrawerIfShowing();
                
                // 记录变更
                String action = intent.getAction();
                Uri data = intent.getData();
                String packageName = data != null ? data.getSchemeSpecificPart() : null;
                Log.d("HomeFragment", "应用变更: " + action + ", 包名: " + packageName);
            }
        }
    }

    private void openWallpaperChooser() {
        // 打开自定义壁纸选择器，而不是系统壁纸选择器
        try {
            Intent intent = new Intent(requireContext(), com.msk.blacklauncher.activities.WallpaperChooserActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("HomeFragment", "启动壁纸选择器失败", e);
            // 如果自定义壁纸选择器失败，尝试使用系统壁纸选择器
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
        startActivity(Intent.createChooser(intent, "选择壁纸"));
        }
    }



    private void launchTVMode() {
        new AlertDialog.Builder(getContext(), R.style.AppDrawer_CustomAlertDialog) .setMessage("TV模式正在开发？").show();
    }

    private void openThemeSettings() {
        try {
            // 打开屏保选择活动
            Intent intent = new Intent(requireContext(), ScreensaverActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("HomeFragment", "启动屏保活动失败", e);
            Toast.makeText(requireContext(), "无法启动屏保设置", Toast.LENGTH_SHORT).show();
        }
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
        // 如果没有找到的应用，示一个提示
        Toast.makeText(getContext(), "未找到电子白板应用", Toast.LENGTH_SHORT).show();
    }


    private void openToolsGrid() {
        // 实现工具网格逻辑
    }

    private void openAppsGrid() {
        // 实现应用网格逻辑
    }
 private void showAppsDialog(String title, AppCategory category){
        // 关闭任何已存在的对话框
        closeAllDialogs();
        
        // 创建全屏模糊背景
        Dialog blurBackgroundDialog = new Dialog(requireContext(), R.style.BlurBackgroundDialog);
        blurBackgroundDialog.setContentView(R.layout.dialog_blur_background);
        blurBackgroundDialog.setCancelable(false);
        
        // 保存为当前模糊背景对话框
        currentBlurBackgroundDialog = blurBackgroundDialog;
        
        // 设置全屏
        Window blurWindow = blurBackgroundDialog.getWindow();
        if (blurWindow != null) {
            blurWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            blurWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
            // 确保状态栏和导航栏不可见
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            blurWindow.getDecorView().setSystemUiVisibility(flags);
        }
        
        // 初始化背景模糊效果
        eightbitlab.com.blurview.BlurView backgroundBlurView = blurBackgroundDialog.findViewById(R.id.background_blur_view);
        ViewGroup rootView = (ViewGroup) requireActivity().getWindow().getDecorView().findViewById(android.R.id.content);
        if (backgroundBlurView != null && rootView != null) {
            backgroundBlurView.setupWith(rootView)
                .setBlurRadius(20f)
                .setBlurAutoUpdate(true)
                .setOverlayColor(Color.parseColor("#33000000"));
        }
        
        // 显示模糊背景
        blurBackgroundDialog.show();
        
        // 创建应用网格对话框
        Dialog dialog = new Dialog(requireContext(), R.style.DialogOverBlurredBackground);
        
        // 保存为当前应用对话框
        currentAppDialog = dialog;
        
        dialog.setContentView(R.layout.dialog_apps_grid);
        FullScreenHelper.setFullScreenDialog(dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            // 设置对话框大小和位置
            WindowManager.LayoutParams params = window.getAttributes();
            params.width =  dpToPx(840); // 固定宽度
            params.height = dpToPx(500); // 固定高度
            params.gravity = Gravity.CENTER; // 居中显示
            window.setAttributes(params);
            
            // 确保状态栏和导航栏不可见
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            window.getDecorView().setSystemUiVisibility(flags);
        }
        
        TextView titleView = dialog.findViewById(R.id.dialogTitle);
        GridLayout dialogAppsGrid = dialog.findViewById(R.id.dialogAppsGrid);
        Button addButton = dialog.findViewById(R.id.addAppButton);

        // 设置标题，展示在对话框顶部
        titleView.setText(title);
        
        // 添加应用按钮点击事件
        addButton.setOnClickListener(v -> {
            // 打开应用选择器
            try {
                // 先关闭当前对话框和模糊背景
                dialog.dismiss();
                blurBackgroundDialog.dismiss();
                
                // 使用延迟打开应用选择器，确保前一个对话框完全关闭
                new Handler().postDelayed(() -> {
                    showAppPickerDialog(category, null);
                }, 150); // 延迟150毫秒
            } catch (Exception e) {
                Log.e("HomeFragment", "打开应用选择器失败: " + e.getMessage());
                Toast.makeText(requireContext(), "操作失败，请重试", Toast.LENGTH_SHORT).show();
                
                // 确保全屏状态
                FullScreenHelper.setImmersiveSticky(requireActivity());
            }
        });
        
        // 设置对话框关闭监听器，同时关闭模糊背景
        dialog.setOnDismissListener(dialogInterface -> {
            // 关闭模糊背景
            if (blurBackgroundDialog != null && blurBackgroundDialog.isShowing()) {
                try {
                    dialog.dismiss();
                    blurBackgroundDialog.dismiss();
                } catch (Exception e) {
                    Log.e("HomeFragment", "关闭模糊背景错误: " + e.getMessage());
                }
            }
            
            // 确保对话框关闭后应用仍然保持全屏状态
            FullScreenHelper.setImmersiveSticky(requireActivity());
        });

        // 根据类别获取对应的原始网格
        GridLayout sourceGrid = null;
        String cardKey = null;
        switch (category) {
            case SETTINGS:
                sourceGrid = settingsGrid;
                cardKey = AppLayoutManager.getSettingsCardKey();
                break;
            case OFFICE:
                sourceGrid = officeGrid;
                cardKey = AppLayoutManager.getOfficeCardKey();
                break;
            case APPS:
                sourceGrid = appsGrid;
                cardKey = AppLayoutManager.getAppsCardKey();
                break;
        }


        // 从原始网格复制应用图标到对话框网格
        if (sourceGrid != null) {
            final String finalCardKey = cardKey;
            final GridLayout finalSourceGrid = sourceGrid;

            for (int i = 0; i < sourceGrid.getChildCount(); i++) {
                View child = sourceGrid.getChildAt(i);
                if (child instanceof LinearLayout) {
                    LinearLayout originalContainer = (LinearLayout) child;



                    LinearLayout newContainer = new LinearLayout(requireContext());
                    newContainer.setOrientation(LinearLayout.VERTICAL);
                    newContainer.setGravity(Gravity.CENTER);


                    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                    params.width = GridLayout.LayoutParams.WRAP_CONTENT;
                    params.height = GridLayout.LayoutParams.WRAP_CONTENT;
                    params.setMargins(16, 24, 16, 24);
                    // 设置填充行为，使图标均匀分布在5列网格中
                    params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                    params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                    newContainer.setLayoutParams(params);

                    // 复制图标
                    ImageView originalIcon = (ImageView) originalContainer.getChildAt(0);
                    ImageView newIcon = new ImageView(requireContext());
                    newIcon.setLayoutParams(new LinearLayout.LayoutParams(180, 180));
//                    newIcon.setImageDrawable(originalIcon.getDrawable());
                    newIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    IconUtils.setRoundedIcon(requireContext(), newIcon, originalIcon.getDrawable(), 32f);
                    Log.d("", "showAppsDialog: ");
                    // 复制文本
                    TextView originalText = (TextView) originalContainer.getChildAt(1);
                    TextView newText = new TextView(requireContext());
                    newText.setText(originalText.getText());
                    newText.setTextColor(Color.BLACK);
                    newText.setTextSize(20);
                    newText.setGravity(Gravity.CENTER);
                    newText.setMaxLines(1);
                    newText.setEllipsize(TextUtils.TruncateAt.END);

                    // 保存应用包名作为标签，用于删除操作
                    String packageName = (String) originalContainer.getTag();
                    newContainer.setTag(packageName);
                    // 添加空值检查
                    if (packageName == null) {
                        Log.w("HomeFragment", "警告：原始容器标签为空，跳过创建此项");
                        String appName = null;
                        if (originalContainer.getChildCount() > 1 &&
                                originalContainer.getChildAt(1) instanceof TextView) {
                            TextView textView = (TextView) originalContainer.getChildAt(1);
                            appName = textView.getText().toString();

                            // 通过应用名称查找包名
                            if (appName != null && !appName.isEmpty()) {
                                // 这里需要通过应用名称查找包名，有点复杂
                                // 暂时跳过，后续可以实现
                                Log.w("HomeFragment", "标签为空的应用名称: " + appName);
                            }
                        }

                        // 如果无法获取包名，跳过创建
                        Log.w("HomeFragment", "警告：原始容器标签为空，跳过创建此项");
                        continue;
                    }

                    Log.d("HomeFragment", "复制应用到对话框，包名: " + packageName);
                    // 添加到新容器
                    newContainer.addView(newIcon);
                    newContainer.addView(newText);
                    fixMissingTags();
                    // 设置点击事件
                    newContainer.setOnClickListener(v -> {

                        if (originalContainer.hasOnClickListeners()) {
                            originalContainer.performClick();
                        }
                        dialog.dismiss();
                    });

                    // 添加长按删除功能
                    newContainer.setOnLongClickListener(v -> {
                        if (packageName == null) {
                            Log.e("HomeFragment", "长按删除失败：包名为空");
                            Toast.makeText(requireContext(), "无法删除此图标", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        // 显示删除确认对话框
                        showDeleteConfirmDialog(newText.getText().toString(), packageName,
                                dialogAppsGrid, finalSourceGrid, newContainer, finalCardKey);
                        return true;
                    });


                    dialogAppsGrid.addView(newContainer);
                }
            }
        }

        addButton.setOnClickListener(v -> {
            showAppPickerDialog(category, dialog);
        });
        dialog.show();
        
        // 在对话框显示后再次确保全屏状态
        if (window != null) {
            // 确保状态栏和导航栏不可见
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            window.getDecorView().setSystemUiVisibility(flags);
        }
        FullScreenHelper.setImmersiveSticky(requireActivity());
    }

    private void showDeleteConfirmDialog(String appName, String packageName,
                                         GridLayout dialogGrid, GridLayout sourceGrid,
                                         View dialogContainerView, String cardKey) {
        // 添加空值检查
        if (sourceGrid == null || packageName == null) {
            Log.e("HomeFragment", "删除失败：源网格或包名为空 - " +
                    "网格:" + (sourceGrid == null ? "null" : "非空") +
                    "，包名:" + (packageName == null ? "null" : packageName));
            Toast.makeText(requireContext(), "无法删除应用图标", Toast.LENGTH_SHORT).show();
            return;
        }

        // 原有代码
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("移除应用图标")
                .setMessage("是否要从此卡片中移除 " + appName + "?")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 从对话框网格中移除
                    dialogGrid.removeView(dialogContainerView);

                    // 从源卡片视图中移除
                    removeAppFromCardView(sourceGrid, packageName);

                    // 从持久化存储中移除
                    AppLayoutManager.removeAppFromCard(requireContext(), cardKey, packageName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 从卡片视图中移除应用图标
     */
    private boolean removeAppFromCardView(GridLayout cardGrid, String packageName) {
        if (cardGrid == null || packageName == null) {
            // 获取调用堆栈以追踪调用来源
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            StringBuilder callPath = new StringBuilder();
            for (int i = 3; i < Math.min(6, stackTrace.length); i++) {
                callPath.append(stackTrace[i].getClassName())
                        .append(".").append(stackTrace[i].getMethodName())
                        .append("(行:").append(stackTrace[i].getLineNumber()).append(")");
                if (i < 5 && i < stackTrace.length - 1) callPath.append(" <- ");
            }

            Log.e("HomeFragment", "网格或包名为空 (调用来源: " + callPath.toString() + ")");
            return false;
        }

        // 记录当前子视图数量
        int childCount = cardGrid.getChildCount();
        Log.d("HomeFragment", "开始在网格中搜索包名: " + packageName + " (共" + childCount + "个子视图)");

        // 查找要移除的视图
        View viewToRemove = null;
        for (int i = 0; i < childCount; i++) {
            View child = cardGrid.getChildAt(i);
            Object tag = child != null ? child.getTag() : null;

            Log.d("HomeFragment", "子视图 #" + i + " 标签: " + (tag == null ? "null" : tag));

            // 添加空值检查
            if (child != null && tag != null && packageName.equals(tag.toString())) {
                viewToRemove = child;
                Log.d("HomeFragment", "找到匹配的子视图，位置: " + i);
                break;
            }
        }

        // 如果找到要移除的视图，则移除它
        if (viewToRemove != null) {
            cardGrid.removeView(viewToRemove);
            Log.d("HomeFragment", "已从卡片视图移除应用: " + packageName);
            return true;
        } else {
            Log.w("HomeFragment", "未找到要移除的应用: " + packageName);
            return false;
        }
    }

    private void showAppPickerDialog(AppCategory category, Dialog parentDialog) {
        // 关闭任何已存在的对话框
        closeAllDialogs();
        
        // 创建全屏模糊背景
        Dialog blurBackgroundDialog = new Dialog(requireContext(), R.style.BlurBackgroundDialog);
        blurBackgroundDialog.setContentView(R.layout.dialog_blur_background);
        blurBackgroundDialog.setCancelable(false);
        
        // 保存为当前选择器模糊背景
        currentPickerBlurDialog = blurBackgroundDialog;
        
        // 设置全屏
        Window blurWindow = blurBackgroundDialog.getWindow();
        if (blurWindow != null) {
            blurWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            blurWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
            // 确保状态栏和导航栏不可见
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            blurWindow.getDecorView().setSystemUiVisibility(flags);
        }
        
        // 初始化背景模糊效果
        eightbitlab.com.blurview.BlurView backgroundBlurView = blurBackgroundDialog.findViewById(R.id.background_blur_view);
        ViewGroup rootView = (ViewGroup) requireActivity().getWindow().getDecorView().findViewById(android.R.id.content);
        if (backgroundBlurView != null && rootView != null) {
            backgroundBlurView.setupWith(rootView)
                .setBlurRadius(20f)
                .setBlurAutoUpdate(true)
                .setOverlayColor(Color.parseColor("#33000000"));
        }
        
        // 显示模糊背景
        blurBackgroundDialog.show();

        // 创建应用选择对话框
        Dialog dialog = new Dialog(requireContext(), R.style.DialogOverBlurredBackground);
        
        // 保存为当前选择器对话框
        currentAppPickerDialog = dialog;
        
        dialog.setContentView(R.layout.dialog_apps_grid);
        // 应用全屏设置
        FullScreenHelper.setFullScreenDialog(dialog);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width =  dpToPx(840); // 固定宽度
            params.height = dpToPx(580); // 固定高度
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
            
            // 确保状态栏和导航栏不可见
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            window.getDecorView().setSystemUiVisibility(flags);
        }

        TextView titleView = dialog.findViewById(R.id.dialogTitle);
        GridLayout dialogAppsGrid = dialog.findViewById(R.id.dialogAppsGrid);
        Button addButton = dialog.findViewById(R.id.addAppButton);

        titleView.setText("选择应用");
        addButton.setVisibility(View.GONE);
        dialogAppsGrid.setColumnCount(5);
        
        // 设置对话框关闭监听器，同时关闭模糊背景
        dialog.setOnDismissListener(dialogInterface -> {
            // 关闭模糊背景
            if (blurBackgroundDialog != null && blurBackgroundDialog.isShowing()) {
                try {
                    blurBackgroundDialog.dismiss();
                } catch (Exception e) {
                    Log.e("HomeFragment", "关闭模糊背景错误: " + e.getMessage());
                }
            }
            
            // 确保对话框关闭后应用仍然保持全屏状态
            FullScreenHelper.setImmersiveSticky(requireActivity());
        });

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

        // 提取应用信息并排序
        for (ResolveInfo resolveInfo : resolveInfos) {
            try {
                // 修复：确保正确获取包名
                String packageName = resolveInfo.activityInfo.packageName;

                // 通过包名获取完整的应用信息
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                allApps.add(appInfo);

                // 添加调试日志
                Log.d("HomeFragment", "找到应用: " + appInfo.loadLabel(pm) + ", 包名: " + packageName);
            } catch (Exception e) {
                Log.e("HomeFragment", "获取应用信息失败: " + e.getMessage());
            }
        }

        // 显示所有可用的应用
        for (ApplicationInfo app : allApps) {
            LinearLayout appContainer = new LinearLayout(requireContext());
            appContainer.setOrientation(LinearLayout.VERTICAL);
            appContainer.setGravity(Gravity.CENTER);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = GridLayout.LayoutParams.WRAP_CONTENT;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(16, 24, 16, 24);
            // 设置填充行为，使图标均匀分布
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
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

            // 修改应用点击逻辑，确保正确关闭对话框
            appContainer.setOnClickListener(v -> {
                try {
                    // 获取应用包名
                    String packageName = app.packageName;
                    Log.d("HomeFragment", "尝试添加应用: " + app.loadLabel(pm) + ", 包名: " + packageName);

                    // 判断是否已经添加到持久化存储中
                    String cardKey = null;
                    switch (category) {
                        case SETTINGS:
                            cardKey = AppLayoutManager.getSettingsCardKey();
                            break;
                        case OFFICE:
                            cardKey = AppLayoutManager.getOfficeCardKey();
                            break;
                        case APPS:
                            cardKey = AppLayoutManager.getAppsCardKey();
                            break;
                    }

                    // 添加应用到持久化存储
                    boolean wasAdded = false;
                    if (cardKey != null) {
                        // 添加到持久化存储
                        List<String> currentApps = AppLayoutManager.getAppsForCard(requireContext(), cardKey);

                        // 检查是否已经添加过
                        if (currentApps.contains(packageName)) {
                            Log.d("HomeFragment", "应用已存在，跳过添加: " + packageName);
                            Toast.makeText(requireContext(), "此应用已添加", Toast.LENGTH_SHORT).show();
                        } else {
                            // 添加到列表
                            currentApps.add(packageName);
                            AppLayoutManager.saveAppsForCard(requireContext(), cardKey, currentApps);

                            // 更新UI
                            Log.d("HomeFragment", "成功添加应用: " + packageName);
                            wasAdded = true;

                            // 创建并添加应用图标
                            try {
                                AppModel appModel = new AppModel(
                                        app.loadLabel(pm).toString(),
                                        app.loadIcon(pm),
                                        packageName
                                );

                                // 获取目标网格
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
                                    addAppToGrid(appModel, targetGrid, cardKey);
                                    Toast.makeText(requireContext(), "已添加: " + appModel.getAppName(), Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Log.e("HomeFragment", "创建应用图标失败: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }

                    // 先关闭应用选择对话框和模糊背景
                    closeAllDialogs();
                    
                    // 如果成功添加了应用，重新打开应用网格对话框显示最新内容
                    if (wasAdded) {
                        // 使用短延迟确保前一个对话框完全关闭
                        new Handler().postDelayed(() -> {
                            String dialogTitle = "";
                            switch (category) {
                                case SETTINGS:
                                    dialogTitle = "基础设置";
                                    break;
                                case OFFICE:
                                    dialogTitle = "办公学习";
                                    break;
                                case APPS:
                                    dialogTitle = "应用宝";
                                    break;
                            }
                            showAppsDialog(dialogTitle, category);
                        }, 200); // 200毫秒延迟确保前一个对话框已完全关闭
                    } else {
                        // 确保全屏状态
                        FullScreenHelper.setImmersiveSticky(requireActivity());
                    }
                } catch (Exception e) {
                    Log.e("HomeFragment", "添加应用失败: " + e.getMessage(), e);
                    Toast.makeText(requireContext(), "添加应用失败", Toast.LENGTH_SHORT).show();
                    
                    // 错误处理：确保对话框关闭
                    closeAllDialogs();
                    
                    // 确保全屏状态
                    FullScreenHelper.setImmersiveSticky(requireActivity());
                }
            });

            dialogAppsGrid.addView(appContainer);
        }

        // 显示对话框
        dialog.show();
        
        // 在对话框显示后再次确保全屏状态
        if (window != null) {
            // 确保状态栏和导航栏不可见
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            window.getDecorView().setSystemUiVisibility(flags);
        }
        FullScreenHelper.setImmersiveSticky(requireActivity());
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

    // 添加一个方法来关闭所有已存在的对话框
    private void closeAllDialogs() {
        try {
            if (currentAppDialog != null && currentAppDialog.isShowing()) {
                currentAppDialog.dismiss();
                currentAppDialog = null;
            }
            
            if (currentBlurBackgroundDialog != null && currentBlurBackgroundDialog.isShowing()) {
                currentBlurBackgroundDialog.dismiss();
                currentBlurBackgroundDialog = null;
            }
            
            if (currentAppPickerDialog != null && currentAppPickerDialog.isShowing()) {
                currentAppPickerDialog.dismiss();
                currentAppPickerDialog = null;
            }
            
            if (currentPickerBlurDialog != null && currentPickerBlurDialog.isShowing()) {
                currentPickerBlurDialog.dismiss();
                currentPickerBlurDialog = null;
            }
            
            if (currentAppDrawerDialog != null && currentAppDrawerDialog.isShowing()) {
                currentAppDrawerDialog.dismiss();
                currentAppDrawerDialog = null;
            }
            
            if (currentAppDrawerBlurDialog != null && currentAppDrawerBlurDialog.isShowing()) {
                currentAppDrawerBlurDialog.dismiss();
                currentAppDrawerBlurDialog = null;
            }
        } catch (Exception e) {
            Log.e("HomeFragment", "关闭对话框出错: " + e.getMessage());
        }
    }

    private void initPageIndicator() {
        if (pageIndicator == null || !isAdded()) {
            return;
        }

        // 如果有ViewPager2，直接关联
        if (mainViewPager != null) {
            Log.d("HomeFragment", "将页面指示器关联到ViewPager2");
            pageIndicator.setupWithViewPager(mainViewPager);
        } else {
            // 如果没有ViewPager2，则手动设置
            Log.d("HomeFragment", "手动设置页面指示器: 总页数=2, 当前页=0");
            pageIndicator.setupManually(2, 0); // 总共2页，当前是第一页(索引0)
        }
    }

    /**
     * 更新页面指示器状态
     * @param currentPage 当前页面位置
     */
    public void updatePageIndicator(int currentPage) {
        if (pageIndicator != null) {
            Log.d("HomeFragment", "更新页面指示器位置: " + currentPage);
            // 当ViewPager2关联时，这个方法可以不调用，因为PageIndicator会自动更新
            // 但为了向后兼容，我们保留此方法
            pageIndicator.setCurrentPage(currentPage);
        }
    }

    private void showAppDrawerDialog() {
        // 关闭任何已存在的对话框
        closeAllDialogs();
        
        // 创建全屏模糊背景
        Dialog blurBackgroundDialog = new Dialog(requireContext(), R.style.AppDrawer_BlurBackgroundDialog);
        blurBackgroundDialog.setContentView(R.layout.dialog_blur_background);
        blurBackgroundDialog.setCancelable(true);
        
        // 保存为当前模糊背景对话框
        currentAppDrawerBlurDialog = blurBackgroundDialog;
        
        // 设置全屏
        Window blurWindow = blurBackgroundDialog.getWindow();
        if (blurWindow != null) {
            blurWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            blurWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
            // 确保状态栏和导航栏不可见
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            blurWindow.getDecorView().setSystemUiVisibility(flags);
        }
        
        // 初始化背景模糊效果
        eightbitlab.com.blurview.BlurView backgroundBlurView = blurBackgroundDialog.findViewById(R.id.background_blur_view);
        ViewGroup rootView = (ViewGroup) requireActivity().getWindow().getDecorView().findViewById(android.R.id.content);
        if (backgroundBlurView != null && rootView != null) {
            backgroundBlurView.setupWith(rootView)
                .setBlurRadius(20f)
                .setBlurAutoUpdate(true)
                .setOverlayColor(Color.parseColor("#33000000"));
        }
        
        // 显示模糊背景
        blurBackgroundDialog.show();
        
        // 创建应用抽屉对话框
        Dialog dialog = new Dialog(requireContext(), R.style.AppDrawer_DialogOverBlurredBackground);
        
        // 保存为当前应用抽屉对话框
        currentAppDrawerDialog = dialog;
        
        dialog.setContentView(R.layout.dialog_app_drawer);
        FullScreenHelper.setFullScreenDialog(dialog);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            // 设置对话框从底部上拉出现的动画
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(R.style.AppDrawerAnimation);
            
            // 设置对话框大小为全屏
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(params);
            
            // 确保状态栏和导航栏不可见
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            window.getDecorView().setSystemUiVisibility(flags);
        }
        
        // 获取搜索框和应用网格视图
        EditText searchEditText = dialog.findViewById(R.id.app_drawer_search);
        GridLayout appDrawerGrid = dialog.findViewById(R.id.app_drawer_grid);
        
        // 初始化应用抽屉内的模糊视图
        eightbitlab.com.blurview.BlurView drawerBlurView = dialog.findViewById(R.id.app_drawer_blur_view);
        if (drawerBlurView != null && rootView != null) {
            drawerBlurView.setupWith(rootView)
                .setBlurRadius(20f)
                .setBlurAutoUpdate(true)
                .setOverlayColor(Color.parseColor("#33000000"));
        }
        
        // 设置网格为9列
        appDrawerGrid.setColumnCount(9);
        appDrawerGrid.setRowCount(5);
        
        // 加载所有应用并显示在网格中
        loadAppsToAppDrawer(appDrawerGrid, searchEditText);
        
        // 设置对话框关闭监听器，同时关闭模糊背景
        dialog.setOnDismissListener(dialogInterface -> {
            // 关闭模糊背景
            if (blurBackgroundDialog != null && blurBackgroundDialog.isShowing()) {
                try {
                    blurBackgroundDialog.dismiss();
                } catch (Exception e) {
                    Log.e("HomeFragment", "关闭模糊背景错误: " + e.getMessage());
                }
            }
            
            // 清除引用
            currentAppDrawerDialog = null;
            currentAppDrawerBlurDialog = null;
            
            // 确保对话框关闭后应用仍然保持全屏状态
            FullScreenHelper.setImmersiveSticky(requireActivity());
        });
        
        // 设置搜索框回车键监听
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                // 隐藏软键盘
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                        requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });
        
        dialog.show();
        
        // 在对话框显示后再次确保全屏状态
        if (window != null) {
            // 确保状态栏和导航栏不可见
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            window.getDecorView().setSystemUiVisibility(flags);
        }
        FullScreenHelper.setImmersiveSticky(requireActivity());
        
        // 记录日志
        Log.d("HomeFragment", "应用抽屉已打开，共加载 " + 
              (allAppDrawerItems != null ? allAppDrawerItems.size() : 0) + " 个应用");
    }

    private void loadAppsToAppDrawer(GridLayout grid, EditText searchEditText) {
        // 获取所有已安装的应用
        PackageManager pm = requireActivity().getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        
        // 创建应用信息列表
        List<AppDrawerItem> appList = new ArrayList<>();
        
        for (ResolveInfo resolveInfo : resolveInfos) {
            try {
                String packageName = resolveInfo.activityInfo.packageName;
                String appName = resolveInfo.loadLabel(pm).toString();
                Drawable appIcon = resolveInfo.loadIcon(pm);
                
                // 生成拼音首字母，用于搜索
                String pinyinFirstLetters = PinyinUtils.getFirstLetters(appName);
                
                appList.add(new AppDrawerItem(appName, appIcon, packageName, pinyinFirstLetters));
            } catch (Exception e) {
                Log.e("HomeFragment", "获取应用信息失败: " + e.getMessage());
            }
        }
        
        // 按应用名称排序
        appList.sort((app1, app2) -> app1.appName.compareToIgnoreCase(app2.appName));
        
        // 获取当前搜索文本，用于筛选应用
        String searchText = searchEditText.getText().toString().toLowerCase().trim();
        List<AppDrawerItem> filteredApps;
        
        if (TextUtils.isEmpty(searchText)) {
            // 搜索框为空，显示所有应用
            filteredApps = appList;
        } else {
            // 筛选匹配搜索文本的应用（包括拼音首字母）
            filteredApps = new ArrayList<>();
            for (AppDrawerItem app : appList) {
                // 检查应用名
                String appNameLower = app.appName.toLowerCase();
                // 检查应用名拼音首字母
                String pinyinLower = app.pinyinFirstLetters.toLowerCase();
                
                // 修改搜索匹配逻辑
                boolean matched = false;
                if (searchText.matches("[a-zA-Z]+")) {
                    // 对于英文搜索，仅匹配应用名起始字母或拼音首字母
                    if (appNameLower.startsWith(searchText)) {
                        matched = true;
                    } else if (pinyinLower.length() > 0) {
                        // 对于拼音首字母，确保不是简单地包含搜索字符
                        // 而是必须以搜索文本开头或者是单词的开头
                        String[] words = pinyinLower.split("[^a-zA-Z0-9]");
                        for (String word : words) {
                            if (word.startsWith(searchText)) {
                                matched = true;
                                break;
                            }
                        }
                    }
                } else {
                    // 对于非拉丁字母输入，使用包含匹配
                    matched = appNameLower.contains(searchText) || 
                             pinyinLower.contains(searchText);
                }
                
                if (matched) {
                    filteredApps.add(app);
                }
            }
        }
        
        // 显示过滤后的应用
        displayAppsInDrawer(grid, filteredApps);
        
        // 搜索监听器仅在首次加载时设置，避免重复设置
        if (searchEditText.getTag() == null) {
            searchEditText.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                
                @Override
                public void afterTextChanged(android.text.Editable s) {
                    String searchText = s.toString().toLowerCase().trim();
                    
                    if (searchText.isEmpty()) {
                        // 如果搜索框为空，显示所有应用
                        displayAppsInDrawer(grid, appList);
                    } else {
                        // 按首字母匹配筛选应用（包括拼音首字母）
                        List<AppDrawerItem> filteredApps = new ArrayList<>();
                        
                        for (AppDrawerItem app : appList) {
                            String appNameLower = app.appName.toLowerCase();
                            String pinyinLower = app.pinyinFirstLetters.toLowerCase();
                            
                            // 修改搜索匹配逻辑
                            // 1. 如果输入的是拉丁字母，则优先匹配应用名的起始字符
                            boolean matched = false;
                            if (searchText.matches("[a-zA-Z]+")) {
                                // 对于英文搜索，仅匹配应用名起始字母或拼音首字母
                                if (appNameLower.startsWith(searchText)) {
                                    matched = true;
                                } else if (pinyinLower.length() > 0) {
                                    // 对于拼音首字母，确保不是简单地包含搜索字符
                                    // 而是必须以搜索文本开头或者是单词的开头
                                    String[] words = pinyinLower.split("[^a-zA-Z0-9]");
                                    for (String word : words) {
                                        if (word.startsWith(searchText)) {
                                            matched = true;
                                            break;
                                        }
                                    }
                                }
                            } else {
                                // 对于非拉丁字母输入，使用包含匹配
                                matched = appNameLower.contains(searchText) || 
                                         pinyinLower.contains(searchText);
                            }
                            
                            if (matched) {
                                filteredApps.add(app);
                            }
                        }
                        
                        displayAppsInDrawer(grid, filteredApps);
                    }
                }
            });
            
            // 标记为已设置监听器
            searchEditText.setTag("listener_set");
        }
        
        // 保存应用列表到Fragment实例变量，方便后续使用
        allAppDrawerItems = appList;
    }
    
    // 应用抽屉项数据类
    private static class AppDrawerItem {
        String appName;
        Drawable appIcon;
        String packageName;
        String pinyinFirstLetters; // 拼音首字母，用于搜索
        
        AppDrawerItem(String appName, Drawable appIcon, String packageName) {
            this(appName, appIcon, packageName, "");
        }
        
        AppDrawerItem(String appName, Drawable appIcon, String packageName, String pinyinFirstLetters) {
            this.appName = appName;
            this.appIcon = appIcon;
            this.packageName = packageName;
            this.pinyinFirstLetters = pinyinFirstLetters;
        }
    }

    /**
     * 如果应用抽屉正在显示，更新其内容
     */
    private void updateAppDrawerIfShowing() {
        // 检查应用抽屉对话框是否存在且正在显示
        if (currentAppDrawerDialog != null && currentAppDrawerDialog.isShowing()) {
            try {
                // 获取对话框中的应用网格和搜索框
                GridLayout appDrawerGrid = currentAppDrawerDialog.findViewById(R.id.app_drawer_grid);
                EditText searchEditText = currentAppDrawerDialog.findViewById(R.id.app_drawer_search);
                
                if (appDrawerGrid != null && searchEditText != null) {
                    // 获取当前搜索框内容
                    String currentSearchText = searchEditText.getText().toString();
                    
                    // 重新加载应用列表
                    loadAppsToAppDrawer(appDrawerGrid, searchEditText);
                    
                    // 保持之前的搜索内容
                    if (!TextUtils.isEmpty(currentSearchText)) {
                        searchEditText.setText(currentSearchText);
                    }
                    
                    Log.d("HomeFragment", "应用抽屉内容已更新");
                }
            } catch (Exception e) {
                Log.e("HomeFragment", "更新应用抽屉内容失败: " + e.getMessage());
            }
        }
    }

    // 用于存储所有应用的列表
    private List<AppDrawerItem> allAppDrawerItems;
    
    private void displayAppsInDrawer(GridLayout grid, List<AppDrawerItem> apps) {
        // 清空网格
        grid.removeAllViews();
        
        // 获取PackageManager
        PackageManager pm = requireActivity().getPackageManager();
        
        // 计算合适的应用图标尺寸
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels - 200; // 考虑搜索栏高度
        
        int columnCount = 9;
        int rowCount = 5;
        
        int iconSize = Math.min(screenWidth / (columnCount + 1), screenHeight / (rowCount + 1));
        iconSize = Math.min(iconSize, 120); // 设置最大尺寸
        
        // 计算每个单元格的宽高，确保均匀分布
        int cellWidth = screenWidth / columnCount;
        int cellHeight = screenHeight / rowCount;
        
        // 添加应用到网格，确保均匀分布
        for (int i = 0; i < Math.min(apps.size(), rowCount * columnCount); i++) {
            AppDrawerItem app = apps.get(i);
            
            // 计算当前项应该在的行和列
            int row = i / columnCount;
            int col = i % columnCount;
            
            // 创建应用图标视图
            LinearLayout appContainer = new LinearLayout(requireContext());
            appContainer.setOrientation(LinearLayout.VERTICAL);
            appContainer.setGravity(Gravity.CENTER);
            
            // 设置布局参数，按照计算好的单元格尺寸均匀分布
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellWidth;
            params.height = cellHeight;
            params.rowSpec = GridLayout.spec(row, 1, GridLayout.FILL);
            params.columnSpec = GridLayout.spec(col, 1, GridLayout.FILL);
            params.setGravity(Gravity.CENTER);
            appContainer.setLayoutParams(params);
            
            // 创建图标
            ImageView appIcon = new ImageView(requireContext());
            appIcon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            IconUtils.setRoundedIcon(requireContext(), appIcon, app.appIcon, 20f);
            appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            
            // 创建应用名称 - 放大字体
            TextView appName = new TextView(requireContext());
            appName.setText(app.appName);
            appName.setTextColor(Color.WHITE);
            appName.setTextSize(16); // 放大字体大小
            appName.setPadding(0, 10, 0, 0); // 增加顶部间距
            appName.setGravity(Gravity.CENTER);
            appName.setMaxLines(1);
            appName.setEllipsize(TextUtils.TruncateAt.END);
            appName.setTypeface(null, Typeface.BOLD); // 设置为粗体以增强可读性
            
            // 添加到容器
            appContainer.addView(appIcon);
            appContainer.addView(appName);
            
            // 设置点击事件
            appContainer.setOnClickListener(v -> {
                try {
                    Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                        
                        // 关闭对话框
                        closeAllDialogs();
                    }
                } catch (Exception e) {
                    Log.e("HomeFragment", "启动应用失败: " + e.getMessage());
                    Toast.makeText(requireContext(), "无法启动应用", Toast.LENGTH_SHORT).show();
                }
            });
            
            // 添加到网格
            grid.addView(appContainer);
        }
        
        // 如果应用数量超过一页，显示"更多"按钮或分页指示器
        if (apps.size() > rowCount * columnCount) {
            // 在这里可以添加分页功能
            Toast.makeText(requireContext(), "共有 " + apps.size() + " 个应用", Toast.LENGTH_SHORT).show();
        }
    }

    int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}