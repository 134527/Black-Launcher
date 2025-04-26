package com.msk.blacklauncher.utils;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.msk.blacklauncher.R;

/**
 * 视图相关的工具类
 */
public class ViewUtils {
    private static final String TAG = "ViewUtils";
    
    /**
     * 尝试找到实际的应用图标视图
     * 有时传入的anchor可能是CellLayout或其他容器，需要找到真正的图标视图
     * 
     * @param originalAnchor 原始锚点视图
     * @return 找到的图标视图，如果找不到则返回原始视图
     */
    public static View findActualIconView(View originalAnchor) {
        if (originalAnchor == null) return null;
        
        // 检查是否为容器
        if (originalAnchor instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) originalAnchor;
            
            // 首先尝试查找ImageView，这通常是图标
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child instanceof ImageView) {
                    Log.d(TAG, "找到应用图标: ImageView in " + originalAnchor.getClass().getSimpleName());
                    return child;
                }
            }
            
            // 如果没有直接的ImageView，尝试查找id包含"icon"的视图
            View iconView = container.findViewById(R.id.app_icon_image);
            if (iconView != null) {
                Log.d(TAG, "找到应用图标: app_icon_image");
                return iconView;
            }
            
            // 如果还是找不到，尝试返回第一个子视图
            if (container.getChildCount() > 0) {
                View firstChild = container.getChildAt(0);
                Log.d(TAG, "使用容器的第一个子视图: " + firstChild.getClass().getSimpleName());
                return firstChild;
            }
        }
        
        // 找不到更好的选择，返回原始视图
        Log.d(TAG, "使用原始锚点视图: " + originalAnchor.getClass().getSimpleName());
        return originalAnchor;
    }
    
    /**
     * 获取视图在屏幕上的绝对位置
     * 
     * @param view 要查询的视图
     * @return 包含[x, y]坐标的整数数组
     */
    public static int[] getViewLocationOnScreen(View view) {
        int[] location = new int[2];
        if (view != null) {
            view.getLocationOnScreen(location);
        }
        return location;
    }
    
    /**
     * 获取视图的中心点坐标
     * 
     * @param view 要查询的视图
     * @return 包含中心点[x, y]坐标的整数数组
     */
    public static int[] getViewCenterCoordinates(View view) {
        int[] centerCoord = new int[2];
        if (view != null) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            centerCoord[0] = location[0] + (view.getWidth() / 2);
            centerCoord[1] = location[1] + (view.getHeight() / 2);
        }
        return centerCoord;
    }
    
    /**
     * 判断一个点是否在视图内部
     * 
     * @param view 要检查的视图
     * @param x 点的x坐标
     * @param y 点的y坐标
     * @return 如果点在视图内部则返回true
     */
    public static boolean isPointInsideView(View view, int x, int y) {
        if (view == null) return false;
        
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        
        int viewX = location[0];
        int viewY = location[1];
        
        return (x >= viewX && x <= viewX + view.getWidth() && 
                y >= viewY && y <= viewY + view.getHeight());
    }
} 