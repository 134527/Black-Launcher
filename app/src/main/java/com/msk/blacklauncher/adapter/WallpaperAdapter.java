package com.msk.blacklauncher.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.model.Wallpaper;

import java.util.List;

public class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.ViewHolder> {

    // 添加TAG常量
    private static final String TAG = "WallpaperAdapter";
    
    private final Context context;
    private final List<Wallpaper> wallpapers;
    private final OnWallpaperClickListener listener;
    private String selectedWallpaperId;
    
    // 定义高质量图片选项
    private static final boolean USE_HIGH_QUALITY = true;

    public WallpaperAdapter(Context context, List<Wallpaper> wallpapers, OnWallpaperClickListener listener) {
        this.context = context;
        this.wallpapers = wallpapers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_wallpaper, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Wallpaper wallpaper = wallpapers.get(position);
        
        // 设置壁纸图片
        if (wallpaper.getThumbnail() != null) {
            // 使用高质量渲染模式
            holder.wallpaperImage.setImageBitmap(wallpaper.getThumbnail());
            if (USE_HIGH_QUALITY) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    holder.wallpaperImage.setImageAlpha(255);
                }
                
                // 使用BitmapDrawable设置过滤选项，而不是直接在ImageView上设置
                Drawable drawable = holder.wallpaperImage.getDrawable();
                if (drawable instanceof BitmapDrawable) {
                    ((BitmapDrawable) drawable).setFilterBitmap(true);
                }
            }
        } else {
            holder.wallpaperImage.setImageURI(wallpaper.getImageUri());
        }
        
        // 设置壁纸名称
        holder.wallpaperName.setText(wallpaper.getName());
        
        // 设置选中状态 - 简化逻辑，确保稳定显示
        String currentId = wallpaper.getId();
        boolean isSelected = false;
        
        if (currentId != null && selectedWallpaperId != null) {
            isSelected = currentId.equals(selectedWallpaperId);
        }
        
        Log.d(TAG, "绑定项目 " + position + ", 壁纸ID: " + currentId + 
              ", 选中状态: " + isSelected + 
              ", 当前选中ID: " + selectedWallpaperId);
        
        // 设置选中指示器的显示状态
        if (isSelected) {
            Log.d(TAG, "设置项目 " + position + " 的选中指示器为可见，ID: " + currentId);
            holder.ensureCheckIndicatorVisible(true);
            
            // 添加一个标记，表示这是选中的项目
            holder.itemView.setTag(R.id.tag_selected_wallpaper, true);
        } else {
            Log.d(TAG, "设置项目 " + position + " 的选中指示器为不可见，ID: " + currentId);
            holder.ensureCheckIndicatorVisible(false);
            
            // 移除标记
            holder.itemView.setTag(R.id.tag_selected_wallpaper, null);
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                Log.d(TAG, "项目 " + position + " 被点击, 壁纸ID: " + wallpaper.getId());
                listener.onWallpaperClick(wallpaper, position);
            }
        });
        
        // 应用高质量显示
        applyHighQualityDisplay(holder.wallpaperImage);
    }
    
    /**
     * 应用高质量图片显示设置
     */
    private void applyHighQualityDisplay(ImageView imageView) {
        // 禁用硬件加速可以在某些设备上提高图片质量
        if (USE_HIGH_QUALITY) {
            // 只在需要的设备上禁用硬件加速
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                imageView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            
            // 确保图片渲染质量设置为高
            imageView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
            
            // 应用高质量缩放类型
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            
            // 检查并设置BitmapDrawable的过滤选项
            Drawable drawable = imageView.getDrawable();
            if (drawable instanceof BitmapDrawable) {
                ((BitmapDrawable) drawable).setFilterBitmap(true);
                // 还可以设置抗锯齿
                Paint paint = ((BitmapDrawable) drawable).getPaint();
                paint.setAntiAlias(true);
                paint.setFilterBitmap(true);
            }
        }
    }

    @Override
    public int getItemCount() {
        return wallpapers.size();
    }
    
    /**
     * 设置当前选中的壁纸ID
     */
    public void setSelectedWallpaperId(String wallpaperId) {
        Log.d(TAG, "设置选中的壁纸ID: " + wallpaperId + ", 当前选中ID: " + this.selectedWallpaperId);
        
        if (wallpaperId == null) {
            Log.w(TAG, "尝试设置null壁纸ID，跳过操作");
            return;
        }
        
        // 打印所有可用的壁纸ID，辅助调试
        if (wallpapers != null) {
            Log.d(TAG, "当前适配器中的壁纸列表:");
            for (int i = 0; i < wallpapers.size(); i++) {
                Wallpaper wp = wallpapers.get(i);
                Log.d(TAG, "  位置 " + i + ": ID=" + wp.getId() + ", 名称=" + wp.getName());
            }
        }
        
        // 记录以前的选中ID
        String oldId = this.selectedWallpaperId;
        
        // 检查是否有变化
        boolean changed = (oldId == null || !oldId.equals(wallpaperId));
        
        // 无论是否变化，都更新当前选中ID
        this.selectedWallpaperId = wallpaperId;
        
        if (changed) {
            Log.d(TAG, "选中的壁纸ID已更改，旧ID: " + oldId + ", 新ID: " + wallpaperId);
            
            // 如果有旧的选中项，查找并更新
            if (oldId != null) {
                for (int i = 0; i < wallpapers.size(); i++) {
                    if (oldId.equals(wallpapers.get(i).getId())) {
                        Log.d(TAG, "更新之前选中的项目: " + i);
                        notifyItemChanged(i);
                        break;
                    }
                }
            }
            
            // 查找并更新新的选中项
            boolean foundNewItem = false;
            for (int i = 0; i < wallpapers.size(); i++) {
                if (wallpaperId.equals(wallpapers.get(i).getId())) {
                    Log.d(TAG, "更新新选中的项目: " + i);
                    notifyItemChanged(i);
                    foundNewItem = true;
                    break;
                }
            }
            
            if (!foundNewItem) {
                Log.w(TAG, "未找到ID为 " + wallpaperId + " 的壁纸，尝试完全刷新适配器");
                notifyDataSetChanged();
            }
        } else {
            Log.d(TAG, "选中的壁纸ID没有变化，强制刷新确保显示: " + wallpaperId);
            
            // 即使ID没有变化，也要确保圆勾显示
            boolean foundItem = false;
            for (int i = 0; i < wallpapers.size(); i++) {
                if (wallpaperId.equals(wallpapers.get(i).getId())) {
                    Log.d(TAG, "强制刷新选中项: " + i);
                    notifyItemChanged(i);
                    foundItem = true;
                    break;
                }
            }
            
            if (!foundItem) {
                Log.w(TAG, "未找到选中的壁纸，执行完全刷新");
                notifyDataSetChanged();
            }
        }
    }
    
    /**
     * 获取当前选中的壁纸ID
     */
    public String getSelectedWallpaperId() {
        return selectedWallpaperId;
    }
    
    /**
     * 壁纸项的ViewHolder
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView wallpaperImage;
        TextView wallpaperName;
        FrameLayout checkIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            wallpaperImage = itemView.findViewById(R.id.wallpaper_image);
            wallpaperName = itemView.findViewById(R.id.wallpaper_name);
            checkIndicator = itemView.findViewById(R.id.check_indicator);
            
            // 确保勾选指示器在最上层
            checkIndicator.setElevation(16f);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                checkIndicator.setTranslationZ(8f);
            }
        }
        
        /**
         * 确保勾选指示器可见
         */
        public void ensureCheckIndicatorVisible(boolean visible) {
            if (visible) {
                // 设置为可见
                checkIndicator.setVisibility(View.VISIBLE);
                checkIndicator.setAlpha(1.0f);
                checkIndicator.setScaleX(1.0f);
                checkIndicator.setScaleY(1.0f);
                
                // 确保在最上层
                checkIndicator.bringToFront();
                
                // 强制重绘
                itemView.invalidate();
            } else {
                // 设置为不可见
                checkIndicator.setVisibility(View.GONE);
            }
        }
    }
    
    /**
     * 壁纸点击事件监听器
     */
    public interface OnWallpaperClickListener {
        void onWallpaperClick(Wallpaper wallpaper, int position);
        void onWallpaperPreviewClick(Wallpaper wallpaper, int position);
    }
} 