package com.msk.blacklauncher.activities;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.msk.blacklauncher.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WallpaperPreviewActivity extends AppCompatActivity {

    private static final String TAG = "WallpaperPreview";
    
    private ImageView previewImage;
    private Button setWallpaperButton;
    private Button cancelButton;
    private ImageButton backButton;
    private CardView setWallpaperContainer;
    private CardView cancelContainer;
    
    private Uri imageUri;
    private String wallpaperName;
    private String wallpaperId;
    private boolean isAsset;
    
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 添加与WallpaperChooserActivity相同的常量
    private static final String PREFS_NAME = "WallpaperPrefs";
    private static final String KEY_SELECTED_WALLPAPER = "selected_wallpaper_id";

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
                
        setContentView(R.layout.activity_wallpaper_preview);
        
        // 初始化控件
        previewImage = findViewById(R.id.preview_image);
        setWallpaperButton = findViewById(R.id.btn_set_wallpaper);
        cancelButton = findViewById(R.id.btn_cancel);
        backButton = findViewById(R.id.btn_back_preview);
        setWallpaperContainer = findViewById(R.id.btn_set_wallpaper_container);
        cancelContainer = findViewById(R.id.btn_cancel_container);
        
        // 从Intent中获取壁纸信息
        Intent intent = getIntent();
        if (intent != null) {
            String uriString = intent.getStringExtra("wallpaper_uri");
            wallpaperName = intent.getStringExtra("wallpaper_name");
            wallpaperId = intent.getStringExtra("wallpaper_id");
            isAsset = intent.getBooleanExtra("is_asset", false);
            
            if (uriString != null) {
                imageUri = Uri.parse(uriString);
                loadWallpaperImage();
            }
        }
        
        // 设置按钮点击效果
        setRippleEffect(setWallpaperContainer, setWallpaperButton);
        setRippleEffect(cancelContainer, cancelButton);
        
        // 设置按钮点击事件
        setWallpaperButton.setOnClickListener(v -> setWallpaper());
        cancelButton.setOnClickListener(v -> finish());
        backButton.setOnClickListener(v -> finish());
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
     * 设置卡片和按钮的水波纹效果
     */
    private void setRippleEffect(CardView cardView, Button button) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    cardView.setCardElevation(2f); // 按下时降低高度
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    cardView.setCardElevation(4f); // 抬起时恢复高度
                    break;
            }
            return false;
        });
    }
    
    /**
     * 加载壁纸图片
     */
    private void loadWallpaperImage() {
        if (isAsset) {
            // 如果是Assets中的壁纸
            String assetPath = imageUri.getPath().replace("/android_asset/", "");
            try (InputStream is = getAssets().open(assetPath)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                previewImage.setImageBitmap(bitmap);
            } catch (IOException e) {
                Log.e(TAG, "加载Assets壁纸失败: " + e.getMessage());
                Toast.makeText(this, "加载壁纸失败", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            // 如果是普通URI
            try {
                previewImage.setImageURI(imageUri);
            } catch (Exception e) {
                Log.e(TAG, "加载壁纸失败: " + e.getMessage());
                Toast.makeText(this, "加载壁纸失败", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    /**
     * 设置壁纸
     */
    private void setWallpaper() {
        Log.d(TAG, "开始设置壁纸, wallpaperId=" + wallpaperId);
        
        // 显示设置中提示
        Toast.makeText(this, "正在设置壁纸...", Toast.LENGTH_SHORT).show();
        
        // 禁用按钮，防止重复操作
        setWallpaperButton.setEnabled(false);
        setWallpaperContainer.setEnabled(false); // 禁用容器防止重复点击
        
        // 在后台线程中设置壁纸
        executor.execute(() -> {
            boolean success = false;
            
            try {
                Log.d(TAG, "壁纸设置线程开始执行");
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
                
                if (isAsset) {
                    // 如果是Assets中的壁纸
                    String assetPath = imageUri.getPath().replace("/android_asset/", "");
                    Log.d(TAG, "设置Assets壁纸: " + assetPath);
                    try (InputStream is = getAssets().open(assetPath)) {
                        Bitmap bitmap = BitmapFactory.decodeStream(is);
                        wallpaperManager.setBitmap(bitmap);
                        success = true;
                        Log.d(TAG, "Assets壁纸设置成功");
                    }
                } else {
                    // 如果是普通URI
                    Log.d(TAG, "设置普通URI壁纸: " + imageUri);
                    // 获取图片输入流
                    try (InputStream is = getContentResolver().openInputStream(imageUri)) {
                        wallpaperManager.setStream(is);
                        success = true;
                        Log.d(TAG, "URI壁纸设置成功");
                    }
                }
                
                // 壁纸设置成功
                if (success) {
                    // 立即保存当前壁纸ID到SharedPreferences
                    android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    android.content.SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(KEY_SELECTED_WALLPAPER, wallpaperId);
                    editor.apply();
                    Log.d(TAG, "已保存选中的壁纸ID到SharedPreferences: " + wallpaperId);
                    
                    // 立即设置结果，包含壁纸ID
                    final Intent resultIntent = new Intent();
                    resultIntent.putExtra("wallpaper_id", wallpaperId);
                    resultIntent.putExtra("wallpaper_set_success", true);
                    Log.d(TAG, "准备返回结果: wallpaper_id=" + wallpaperId + ", wallpaper_set_success=true");
                    
                    // 在UI线程中显示成功提示并完成活动
                    handler.post(() -> {
                        setResult(Activity.RESULT_OK, resultIntent);
                        Log.d(TAG, "已设置Activity结果, RESULT_OK");
                        Toast.makeText(this, "壁纸设置成功", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "设置壁纸失败: " + e.getMessage(), e);
                success = false;
                
                // 在UI线程中显示错误提示
                handler.post(() -> {
                    Toast.makeText(this, "设置壁纸失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    setWallpaperButton.setEnabled(true);
                    setWallpaperContainer.setEnabled(true);
                });
            }
        });
    }
} 