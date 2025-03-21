package com.msk.blacklauncher.adapters;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DialogAppsAdapter extends RecyclerView.Adapter<DialogAppsAdapter.AppPageViewHolder> {
    private static final int APPS_PER_PAGE = 16; // 8列2行
    private final List<ApplicationInfo> apps;
    private final Context context;
    private final PackageManager pm;

    public DialogAppsAdapter(Context context, List<ApplicationInfo> apps) {
        this.context = context;
        this.apps = apps;
        this.pm = context.getPackageManager();
    }

    @NonNull
    @Override
    public AppPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        GridLayout gridLayout = new GridLayout(context);
        gridLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        gridLayout.setColumnCount(8);
        gridLayout.setPadding(16, 16, 16, 16);
        return new AppPageViewHolder(gridLayout);
    }

    @Override
    public void onBindViewHolder(@NonNull AppPageViewHolder holder, int position) {
        int startIndex = position * APPS_PER_PAGE;
        int endIndex = Math.min(startIndex + APPS_PER_PAGE, apps.size());

        holder.gridLayout.removeAllViews();

        for (int i = startIndex; i < endIndex; i++) {
            ApplicationInfo app = apps.get(i);

            LinearLayout appContainer = new LinearLayout(context);
            appContainer.setOrientation(LinearLayout.VERTICAL);
            appContainer.setGravity(Gravity.CENTER);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = GridLayout.LayoutParams.WRAP_CONTENT;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(8, 8, 8, 8);
            appContainer.setLayoutParams(params);

            ImageView iconView = new ImageView(context);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(80, 80);
            iconView.setLayoutParams(iconParams);
            iconView.setImageDrawable(app.loadIcon(pm));
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            TextView nameView = new TextView(context);
            nameView.setText(app.loadLabel(pm));
            nameView.setTextColor(Color.WHITE);
            nameView.setTextSize(12);
            nameView.setGravity(Gravity.CENTER);
            nameView.setMaxLines(1);
            nameView.setEllipsize(TextUtils.TruncateAt.END);

            appContainer.addView(iconView);
            appContainer.addView(nameView);

            appContainer.setOnClickListener(v -> {
                Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                if (launchIntent != null) {
                    context.startActivity(launchIntent);
                    if (context instanceof Activity) {
                        ((Activity) context).finish();
                    }
                }
            });

            holder.gridLayout.addView(appContainer);
        }
    }

    @Override
    public int getItemCount() {
        return (int) Math.ceil(apps.size() / (float) APPS_PER_PAGE);
    }

    static class AppPageViewHolder extends RecyclerView.ViewHolder {
        GridLayout gridLayout;

        AppPageViewHolder(@NonNull GridLayout gridLayout) {
            super(gridLayout);
            this.gridLayout = gridLayout;
        }
    }
}
