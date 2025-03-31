package com.msk.blacklauncher.view;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ScrollView;

/**
 * 卡片触摸拦截器：区分应用图标点击和空白区域点击
 */
public class CardTouchInterceptor extends View {
    private static final String TAG = "CardTouchInterceptor";
    private OnClickListener cardClickListener; // 空白区域点击监听器
    private float downX, downY;
    private boolean isProcessingClick = false;

    public CardTouchInterceptor(Context context) {
        super(context);
    }

    public CardTouchInterceptor(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // 完全拦截所有事件，由onTouchEvent处理
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 记录按下位置
                downX = event.getX();
                downY = event.getY();
                isProcessingClick = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                // 检查是否移动太远（超过10像素），如果是则不算点击
                if (isProcessingClick &&
                        (Math.abs(event.getX() - downX) > 10 || Math.abs(event.getY() - downY) > 10)) {
                    isProcessingClick = false;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!isProcessingClick) return true;

                // 尝试在应用图标上点击
                View clickedIcon = findAppIconAt(downX, downY);

                if (clickedIcon != null) {
                    // 在应用图标上点击，直接触发图标的点击事件
                    Log.d(TAG, "点击在应用图标上 - 启动应用");
                    clickedIcon.performClick();
                } else if (cardClickListener != null) {
                    // 在空白区域点击，打开卡片对话框
                    Log.d(TAG, "点击在空白区域 - 打开对话框");
                    cardClickListener.onClick(this);
                }

                isProcessingClick = false;
                return true;
        }

        return super.onTouchEvent(event);
    }

    /**
     * 查找指定位置的应用图标
     */
    private View findAppIconAt(float x, float y) {
        // 获取父布局
        ViewGroup parent = (ViewGroup) getParent();
        if (parent == null) return null;

        // 查找卡片内的GridLayout
        GridLayout gridLayout = findGridInParent(parent);
        if (gridLayout == null) {
            Log.e(TAG, "找不到GridLayout");
            return null;
        }

        // 转换点击坐标到GridLayout坐标系
        int[] myLoc = new int[2];
        int[] gridLoc = new int[2];

        this.getLocationOnScreen(myLoc);
        gridLayout.getLocationOnScreen(gridLoc);

        // 计算相对于GridLayout的坐标
        float gridX = x + (myLoc[0] - gridLoc[0]);
        float gridY = y + (myLoc[1] - gridLoc[1]);

        // 考虑滚动
        if (findScrollViewParent(gridLayout) != null) {
            ScrollView scrollView = findScrollViewParent(gridLayout);
            gridY += scrollView.getScrollY();
        }

        Log.d(TAG, "转换后坐标: gridX=" + gridX + ", gridY=" + gridY);

        // 遍历GridLayout中的所有子视图，检查点击位置
        for (int i = 0; i < gridLayout.getChildCount(); i++) {
            View child = gridLayout.getChildAt(i);

            // 获取子视图在GridLayout中的边界
            Rect hitRect = new Rect();
            child.getHitRect(hitRect);

            Log.d(TAG, "图标 " + i + " 边界: " + hitRect.toString());

            // 检查点击是否在这个子视图上
            if (hitRect.contains((int)gridX, (int)gridY)) {
                Log.d(TAG, "找到被点击的图标: " + i);
                return child;
            }
        }

        Log.d(TAG, "未找到被点击的图标");
        return null;
    }

    /**
     * 递归查找GridLayout
     */
    private GridLayout findGridInParent(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            if (child instanceof GridLayout) {
                return (GridLayout) child;
            }

            if (child instanceof ScrollView) {
                View scrollContent = ((ScrollView) child).getChildAt(0);
                if (scrollContent instanceof GridLayout) {
                    return (GridLayout) scrollContent;
                } else if (scrollContent instanceof ViewGroup) {
                    GridLayout result = findGridInParent((ViewGroup) scrollContent);
                    if (result != null) return result;
                }
            }

            if (child instanceof ViewGroup && !(child instanceof CardTouchInterceptor)) {
                GridLayout result = findGridInParent((ViewGroup) child);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * 查找包含指定视图的ScrollView
     */
    private ScrollView findScrollViewParent(View view) {
        if (view == null) return null;

        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent == null) return null;

        if (parent instanceof ScrollView) {
            return (ScrollView) parent;
        }

        return findScrollViewParent(parent);
    }

    /**
     * 设置空白区域点击监听器
     */
    @Override
    public void setOnClickListener(OnClickListener l) {
        this.cardClickListener = l;
    }
}