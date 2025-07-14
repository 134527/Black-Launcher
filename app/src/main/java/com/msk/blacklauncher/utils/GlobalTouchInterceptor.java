package com.msk.blacklauncher.utils;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.msk.blacklauncher.service.IdleModeService;

/**
 * 全局触摸事件拦截器
 * 用于拦截整个应用中的触摸事件，并重置空闲模式计时器
 */
public class GlobalTouchInterceptor {
    
    private static final String TAG = "GlobalTouchInterceptor";

    /**
     * 为Activity设置触摸事件分发监听
     * @param activity 需要监听触摸事件的活动
     */
    public static void setupForActivity(Activity activity) {
        if (activity == null) return;
        
        // 为整个活动的根视图应用触摸监听
        View rootView = activity.getWindow().getDecorView().getRootView();
        applyTouchListenerRecursively(rootView);
        
        Log.d(TAG, "为活动" + activity.getClass().getSimpleName() + "设置了触摸事件监听");
    }
    
    /**
     * 递归地为视图及其所有子视图应用触摸监听器
     * @param view 需要应用监听器的视图
     */
    private static void applyTouchListenerRecursively(View view) {
        if (view == null) return;
        
        // 为当前视图设置触摸监听器
        view.setOnTouchListener((v, event) -> {
            // 触摸事件发生时重置空闲模式计时器
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                IdleModeService.resetIdleTimerOnUserInteraction();
            }
            // 返回false表示不消费事件，允许事件继续传递
            return false;
        });
        
        // 如果是ViewGroup，则递归应用到所有子视图
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                applyTouchListenerRecursively(viewGroup.getChildAt(i));
            }
        }
    }
    
    /**
     * 处理活动的触摸事件
     * 可以在Activity的dispatchTouchEvent方法中调用此方法
     * @param event 触摸事件
     * @return 总是返回false，表示不消费事件
     */
    public static boolean onActivityTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            IdleModeService.resetIdleTimerOnUserInteraction();
            Log.d(TAG, "检测到用户触摸事件，重置空闲模式计时器");
        }
        return false;
    }
} 