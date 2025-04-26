package com.msk.blacklauncher.adapter;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.R;

import java.util.List;

public class ShortcutAdapter extends RecyclerView.Adapter<ShortcutAdapter.ShortcutViewHolder> {

    private final List<ShortcutInfo> shortcuts;
    private final LauncherApps launcherApps;
    private final OnShortcutClickListener listener;

    public interface OnShortcutClickListener {
        void onShortcutClick(ShortcutInfo shortcutInfo);
    }

    public ShortcutAdapter(List<ShortcutInfo> shortcuts, LauncherApps launcherApps, OnShortcutClickListener listener) {
        this.shortcuts = shortcuts;
        this.launcherApps = launcherApps;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ShortcutViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shortcut_item, parent, false);
        return new ShortcutViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShortcutViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        ShortcutInfo shortcutInfo = shortcuts.get(position);
        
        // 设置快捷方式标签
        holder.shortcutLabel.setText(shortcutInfo.getShortLabel());
        
        // 设置快捷方式图标
        try {
            Drawable icon = launcherApps.getShortcutIconDrawable(shortcutInfo, 0);
            if (icon != null) {
                holder.shortcutIcon.setImageDrawable(icon);
            } else {
                holder.shortcutIcon.setImageResource(android.R.drawable.ic_menu_send);
            }
        } catch (Exception e) {
            holder.shortcutIcon.setImageResource(android.R.drawable.ic_menu_send);
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShortcutClick(shortcutInfo);
            }
        });
    }

    @Override
    public int getItemCount() {
        return shortcuts.size();
    }

    static class ShortcutViewHolder extends RecyclerView.ViewHolder {
        ImageView shortcutIcon;
        TextView shortcutLabel;

        ShortcutViewHolder(@NonNull View itemView) {
            super(itemView);
            shortcutIcon = itemView.findViewById(R.id.shortcut_icon);
            shortcutLabel = itemView.findViewById(R.id.shortcut_label);
        }
    }
} 