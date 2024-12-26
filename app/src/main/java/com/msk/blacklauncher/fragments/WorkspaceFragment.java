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
    private static final int PAGE_COUNT = 2; // 默认页数
    private static final int COLUMNS = 4;
    private static final int ROWS = 5;

    private ViewPager workspacePager;
    private LinearLayout pageIndicator;
    private List<List<CellLayout.Cell>> workspaceCells;
    private PackageManager packageManager;

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

    private List<List<CellLayout.Cell>> initializeWorkspaceApps() {
        List<List<CellLayout.Cell>> pages = new ArrayList<>();
        List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        List<AppModel> allApps = new ArrayList<>();

        for (ApplicationInfo appInfo : installedApps) {
            if (packageManager.getLaunchIntentForPackage(appInfo.packageName) != null) {
                // 可启动的应用
                Drawable icon = appInfo.loadIcon(packageManager);
                String label = appInfo.loadLabel(packageManager).toString();
                String packageName = appInfo.packageName;
                allApps.add(new AppModel(label, icon, packageName));
            }
        }

        // 对应用进行排序
        Collections.sort(allApps, new Comparator<AppModel>() {
            @Override
            public int compare(AppModel app1, AppModel app2) {
                return app1.getAppName().compareToIgnoreCase(app2.getAppName());
            }
        });

        // 将应用分配到页面
        int appsPerPage = COLUMNS * ROWS;
        for (int i = 0; i < PAGE_COUNT; i++) {
            List<CellLayout.Cell> pageCells = new ArrayList<>();
            for (int j = 0; j < appsPerPage; j++) {
                int appIndex = i * appsPerPage + j;
                if (appIndex < allApps.size()) {
                    AppModel app = allApps.get(appIndex);
                    View appIconView = createAppIconView(app);
                    CellLayout.Cell cell = new CellLayout.Cell(app.getPackageName(), appIconView);
                    pageCells.add(cell);
                } else {
                    // 添加一个空的 Cell
                    View emptyView = new View(getContext()); // 创建一个空的 View
                    emptyView.setVisibility(View.INVISIBLE); // 设置为不可见
                    CellLayout.Cell emptyCell = new CellLayout.Cell("empty", emptyView);
                    pageCells.add(emptyCell);
                }
            }
            pages.add(pageCells);
        }

        return pages;
    }

    private void initPageIndicator() {
        for (int i = 0; i < PAGE_COUNT; i++) {
            ImageView dot = new ImageView(getContext());
            dot.setImageResource(i == 0 ? R.drawable.ic_dot_selected : R.drawable.ic_dot_unselected);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            pageIndicator.addView(dot, params);
        }
    }

    private void updatePageIndicator(int position) {
        for (int i = 0; i < pageIndicator.getChildCount(); i++) {
            ImageView dot = (ImageView) pageIndicator.getChildAt(i);
            dot.setImageResource(i == position ? R.drawable.ic_dot_selected : R.drawable.ic_dot_unselected);
        }
    }

    private List<List<CellLayout.Cell>> loadAppPositions() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(APP_POSITIONS_KEY, "");
        if (!json.isEmpty()) {
            Gson gson = new Gson();
            Type type = new TypeToken<List<List<CellLayout.Cell>>>() {}.getType();
            List<List<CellLayout.Cell>> loadedCells = gson.fromJson(json, type);

            // 遍历加载的 Cell 列表，为每个 Cell 重新创建 View
            for (List<CellLayout.Cell> pageCells : loadedCells) {
                for (CellLayout.Cell cell : pageCells) {
                    if (!cell.getTag().equals("empty")) {
                        try {
                            ApplicationInfo appInfo = packageManager.getApplicationInfo(cell.getTag(), 0);
                            AppModel app = new AppModel(appInfo.loadLabel(packageManager).toString(), appInfo.loadIcon(packageManager), cell.getTag());
                            View appIconView = createAppIconView(app);
                            cell.setContentView(appIconView); // 更新 Cell 的 ContentView
                        } catch (PackageManager.NameNotFoundException e) {
                            Log.e("WorkspaceFragment", "App not found: " + cell.getTag(), e);
                        }
                    } else {
                        // 为空的 Cell 创建一个空的 View
                        View emptyView = new View(getContext());
                        emptyView.setVisibility(View.INVISIBLE);
                        cell.setContentView(emptyView);
                    }
                }
            }

            return loadedCells;
        }
        return Collections.emptyList();
    }

    private void saveAppPositions() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(workspaceCells);
        editor.putString(APP_POSITIONS_KEY, json);
        editor.apply();
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
            CellLayout cellLayout = pageView.findViewById(R.id.workspace_grid);
            cellLayout.setColumns(COLUMNS);
            cellLayout.setRows(ROWS);

            List<CellLayout.Cell> pageCells = pages.get(position);
            for (int i = 0; i < pageCells.size(); i++) {
                CellLayout.Cell cell = pageCells.get(i);
                cellLayout.addCell(cell);
                // 为每个 Cell 设置拖拽监听器
                if (!cell.getTag().equals("empty")) {
                    cell.getContentView().setOnLongClickListener(v -> {
                        ClipData.Item item = new ClipData.Item(cell.getTag());
                        ClipData dragData = new ClipData(
                                cell.getTag(),
                                new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN},
                                item);
                        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
                        v.startDrag(dragData, shadowBuilder, cell, 0); // 将 Cell 作为 local state 传递
                        v.setVisibility(View.INVISIBLE);
                        return true;
                    });
                }
            }

            // 设置 CellLayout 的拖放监听器
            cellLayout.setOnDragListener((v, event) -> {
                CellLayout.Cell draggedCell = (CellLayout.Cell) event.getLocalState();
                int fromPageIndex = findPageIndexForCell(draggedCell);

                switch (event.getAction()) {
                    case android.view.DragEvent.ACTION_DRAG_STARTED:
                        return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                    case android.view.DragEvent.ACTION_DRAG_ENTERED:
                        v.setBackgroundColor(Color.LTGRAY);
                        break;
                    case android.view.DragEvent.ACTION_DRAG_EXITED:
                        v.setBackgroundColor(Color.TRANSPARENT);
                        break;
                    case android.view.DragEvent.ACTION_DROP:
                        float dropX = event.getX();
                        float dropY = event.getY();

                        CellLayout.Cell targetCell = findCellAtPosition(cellLayout, dropX, dropY);
                        int toPageIndex = position;

                        if (targetCell != null) {
                            if (targetCell.getTag().equals("empty")) {
                                // 目标位置是空的，直接移动
                                workspaceCells.get(fromPageIndex).remove(draggedCell);
                                workspaceCells.get(toPageIndex).remove(targetCell);
                                workspaceCells.get(toPageIndex).add(draggedCell);
                                // 找到并添加一个空的 Cell 到原来的位置
                                View emptyView = new View(getContext());
                                emptyView.setVisibility(View.INVISIBLE);
                                CellLayout.Cell emptyCell = new CellLayout.Cell("empty", emptyView);
                                workspaceCells.get(fromPageIndex).add(emptyCell);
                            } else {
                                // 目标位置有应用，交换位置
                                int draggedIndex = workspaceCells.get(fromPageIndex).indexOf(draggedCell);
                                int targetIndex = workspaceCells.get(toPageIndex).indexOf(targetCell);

                                if (fromPageIndex == toPageIndex) {
                                    // 同一页内交换
                                    if (draggedIndex != -1 && targetIndex != -1) {
                                        Collections.swap(workspaceCells.get(fromPageIndex), draggedIndex, targetIndex);
                                    }
                                } else {
                                    // 不同页之间交换
                                    workspaceCells.get(fromPageIndex).remove(draggedCell);
                                    workspaceCells.get(toPageIndex).remove(targetCell);
                                    workspaceCells.get(fromPageIndex).add(targetIndex, targetCell);
                                    workspaceCells.get(toPageIndex).add(draggedIndex, draggedCell);
                                }
                            }

                            // 更新页面并保存位置
                            notifyDataSetChanged();
                            saveAppPositions();
                        }
                        break;

                    case android.view.DragEvent.ACTION_DRAG_ENDED:
                        v.setBackgroundColor(Color.TRANSPARENT);
                        if (!event.getResult() && draggedCell != null) {
                            draggedCell.getContentView().post(() -> draggedCell.getContentView().setVisibility(View.VISIBLE));
                        }
                        break;
                }
                return true;
            });

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