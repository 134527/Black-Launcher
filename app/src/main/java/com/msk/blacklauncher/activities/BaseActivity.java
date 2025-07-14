package com.msk.blacklauncher.activities;

import android.os.Bundle;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.msk.blacklauncher.utils.GlobalTouchInterceptor;

/**
 * 基础活动类
 * 所有活动类应继承此类，以便统一处理触摸事件
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 在活动恢复时设置触摸事件监听
        GlobalTouchInterceptor.setupForActivity(this);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 拦截所有触摸事件并重置空闲模式计时器
        GlobalTouchInterceptor.onActivityTouchEvent(ev);
        // 继续正常的事件分发
        return super.dispatchTouchEvent(ev);
    }
} 