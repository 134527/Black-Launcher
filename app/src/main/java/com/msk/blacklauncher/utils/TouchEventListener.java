package com.msk.blacklauncher.utils;

import android.view.MotionEvent;
import android.view.View;

import com.msk.blacklauncher.service.IdleModeService;

/**
 * 触摸事件监听器
 * 用于在用户触摸屏幕时重置空闲模式计时器
 */
public class TouchEventListener implements View.OnTouchListener {

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // 在任何触摸事件发生时重置空闲模式计时器
        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE ||
                event.getAction() == MotionEvent.ACTION_UP) {
            IdleModeService.resetIdleTimerOnUserInteraction();
        }
        
        // 返回false表示不消费此事件，让事件继续传递
        return false;
    }
    
    /**
     * 应用触摸监听器到给定视图
     * @param view 需要监听触摸事件的视图
     */
    public static void applyTo(View view) {
        if (view != null) {
            view.setOnTouchListener(new TouchEventListener());
        }
    }
} 