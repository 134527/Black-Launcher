package com.msk.blacklauncher.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.adapters.ScreensaverAdapter;

import java.util.ArrayList;
import java.util.List;

public class ScreensaverActivity extends Activity implements ScreensaverAdapter.OnScreensaverSelectedListener {
    
    private static final String TAG = "ScreensaverActivity";
    private ComponentName selectedScreensaver;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screensaver);
        
        RecyclerView recyclerView = findViewById(R.id.screensaver_recycler_view);
        Button activateButton = findViewById(R.id.btn_activate_screensaver);
        
        // 设置网格布局
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        
        // 获取所有可用的屏保
        List<ResolveInfo> screensavers = getAvailableScreensavers();
        
        if (screensavers.isEmpty()) {
            Toast.makeText(this, "没有找到可用的屏保", Toast.LENGTH_SHORT).show();
        }
        
        // 设置适配器
        ScreensaverAdapter adapter = new ScreensaverAdapter(this, screensavers, this);
        recyclerView.setAdapter(adapter);
        
        // 设置激活按钮点击事件
        activateButton.setOnClickListener(v -> {
            if (selectedScreensaver != null) {
                activateScreensaver();
            } else {
                Toast.makeText(this, "请先选择一个屏保", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private List<ResolveInfo> getAvailableScreensavers() {
        PackageManager pm = getPackageManager();
        Intent dreamIntent = new Intent(DreamService.SERVICE_INTERFACE);
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(dreamIntent, PackageManager.GET_META_DATA);
        
        Log.d(TAG, "找到 " + resolveInfos.size() + " 个可用屏保");
        return resolveInfos;
    }

    @Override
    public void onScreensaverSelected(ComponentName componentName) {
        selectedScreensaver = componentName;
        Log.d(TAG, "已选择屏保: " + componentName.flattenToString());

        // 不直接修改系统设置，而是引导用户到系统设置页面
        try {
            // 保存选择但不直接应用设置
            Toast.makeText(this, "已选择屏保: " + componentName.getShortClassName(), Toast.LENGTH_SHORT).show();

            // 可以保存用户的选择到应用内部存储
            getSharedPreferences("screensaver_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("selected_screensaver", componentName.flattenToString())
                    .apply();

        } catch (Exception e) {
            Log.e(TAG, "保存屏保选择失败", e);
            Toast.makeText(this, "保存选择失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void activateScreensaver() {
        if (selectedScreensaver != null) {
            try {
                // 引导用户到系统屏保设置页面
                Intent intent = new Intent(Settings.ACTION_DREAM_SETTINGS);
                startActivity(intent);

                // 提示用户如何设置
                Toast.makeText(this, "请在系统设置中选择刚才选择的屏保", Toast.LENGTH_LONG).show();

            } catch (Exception e) {
                Log.e(TAG, "启动屏保设置失败", e);
                Toast.makeText(this, "无法打开屏保设置: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
} 