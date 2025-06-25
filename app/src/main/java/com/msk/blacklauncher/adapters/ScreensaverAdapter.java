package com.msk.blacklauncher.adapters;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.service.dreams.DreamService;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.R;

import java.util.List;

public class ScreensaverAdapter extends RecyclerView.Adapter<ScreensaverAdapter.ScreensaverViewHolder> {
    
    private final List<ResolveInfo> screensaverList;
    private final Context context;
    private final OnScreensaverSelectedListener listener;
    private int selectedPosition = -1;
    
    public interface OnScreensaverSelectedListener {
        void onScreensaverSelected(ComponentName componentName);
    }
    
    public ScreensaverAdapter(Context context, List<ResolveInfo> screensaverList, OnScreensaverSelectedListener listener) {
        this.context = context;
        this.screensaverList = screensaverList;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ScreensaverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_screensaver, parent, false);
        return new ScreensaverViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ScreensaverViewHolder holder, int position) {
        ResolveInfo info = screensaverList.get(position);
        PackageManager pm = context.getPackageManager();
        
        ServiceInfo serviceInfo = info.serviceInfo;
        String name = serviceInfo.loadLabel(pm).toString();
        Drawable icon = serviceInfo.loadIcon(pm);
        
        holder.nameTextView.setText(name);
        holder.iconImageView.setImageDrawable(icon);

        // 设置选中状态
        // 设置选中状态
        holder.cardView.setSelected(position == selectedPosition);

// 兼容不同版本的颜色获取方法

        int selectedColor, defaultColor;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // 对于 Android 6.0 及以上版本
            selectedColor = context.getResources().getColor(R.color.black, context.getTheme());
            defaultColor = context.getResources().getColor(R.color.white, context.getTheme());
        } else {
            // 对于旧版本
            selectedColor = context.getResources().getColor(R.color.black);
            defaultColor = context.getResources().getColor(R.color.white);
        }

// 使用选择的颜色
        holder.cardView.setCardBackgroundColor(position == selectedPosition ? selectedColor : defaultColor);
        
        holder.cardView.setOnClickListener(v -> {
            int oldSelectedPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            
            // 更新之前选中的项和新选中的项
            if (oldSelectedPosition != -1) {
                notifyItemChanged(oldSelectedPosition);
            }
            notifyItemChanged(selectedPosition);
            
            // 通知监听器
            ComponentName componentName = new ComponentName(
                    serviceInfo.packageName, 
                    serviceInfo.name);
            listener.onScreensaverSelected(componentName);
        });
    }
    
    @Override
    public int getItemCount() {
        return screensaverList.size();
    }
    
    static class ScreensaverViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView iconImageView;
        TextView nameTextView;
        
        public ScreensaverViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.screensaver_card);
            iconImageView = itemView.findViewById(R.id.screensaver_icon);
            nameTextView = itemView.findViewById(R.id.screensaver_name);
        }
    }
} 