package com.msk.blacklauncher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class WorkspaceLayout extends ViewGroup {
    private static final int COLUMNS = 4;
    private static final int ROWS = 5;
    private int cellWidth;
    private int cellHeight;
    private boolean isDragging = false;
    private View dragView = null;

    public WorkspaceLayout(Context context) {
        super(context);
        init();
    }

    public WorkspaceLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        cellWidth = width / COLUMNS;
        cellHeight = height / ROWS;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            measureChild(child,
                    MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY));
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();

            int left = lp.x * cellWidth;
            int top = lp.y * cellHeight;
            child.layout(left, top, left + cellWidth, top + cellHeight);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return findDragTarget(ev.getX(), ev.getY()) != null;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private View findDragTarget(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (isViewUnder(child, (int) x, (int) y)) {
                return child;
            }
        }
        return null;
    }

    private boolean isViewUnder(View view, int x, int y) {
        return x >= view.getLeft() && x < view.getRight()
                && y >= view.getTop() && y < view.getBottom();
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int x;
        public int y;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }
    }
}