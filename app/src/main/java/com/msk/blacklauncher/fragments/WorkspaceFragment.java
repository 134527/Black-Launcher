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
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
    private static final int COLUMNS = 4;
    private static final int ROWS = 5;

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

     // 添加应用到工作区
     public void addAppToWorkspace(AppModel app) {
         // 查找可用位置
         Pair<Integer, Integer> position = findAvailablePosition();
         int pageIndex = position.first;
         int positionInPage = position.second;

         // 创建应用图标视图
         View appView = createAppIconView(app);
         CellLayout.Cell cell = new CellLayout.Cell(app.getPackageName(), appView);

         // 更新工作区数据
         workspaceCells.get(pageIndex).set(positionInPage, cell);

         // 如果当前显示的是该页面，立即更新
         if (workspacePager.getCurrentItem() == pageIndex) {
             setupWorkspacePage(pageIndex);
         }

         // 保存新的应用位置
         saveAppPositions();

         // 通知适配器数据已变化
         if (workspacePager.getAdapter() != null) {
             workspacePager.getAdapter().notifyDataSetChanged();
         }
     }

     // 从工作区移除应用
     public void removeAppFromWorkspace(String packageName) {
         boolean removed = false;

         // 查找并移除应用
         for (int pageIndex = 0; pageIndex < workspaceCells.size(); pageIndex++) {
             List<CellLayout.Cell> page = workspaceCells.get(pageIndex);
             for (int positionInPage = 0; positionInPage < page.size(); positionInPage++) {
                 CellLayout.Cell cell = page.get(positionInPage);
                 if (cell.getTag().equals(packageName)) {
                     // 替换为空单元格
                     View emptyView = new View(getContext());
                     emptyView.setVisibility(View.INVISIBLE);
                     CellLayout.Cell emptyCell = new CellLayout.Cell("empty", emptyView);
                     page.set(positionInPage, emptyCell);
                     removed = true;
                     break;
                 }
             }
             if (removed) break;
         }

         if (removed) {
             // 检查是否有空页面可以移除
             checkAndRemoveEmptyPages();

             // 保存新的应用位置
             saveAppPositions();

             // 通知适配器数据已变化
             if (workspacePager.getAdapter() != null) {
                 workspacePager.getAdapter().notifyDataSetChanged();
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
         workspaceCells = loadAppPositions();
         if (workspaceCells.isEmpty()) {
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
     private boolean handleCellOverflow(CellLayout.Cell overflowCell, int pageIndex) {
         Log.d(TAG, "处理溢出单元格: " + (overflowCell != null ? overflowCell.getTag() : "null") + " 页面: " + pageIndex);

         // 防止并发处理
         synchronized (this) {
             if (isHandlingOverflow) {
                 return false;
             }
             isHandlingOverflow = true;
         }

         try {
             // 检查单元格有效性
             if (overflowCell == null || overflowCell.getContentView() == null) {
                 return false;
             }

             // 创建新页面
             List<CellLayout.Cell> newPage = createEmptyPage();

             // 放置溢出单元格到新页面第一个位置
             newPage.set(0, overflowCell);

             // 添加新页面
             workspaceCells.add(newPage);

             // 在UI线程更新视图 - 但使用post确保布局完成后执行
             if (isAdded() && !isDetached()) {
                 new Handler().post(() -> {
                     if (isAdded() && !isDetached()) {
                         try {
                             // 更新页面指示器
                             initPageIndicator();

                             // 使用post确保通知发生在下一个布局周期
                             workspacePager.post(() -> {
                                 if (workspacePager != null && workspacePager.getAdapter() != null) {
                                     workspacePager.getAdapter().notifyDataSetChanged();
                                 }

                                 // 保存应用位置
                                 saveAppPositions();

                                 // 延迟切换到新页面
                                 new Handler().postDelayed(() -> {
                                     if (isAdded() && !isDetached() && workspacePager != null) {
                                         int lastPage = workspaceCells.size() - 1;
                                         workspacePager.setCurrentItem(lastPage, true);
                                     }
                                 }, 200);
                             });
                         } catch (Exception e) {
                             Log.e(TAG, "更新UI时出错", e);
                         }
                     }
                 });
             }

             return true;
         } finally {
             isHandlingOverflow = false;
         }
     }

     // 初始化页面指示器
     private void initPageIndicator() {
         if (pageIndicator != null) {
             pageIndicator.removeAllViews();
             int pageCount = workspaceCells.size();

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
                 updatePageIndicator(workspacePager.getCurrentItem());
             }
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

     private void setupWorkspacePage(int pageIndex)  {
         if (pageIndex < 0 || pageIndex >= workspaceCells.size()) {
             Log.e("WorkspaceFragment", "Invalid page index: " + pageIndex);
             return;
         }

         // 获取页面视图
         View pageView = null;
         RecyclerView.Adapter adapter = workspacePager.getAdapter();
         if (adapter != null && adapter instanceof WorkspacePagerAdapter) {
             // 找到当前页面视图
             for (int i = 0; i < workspacePager.getChildCount(); i++) {
                 View child = workspacePager.getChildAt(i);
                 if (child.getTag() != null && child.getTag().equals("page_" + pageIndex)) {
                     pageView = child;
                     break;
                 }
             }
         }

         if (pageView == null) {
             Log.d("WorkspaceFragment", "Page view not found for index: " + pageIndex);
             return;
         }

         CellLayout cellLayout = pageView.findViewById(R.id.workspace_grid);
         if (cellLayout == null) {
             Log.e("WorkspaceFragment", "CellLayout not found in page view");
             return;
         }

         // 清空现有内容
         cellLayout.removeAllViews();

         // 添加应用图标
         List<CellLayout.Cell> pageCells = workspaceCells.get(pageIndex);
         for (CellLayout.Cell cell : pageCells) {
             if (cell != null && !cell.getTag().equals("empty") && cell.getContentView() != null) {
                 // 重置Cell的期望位置，让CellLayout重新计算
                 cell.setExpectColumnIndex(-1);
                 cell.setExpectRowIndex(-1);
                 cellLayout.addCell(cell);
             }
         }
     }
   private List<List<CellLayout.Cell>> initializeWorkspaceApps() {
         List<List<CellLayout.Cell>> pages = new ArrayList<>();
         List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
         List<AppModel> allApps = new ArrayList<>();

         // 过滤可启动的应用
         for (ApplicationInfo appInfo : installedApps) {
             if (packageManager.getLaunchIntentForPackage(appInfo.packageName) != null) {
                 Drawable icon = appInfo.loadIcon(packageManager);
                 String label = appInfo.loadLabel(packageManager).toString();
                 allApps.add(new AppModel(label, icon, appInfo.packageName));
             }
         }

         // 计算需要的页数
         int appsPerPage = COLUMNS * ROWS;
         int totalPages = Math.max(1, (int) Math.ceil(allApps.size() / (float) appsPerPage));

         // 为每一页创建 CellLayout
         for (int i = 0; i < totalPages; i++) {
             List<CellLayout.Cell> pageCells = new ArrayList<>();
             int startIndex = i * appsPerPage;
             int endIndex = Math.min((i + 1) * appsPerPage, allApps.size());

             // 添加当前页面的应用图标
             for (int j = startIndex; j < endIndex; j++) {
                 AppModel app = allApps.get(j);
                 View appIconView = createAppIconView(app);
                 CellLayout.Cell cell = new CellLayout.Cell(app.getPackageName(), appIconView);
                 pageCells.add(cell);
             }

             // 填充空白位置
             while (pageCells.size() < appsPerPage) {
                 View emptyView = new View(getContext());
                 emptyView.setVisibility(View.INVISIBLE);
                 CellLayout.Cell emptyCell = new CellLayout.Cell("empty", emptyView);
                 pageCells.add(emptyCell);
             }

             pages.add(pageCells);
         }

         return pages;
     }



private List<List<CellLayout.Cell>> loadAppPositions() {
         List<List<CellLayout.Cell>> result = new ArrayList<>();

         try {
             SharedPreferences prefs = requireActivity().getSharedPreferences(
                     "workspace_prefs", Context.MODE_PRIVATE);
             String json = prefs.getString("app_positions", "");

             if (!TextUtils.isEmpty(json)) {
                 Gson gson = new Gson();
                 Type type = new TypeToken<List<SerializableAppPosition>>(){}.getType();
                 List<SerializableAppPosition> savedPositions = gson.fromJson(json, type);

                 // 初始化空白桌面
                 result = initializeEmptyWorkspace(Math.max(1, getMaxPageIndex(savedPositions) + 1));

                 // 恢复应用位置
                 for (SerializableAppPosition position : savedPositions) {
                     // 确保页面索引有效
                     while (result.size() <= position.getPageIndex()) {
                         result.add(createEmptyPage());
                     }

                     // 获取应用信息
                     String packageName = position.getPackageName();

                     try {
                         ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                         Drawable icon = appInfo.loadIcon(packageManager);
                         String label = appInfo.loadLabel(packageManager).toString();

                         // 创建应用图标
                         View appView = createAppIconView(new AppModel(label, icon, packageName));
                         CellLayout.Cell cell = new CellLayout.Cell(packageName, appView);

                         // 放置到指定位置
                         List<CellLayout.Cell> page = result.get(position.getPageIndex());
                         if (position.getPositionInPage() < page.size()) {
                             page.set(position.getPositionInPage(), cell);
                         }
                     } catch (PackageManager.NameNotFoundException e) {
                         Log.w("WorkspaceFragment", "应用不存在: " + packageName);
                     }
                 }
             }
         } catch (Exception e) {
             Log.e("WorkspaceFragment", "加载应用位置时出错", e);
             // 如果加载失败，返回空列表，外部会处理
         }

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

    private void saveAppPositions() {
         try {
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

             // 使用普通Gson对象而不是GsonBuilder
             SharedPreferences prefs = requireActivity().getSharedPreferences(
                     "workspace_prefs", Context.MODE_PRIVATE);
             Gson gson = new Gson(); // 不使用GsonBuilder和注解
             String json = gson.toJson(appPositions);

             prefs.edit().putString("app_positions", json).apply();
         } catch (Exception e) {
             Log.e("WorkspaceFragment", "保存应用位置时出错", e);
         }
     }

    private View createAppIconView(AppModel app) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_app_icon, null, false);
        ImageView iconView = view.findViewById(R.id.app_icon_image);
        TextView labelView = view.findViewById(R.id.app_icon_label);

        if (app != null) {
            iconView.setImageDrawable(app.getAppIcon());
            labelView.setText(app.getAppName());

            // 设置图标大小和布局
            int iconSize = (int) getResources().getDimension(R.dimen.app_icon_size);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconView.setLayoutParams(iconParams);

            // 设置应用名称的样式
            labelView.setTextColor(Color.WHITE);
            labelView.setTextSize(12);
            labelView.setMaxLines(1);
            labelView.setEllipsize(TextUtils.TruncateAt.END);

            view.setOnClickListener(v -> {
                Intent launchIntent = packageManager.getLaunchIntentForPackage(app.getPackageName());
                if (launchIntent != null) {
                    startActivity(launchIntent);
                }
            });

            view.setOnLongClickListener(v -> {
                ClipData.Item item = new ClipData.Item(app.getPackageName());
                ClipData dragData = new ClipData(
                        app.getAppName(),
                        new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN},
                        item);
                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
                view.startDrag(dragData, shadowBuilder, v, 0);
                view.setVisibility(View.INVISIBLE);
                return true;
            });
        }


        return view;
    }



}