package com.msk.blacklauncher.activities;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.adapter.WallpaperAdapter;
import com.msk.blacklauncher.model.Wallpaper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WallpaperChooserActivity extends AppCompatActivity implements WallpaperAdapter.OnWallpaperClickListener {

    private static final String TAG = "WallpaperChooser";
    private static final int REQUEST_CODE_SELECT_WALLPAPER = 1001;
    private static final int REQUEST_CODE_PREVIEW_WALLPAPER = 1002;
    private static final int SPAN_COUNT = 4; // 每行显示4个壁纸
    private static final int ROW_COUNT = 4; // 显示4行
    private static final int ITEM_SPACING = 24; // 用于壁纸项之间的间距，对应24dp
    private static final String PREFS_NAME = "WallpaperPrefs";
    private static final String KEY_SELECTED_WALLPAPER = "selected_wallpaper_id";
    
    private RecyclerView recyclerView;
    private WallpaperAdapter adapter;
    private List<Wallpaper> wallpapers;
    
    // 当前选中的壁纸ID
    private String currentWallpaperId = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置全屏显示
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
                
        // 确保在4K屏幕上也能以全屏方式显示
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        
        setContentView(R.layout.activity_wallpaper_chooser);
        
        // 初始化控件
        recyclerView = findViewById(R.id.wallpapers_recycler_view);
        ImageButton backButton = findViewById(R.id.btn_back);
        LinearLayout titleBar = findViewById(R.id.title_bar);
        
        // 设置返回按钮点击事件
        backButton.setOnClickListener(v -> finish());
        
        // 设置标题栏点击事件，点击也可返回
        titleBar.setOnClickListener(v -> finish());
        
        // 准备壁纸数据
        wallpapers = new ArrayList<>();
        loadWallpapers();
        
        // 获取当前壁纸
        getCurrentWallpaperId();
        
        // 设置RecyclerView
        GridLayoutManager layoutManager = new GridLayoutManager(this, SPAN_COUNT);
        recyclerView.setLayoutManager(layoutManager);
        
        // 调整网格项的大小以匹配4行4列的布局
        adjustItemSize();
        
        // 设置两侧边距
        setSideMargins();
        
        // 应用项目间距（使用装饰器添加间距）
        HorizontalSpaceItemDecoration itemDecoration = new HorizontalSpaceItemDecoration(96); // 两侧边距各96dp
        recyclerView.addItemDecoration(itemDecoration);
        
        // 初始化适配器
        adapter = new WallpaperAdapter(this, wallpapers, this);
        adapter.setSelectedWallpaperId(currentWallpaperId);
        recyclerView.setAdapter(adapter);
        
        // 如果有当前选中的壁纸ID，滚动到该位置
        if (currentWallpaperId != null) {
            for (int i = 0; i < wallpapers.size(); i++) {
                if (wallpapers.get(i).getId().equals(currentWallpaperId)) {
                    final int position = i;
                    recyclerView.post(() -> {
                        recyclerView.scrollToPosition(position);
                        // 延迟一下再次通知适配器，确保选中标记显示
                        new Handler().postDelayed(() -> {
                            adapter.notifyItemChanged(position);
                        }, 200);
                    });
                    break;
                }
            }
        }
        
        Log.d(TAG, "已完成WallpaperChooserActivity初始化");
    }
    
    /**
     * 设置RecyclerView两侧边距
     */
    private void setSideMargins() {
        // 移除XML中设置的内边距
        recyclerView.setPadding(0, 12, 0, 12);
    }
    
    /**
     * 自定义装饰器，为RecyclerView添加左右边距
     */
    public class HorizontalSpaceItemDecoration extends RecyclerView.ItemDecoration {
        private final int horizontalMargin;
        
        public HorizontalSpaceItemDecoration(int horizontalMargin) {
            this.horizontalMargin = horizontalMargin;
        }
        
        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int totalSpanCount = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();
            int column = position % totalSpanCount;
            
            // 计算每个项目的横向间距
            int totalWidth = parent.getWidth();
            int itemWidth = (totalWidth - 2 * horizontalMargin) / totalSpanCount;
            
            // 第一列和最后一列有特殊处理
            if (column == 0) {
                // 第一列：左侧添加边距
                outRect.left = horizontalMargin;
                outRect.right = 0;
            } else if (column == totalSpanCount - 1) {
                // 最后一列：右侧添加边距
                outRect.left = 0;
                outRect.right = horizontalMargin;
            } else {
                // 中间列：不添加边距
                outRect.left = 0;
                outRect.right = 0;
            }
            
            // 添加垂直间距
            outRect.top = 24;
            outRect.bottom = 24;
        }
    }
    
    /**
     * 根据屏幕尺寸调整壁纸项的大小
     */
    private void adjustItemSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        
        // 如果是4K或更高分辨率，调整项目大小
        if (screenWidth >= 3840 || screenHeight >= 2160) {
            // 4K屏幕下的特殊处理，不需要额外内边距，由item自己的margin处理
            recyclerView.setPadding(0, 0, 0, 0);
        } else {
            // 普通分辨率下不使用额外内边距，由item自己的margin处理
            recyclerView.setPadding(0, 0, 0, 0);
        }
        
        Log.d(TAG, "屏幕分辨率: " + screenWidth + "x" + screenHeight);
    }
    
    /**
     * RecyclerView的项目间隔装饰器
     */
    private static class ItemSpacingDecoration extends RecyclerView.ItemDecoration {
        private final int spacing;
        
        public ItemSpacingDecoration(int spacing) {
            this.spacing = spacing;
        }
        
        @Override
        public void getItemOffsets(android.graphics.Rect outRect, View view, 
                                   RecyclerView parent, RecyclerView.State state) {
            // 不添加额外间距，由item自己的margin处理
        }
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // 重新应用全屏设置，确保状态栏不会显示
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
    
    /**
     * 尝试获取当前壁纸ID
     */
    private void getCurrentWallpaperId() {
        try {
            // 从SharedPreferences读取当前壁纸ID
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            currentWallpaperId = prefs.getString(KEY_SELECTED_WALLPAPER, null);
            
            Log.d(TAG, "从SharedPreferences读取当前壁纸ID: " + currentWallpaperId);
            
            // 如果没有保存的壁纸ID，尝试使用第一个壁纸
            if (currentWallpaperId == null && wallpapers != null && !wallpapers.isEmpty()) {
                currentWallpaperId = wallpapers.get(0).getId();
                Log.d(TAG, "没有保存的壁纸ID，使用第一个壁纸: " + currentWallpaperId);
            }
            
            Log.d(TAG, "当前选中的壁纸ID: " + currentWallpaperId);
        } catch (Exception e) {
            Log.e(TAG, "获取当前壁纸失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 加载壁纸数据（从资源和内部存储）
     */
    private void loadWallpapers() {
        // 先加载内置壁纸
        loadBuiltInWallpapers();
        
        // TODO: 可以在这里添加加载用户保存的壁纸的代码
    }

    /**
     * 加载内置壁纸
     */
    private void loadBuiltInWallpapers() {
        try {
            // 从assets/wallpapers目录加载壁纸
            AssetManager assetManager = getAssets();
            String[] files = assetManager.list("wallpapers");
            
            if (files != null) {
                for (String file : files) {
                    // 只处理图片文件
                    if (file.endsWith(".jpg") || file.endsWith(".jpeg") || file.endsWith(".png")) {
                        // 创建Uri
                        String assetPath = "wallpapers/" + file;
                        Uri assetUri = Uri.parse("file:///android_asset/" + assetPath);
                        
                        // 提取名称
                        String name = file.substring(0, file.lastIndexOf("."));
                        name = name.replace("_", " ");
                        
                        // 首字母大写
                        if (name.length() > 0) {
                            name = name.substring(0, 1).toUpperCase() + name.substring(1);
                        }
                        
                        // 生成稳定的壁纸ID，基于文件名而不是随机UUID
                        String stableWallpaperId = "wallpaper_" + file.replace(".", "_");
                        Log.d(TAG, "生成稳定的壁纸ID: " + stableWallpaperId + " 对应文件: " + file);
                        
                        // 创建壁纸对象
                        Wallpaper wallpaper = new Wallpaper(
                                stableWallpaperId,
                                name,
                                assetUri,
                                "内置壁纸",
                                true);
                        // 加载高质量缩略图
                        try (InputStream is = assetManager.open(assetPath)) {
                            // 创建缩略图但提高质量
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inSampleSize = 2; // 缩小比例降至1/2而不是1/4，提高质量
                            options.inPreferQualityOverSpeed = true; // 优先质量
                            
                            Bitmap thumbnail = BitmapFactory.decodeStream(is, null, options);
                            wallpaper.setThumbnail(thumbnail);
                        } catch (IOException e) {
                            Log.e(TAG, "加载壁纸缩略图失败: " + e.getMessage());
                        }
                        
                        // 添加到列表
                        wallpapers.add(wallpaper);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "加载内置壁纸失败: " + e.getMessage());
            Toast.makeText(this, "加载壁纸失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onWallpaperClick(Wallpaper wallpaper, int position) {
        // 直接打开预览页面
        openWallpaperPreview(wallpaper);
    }
    
    @Override
    public void onWallpaperPreviewClick(Wallpaper wallpaper, int position) {
        // 不再需要此方法，移除了预览按钮
        openWallpaperPreview(wallpaper);
    }
    
    /**
     * 打开壁纸预览页面
     */
    private void openWallpaperPreview(Wallpaper wallpaper) {
        Intent intent = new Intent(this, WallpaperPreviewActivity.class);
        intent.putExtra("wallpaper_uri", wallpaper.getImageUri().toString());
        intent.putExtra("wallpaper_name", wallpaper.getName());
        intent.putExtra("wallpaper_id", wallpaper.getId());
        intent.putExtra("is_asset", wallpaper.isAsset());
        startActivityForResult(intent, REQUEST_CODE_PREVIEW_WALLPAPER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.d(TAG, "收到Activity结果: requestCode=" + requestCode + ", resultCode=" + resultCode);
        
        if (requestCode == REQUEST_CODE_PREVIEW_WALLPAPER && resultCode == Activity.RESULT_OK) {
            // 用户设置了壁纸，更新选中状态
            if (data != null && data.hasExtra("wallpaper_id")) {
                String wallpaperId = data.getStringExtra("wallpaper_id");
                boolean isSetSuccess = data.getBooleanExtra("wallpaper_set_success", false);
                
                Log.d(TAG, "从结果中获取壁纸ID: " + wallpaperId + ", 设置成功: " + isSetSuccess);
                
                if (isSetSuccess) {
                    // 更新当前选中的壁纸ID
                    currentWallpaperId = wallpaperId;
                    
                    // 持久化保存选中的壁纸ID
                    android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    android.content.SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(KEY_SELECTED_WALLPAPER, wallpaperId);
                    editor.apply();
                    
                    Log.d(TAG, "已保存选中的壁纸ID: " + wallpaperId);
                    
                    adapter.setSelectedWallpaperId(wallpaperId);
                    Log.d(TAG, "已设置适配器的选中壁纸ID: " + wallpaperId);
                    
                    // 找到对应的壁纸项并滚动到该位置
                    for (int i = 0; i < wallpapers.size(); i++) {
                        if (wallpapers.get(i).getId().equals(wallpaperId)) {
                            // 滚动到该位置并立即更新视图
                            final int position = i;
                            Log.d(TAG, "找到选中的壁纸位置: " + position);
                            
                            recyclerView.smoothScrollToPosition(i);
                            
                            // 确保勾选标记可见
                            recyclerView.post(() -> {
                                Log.d(TAG, "准备更新适配器项目: " + position);
                                // 强制更新该项，显示勾选标记
                                adapter.notifyItemChanged(position);
                                Log.d(TAG, "已更新适配器项目: " + position);
                                
                                // 同时通知整个适配器刷新，确保其他项的勾选标记被清除
                                adapter.notifyDataSetChanged();
                                Log.d(TAG, "已通知适配器数据集变更");
                            });
                            break;
                        }
                    }
                    

                } else {
                    Log.w(TAG, "壁纸设置未成功标记");
                }
            } else {
                Log.w(TAG, "返回的Intent数据无效或不包含wallpaper_id");
            }
            
            // 返回主界面的结果
            setResult(Activity.RESULT_OK);
            // 不要立即关闭，让用户可以看到选中的壁纸
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 应用恢复时，重新检查并应用选中状态
        Log.d(TAG, "onResume: 重新检查选中状态");
        
        // 重新获取当前壁纸ID
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedWallpaperId = prefs.getString(KEY_SELECTED_WALLPAPER, null);
        
        Log.d(TAG, "从SharedPreferences读取壁纸ID: " + savedWallpaperId + ", 当前ID: " + currentWallpaperId);
        
        // 如果有保存的壁纸ID，检查是否需要更新
        if (savedWallpaperId != null) {
            if (!savedWallpaperId.equals(currentWallpaperId)) {
                // 更新当前选中的壁纸ID
                currentWallpaperId = savedWallpaperId;
                adapter.setSelectedWallpaperId(currentWallpaperId);
                Log.d(TAG, "在onResume中更新选中的壁纸ID: " + currentWallpaperId);
            } else {
                // 如果ID相同，仍然刷新适配器以确保显示
                adapter.setSelectedWallpaperId(currentWallpaperId);
                Log.d(TAG, "在onResume中刷新相同的选中壁纸ID: " + currentWallpaperId);
            }
            
            // 滚动到选中的壁纸
            scrollToSelectedWallpaper();
        }
    }
    
    /**
     * 滚动到当前选中的壁纸位置
     */
    private void scrollToSelectedWallpaper() {
        if (currentWallpaperId != null && recyclerView != null) {
            for (int i = 0; i < wallpapers.size(); i++) {
                if (wallpapers.get(i).getId().equals(currentWallpaperId)) {
                    final int position = i;
                    Log.d(TAG, "滚动到选中的壁纸位置: " + position);
                    
                    recyclerView.post(() -> {
                        recyclerView.scrollToPosition(position);
                        // 确保选中状态显示
                        new Handler().postDelayed(() -> {
                            Log.d(TAG, "延迟更新选中项: " + position);
                            adapter.notifyItemChanged(position);
                            // 强制重绘RecyclerView确保UI更新
                            recyclerView.invalidate();
                        }, 200);
                    });
                    break;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: 保存选中状态");
        
        // 确保当前壁纸ID被保存
        if (currentWallpaperId != null) {
            // 保存当前选中的壁纸ID
            android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_SELECTED_WALLPAPER, currentWallpaperId);
            editor.apply();
            
            Log.d(TAG, "已在onDestroy中保存选中的壁纸ID: " + currentWallpaperId);
        }
        
        super.onDestroy();
    }
} 