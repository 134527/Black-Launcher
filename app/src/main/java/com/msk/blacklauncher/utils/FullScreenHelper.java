package com.msk.blacklauncher.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;

/**
 * 全屏模式辅助工具
 */
public class FullScreenHelper {

    private static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 0x00001000;
    
    /**
     * 设置活动为全屏模式
     *
     * @param activity 要设置的活动
     */
    public static void setFullScreen(Activity activity) {
        if (activity == null) {
            return;
        }
        
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        
        // 适配不同版本的Android系统
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFullScreenApi30(window);
        } else {
            setFullScreenLegacy(window);
        }
    }
    
    /**
     * 设置对话框为全屏模式
     *
     * @param dialog 要设置的对话框
     */
    public static void setDialogFullScreen(Dialog dialog) {
        if (dialog == null) {
            return;
        }
        
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        
        // 适配不同版本的Android系统
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFullScreenApi30(window);
        } else {
            setFullScreenLegacy(window);
        }
    }
    
    /**
     * 在窗口焦点变化时保持全屏模式
     *
     * @param activity 活动
     * @param hasFocus 是否有焦点
     */
    public static void maintainFullScreen(Activity activity, boolean hasFocus) {
        if (activity == null) {
            return;
        }
        
        if (hasFocus) {
            setFullScreen(activity);
        }
    }
    
    /**
     * 确保PopupWindow显示时也保持全屏模式
     * 
     * @param activity 当前活动
     */
    public static void ensureFullScreenWithPopup(Activity activity) {
        if (activity == null) {
            return;
        }
        
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        
        // 为Window设置一些特殊标志来保持全屏状态
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN 
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        
        // 强制重新应用全屏模式
        setFullScreen(activity);
    }
    
    /**
     * 使用Android 11+的API设置全屏模式
     */
    @TargetApi(Build.VERSION_CODES.R)
    private static void setFullScreenApi30(Window window) {
        // 设置状态栏透明
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        
        // 使用WindowInsetsController控制系统栏
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null) {
            // 隐藏系统栏并改变行为
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
    
    /**
     * 使用旧API设置全屏模式（Android 10及以下）
     */
    private static void setFullScreenLegacy(Window window) {
        View decorView = window.getDecorView();
        
        // 设置状态栏透明
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(Color.TRANSPARENT);
        
        // 使用系统UI标志设置全屏
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        
        decorView.setSystemUiVisibility(flags);
    }
    
    /**
     * 重置所有视图的布局参数，以避免PopupWindow显示后UI混乱
     * 
     * @param rootView 根视图
     */
    public static void resetLayoutParamsAfterPopup(View rootView) {
        if (rootView == null) {
            return;
        }
        
        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                child.requestLayout();
                resetLayoutParamsAfterPopup(child);
            }
        }
    }

    /**
     * 设置活动为沉浸式粘性模式
     * @param activity 要设置的活动
     */
    public static void setImmersiveSticky(Activity activity) {
        if (activity == null) return;

        Window window = activity.getWindow();
        if (window == null) return;

        // 适用于API 19及以上版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

            window.getDecorView().setSystemUiVisibility(flags);

            // 适用于API 30及以上版本，添加额外控制
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
                window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 适用于API 21-29，设置透明状态栏和导航栏
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
                window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            }
        }
    }

    /**
     * 为FragmentActivity设置沉浸式粘性模式
     * @param activity 要设置的FragmentActivity
     */
    public static void setImmersiveSticky(FragmentActivity activity) {
        setImmersiveSticky((Activity) activity);
    }

    /**
     * 将对话框设置为全屏模式
     * @param dialog 要设置的对话框
     */
    public static void setFullScreenDialog(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;

        Window window = dialog.getWindow();

        // 设置全屏布局
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);

        // 适用于API 19及以上版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

            window.getDecorView().setSystemUiVisibility(flags);

            // 适用于API 30及以上版本
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
            }
        }
    }
}