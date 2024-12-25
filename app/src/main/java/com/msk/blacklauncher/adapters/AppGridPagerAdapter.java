package com.msk.blacklauncher.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import java.util.List;

public class AppGridPagerAdapter extends PagerAdapter {
    private List<ApplicationInfo> apps;
    private Context context;
    private PackageManager pm;
    private static final int GRID_SIZE = 9; // 3x3网格

    public AppGridPagerAdapter(Context context, List<ApplicationInfo> apps) {
        this.context = context;
        this.apps = apps;
        this.pm = context.getPackageManager();
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        grid.setPadding(8, 8, 8, 8);

        int startIndex = position * GRID_SIZE;
        int endIndex = Math.min(startIndex + GRID_SIZE, apps.size());

        for (int i = startIndex; i < endIndex; i++) {
            ApplicationInfo app = apps.get(i);
            ImageView appIcon = new ImageView(context);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 120; // 固定图标大小
            params.height = 120;
            params.setMargins(8, 8, 8, 8);
            appIcon.setLayoutParams(params);

            appIcon.setImageDrawable(app.loadIcon(pm));
            appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

            appIcon.setOnClickListener(v -> {
                Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                if (launchIntent != null) {
                    context.startActivity(launchIntent);
                }
            });

            grid.addView(appIcon);
        }

        container.addView(grid);
        return grid;
    }

    @Override
    public int getCount() {
        return (int) Math.ceil(apps.size() / (float) GRID_SIZE);
    }


    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    // 添加调试方法
    public void logPageInfo() {
        int totalPages = getCount();
        int totalApps = apps.size();
        android.util.Log.d("AppGridPager", "Total pages: " + totalPages + ", Total apps: " + totalApps);
    }
}