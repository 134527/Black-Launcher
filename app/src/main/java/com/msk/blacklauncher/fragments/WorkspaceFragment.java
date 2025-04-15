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
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
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
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.msk.blacklauncher.R;
import com.msk.blacklauncher.Utils.IconUtils;
import com.msk.blacklauncher.adapters.WorkspacePagerAdapter;
import com.msk.blacklauncher.model.AppModel;
import com.msk.blacklauncher.view.CellLayout;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

 public class WorkspaceFragment extends Fragment {

    private static final String PREFS_NAME = "WorkspacePrefs";
    private static final String APP_POSITIONS_KEY = "AppPositions";
//    private static final int PAGE_COUNT = 2; // 默认页数
    private static final int COLUMNS = 9;
    private static final int ROWS = 4;

    private ViewPager2 workspacePager;
    private LinearLayout pageIndicator;
    private List<List<CellLayout.Cell>> workspaceCells;
    private PackageManager packageManager;
     private static final String TAG = "WorkspaceFragment";
     // 控制标志
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

         // getter方法
         public String getPackageName() { return packageName; }
         public int getPageIndex() { return pageIndex; }
         public int getPositionInPage() { return positionInPage; }
     }

     // 在 onCreate 方法中注册广播接收器
     @Override
     public void onCreate(Bundle savedInstanceState) {
         super.onCreate(savedInstanceState);

         // 注册应用安装/卸载广播接收器
         IntentFilter filter = new IntentFilter();
         filter.addAction(Intent.ACTION_PACKAGE_ADDED);
         filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
         filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
         filter.addDataScheme("package");

         requireActivity().registerReceiver(appChangeReceiver, filter);
     }

     // 在 onDestroy 方法中取消注册
     @Override
     public void onDestroy() {
         super.onDestroy();
         if (appChangeReceiver != null) {
             requireActivity().unregisterReceiver(appChangeReceiver);
         }
     }

     // 应用变化广播接收器
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
                     // 处理新应用安装或应用更新
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
                 // 处理应用卸载
                 removeAppFromWorkspace(packageName);
             }
         }
     };
     // 添加新的页面
     private void addNewPage() {
         List<CellLayout.Cell> newPage = createEmptyPage();
         workspaceCells.add(newPage);

         // 更新页面指示器
         initPageIndicator();

         // 通知适配器数据已变化
         if (workspacePager.getAdapter() != null) {
             workspacePager.getAdapter().notifyDataSetChanged();
         }
     }

     // 获取空闲的单元格位置
     private Pair<Integer, Integer> findAvailablePosition() {
         // 首先检查现有页面是否有空位
         for (int pageIndex = 0; pageIndex < workspaceCells.size(); pageIndex++) {
             List<CellLayout.Cell> page = workspaceCells.get(pageIndex);
             for (int position = 0; position < page.size(); position++) {
                 CellLayout.Cell cell = page.get(position);
                 if (cell.getTag().equals("empty")) {
                     return new Pair<>(pageIndex, position);
                 }
             }
         }

         // 如果没有空位，创建新页面并返回首位置
         addNewPage();
         return new Pair<>(workspaceCells.size() - 1, 0);
     }

     public void addAppToWorkspace(AppModel app) {
         try {
             // 查找可用位置
             Pair<Integer, Integer> position = findAvailablePosition();
             int pageIndex = position.first;
             int positionInPage = position.second;

             // 创建应用图标视图
             View appView = createAppIconView(app);
             CellLayout.Cell cell = new CellLayout.Cell(app.getPackageName(), appView);

             // 更新工作区数据
             workspaceCells.get(pageIndex).set(positionInPage, cell);

             // 保存新的应用位置
             saveAppPositions();

             // 只通知修改的页面变化，而不是整个适配器
             if (workspacePager.getAdapter() != null) {
                 // 只通知特定页面变化
                 workspacePager.getAdapter().notifyItemChanged(pageIndex);

                 // 如果当前显示的是该页面，立即更新
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

     public void removeAppFromWorkspace(String packageName)  {
         boolean removed = false;
         int removedPageIndex = -1;

         // 查找并移除应用
         for (int pageIndex = 0; pageIndex < workspaceCells.size(); pageIndex++) {
             List<CellLayout.Cell> page = workspaceCells.get(pageIndex);
             for (int positionInPage = 0; positionInPage < page.size(); positionInPage++) {
                 CellLayout.Cell cell = page.get(positionInPage);
                 if (cell.getTag().equals(packageName)) {
                     // 替换为空单元格，保持结构不变
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

             // 保存新的应用位置
             saveAppPositions();

             // 只更新需要更改的页面
             if (workspacePager.getAdapter() != null) {
                 // 只通知特定页面变化
                 workspacePager.getAdapter().notifyItemChanged(updatedPageIndex);

                 // 如果当前显示的是该页面，立即更新
                 if (workspacePager.getCurrentItem() == updatedPageIndex) {
                     new Handler(Looper.getMainLooper()).post(() -> {
                         setupWorkspacePage(updatedPageIndex);
                     });
                 }
             }
         }
     }

     // 检查并移除空页面
     private void checkAndRemoveEmptyPages() {
         List<Integer> emptyPageIndices = new ArrayList<>();

         // 找出所有空页面
         for (int pageIndex = 0; pageIndex < workspaceCells.size(); pageIndex++) {
             boolean pageIsEmpty = true;
             List<CellLayout.Cell> page = workspaceCells.get(pageIndex);

             for (CellLayout.Cell cell : page) {
                 if (!cell.getTag().equals("empty")) {
                     pageIsEmpty = false;
                     break;
                 }
             }

             if (pageIsEmpty && pageIndex > 0) { // 至少保留一个页面
                 emptyPageIndices.add(pageIndex);
             }
         }

         // 从后向前移除空页面
         for (int i = emptyPageIndices.size() - 1; i >= 0; i--) {
             int index = emptyPageIndices.get(i);
             workspaceCells.remove(index);
         }

         // 更新页面指示器
         if (!emptyPageIndices.isEmpty()) {
             initPageIndicator();
         }
     }



     public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
         View view = inflater.inflate(R.layout.fragment_workspace, container, false);

         // 初始化视图
         workspacePager = view.findViewById(R.id.workspace_pager);
         pageIndicator = view.findViewById(R.id.page_indicator);
         packageManager = requireActivity().getPackageManager();


         // 加载或初始化应用位置
         boolean forceInit = false; // 添加标志以强制初始化
         workspaceCells = loadAppPositions();
         // 加载或初始化应用位置
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
// 如果没有应用或被强制初始化，重新加载所有应用
         if (workspaceCells.isEmpty() || !hasApps || forceInit) {
             Log.d(TAG, "没有找到应用或强制初始化，重新加载所有应用");
             workspaceCells = initializeWorkspaceApps();
             saveAppPositions();
         }
         // 确保至少有一个页面
         if (workspaceCells.isEmpty()) {
             workspaceCells.add(createEmptyPage());
         }

         // 创建适配器
         final WorkspacePagerAdapter adapter = new WorkspacePagerAdapter(
                 requireContext(),
                 workspaceCells,
                 COLUMNS,
                 ROWS,
                 new WorkspacePagerAdapter.OnCellActionListener() {
                     @Override
                     public void onCellSwapped(CellLayout.Cell draggingCell, int fromPage, int toPage, int targetColumn, int targetRow) {
                         // 实现逻辑
                     }

                     @Override
                     public void saveAppPositions() {
                         WorkspaceFragment.this.saveAppPositions();
                     }

                     @Override
                     public void setupWorkspacePage(int pageIndex) {
                         if (pageIndex >= 0 && pageIndex < workspaceCells.size()) {
                             // ViewPager2不再需要这个方法，在onBindViewHolder中处理
                         }
                     }

                     @Override
                     public boolean onCellOverflow(CellLayout.Cell overflowCell, int pageIndex) {
                         return WorkspaceFragment.this.handleCellOverflow(overflowCell, pageIndex);
                     }
                 }
         );

         // 初始化页面指示器
         initPageIndicator();

         // 设置适配器
         workspacePager.setAdapter(adapter);

         // 注册页面变化监听器
         workspacePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
             @Override
             public void onPageSelected(int position) {
                 updatePageIndicator(position);
             }
         });

         return view;
     }
     // 处理单元格溢出
     private boolean handleCellOverflow(CellLayout.Cell overflowCell, int pageIndex)   {
         Log.d(TAG, "处理溢出单元格: " + (overflowCell != null ? overflowCell.getTag() : "null") + " 页面: " + pageIndex);

         // 防止并发处理
         synchronized (this) {
             if (isHandlingOverflow) {
                 Log.w(TAG, "已有溢出处理进行中，忽略此次溢出");
                 return false;
             }
             isHandlingOverflow = true;
         }

         try {
             // 检查单元格有效性
             if (overflowCell == null || overflowCell.getContentView() == null) {
                 Log.e(TAG, "无效的溢出单元格");
                 return false;
             }

             // 确保在UI线程中执行
             if (!isAdded() || requireActivity() == null) {
                 Log.e(TAG, "片段未附加到活动");
                 return false;
             }

             requireActivity().runOnUiThread(() -> {
                 try {
                     // 查找空位置
                     Pair<Integer, Integer> emptyPosition = findEmptyPositionInExistingPages();
                     if (emptyPosition != null) {
                         int targetPage = emptyPosition.first;
                         int targetPos = emptyPosition.second;

                         // 放置溢出单元格到空位
                         workspaceCells.get(targetPage).set(targetPos, overflowCell);

                         // 保存应用位置
                         saveAppPositions();

                         // 更新UI
                         if (workspacePager != null && workspacePager.getAdapter() != null) {
                             workspacePager.getAdapter().notifyItemChanged(targetPage);
                             workspacePager.setCurrentItem(targetPage, true);
                         }
                     } else if (workspaceCells.size() < 5) {
                          // 创建新页面
                         List<CellLayout.Cell> newPage = createEmptyPage();
                         newPage.set(0, overflowCell);
                         workspaceCells.add(newPage);

                         // 保存应用位置
                         saveAppPositions();

                         // 更新UI
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
                     // 重置处理标志
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

     // 在现有页面中查找空位置
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

     // 查找所有页面中的空位置
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

     // 初始化页面指示器
     private void initPageIndicator() {
         if (pageIndicator == null || !isAdded()) {
             return;
         }

         // 清空现有指示器
         pageIndicator.removeAllViews();

         // 确保页面数量合理
         final int MAX_PAGES = 5;
         int pageCount = Math.min(MAX_PAGES, workspaceCells.size());

         // 如果页面数量异常，重置为1页
         if (pageCount > MAX_PAGES || pageCount <= 0) {
             Log.e(TAG, "页面数量异常: " + workspaceCells.size() + "，重置为1页");
             workspaceCells.clear();
             workspaceCells.add(createEmptyPage());
             pageCount = 1;

             // 通知适配器数据已完全改变
             if (workspacePager != null && workspacePager.getAdapter() != null) {
                 workspacePager.getAdapter().notifyDataSetChanged();
             }
         }

         Log.d(TAG, "初始化页面指示器，页面数: " + pageCount);

         // 创建新指示器
         for (int i = 0; i < pageCount; i++) {
             View dot = new View(requireContext());
             LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                     (int) getResources().getDimension(R.dimen.dot_size),
                     (int) getResources().getDimension(R.dimen.dot_size)
             );
             params.setMargins(8, 0, 8, 0);
             dot.setLayoutParams(params);
             dot.setBackgroundResource(R.drawable.dot_indicator);
             pageIndicator.addView(dot);
         }

         // 更新当前页面指示器
         if (workspacePager != null) {
             updatePageIndicator(Math.min(workspacePager.getCurrentItem(), pageCount - 1));
         }
     }

     // 更新页面指示器状态
     private void updatePageIndicator(int position) {
         if (pageIndicator != null) {
             for (int i = 0; i < pageIndicator.getChildCount(); i++) {
                 View dot = pageIndicator.getChildAt(i);
                 dot.setSelected(i == position);
             }
         }
     }

     // 设置所有工作区页面
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
             // 获取当前页面的视图
             View pageView = workspacePager.findViewWithTag("page_" + pageIndex);
             if (pageView != null) {
                 CellLayout cellLayout = pageView.findViewById(R.id.workspace_grid);
                 if (cellLayout != null) {
                     // 清除所有视图但保持结构
                     cellLayout.removeAllViews();

                     // 保持原有单元格顺序重新添加
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

         // 创建一个空白工作区
         List<List<CellLayout.Cell>> result = new ArrayList<>();
         result.add(createEmptyPage());

         try {
             // 获取所有应用
             List<AppModel> allApps = getAllApps();
             Log.d(TAG, "找到 " + allApps.size() + " 个应用");

             // 按照名称排序
             allApps.sort(Comparator.comparing(AppModel::getAppName));

             // 每页最多容纳的应用数
             final int appsPerPage = COLUMNS * ROWS;

             // 按顺序放置应用
             int currentPage = 0;
             int currentPosition = 0;

             for (AppModel app : allApps) {
                 // 创建应用图标视图
                 View appView = createAppIconView(app);
                 CellLayout.Cell cell = new CellLayout.Cell(app.getPackageName(), appView);

                 // 确保视图可见
                 appView.setVisibility(View.VISIBLE);

                 // 获取当前页
                 List<CellLayout.Cell> page = result.get(currentPage);

                 // 放置应用
                 page.set(currentPosition, cell);

                 // 更新位置
                 currentPosition++;

                 // 如果当前页已满，创建新页
                 if (currentPosition >= appsPerPage) {
                     currentPosition = 0;
                     currentPage++;

                     // 如果需要创建新页
                     if (currentPage >= result.size() && currentPage < 5) { // 限制最大页数
                         result.add(createEmptyPage());
                     } else if (currentPage >= 5) {
                         // 达到最大页数，停止添加
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

     // 获取所有已安装应用
     private List<AppModel> getAllApps() {
         List<AppModel> apps = new ArrayList<>();

         // 获取所有已安装的应用
         Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
         mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

         List<ResolveInfo> availableActivities = packageManager.queryIntentActivities(mainIntent, 0);

         for (ResolveInfo resolveInfo : availableActivities) {
             String packageName = resolveInfo.activityInfo.packageName;

             // 跳过自己的应用
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

                 // 限制最大页面数
                 int maxPages = 5; // 设置最大页面数
                 int estimatedPages = Math.min(maxPages, Math.max(1, getMaxPageIndex(savedPositions) + 1));

                 // 初始化空白桌面
                 result = initializeEmptyWorkspace(estimatedPages);

                 // 恢复应用位置，但仅处理在最大页面数内的应用
                 for (SerializableAppPosition position : savedPositions) {
                     // 忽略超出页面限制的应用
                     if (position.getPageIndex() >= maxPages) {
                         continue;
                     }

                     // 确保页面索引有效
                     while (result.size() <= position.getPageIndex() && result.size() < maxPages) {
                         result.add(createEmptyPage());
                     }

                     try {
                         // 获取应用信息
                         String packageName = position.getPackageName();
                         ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);

                         // 创建应用图标
                         View appView = createAppIconView(new AppModel(
                                 appInfo.loadLabel(packageManager).toString(),
                                 appInfo.loadIcon(packageManager),
                                 packageName
                         ));

                         CellLayout.Cell cell = new CellLayout.Cell(packageName, appView);

                         // 放置到指定位置
                         if (position.getPageIndex() < result.size()) {
                             List<CellLayout.Cell> page = result.get(position.getPageIndex());
                             if (position.getPositionInPage() < page.size()) {
                                 page.set(position.getPositionInPage(), cell);
                             }
                         }
                     } catch (PackageManager.NameNotFoundException e) {
                         Log.w(TAG, "应用不存在: " );
                     } catch (Exception e) {
                         Log.e(TAG, "加载应用时出错: " , e);
                     }
                 }
             }
         } catch (Exception e) {
             Log.e(TAG, "加载应用位置时出错", e);
         }

         // 确保至少有一个页面
         if (result.isEmpty()) {
             result.add(createEmptyPage());
         }

         // 验证最终页面数
         Log.d(TAG, "加载完成，页面数量: " + result.size());
         return result;
     }

     /**
      * 初始化空白工作区
      */
     private List<List<CellLayout.Cell>> initializeEmptyWorkspace(int pageCount) {
         List<List<CellLayout.Cell>> pages = new ArrayList<>();

         for (int i = 0; i < pageCount; i++) {
             pages.add(createEmptyPage());
         }

         return pages;
     }



     // 获取最大页面索引的辅助方法
     private int getMaxPageIndex(List<SerializableAppPosition> positions) {
         int maxPageIndex = 0;
         for (SerializableAppPosition position : positions) {
             maxPageIndex = Math.max(maxPageIndex, position.getPageIndex());
         }
         return maxPageIndex;
     }

     // 创建空白页的辅助方法
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

    private void saveAppPositions()  {
         try {
             if (!isAdded() || requireActivity() == null) {
                 Log.e(TAG, "片段未附加到活动，无法保存应用位置");
                 return;
             }

             List<SerializableAppPosition> appPositions = new ArrayList<>();

             // 遍历所有页面和应用，只保存非空应用的位置信息
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

             // 使用普通Gson对象
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

    private View createAppIconView(AppModel app)   {
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

}