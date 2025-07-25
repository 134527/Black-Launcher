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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.Configuration;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.msk.blacklauncher.R;
import com.msk.blacklauncher.utils.IconUtils;
import com.msk.blacklauncher.adapters.WorkspaceAdapter;
import com.msk.blacklauncher.model.AppModel;
import com.msk.blacklauncher.view.CellLayout;


import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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

    private static final int VIBRATION_DRAG_START = 50; // 拖拽开始的振动时长(ms)
    private static final int VIBRATION_DRAG_END = 35; // 拖拽结束的振动时长(ms)
    private static final int VIBRATION_DRAG_MOVE = 15; // 拖拽移动的振动时长(ms)
    
    private Vibrator vibrator;

    // 添加UI状态变化监听常量
    private static final int SYSTEM_UI_IMMERSIVE_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            
    private static final long HIDE_UI_DELAY_MS = 100; // 延迟隐藏UI的时间（毫秒）
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideSystemUIRunnable = this::hideSystemUI;

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

    private boolean isNavigationBarVisible = false;
    private View decorView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");

        requireActivity().registerReceiver(appChangeReceiver, filter);

        // 获取系统振动服务
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        
        // 获取DecorView用于监控导航栏状态
        decorView = requireActivity().getWindow().getDecorView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (appChangeReceiver != null) {
            requireActivity().unregisterReceiver(appChangeReceiver);
        }
        
        // 确保重置所有拖拽状态
        CellLayout.setDraggingState(false);
    }

    // 修改BroadcastReceiver以确保立即刷新
    private BroadcastReceiver appChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            Uri data = intent.getData();
            if (data == null) return;

            String packageName = data.getSchemeSpecificPart();
            Log.d(TAG, "检测到应用变化: " + action + ", 包名: " + packageName);

            if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
                    Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                    if (packageManager.getLaunchIntentForPackage(packageName) != null) {
                        Drawable icon = appInfo.loadIcon(packageManager);
                        String label = appInfo.loadLabel(packageManager).toString();
                        AppModel newApp = new AppModel(label, icon, packageName);
                        
                        // 在UI线程上添加应用
                        new Handler(Looper.getMainLooper()).post(() -> {
                            addAppToWorkspace(newApp);
                            // 立即保存应用位置
                            saveAppPositions();
                            // 强制更新当前页面
                            refreshCurrentPage();
                        });
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "找不到包: " + packageName, e);
                }
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                // 在UI线程上移除应用
                new Handler(Looper.getMainLooper()).post(() -> {
                    removeAppFromWorkspace(packageName);
                    // 立即保存应用位置
                    saveAppPositions();
                    // 强制更新当前页面
                    refreshCurrentPage();
                });
            }
        }
    };

    /**
     * 刷新当前显示的页面
     */
    private void refreshCurrentPage() {
        if (workspacePager != null && workspaceCells != null && !workspaceCells.isEmpty()) {
            int currentPage = workspacePager.getCurrentItem();
            if (currentPage >= 0 && currentPage < workspaceCells.size()) {
                Log.d(TAG, "刷新当前页面: " + currentPage);
                setupWorkspacePage(currentPage);
                
                // 触发ViewPager2适配器更新
                if (workspacePager.getAdapter() != null) {
                    workspacePager.getAdapter().notifyItemChanged(currentPage);
                }
            }
        }
    }

    /**
     * 保存应用位置的优化版本
     */
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

            // 使用commit()而非apply()确保立即写入
            prefs.edit().putString(APP_POSITIONS_KEY, json).commit();
            Log.d(TAG, "应用位置已保存，共 " + appPositions.size() + " 个应用");
        } catch (Exception e) {
            Log.e(TAG, "保存应用位置时出错", e);
        }
    }

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
        
        // 设置手势监听，处理滑动返回
        setupWorkspaceGestures(view);

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
                
                // 检查是否需要删除空白页面
                if (fromPage >= 0 && fromPage < workspaceCells.size() && 
                    fromPage != toPage && isPageEmpty(fromPage)) {
                    // 如果源页面为空且不是第一页，删除源页面
                    if (fromPage > 0) {
                        removePage(fromPage);
                    }
                }
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
            
            @Override
            public int getPageCount() {
                return workspaceCells != null ? workspaceCells.size() : 0;
            }
            
            @Override
            public int createNewPage() {
                // 创建一个新的空白页面
                List<CellLayout.Cell> newPage = createEmptyPage();
                
                // 添加到页面列表
                workspaceCells.add(newPage);
                
                // 更新页面指示器
                initPageIndicator();
                
                // 通知适配器更新
                if (workspacePager.getAdapter() != null) {
                    workspacePager.getAdapter().notifyItemInserted(workspaceCells.size() - 1);
                    
                    // 添加额外的通知，确保适配器完全刷新
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isAdded() && !isDetached() && workspacePager.getAdapter() != null) {
                            // 通知数据集更改确保ViewPager2正确更新
                            notifyPageAdapterChanged();
                        }
                    }, 50);
                }
                
                // 返回新页面的索引
                return workspaceCells.size() - 1;
            }
            
            @Override
            public boolean canCreateNewPage() {
                // 检查是否已达到最大页面数
                return workspaceCells != null && workspaceCells.size() < 5; // 限制最多5个页面
            }

            @Override
            public int requestNewPageAndMoveItem(CellLayout.Cell cellToMove, int fromPageIndex) {
                if (workspaceCells == null || cellToMove == null) {
                    return -1;
                }
                
                try {
                    // 确保未超过最大页面数限制
                    if (workspaceCells.size() >= 5) {
                        return -1;
                    }
                    
                    // 创建新页面
                    List<CellLayout.Cell> newPage = createEmptyPage();
                    
                    // 将项目放在新页面的第一个位置
                    newPage.set(0, cellToMove);
                    
                    // 确定插入位置（通常是末尾）
                    int newPageIndex = workspaceCells.size();
                    
                    // 添加新页面
                    workspaceCells.add(newPage);
                    
                    // 更新页面指示器
                    initPageIndicator();
                    
                    // 通知适配器更新
                    if (workspacePager.getAdapter() != null) {
                        workspacePager.getAdapter().notifyItemInserted(newPageIndex);
                    }
                    
                    // 保存应用位置
                    saveAppPositions();
                    
                    // 返回新页面的索引
                    return newPageIndex;
                } catch (Exception e) {
                    Log.e(TAG, "创建新页面失败", e);
                    return -1;
                }
            }

            @Override
            public boolean isPageEmpty(int pageIndex) {
                // 调用Fragment的isPageEmpty方法
                return WorkspaceFragment.this.isPageEmpty(pageIndex);
            }
            
            @Override
            public void removePage(int pageIndex) {
                // 调用Fragment的removePage方法
                WorkspaceFragment.this.removePage(pageIndex);
            }

            @Override
            public List<CellLayout.Cell> getPageCells(int pageIndex) {
                // 获取指定页面的单元格列表
                if (pageIndex >= 0 && pageIndex < workspaceCells.size()) {
                    return workspaceCells.get(pageIndex);
                }
                return null;
            }

            public void notifyPageAdapterChanged() {
                // 通知ViewPager2数据已更新
                if (workspacePager != null && workspacePager.getAdapter() != null) {
                    workspacePager.getAdapter().notifyDataSetChanged();
                    Log.d(TAG, "已通知ViewPager2适配器数据已更改");
                }
            }

            @Override
            public boolean requestReturnToPage(CellLayout.Cell cellToMove, int targetPageIndex) {
return true;
            }

            @Override
            public void notifyPageDataChanged(int pageIndex) {

            }


            public void notifyIconTemporarilyRemovedFromPage(String packageName, int pageIndex) {

            }

           
            public void removeAppFromPage(String packageName, int pageIndex) {

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

        // 监控UI可见性变化
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            // 检查导航栏和状态栏是否隐藏
            boolean isImmersiveModeEnabled = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
            isNavigationBarVisible = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
            Log.d(TAG, "系统UI可见性变化: " + visibility + ", 导航栏可见: " + isNavigationBarVisible + 
                      ", 全屏模式: " + isImmersiveModeEnabled);
            
            // 如果导航栏显示，尝试恢复全屏状态
            if (!isImmersiveModeEnabled || isNavigationBarVisible) {
                // 移除之前的回调，避免重复
                uiHandler.removeCallbacks(hideSystemUIRunnable);
                // 延迟执行，避免与系统冲突
                uiHandler.postDelayed(hideSystemUIRunnable, HIDE_UI_DELAY_MS);
            }
        });
        
        // 设置触摸监听器，确保任何触摸操作都保持全屏状态
        setupTouchListener(view);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 确保视图创建后进入全屏模式
        hideSystemUI();
        
        // 添加ViewPager2页面滑动监听器，确保页面正确显示
        workspacePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                // 当页面选中时，确保页面内容已正确设置
                if (position >= 0 && position < workspaceCells.size()) {
                    // 延迟设置页面，确保页面已完全加载
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isAdded() && !isDetached()) {
                            setupWorkspacePage(position);
                            Log.d(TAG, "选中页面 " + position + " 并设置内容");
                        }
                    }, 100);
                }
                
                // 更新页面指示器
                updatePageIndicator(position);
            }
        });
        
        // 增加定期保存应用位置的机制
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded() && !isDetached()) {
                    saveAppPositions();
                    // 每30秒运行一次
                    new Handler(Looper.getMainLooper()).postDelayed(this, 30000);
                }
            }
        }, 30000); // 30秒后开始第一次保存
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
        
        // 确保UI处于全屏状态
        hideSystemUI();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // 移除待处理的隐藏UI回调
        uiHandler.removeCallbacks(hideSystemUIRunnable);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        // 在Fragment可见时确保全屏
        hideSystemUI();
    }
    
    @Override
    public void onStop() {
        super.onStop();
        // 移除待处理的隐藏UI回调
        uiHandler.removeCallbacks(hideSystemUIRunnable);
        // 在Fragment不可见时保存应用位置
        saveAppPositions();
    }

    /**
     * 通知页面适配器数据已更改
     * 用于外部组件调用，以刷新ViewPager2
     */
    public void notifyPageAdapterChanged() {
        if (workspacePager != null && workspacePager.getAdapter() != null) {
            // 通知适配器数据已更改
            workspacePager.getAdapter().notifyDataSetChanged();
            Log.d(TAG, "已通知ViewPager2适配器数据已更改(外部调用)");
            
            // 确保页面指示器也更新
            initPageIndicator();
        }
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

                        Log.d(TAG, "找到空位置：页面 " + targetPage + ", 位置 " + targetPos);
                        
                        // 保存原来的视图状态
                        View contentView = overflowCell.getContentView();
                        String packageName = overflowCell.getTag();
                        
                        // 创建单元格
                        CellLayout.Cell crossPageCell = new CellLayout.Cell(packageName, contentView);
                        
                        // 设置为跨页拖拽，添加时间戳避免解析错误
                        crossPageCell.setTag(packageName + ":cross_page:" + pageIndex + ":" + System.currentTimeMillis());
                        
                        // 放置到目标页面
                        workspaceCells.get(targetPage).set(targetPos, crossPageCell);

                        saveAppPositions();

                        if (workspacePager != null && workspacePager.getAdapter() != null) {
                            workspacePager.getAdapter().notifyItemChanged(targetPage);
                            
                            // 平滑滚动到目标页面
                            workspacePager.setCurrentItem(targetPage, true);
                            
                            // 添加页面过渡动画
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (isAdded() && !isDetached()) {
                                    setupWorkspacePage(targetPage);
                                    
                                    // 获取视图并添加动画效果
                                    View targetView = getViewForPage(targetPage);
                                    if (targetView != null) {
                                        // 应用进入动画
                                        targetView.setAlpha(0.9f);
                                        targetView.animate()
                                            .alpha(1.0f)
                                            .setDuration(200)
                                            .start();
                                    }
                                }
                            }, 100);
                        }
                    } else if (workspaceCells.size() < 5) {
                        // 添加新页面
                        Log.d(TAG, "创建新页面");
                        List<CellLayout.Cell> newPage = createEmptyPage();
                        
                        // 将溢出单元格放在新页面的第一个位置
                        newPage.set(0, overflowCell);
                        
                        // 添加新页面
                        workspaceCells.add(newPage);

                        saveAppPositions();

                        initPageIndicator();
                        if (workspacePager != null && workspacePager.getAdapter() != null) {
                            int newPageIndex = workspaceCells.size() - 1;
                            workspacePager.getAdapter().notifyItemInserted(newPageIndex);

                            // 这里添加延迟是为了确保页面完全创建后再切换
                            new Handler().postDelayed(() -> {
                                if (isAdded() && !isDetached()) {
                                    // 平滑滚动到新页面
                                    workspacePager.setCurrentItem(newPageIndex, true);
                                    
                                    // 添加页面创建和进入动画
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        if (isAdded() && !isDetached()) {
                                            setupWorkspacePage(newPageIndex);
                                            
                                            // 获取视图并添加动画效果
                                            View newPageView = getViewForPage(newPageIndex);
                                            if (newPageView != null) {
                                                // 应用进入动画
                                                newPageView.setScaleX(0.9f);
                                                newPageView.setScaleY(0.9f);
                                                newPageView.setAlpha(0.8f);
                                                
                                                newPageView.animate()
                                                    .scaleX(1.0f)
                                                    .scaleY(1.0f)
                                                    .alpha(1.0f)
                                                    .setDuration(300)
                                                    .setInterpolator(new OvershootInterpolator(0.8f))
                                                    .start();
                                                    
                                                // 额外的提示动画
                                                Toast.makeText(requireContext(), "已创建新页面", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    }, 200);
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

        // 使用ViewPager2关联页面指示器，自动处理页数变化
        pageIndicator.setupWithViewPager(workspacePager);
        Log.d(TAG, "PageIndicator已关联到WorkspacePager");
    }

    /**
     * 更新页面指示器状态
     * 注意：当使用setupWithViewPager时，此方法不再需要调用，因为PageIndicator会自动更新
     * 但为了兼容性保留此方法
     * @param currentPage 当前页面位置
     */
    public void updatePageIndicator(int currentPage) {
        if (pageIndicator != null) {
            // 由于已使用setupWithViewPager，此方法通常不需要手动调用
            pageIndicator.setCurrentPage(currentPage);
            Log.d(TAG, "手动更新页面指示器位置: " + currentPage);
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

    /**
     * 优化应用位置加载逻辑
     */
    private List<List<CellLayout.Cell>> loadAppPositions() {
        List<List<CellLayout.Cell>> result = new ArrayList<>();

        try {
            SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(APP_POSITIONS_KEY, "");

            if (!TextUtils.isEmpty(json)) {
                Gson gson = new Gson();
                Type type = new TypeToken<List<SerializableAppPosition>>(){}.getType();
                List<SerializableAppPosition> savedPositions = gson.fromJson(json, type);

                // 检查已保存位置的有效性
                if (savedPositions != null && !savedPositions.isEmpty()) {
                    int maxPages = 5;
                    int estimatedPages = Math.min(maxPages, Math.max(1, getMaxPageIndex(savedPositions) + 1));
                    
                    // 初始化空页面
                    result = initializeEmptyWorkspace(estimatedPages);
                    
                    // 创建应用信息缓存以提高性能
                    Map<String, AppModel> appCache = new HashMap<>();
                    
                    // 应用放置计数器
                    int appsPlaced = 0;
                    int appsSkipped = 0;

                    // 按页面和位置顺序处理保存的位置
                    for (SerializableAppPosition position : savedPositions) {
                        if (position.getPageIndex() >= maxPages) {
                            continue;
                        }

                        try {
                            String packageName = position.getPackageName();
                            
                            // 检查缓存中是否已有此应用
                            AppModel appModel = appCache.get(packageName);
                            
                            // 如果缓存中没有，尝试从系统获取
                            if (appModel == null) {
                                try {
                                    ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                                    appModel = new AppModel(
                                            appInfo.loadLabel(packageManager).toString(),
                                            appInfo.loadIcon(packageManager),
                                            packageName
                                    );
                                    
                                    // 添加到缓存
                                    appCache.put(packageName, appModel);
                                } catch (PackageManager.NameNotFoundException e) {
                                    Log.w(TAG, "应用不存在: " + packageName);
                                    appsSkipped++;
                                    continue;
                                }
                            }
                            
                            // 创建应用视图
                            View appView = createAppIconView(appModel);
                            CellLayout.Cell cell = new CellLayout.Cell(packageName, appView);

                            // 确保有足够的页面
                            while (result.size() <= position.getPageIndex()) {
                                result.add(createEmptyPage());
                            }
                            
                            // 放置应用到指定位置
                            List<CellLayout.Cell> page = result.get(position.getPageIndex());
                            if (position.getPositionInPage() < page.size()) {
                                page.set(position.getPositionInPage(), cell);
                                appsPlaced++;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "加载应用时出错: " + position.getPackageName(), e);
                            appsSkipped++;
                        }
                    }
                    
                    Log.d(TAG, "应用加载完成: 成功=" + appsPlaced + ", 跳过=" + appsSkipped);
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

    // 3. 优化长按触发拖拽的逻辑
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
        
        // 检查屏幕方向
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // 竖屏模式 - 使用单行和省略号
            labelView.setMaxLines(1);
            labelView.setEllipsize(TextUtils.TruncateAt.END);
            labelView.setSingleLine(true);
            Log.d(TAG, "竖屏模式: 应用单行文本显示和省略号");
        } else {
            // 横屏模式 - 可以显示多行
            labelView.setMaxLines(2);
            labelView.setSingleLine(false);
            labelView.setEllipsize(null);
            Log.d(TAG, "横屏模式: 应用多行文本显示");
        }
 
        // 设置应用名称
        labelView.setText(app.getAppName());
        
        // 为视图设置标签数据，方便后续识别
        appView.setTag(app);

        // 设置长按监听器以支持拖拽
        appView.setOnLongClickListener(null); // 移除原有长按监听器

        // 设置极短的拖拽触发时间 - 从180ms减少到80ms
        final int ULTRA_SHORT_TOUCH_TIMEOUT = 80;
        
        // 使用自定义触摸监听代替长按监听
        appView.setOnTouchListener(new View.OnTouchListener() {
            private long pressStartTime;
            private float startX, startY;
            private boolean longPressTriggered = false;
            private boolean isDraggingStarted = false;
            private final Handler handler = new Handler(Looper.getMainLooper());
            
            private final Runnable dragStartRunnable = new Runnable() {
                @Override
                public void run() {
                    if (longPressTriggered || isDraggingStarted) return;
                    
                    // 检查是否为空单元格或已在拖拽中
                    if (app.getPackageName().equals("empty") || CellLayout.isInDragging()) {
                        return;
                    }
                    
                    longPressTriggered = true;
                    isDraggingStarted = true;
                    
                    // 触发振动反馈
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, VIBRATION_DRAG_START);
                    
                    // 开始拖拽操作
                    startDragOperation(appView, app.getPackageName());
                }
            };
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 记录按下的位置和时间
                        startX = event.getX();
                        startY = event.getY();
                        pressStartTime = System.currentTimeMillis();
                        longPressTriggered = false;
                        isDraggingStarted = false;
                        
                        // 极短延迟后检测是否应该拖拽 - 几乎立即触发
                        handler.postDelayed(dragStartRunnable, ULTRA_SHORT_TOUCH_TIMEOUT);
                        return true; // 消费事件，阻止点击事件传递
                        
                    case MotionEvent.ACTION_MOVE:
                        // 如果移动距离超过阈值，可以立即启动拖拽
                        float moveX = Math.abs(event.getX() - startX);
                        float moveY = Math.abs(event.getY() - startY);
                        
                        // 如果已经开始拖动超过一定距离，立即开始拖拽而不等待
                        if (!isDraggingStarted && (moveX > 10 || moveY > 10) && 
                            System.currentTimeMillis() - pressStartTime > 30) {
                            handler.removeCallbacks(dragStartRunnable);
                            dragStartRunnable.run(); // 立即启动拖拽
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 取消拖拽开始计时
                        handler.removeCallbacks(dragStartRunnable);
                        
                        // 如果未触发拖拽，且点击时间短，则视为点击
                        if (!longPressTriggered && 
                            System.currentTimeMillis() - pressStartTime < ULTRA_SHORT_TOUCH_TIMEOUT) {
                            // 启动应用
                            v.performClick();
                        }
                        return true;
                }
                return true; // 始终消费触摸事件以确保控制完全在我们手中
            }
        });

        // 设置点击监听器以打开应用
        appView.setOnClickListener(v -> {
            try {
                // 拖拽操作期间不触发点击
                if (CellLayout.isInDragging()) return;
                
                Intent launchIntent = packageManager.getLaunchIntentForPackage(app.getPackageName());
                if (launchIntent != null) {
                    startActivity(launchIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "启动应用失败: " + app.getPackageName(), e);
                Toast.makeText(requireContext(), "无法启动应用", Toast.LENGTH_SHORT).show();
            }
        });

        return appView;
    }

    // 抽取拖拽操作逻辑为单独的方法，减少代码重复
    private void startDragOperation(View view, String packageName) {
        try {
            // 创建要传递的数据
            ClipData dragData = new ClipData(
                    packageName,
                    new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN },
                    new ClipData.Item(packageName)
            );

            // 创建拖拽阴影
            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view) {
                @Override
                public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
                    super.onProvideShadowMetrics(outShadowSize, outShadowTouchPoint);
                    outShadowTouchPoint.set(outShadowSize.x / 2, outShadowSize.y / 3);
                }
                
                @Override
                public void onDrawShadow(Canvas canvas) {
                    canvas.save();
                    canvas.translate(0, -12);
                    super.onDrawShadow(canvas);
                    canvas.restore();
                }
            };
            
            // 设置静态标志，标记已开始拖拽
            CellLayout.setDraggingState(true);
            
            // 避免动画造成的卡顿，直接开始拖拽
            view.setVisibility(View.INVISIBLE);
            
            // 开始拖拽操作
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                view.startDragAndDrop(
                        dragData,
                        shadowBuilder,
                        new CellLayout.Cell(packageName, view),
                        View.DRAG_FLAG_OPAQUE
                );
            } else {
                view.startDrag(
                        dragData,
                        shadowBuilder,
                        new CellLayout.Cell(packageName, view),
                        View.DRAG_FLAG_OPAQUE
                );
            }
            
            // 添加延迟重置拖拽状态的机制，防止卡死
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // 如果拖拽超时，强制重置状态
                if (CellLayout.isInDragging()) {
                    Log.w(TAG, "检测到拖拽状态超时，强制重置");
                    CellLayout.setDraggingState(false);
                    
                    // 确保视图可见
                    if (view.getVisibility() != View.VISIBLE) {
                        view.setVisibility(View.VISIBLE);
                    }
                }
            }, 8000); // 8秒超时保护
        } catch (Exception e) {
            Log.e(TAG, "启动拖拽操作失败: " + e.getMessage());
            // 重置拖拽状态
            CellLayout.setDraggingState(false);
            // 确保视图可见
            view.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 提供统一的触感反馈方法，根据不同设备特性提供最佳体验
     */
    private void performHapticFeedback(int feedbackConstant, int vibrationDuration) {
        try {
            // 尝试使用系统内置的触感反馈
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View view = getView();
                if (view != null) {
                    view.performHapticFeedback(feedbackConstant, 
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING | 
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                }
            }
            
            // 如果是Android O及以上版本，使用更精细的振动效果
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null && vibrator.hasVibrator()) {
                int amplitude = 
                    vibrationDuration == VIBRATION_DRAG_START ? 255 :
                    vibrationDuration == VIBRATION_DRAG_END ? 175 : 80;

                
                VibrationEffect effect = VibrationEffect.createOneShot(vibrationDuration, amplitude);
                vibrator.vibrate(effect);
            } 
            // 兼容旧版本
            else if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(vibrationDuration);
            }
        } catch (Exception e) {
            Log.e(TAG, "执行触感反馈失败", e);
        }
    }

    /**
     * 处理单元格在不同页面间的交换的优化版本
     */
    private void handleCellSwap(CellLayout.Cell draggingCell, int fromPage, int toPage, int targetColumn, int targetRow) {
        if (draggingCell == null) return;
        
        try {
            Log.d(TAG, "处理跨页面拖拽: 从页面 " + fromPage + " 到页面 " + toPage + 
                    " 位置(" + targetColumn + "," + targetRow + ")");
            
            AppModel app = null;
            
            // 获取拖拽的应用信息
            String packageName = draggingCell.getTag();
            
            // 检查是否是跨页面拖拽
            boolean isCrossPageDrag = packageName != null && packageName.contains(":cross_page");
            int originalFromPage = fromPage;
            
            // 如果是跨页拖拽，提取原始包名和真实源页面
            if (isCrossPageDrag) {
                String[] parts = packageName.split(":");
                if (parts.length >= 3) {
                    try {
                        packageName = parts[0]; // 恢复原始包名
                        draggingCell.setTag(packageName); // 更新单元格标签
                        
                        // 更新源页面索引 - 修复格式为 packageName:cross_page:pageIndex:timestamp
                        if (parts.length >= 4 && "cross_page".equals(parts[1])) {
                            originalFromPage = Integer.parseInt(parts[2]); // 获取页面索引部分
                        } else {
                            // 兼容旧格式
                            originalFromPage = Integer.parseInt(parts[parts.length - 1]);
                        }
                        
                        Log.d(TAG, "跨页拖拽: 原始来源页面=" + originalFromPage + ", 当前来源页面=" + fromPage);
                    } catch (Exception e) {
                        Log.e(TAG, "解析跨页拖拽信息失败", e);
                    }
                }
            }
            
            // 如果包名为空或为"empty"，返回
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
                        
                        // 添加移除时的触感反馈
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, VIBRATION_DRAG_MOVE);
                        
                        // 添加动画效果
                        if (isAdded() && !isDetached()) {
                            View oldCellView = getViewForPage(fromPage);
                            if (oldCellView != null) {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    setupWorkspacePage(fromPage);
                                    oldCellView.animate()
                                        .alpha(0.7f)
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .setInterpolator(new AccelerateDecelerateInterpolator())
                                        .setDuration(150)
                                        .withEndAction(() -> {
                                            oldCellView.animate()
                                                .alpha(1.0f)
                                                .scaleX(1.0f)
                                                .scaleY(1.0f)
                                                .setDuration(200)
                                                .start();
                                        })
                                        .start();
                                });
                            }
                        }
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
                
                // 设置初始动画属性
                appView.setScaleX(1.0f);
                appView.setScaleY(1.0f);
                appView.setAlpha(0.7f);
                
                // 创建单元格并替换目标位置
                CellLayout.Cell appCell = new CellLayout.Cell(packageName, appView);
                CellLayout.Cell originalCell = targetPage.get(positionInPage);
                
                // 如果目标不是空白单元格，需要处理位置交换
                if (originalCell != null && !"empty".equals(originalCell.getTag())) {
                    // 判断是否需要处理跨页面挤出效果
                    boolean needCrossPageEffect = targetPageIndex == currentPageCount - 1 && 
                            positionInPage == targetPage.size() - 1;
                    
                    if (needCrossPageEffect && workspaceCells.size() < 5) {
                        // 需要创建新页面并显示挤出动画
                        List<CellLayout.Cell> newPage = createEmptyPage();
                        // 挤出的单元格移到新页面第一个位置
                        newPage.set(0, originalCell);
                        // 添加新页面
                        workspaceCells.add(newPage);
                        
                        // 创建挤出动画
                        if (isAdded() && !isDetached()) {
                            // 保存目标单元格的视图状态
                            View targetCellView = originalCell.getContentView();
                            
                            // 创建挤出动画视图（临时添加到当前页面）
                            if (targetCellView != null) {
                                // 复制视图位置和属性
                                int[] location = new int[2];
                                targetCellView.getLocationOnScreen(location);
                                
                                // 添加临时视图
                                View tempView = new View(requireContext());
                                tempView.setBackground(targetCellView.getBackground());
                                tempView.setLayoutParams(targetCellView.getLayoutParams());
                                
                                // 将临时视图添加到当前页面
                                ViewGroup currentPageView = (ViewGroup) getViewForPage(targetPageIndex);
                                if (currentPageView != null) {
                                    currentPageView.addView(tempView);
                                    
                                    // 设置初始位置
                                    tempView.setX(targetCellView.getX());
                                    tempView.setY(targetCellView.getY());
                                    
                                    // 启动挤出动画
                                    tempView.animate()
                                        .translationXBy(currentPageView.getWidth())
                                        .alpha(0f)
                                        .setDuration(300)
                                        .setInterpolator(new AccelerateDecelerateInterpolator())
                                        .withEndAction(() -> {
                                            currentPageView.removeView(tempView);
                                            
                                            // 更新页面指示器
                                            initPageIndicator();
                                            
                                            // 通知适配器更新
                                            if (workspacePager.getAdapter() != null) {
                                                workspacePager.getAdapter().notifyItemInserted(workspaceCells.size() - 1);
                                            }
                                        })
                                        .start();
                                }
                            }
                        }
                    } else {
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
                }
                
                // 设置新单元格到目标位置
                targetPage.set(positionInPage, appCell);
                
                // 刷新目标页面视图
                View targetPageView = getViewForPage(targetPageIndex);
                
                // 播放放置动画
                if (targetPageView != null) {
                    int finalTargetPageIndex = targetPageIndex;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        setupWorkspacePage(finalTargetPageIndex);
                        
                        // 提供放置时的触感反馈
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, VIBRATION_DRAG_END);
                        
                        // 添加图标放置动画效果 - 使用OvershootInterpolator实现更生动的放置效果
                        appView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .alpha(1.0f)
                            .setInterpolator(new OvershootInterpolator(0.8f))
                            .setDuration(300)
                            .start();
                        
                        // 页面微震动动画
                        targetPageView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(120)
                            .withEndAction(() -> {
                                targetPageView.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setInterpolator(new OvershootInterpolator(0.3f))
                                    .setDuration(180)
                                    .start();
                            }).start();
                    });
                }
                
                // 刷新视图
                setupWorkspacePage(targetPageIndex);
                if (needRemoveFromSource && fromPage >= 0 && fromPage != targetPageIndex) {
                    setupWorkspacePage(fromPage);
                }
                
                // 保存更新后的位置 (确保立即保存)
                saveAppPositions();
                
                // 强制更新所有受影响的页面
                setupWorkspacePage(targetPageIndex);
                if (needRemoveFromSource && fromPage >= 0 && fromPage != targetPageIndex) {
                    setupWorkspacePage(fromPage);
                }
                
                // 确保重置拖拽状态
                CellLayout.setDraggingState(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "处理单元格交换失败: " + e.getMessage());
            // 确保重置拖拽状态
            CellLayout.setDraggingState(false);
        }
    }

    /**
     * 获取指定页面的视图
     */
    private View getViewForPage(int pageIndex) {
        if (workspacePager == null) return null;
        
        // 尝试通过tag查找页面视图
        View pageView = workspacePager.findViewWithTag("page_" + pageIndex);
        
        // 如果找不到，则通过RecyclerView查找
        if (pageView == null && workspacePager.getChildAt(0) instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) workspacePager.getChildAt(0);
            
            // 遍历可见的子视图
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View child = recyclerView.getChildAt(i);
                if (child.getTag() != null && child.getTag().equals("page_" + pageIndex)) {
                    pageView = child;
                    break;
                }
            }
        }
        
        return pageView;
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
     * 查找页面中的空白单元格位置
     * @param page 目标页面
     * @return 找到的空白单元格索引，如果没有找到返回-1
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

    /**
     * 隐藏系统UI元素，包括状态栏和导航栏
     */
    private void hideSystemUI() {
        if (isAdded() && !isDetached() && getActivity() != null) {
            View decorView = getActivity().getWindow().getDecorView();
            
            Log.d(TAG, "强制进入全屏模式");
            
            // 添加窗口标志
            getActivity().getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
            
            // 设置系统UI可见性标志
            decorView.setSystemUiVisibility(SYSTEM_UI_IMMERSIVE_FLAGS);
        }
    }
    
    /**
     * 一旦触摸屏幕任何位置，就确保全屏模式
     * 在FragmentWorkspace的布局根元素添加此监听
     */
    private void setupTouchListener(View rootView) {
        rootView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // 在触摸开始时确保全屏
                hideSystemUI();
            }
            return false; // 不消费事件，让其继续传递
        });
    }

    /**
     * 检查指定页面是否为空（只包含空白单元格）
     * @param pageIndex 页面索引
     * @return 如果页面为空返回true
     */
    private boolean isPageEmpty(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= workspaceCells.size()) {
            return false;
        }
        
        List<CellLayout.Cell> page = workspaceCells.get(pageIndex);
        for (CellLayout.Cell cell : page) {
            if (cell != null && !cell.getTag().equals("empty")) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 移除指定的页面
     * @param pageIndex 要移除的页面索引
     */
    private void removePage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= workspaceCells.size()) {
            return;
        }
        
        // 记录当前页面
        int currentPage = workspacePager.getCurrentItem();
        
        // 移除页面
        workspaceCells.remove(pageIndex);
        
        // 更新页面指示器
        initPageIndicator();
        
        // 通知适配器更新，使用正确的更新方法
        if (workspacePager.getAdapter() != null) {
            workspacePager.getAdapter().notifyItemRemoved(pageIndex);
            
            // 通知范围变化，确保整个ViewPager2刷新
            if (pageIndex < workspacePager.getAdapter().getItemCount()) {
                workspacePager.getAdapter().notifyItemRangeChanged(pageIndex, 
                                                                workspacePager.getAdapter().getItemCount() - pageIndex);
            }
            
            // 添加日志记录
            Log.d(TAG, "已移除页面: " + pageIndex + ", 当前总页数: " + workspaceCells.size());
        }
        
        // 如果当前页面被删除或索引变化，更新当前页面
        if (currentPage >= pageIndex) {
            int newCurrentPage = Math.max(0, Math.min(currentPage - 1, workspaceCells.size() - 1));
            workspacePager.setCurrentItem(newCurrentPage, false);
        }
        
        // 保存应用位置
        saveAppPositions();
        
        // 显示提示
        Toast.makeText(requireContext(), "已删除空白页面", Toast.LENGTH_SHORT).show();
    }

    /**
     * 设置工作区页面的手势导航
     */
    private void setupWorkspaceGestures(View rootView) {
        // 获取全局ViewPager2
        ViewPager2 mainViewPager = requireActivity().findViewById(R.id.viewPager);
        
        // 监听工作区ViewPager的滑动
        workspacePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            private float initialDragX = 0;
            private boolean isRightSwipeDetected = false;
            
            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_IDLE && isRightSwipeDetected) {
                    // 重置右滑检测标志
                    isRightSwipeDetected = false;
                    
                    if (workspacePager.getCurrentItem() == 0) {
                        // 如果已经在第一页并检测到右滑，切换到HomeFragment
                        Log.d(TAG, "检测到右滑手势，从WorkspaceFragment返回到HomeFragment");
                        mainViewPager.setCurrentItem(0, true);
                    }
                }
            }
            
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // 检测是否是右滑手势（从第一页右滑）
                if (position == 0 && positionOffset == 0 && positionOffsetPixels == 0) {
                    // 可能是右滑尝试
                    isRightSwipeDetected = true;
                }
            }
        });
        
        // 添加触摸监听器以捕获更精细的手势
        workspacePager.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private static final float SWIPE_THRESHOLD = 100;
            private boolean isProcessingRightSwipe = false;
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        isProcessingRightSwipe = false;
                        break;
                        
                    case MotionEvent.ACTION_MOVE:
                        float currentX = event.getX();
                        float deltaX = currentX - startX;
                        
                        // 检测右滑手势
                        if (!isProcessingRightSwipe && deltaX > SWIPE_THRESHOLD && workspacePager.getCurrentItem() == 0) {
                            // 如果在第一页右滑且移动距离超过阈值，标记为正在处理右滑
                            isProcessingRightSwipe = true;
                            Log.d(TAG, "检测到右滑手势，准备返回HomeFragment");
                        }
                        break;
                        
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float endX = event.getX();
                        float finalDeltaX = endX - startX;
                        
                        // 检测右滑手势
                        if (isProcessingRightSwipe || (finalDeltaX > SWIPE_THRESHOLD && workspacePager.getCurrentItem() == 0)) {
                            // 如果在第一页右滑，返回到HomeFragment
                            Log.d(TAG, "执行右滑返回到HomeFragment");
                            ViewPager2 mainViewPager = requireActivity().findViewById(R.id.viewPager);
                            if (mainViewPager != null) {
                                mainViewPager.setCurrentItem(0, true);
                                return true; // 消费事件
                            }
                        }
                        
                        isProcessingRightSwipe = false;
                        break;
                }
                
                return false; // 不消费事件，允许正常滚动
            }
        });
    }
}
