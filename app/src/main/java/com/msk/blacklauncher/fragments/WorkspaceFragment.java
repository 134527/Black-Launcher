package com.msk.blacklauncher.fragments;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.msk.blacklauncher.R;
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

    private ViewPager workspacePager;
    private LinearLayout pageIndicator;
    private List<List<CellLayout.Cell>> workspaceCells;
    private PackageManager packageManager;

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
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_workspace, container, false);
        workspacePager = view.findViewById(R.id.workspace_pager);
        pageIndicator = view.findViewById(R.id.page_indicator);
        packageManager = requireActivity().getPackageManager();

        workspaceCells = loadAppPositions();
        if (workspaceCells.isEmpty()) {
            workspaceCells = initializeWorkspaceApps();
        }

        WorkspacePagerAdapter adapter = new WorkspacePagerAdapter(getContext(), workspaceCells);
        workspacePager.setAdapter(adapter);
        workspacePager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                updatePageIndicator(position);
            }
        });

        initPageIndicator();
        return view;
    }
     private void setupWorkspacePage(int pageIndex) {
         if (pageIndex >= workspaceCells.size()) {
             return;
         }

         List<CellLayout.Cell> pageCells = workspaceCells.get(pageIndex);
         for (int i = 0; i < pageCells.size(); i++) {
             CellLayout.Cell cell = pageCells.get(i);

             // 检查contentView是否为空，为空则创建
             if (cell.getContentView() == null) {
                 if (!cell.getTag().equals("empty")) {
                     try {
                         ApplicationInfo appInfo = packageManager.getApplicationInfo(cell.getTag(), 0);
                         Drawable icon = appInfo.loadIcon(packageManager);
                         String label = appInfo.loadLabel(packageManager).toString();

                         // 创建应用图标视图
                         View appView = createAppIconView(new AppModel(label, icon, cell.getTag()));
                         cell.setContentView(appView);
                     } catch (Exception e) {
                         Log.e("WorkspaceFragment", "创建应用图标失败: " + e.getMessage());
                     }
                 } else {
                     // 创建空白视图
                     View emptyView = new View(getContext());
                     emptyView.setVisibility(View.INVISIBLE);
                     cell.setContentView(emptyView);
                 }
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
         int totalPages = (int) Math.ceil(allApps.size() / (float) appsPerPage);

         // 为每一页创建 CellLayout
         for (int i = 0; i < totalPages; i++) {
             List<CellLayout.Cell> pageCells = new ArrayList<>();
             int startIndex = i * appsPerPage;
             int endIndex = Math.min((i + 1) * appsPerPage, allApps.size());

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

     // 更新页面指示器，使其动态适应页面数量
     private void initPageIndicator() {
         pageIndicator.removeAllViews();
         for (int i = 0; i < workspaceCells.size(); i++) {
             View indicator = new View(getContext());
             int size = (int) getResources().getDimension(R.dimen.page_indicator_size);
             LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
             int margin = (int) getResources().getDimension(R.dimen.page_indicator_margin);
             params.setMargins(margin, 0, margin, 0);
             indicator.setLayoutParams(params);
             indicator.setBackgroundResource(R.drawable.page_indicator);
             pageIndicator.addView(indicator);
         }
         updatePageIndicator(0);
     }

     private void updatePageIndicator(int currentPage) {
         for (int i = 0; i < pageIndicator.getChildCount(); i++) {
             View indicator = pageIndicator.getChildAt(i);
             indicator.setSelected(i == currentPage);
         }
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

    private class WorkspacePagerAdapter extends PagerAdapter {
        private Context context;
        private List<List<CellLayout.Cell>> pages;

        public WorkspacePagerAdapter(Context context, List<List<CellLayout.Cell>> pages) {
            this.context = context;
            this.pages = pages;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View pageView = LayoutInflater.from(context).inflate(R.layout.item_workspace_page, container, false);
            pageView.setTag(position); // 标记页面位置

            CellLayout cellLayout = pageView.findViewById(R.id.workspace_grid);
            cellLayout.setColumns(COLUMNS);
            cellLayout.setRows(ROWS);

            // 确保此页面的Cell已准备好
            setupWorkspacePage(position);

            List<CellLayout.Cell> pageCells = pages.get(position);
            for (CellLayout.Cell cell : pageCells) {
                if (cell.getContentView() != null) {
                    cellLayout.addCell(cell);
                }
            }

            container.addView(pageView);
            return pageView;
        }

        private int findPageIndexForCell(CellLayout.Cell cell) {
            for (int i = 0; i < workspaceCells.size(); i++) {
                if (workspaceCells.get(i).contains(cell)) {
                    return i;
                }
            }
            return -1;
        }

        private CellLayout.Cell findCellAtPosition(CellLayout cellLayout, float x, float y) {
            for (List<CellLayout.Cell> page : workspaceCells) {
                for (CellLayout.Cell cell : page) {
                    View view = cell.getContentView();
                    if (view.getLeft() <= x && x <= view.getRight() && view.getTop() <= y && y <= view.getBottom()) {
                        return cell;
                    }
                }
            }
            return null;
        }

        @Override
        public int getCount() {
            return pages.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getItemPosition(Object object) {
            // 强制 PagerAdapter 重新加载所有页面
            return POSITION_NONE;
        }







    }

}