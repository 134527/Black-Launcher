package com.msk.blacklauncher.fragments;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.WallpaperManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.msk.blacklauncher.R;
import com.msk.blacklauncher.Utils.IconUtils;
import com.msk.blacklauncher.adapters.WorkspaceAdapter;
import com.msk.blacklauncher.adapters.WorkspacePagerAdapter;
import com.msk.blacklauncher.model.AppModel;
import com.msk.blacklauncher.view.CellLayout;


import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class WorkspaceFragment extends Fragment {

    private static final String PREFS_NAME = "WorkspacePrefs";
    private static final String APP_POSITIONS_KEY = "AppPositions";
    private static final int COLUMNS = 9;
    private static final int ROWS = 4;

    private ViewPager2 workspacePager;
    private com.msk.blacklauncher.view.PageIndicator pageIndicator;
    private List<List<CellLayout.Cell>> workspaceCells;
    private PackageManager packageManager;
    private static final String TAG = "WorkspaceFragment";
    private volatile boolean isHandlingOverflow = false;

    private static class SerializableAppPosition {
        @Expose
        private String packageName;
        @Expose
        private int pageIndex;
        @Expose
        private int positionInPage;

        public SerializableAppPosition(String packageName, int pageIndex, int positionInPage) {
            this.packageName = packageName;
            this.pageIndex = pageIndex;
            this.positionInPage = positionInPage;
        }

        public String getPackageName() { return packageName; }
        public int getPageIndex() { return pageIndex; }
        public int getPositionInPage() { return positionInPage; }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");

        requireActivity().registerReceiver(appChangeReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (appChangeReceiver != null) {
            requireActivity().unregisterReceiver(appChangeReceiver);
        }
    }

    private BroadcastReceiver appChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            Uri data = intent.getData();
            if (data == null) return;

            String packageName = data.getSchemeSpecificPart();

            if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
                    Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                    if (packageManager.getLaunchIntentForPackage(packageName) != null) {
                        Drawable icon = appInfo.loadIcon(packageManager);
                        String label = appInfo.loadLabel(packageManager).toString();
                        AppModel newApp = new AppModel(label, icon, packageName);
                        addAppToWorkspace(newApp);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e("WorkspaceFragment", "找不到包: " + packageName, e);
                }
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                removeAppFromWorkspace(packageName);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_workspace, container, false);

        workspacePager = view.findViewById(R.id.workspace_pager);
        pageIndicator = view.findViewById(R.id.page_indicator);
        packageManager = requireActivity().getPackageManager();

        boolean forceInit = false;
        workspaceCells = loadAppPositions();
        
        boolean hasApps = false;
        for (List<CellLayout.Cell> page : workspaceCells) {
            for (CellLayout.Cell cell : page) {
                if (cell != null && !cell.getTag().equals("empty")) {
                    hasApps = true;
                    break;
                }
            }
            if (hasApps) break;
        }
        
        if (workspaceCells.isEmpty() || !hasApps || forceInit) {
            Log.d(TAG, "没有找到应用或强制初始化，重新加载所有应用");
            workspaceCells = initializeWorkspaceApps();
            saveAppPositions();
        }
        
        if (workspaceCells.isEmpty()) {
            workspaceCells.add(createEmptyPage());
        }

        // 设置页面适配器和CellLayout监听器
        workspacePager.setAdapter(new WorkspaceAdapter(new CellLayout.OnCellOverflowListener() {
            @Override
            public boolean onCellOverflow(CellLayout.Cell overflowCell, int pageIndex) {
                // 处理单元格溢出
                return handleCellOverflow(overflowCell, pageIndex);
            }

            @Override
            public void onCellSwapped(CellLayout.Cell draggingCell, int fromPage, int toPage, int targetColumn, int targetRow) {
                // 处理跨页面拖拽
                handleCellSwap(draggingCell, fromPage, toPage, targetColumn, targetRow);
            }

            @Override
            public void saveAppPositions() {
                // 保存应用位置信息
                WorkspaceFragment.this.saveAppPositions();
            }

            @Override
            public void setupWorkspacePage(int pageIndex) {
                // 设置工作区页面
                WorkspaceFragment.this.setupWorkspacePage(pageIndex);
            }
        }));

        // 手动设置页面指示器 - 总共2页，当前是第1页（索引从0开始）
        new Handler(Looper.getMainLooper()).post(() -> {
            if (pageIndicator != null && isAdded()) {
                Log.d(TAG, "设置全局页面指示器: 总页数=2, 当前页=1");
                
                // 手动设置为主Activity的ViewPager2状态
                pageIndicator.setupManually(2, 1); // 总共2页，当前是第二页(索引1)
                
                // 确保显示
                pageIndicator.requestLayout();
                pageIndicator.invalidate();
            }
        });

        workspacePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageIndicator(position);
            }
        });

        return view;
    }

    private void addNewPage() {
        List<CellLayout.Cell> newPage = createEmptyPage();
        workspaceCells.add(newPage);

        initPageIndicator();

        if (workspacePager.getAdapter() != null) {
            workspacePager.getAdapter().notifyDataSetChanged();
        }
    }

    private Pair<Integer, Integer> findAvailablePosition() {
        for (int pageIndex = 0; pageIndex < workspaceCells.size(); pageIndex++) {
            List<CellLayout.Cell> page = workspaceCells.get(pageIndex);
            for (int position = 0; position < page.size(); position++) {
                CellLayout.Cell cell = page.get(position);
                if (cell.getTag().equals("empty")) {
                    return new Pair<>(pageIndex, position);
                }
            }
        }

        addNewPage();
        return new Pair<>(workspaceCells.size() - 1, 0);
    }

    public void addAppToWorkspace(AppModel app) {
        try {
            Pair<Integer, Integer> position = findAvailablePosition();
            int pageIndex = position.first;
            int positionInPage = position.second;

            View appView = createAppIconView(app);
            CellLayout.Cell cell = new CellLayout.Cell(app.getPackageName(), appView);

            workspaceCells.get(pageIndex).set(positionInPage, cell);

            saveAppPositions();

            if (workspacePager.getAdapter() != null) {
                workspacePager.getAdapter().notifyItemChanged(pageIndex);

                if (workspacePager.getCurrentItem() == pageIndex) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        setupWorkspacePage(pageIndex);
                    });
                }
            }

            Log.d(TAG, "已添加应用到工作区: " + app.getAppName());
        } catch (Exception e) {
            Log.e(TAG, "添加应用到工作区失败", e);
        }
    }

    public void removeAppFromWorkspace(String packageName) {
        boolean removed = false;
        int removedPageIndex = -1;

        for (int pageIndex = 0; pageIndex < workspaceCells.size(); pageIndex++) {
            List<CellLayout.Cell> page = workspaceCells.get(pageIndex);
            for (int positionInPage = 0; positionInPage < page.size(); positionInPage++) {
                CellLayout.Cell cell = page.get(positionInPage);
                if (cell.getTag().equals(packageName)) {
                    View emptyView = new View(getContext());
                    emptyView.setVisibility(View.INVISIBLE);
                    CellLayout.Cell emptyCell = new CellLayout.Cell("empty", emptyView);
                    page.set(positionInPage, emptyCell);
                    removed = true;
                    removedPageIndex = pageIndex;
                    Log.d(TAG, "已从工作区移除应用: " + packageName);
                    break;
                }
            }
            if (removed) break;
        }

        if (removed) {
            final int updatedPageIndex = removedPageIndex;

            saveAppPositions();

            if (workspacePager.getAdapter() != null) {
                workspacePager.getAdapter().notifyItemChanged(updatedPageIndex);

                if (workspacePager.getCurrentItem() == updatedPageIndex) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        setupWorkspacePage(updatedPageIndex);
                    });
                }
            }
        }
    }

    private boolean handleCellOverflow(CellLayout.Cell overflowCell, int pageIndex) {
        Log.d(TAG, "处理溢出单元格: " + (overflowCell != null ? overflowCell.getTag() : "null") + " 页面: " + pageIndex);

        synchronized (this) {
            if (isHandlingOverflow) {
                Log.w(TAG, "已有溢出处理进行中，忽略此次溢出");
                return false;
            }
            isHandlingOverflow = true;
        }

        try {
            if (overflowCell == null || overflowCell.getContentView() == null) {
                Log.e(TAG, "无效的溢出单元格");
                return false;
            }

            if (!isAdded() || requireActivity() == null) {
                Log.e(TAG, "片段未附加到活动");
                return false;
            }

            requireActivity().runOnUiThread(() -> {
                try {
                    Pair<Integer, Integer> emptyPosition = findEmptyPositionInExistingPages();
                    if (emptyPosition != null) {
                        int targetPage = emptyPosition.first;
                        int targetPos = emptyPosition.second;

                        workspaceCells.get(targetPage).set(targetPos, overflowCell);

                        saveAppPositions();

                        if (workspacePager != null && workspacePager.getAdapter() != null) {
                            workspacePager.getAdapter().notifyItemChanged(targetPage);
                            workspacePager.setCurrentItem(targetPage, true);
                        }
                    } else if (workspaceCells.size() < 5) {
                        List<CellLayout.Cell> newPage = createEmptyPage();
                        newPage.set(0, overflowCell);
                        workspaceCells.add(newPage);

                        saveAppPositions();

                        initPageIndicator();
                        if (workspacePager != null && workspacePager.getAdapter() != null) {
                            int newPageIndex = workspaceCells.size() - 1;
                            workspacePager.getAdapter().notifyItemInserted(newPageIndex);

                            new Handler().postDelayed(() -> {
                                if (isAdded() && !isDetached()) {
                                    workspacePager.setCurrentItem(newPageIndex, true);
                                }
                            }, 100);
                        }
                    } else {
                        Toast.makeText(requireContext(), "已达到最大页面数量", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "处理溢出时出错", e);
                } finally {
                    isHandlingOverflow = false;
                }
            });

            return true;
        } catch (Exception e) {
            Log.e(TAG, "处理溢出单元格时出错", e);
            isHandlingOverflow = false;
            return false;
        }
    }

    private Pair<Integer, Integer> findEmptyPositionInExistingPages() {
        for (int pageIndex = 0; pageIndex < workspaceCells.size(); pageIndex++) {
            List<CellLayout.Cell> page = workspaceCells.get(pageIndex);
            for (int position = 0; position < page.size(); position++) {
                if (page.get(position) != null && "empty".equals(page.get(position).getTag())) {
                    return new Pair<>(pageIndex, position);
                }
            }
        }
        return null;
    }

    private Pair<Integer, Integer> findEmptyPositionInPages() {
        for (int pageIndex = 0; pageIndex < workspaceCells.size(); pageIndex++) {
            List<CellLayout.Cell> page = workspaceCells.get(pageIndex);
            for (int position = 0; position < page.size(); position++) {
                if (page.get(position).getTag().equals("empty")) {
                    return new Pair<>(pageIndex, position);
                }
            }
        }
        return null;
    }

    private void initPageIndicator() {
        if (pageIndicator == null || !isAdded()) {
            return;
        }

        pageIndicator.setupWithViewPager(workspacePager);
    }

    /**
     * 更新页面指示器状态
     * @param currentPage 当前页面位置
     */
    public void updatePageIndicator(int currentPage) {
        if (pageIndicator != null) {
            Log.d(TAG, "更新页面指示器位置: " + currentPage);
            pageIndicator.setCurrentPage(currentPage);
        }
    }

    private void setupAllWorkspacePages() {
        for (int i = 0; i < workspaceCells.size(); i++) {
            setupWorkspacePage(i);
        }
    }

    private void setupWorkspacePage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= workspaceCells.size()) {
            Log.e(TAG, "尝试设置无效页面: " + pageIndex);
            return;
        }

        try {
            View pageView = workspacePager.findViewWithTag("page_" + pageIndex);
            if (pageView != null) {
                CellLayout cellLayout = pageView.findViewById(R.id.workspace_grid);
                if (cellLayout != null) {
                    cellLayout.removeAllViews();

                    List<CellLayout.Cell> pageCells = workspaceCells.get(pageIndex);
                    for (int i = 0; i < pageCells.size(); i++) {
                        CellLayout.Cell cell = pageCells.get(i);
                        if (cell != null && cell.getContentView() != null) {
                            cellLayout.addCell(cell);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "设置工作区页面时出错: " + pageIndex, e);
        }
    }

    private List<List<CellLayout.Cell>> initializeWorkspaceApps() {
        Log.d(TAG, "初始化工作区应用");

        List<List<CellLayout.Cell>> result = new ArrayList<>();
        result.add(createEmptyPage());

        try {
            List<AppModel> allApps = getAllApps();
            Log.d(TAG, "找到 " + allApps.size() + " 个应用");

            allApps.sort((app1, app2) -> app1.getAppName().compareToIgnoreCase(app2.getAppName()));

            final int appsPerPage = COLUMNS * ROWS;

            int currentPage = 0;
            int currentPosition = 0;

            for (AppModel app : allApps) {
                View appView = createAppIconView(app);
                CellLayout.Cell cell = new CellLayout.Cell(app.getPackageName(), appView);

                appView.setVisibility(View.VISIBLE);

                List<CellLayout.Cell> page = result.get(currentPage);

                page.set(currentPosition, cell);

                currentPosition++;

                if (currentPosition >= appsPerPage) {
                    currentPosition = 0;
                    currentPage++;

                    if (currentPage >= result.size() && currentPage < 5) {
                        result.add(createEmptyPage());
                    } else if (currentPage >= 5) {
                        Log.w(TAG, "已达到最大页面数5，停止添加应用");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化应用时出错", e);
        }

        Log.d(TAG, "初始化完成，共 " + result.size() + " 页");
        return result;
    }

    private List<AppModel> getAllApps() {
        List<AppModel> apps = new ArrayList<>();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> availableActivities = packageManager.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo resolveInfo : availableActivities) {
            String packageName = resolveInfo.activityInfo.packageName;

            if (packageName.equals(requireContext().getPackageName())) {
                continue;
            }

            try {
                String appName = resolveInfo.loadLabel(packageManager).toString();
                Drawable appIcon = resolveInfo.loadIcon(packageManager);

                apps.add(new AppModel(appName, appIcon, packageName));
            } catch (Exception e) {
                Log.e(TAG, "加载应用信息失败: " + packageName, e);
            }
        }

        return apps;
    }

    private List<List<CellLayout.Cell>> loadAppPositions() {
        List<List<CellLayout.Cell>> result = new ArrayList<>();

        try {
            SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(APP_POSITIONS_KEY, "");

            if (!TextUtils.isEmpty(json)) {
                Gson gson = new Gson();
                Type type = new TypeToken<List<SerializableAppPosition>>(){}.getType();
                List<SerializableAppPosition> savedPositions = gson.fromJson(json, type);

                int maxPages = 5;
                int estimatedPages = Math.min(maxPages, Math.max(1, getMaxPageIndex(savedPositions) + 1));

                result = initializeEmptyWorkspace(estimatedPages);

                for (SerializableAppPosition position : savedPositions) {
                    if (position.getPageIndex() >= maxPages) {
                        continue;
                    }

                    while (result.size() <= position.getPageIndex() && result.size() < maxPages) {
                        result.add(createEmptyPage());
                    }

                    try {
                        String packageName = position.getPackageName();
                        ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);

                        View appView = createAppIconView(new AppModel(
                                appInfo.loadLabel(packageManager).toString(),
                                appInfo.loadIcon(packageManager),
                                packageName
                        ));

                        CellLayout.Cell cell = new CellLayout.Cell(packageName, appView);

                        if (position.getPageIndex() < result.size()) {
                            List<CellLayout.Cell> page = result.get(position.getPageIndex());
                            if (position.getPositionInPage() < page.size()) {
                                page.set(position.getPositionInPage(), cell);
                            }
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.w(TAG, "应用不存在: " + position.getPackageName());
                    } catch (Exception e) {
                        Log.e(TAG, "加载应用时出错: " + position.getPackageName(), e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "加载应用位置时出错", e);
        }

        if (result.isEmpty()) {
            result.add(createEmptyPage());
        }

        Log.d(TAG, "加载完成，页面数量: " + result.size());
        return result;
    }

    private List<List<CellLayout.Cell>> initializeEmptyWorkspace(int pageCount) {
        List<List<CellLayout.Cell>> pages = new ArrayList<>();

        for (int i = 0; i < pageCount; i++) {
            pages.add(createEmptyPage());
        }

        return pages;
    }

    private int getMaxPageIndex(List<SerializableAppPosition> positions) {
        int maxPageIndex = 0;
        for (SerializableAppPosition position : positions) {
            maxPageIndex = Math.max(maxPageIndex, position.getPageIndex());
        }
        return maxPageIndex;
    }

    private List<CellLayout.Cell> createEmptyPage() {
        List<CellLayout.Cell> page = new ArrayList<>();
        int appsPerPage = COLUMNS * ROWS;

        for (int i = 0; i < appsPerPage; i++) {
            View emptyView = new View(getContext());
            emptyView.setVisibility(View.INVISIBLE);
            CellLayout.Cell emptyCell = new CellLayout.Cell("empty", emptyView);
            page.add(emptyCell);
        }

        return page;
    }

    private void saveAppPositions() {
        try {
            if (!isAdded() || requireActivity() == null) {
                Log.e(TAG, "片段未附加到活动，无法保存应用位置");
                return;
            }

            List<SerializableAppPosition> appPositions = new ArrayList<>();

            for (int pageIndex = 0; pageIndex < workspaceCells.size(); pageIndex++) {
                List<CellLayout.Cell> pageCells = workspaceCells.get(pageIndex);
                for (int positionInPage = 0; positionInPage < pageCells.size(); positionInPage++) {
                    CellLayout.Cell cell = pageCells.get(positionInPage);
                    if (cell != null && !cell.getTag().equals("empty")) {
                        appPositions.add(new SerializableAppPosition(
                                cell.getTag(),
                                pageIndex,
                                positionInPage
                        ));
                    }
                }
            }

            SharedPreferences prefs = requireActivity().getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            Gson gson = new Gson();
            String json = gson.toJson(appPositions);

            prefs.edit().putString(APP_POSITIONS_KEY, json).apply();
            Log.d(TAG, "应用位置已保存，共 " + appPositions.size() + " 个应用");
        } catch (Exception e) {
            Log.e(TAG, "保存应用位置时出错", e);
        }
    }

    private View createAppIconView(AppModel app) {
        if (app == null) {
            Log.e(TAG, "应用模型为空，无法创建图标");
            return null;
        }

        // 使用布局填充器创建视图
        View appView = LayoutInflater.from(requireContext()).inflate(R.layout.item_app_icon, null, false);

        // 获取图标和标签视图
        ImageView iconView = appView.findViewById(R.id.app_icon_image);
        TextView labelView = appView.findViewById(R.id.app_icon_label);

        Log.d(TAG, "创建应用图标: " + app.getAppName() + ", 包名: " + app.getPackageName());

        // 设置图标大小
        ViewGroup.LayoutParams iconParams = iconView.getLayoutParams();
        iconParams.width = 120; // 恢复到原来的宽度
        iconParams.height = 120; // 恢复到原来的高度
        iconView.setLayoutParams(iconParams);

        // 设置图标
        IconUtils.setRoundedIcon(requireContext(), iconView, app.getAppIcon() != null ?
                app.getAppIcon() : getResources().getDrawable(android.R.drawable.sym_def_app_icon), 64f);

        // 显示文字标签
        labelView.setVisibility(View.VISIBLE);
        // 设置文本样式
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(20);
        labelView.setPadding(0,16,0,0);
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(2);
 
        // 设置应用名称
        labelView.setText(app.getAppName());

        // 设置点击监听器
        appView.setOnClickListener(v -> {
            try {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(app.getPackageName());
                if (launchIntent != null) {
                    startActivity(launchIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "启动应用失败: " + app.getPackageName(), e);
                Toast.makeText(requireContext(), "无法启动应用", Toast.LENGTH_SHORT).show();
            }
        });

        // 设置长按监听器以支持拖拽
        appView.setOnLongClickListener(v -> {
            // 空单元格不能拖拽
            if (app.getPackageName().equals("empty")) {
                return false;
            }

            // 执行触感反馈，模拟Launcher3的体验
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            }

            // 创建要传递的数据
            ClipData dragData = new ClipData(
                    app.getPackageName(),
                    new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN },
                    new ClipData.Item(app.getPackageName())
            );

            // 创建拖拽阴影
            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v) {
                @Override
                public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
                    super.onProvideShadowMetrics(outShadowSize, outShadowTouchPoint);
                    
                    // 调整触摸点在阴影中的位置，使其在图标中心
                    outShadowTouchPoint.set(outShadowSize.x / 2, outShadowSize.y / 3);
                }
            };

            // 开始拖拽操作
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(
                        dragData,
                        shadowBuilder,
                        new CellLayout.Cell(app.getPackageName(), v), // 传递Cell对象作为本地状态
                        View.DRAG_FLAG_OPAQUE
                );
            } else {
                v.startDrag(
                        dragData,
                        shadowBuilder,
                        new CellLayout.Cell(app.getPackageName(), v),
                        View.DRAG_FLAG_OPAQUE
                );
            }

            return true;
        });

        return appView;
    }

    @Override
    public void onResume() {
        super.onResume();

        // 在恢复时刷新当前页面，确保文本显示正确
        if (workspacePager != null && workspaceCells != null && !workspaceCells.isEmpty()) {
            int currentPage = workspacePager.getCurrentItem();
            if (currentPage >= 0 && currentPage < workspaceCells.size()) {
                setupWorkspacePage(currentPage);

                // 延迟进一步刷新，确保视图完全加载
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded() && !isDetached()) {
                        setupWorkspacePage(currentPage);
                        workspacePager.invalidate();
                    }
                }, 300);
            }
        }
    }

    /**
     * 处理单元格在不同页面间的交换
     */
    private void handleCellSwap(CellLayout.Cell draggingCell, int fromPage, int toPage, int targetColumn, int targetRow) {
        if (draggingCell == null) return;
        
        Log.d(TAG, "处理跨页面拖拽: 从页面 " + fromPage + " 到页面 " + toPage + 
                " 位置(" + targetColumn + "," + targetRow + ")");
        
        AppModel app = null;
        
        // 获取拖拽的应用信息
        String packageName = draggingCell.getTag();
        if (packageName == null || "empty".equals(packageName)) return;
        
        // 从当前页面中移除应用
        boolean needRemoveFromSource = false;
        if (fromPage >= 0 && fromPage < workspaceCells.size()) {
            List<CellLayout.Cell> sourcePage = workspaceCells.get(fromPage);
            for (int i = 0; i < sourcePage.size(); i++) {
                CellLayout.Cell cell = sourcePage.get(i);
                if (cell != null && packageName.equals(cell.getTag())) {
                    // 获取应用模型
                    View view = cell.getContentView();
                    if (view != null && view.getTag() instanceof AppModel) {
                        app = (AppModel) view.getTag();
                    }
                    // 将源位置替换为空白单元格
                    View emptyView = new View(getContext());
                    emptyView.setVisibility(View.INVISIBLE);
                    CellLayout.Cell emptyCell = new CellLayout.Cell("empty", emptyView);
                    sourcePage.set(i, emptyCell);
                    needRemoveFromSource = true;
                    break;
                }
            }
        }
        
        // 如果无法找到应用信息，尝试从packageName创建
        if (app == null) {
            try {
                // 从系统获取应用信息
                app = createAppFromPackage(packageName);
            } catch (Exception e) {
                Log.e(TAG, "无法创建应用: " + e.getMessage());
                return;
            }
        }
        
        if (app == null) {
            Log.e(TAG, "没有找到应用信息: " + packageName);
            return;
        }
        
        // 计算在页面中的确切位置
        int positionInPage = targetRow * COLUMNS + targetColumn;
        
        // 确保目标页面存在
        int currentPageCount = workspaceCells.size();
        int targetPageIndex = toPage;
        if (toPage < 0) {
            // 使用当前选中的页面
            targetPageIndex = workspacePager.getCurrentItem();
        }
        
        // 确保页面索引有效
        if (targetPageIndex >= currentPageCount) {
            // 添加新页面直到目标页面存在
            while (workspaceCells.size() <= targetPageIndex) {
                addNewPage();
            }
        }
        
        // 获取目标页面
        List<CellLayout.Cell> targetPage = workspaceCells.get(targetPageIndex);
        
        // 确保位置索引有效
        if (positionInPage >= 0 && positionInPage < targetPage.size()) {
            // 创建应用图标视图
            View appView = createAppIconView(app);
            if (appView == null) return;
            
            // 创建单元格并替换目标位置
            CellLayout.Cell appCell = new CellLayout.Cell(packageName, appView);
            CellLayout.Cell originalCell = targetPage.get(positionInPage);
            
            // 如果目标不是空白单元格，需要处理位置交换
            if (originalCell != null && !"empty".equals(originalCell.getTag())) {
                // 寻找空白单元格位置
                int emptyIndex = findEmptyCell(targetPage);
                if (emptyIndex >= 0) {
                    // 将原单元格移至空白位置
                    targetPage.set(emptyIndex, originalCell);
                } else {
                    // 无空白单元格，添加到下一页
                    handleCellOverflow(originalCell, targetPageIndex);
                }
            }
            
            // 设置新单元格到目标位置
            targetPage.set(positionInPage, appCell);
            
            // 刷新视图
            setupWorkspacePage(targetPageIndex);
            if (needRemoveFromSource && fromPage >= 0 && fromPage != targetPageIndex) {
                setupWorkspacePage(fromPage);
            }
            
            // 保存更新后的位置
            saveAppPositions();
        }
    }

    /**
     * 从包名创建应用模型
     */
    private AppModel createAppFromPackage(String packageName) {
        if (packageManager == null) {
            packageManager = getContext().getPackageManager();
        }
        
        try {
            // 获取应用信息
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            String appName = packageManager.getApplicationLabel(appInfo).toString();
            Drawable appIcon = packageManager.getApplicationIcon(appInfo);
            
            return new AppModel(appName, appIcon, packageName);
        } catch (Exception e) {
            Log.e(TAG, "无法获取应用信息: " + e.getMessage());
            return null;
        }
    }

    /**
     * 查找页面中的空白单元格
     */
    private int findEmptyCell(List<CellLayout.Cell> page) {
        if (page == null) return -1;
        
        for (int i = 0; i < page.size(); i++) {
            CellLayout.Cell cell = page.get(i);
            if (cell != null && "empty".equals(cell.getTag())) {
                return i;
            }
        }
        
        return -1;
    }
}