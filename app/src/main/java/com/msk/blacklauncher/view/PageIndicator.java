package com.msk.blacklauncher.view;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.msk.blacklauncher.R;

public class PageIndicator extends View {
    private static final String TAG = "PageIndicator";
    private static final int DEFAULT_DOT_RADIUS = 6; // dp
    private static final int DEFAULT_DOT_SPACING = 8; // dp
    private static final int DEFAULT_DOT_MARGIN_START = 16; // dp
    private static final int DEFAULT_SELECTED_COLOR = Color.WHITE;
    private static final int DEFAULT_UNSELECTED_COLOR = Color.parseColor("#50FFFFFF");
    
    private Paint dotPaint;
    private int dotRadius;
    private int dotSpacing;
    private int dotMarginStart;
    private int selectedColor;
    private int unselectedColor;
    private int pageCount = 0;
    private int currentPage = 0;
    private float currentPageOffset = 0;
    private ViewPager2 viewPager;
    
    // 保存每个点的颜色和缩放值
    private float[] dotScales;
    private int[] dotColors;

    public PageIndicator(Context context) {
        this(context, null);
    }
    
    public PageIndicator(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public PageIndicator(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }
    
    private void init(Context context, AttributeSet attrs) {
        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        // 获取自定义属性
        if (attrs != null) {
            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PageIndicator);
            dotRadius = ta.getDimensionPixelSize(R.styleable.PageIndicator_dotRadius, 
                    dpToPx(DEFAULT_DOT_RADIUS));
            dotSpacing = ta.getDimensionPixelSize(R.styleable.PageIndicator_dotSpacing, 
                    dpToPx(DEFAULT_DOT_SPACING));
            dotMarginStart = ta.getDimensionPixelSize(R.styleable.PageIndicator_dotMarginStart,
                    dpToPx(DEFAULT_DOT_MARGIN_START));
            selectedColor = ta.getColor(R.styleable.PageIndicator_selectedColor, 
                    DEFAULT_SELECTED_COLOR);
            unselectedColor = ta.getColor(R.styleable.PageIndicator_unselectedColor, 
                    DEFAULT_UNSELECTED_COLOR);
            ta.recycle();
        } else {
            dotRadius = dpToPx(DEFAULT_DOT_RADIUS);
            dotSpacing = dpToPx(DEFAULT_DOT_SPACING);
            dotMarginStart = dpToPx(DEFAULT_DOT_MARGIN_START);
            selectedColor = DEFAULT_SELECTED_COLOR;
            unselectedColor = DEFAULT_UNSELECTED_COLOR;
        }
        
        Log.d(TAG, "初始化: dotMarginStart=" + dotMarginStart + "px");
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = (pageCount > 0) ? 
                pageCount * (2 * dotRadius) + (pageCount - 1) * dotSpacing : 0;
        int desiredHeight = Math.max(2 * dotRadius, dpToPx(48)); // 确保最小高度
        
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        
        int width;
        int height;
        
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(desiredWidth, widthSize);
        } else {
            width = desiredWidth;
        }
        
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredHeight, heightSize);
        } else {
            height = desiredHeight;
        }
        
        setMeasuredDimension(width, height);
        
        Log.d(TAG, String.format("onMeasure: width=%d, height=%d", width, height));
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (pageCount <= 0) {
            Log.d(TAG, "onDraw: 没有页面需要绘制");
            return;
        }
        
        Log.d(TAG, "onDraw: 绘制 " + pageCount + " 个点, 视图大小: " + getWidth() + "x" + getHeight());
        
        // 计算总宽度
        int totalWidth = pageCount * (2 * dotRadius) + (pageCount - 1) * dotSpacing;
        
        // 使用自定义左边距
        int startX = dotMarginStart;
        
        // 确保垂直居中
        int centerY = getHeight() / 2;
        
        for (int i = 0; i < pageCount; i++) {
            // 计算当前点的x坐标
            int cx = startX + i * (2 * dotRadius + dotSpacing) + dotRadius;
            
            // 获取当前点的颜色和缩放
            int color = (dotColors != null && i < dotColors.length) ? dotColors[i] : unselectedColor;
            float scale = (dotScales != null && i < dotScales.length) ? dotScales[i] : 1.0f;
            
            // 绘制点
            dotPaint.setColor(color);
            canvas.drawCircle(cx, centerY, dotRadius * scale, dotPaint);
            
            Log.d(TAG, String.format("绘制点 %d: 位置(%d, %d), 颜色: %08X, 缩放: %.2f, 左边距: %d", 
                i, cx, centerY, color, scale, dotMarginStart));
        }
    }
    
    public void setPageCount(int count) {
        Log.d(TAG, "setPageCount: " + count);
        if (this.pageCount == count) return;
        
        this.pageCount = count;
        
        // 重新初始化点的颜色和缩放数组
        dotScales = new float[pageCount];
        dotColors = new int[pageCount];
        
        // 初始化默认值
        for (int i = 0; i < pageCount; i++) {
            dotScales[i] = 1.0f;
            dotColors[i] = (i == currentPage) ? selectedColor : unselectedColor;
        }
        
        // 强制重新计算布局大小
        requestLayout();
        invalidate();
    }
    
    public void setCurrentPage(int page) {
        if (page < 0 || page >= pageCount) return;
        
        int oldPage = this.currentPage;
        this.currentPage = page;
        
        // 更新选中点
        updateDotColor(oldPage, page);
    }
    
    public void setPageOffset(float offset) {
        this.currentPageOffset = offset;
        invalidate();
    }

    public void setupWithViewPager(ViewPager2 viewPager) {
        Log.d(TAG, "setupWithViewPager: 开始设置");
        this.viewPager = viewPager;
        
        if (viewPager.getAdapter() == null) {
            Log.e(TAG, "setupWithViewPager: ViewPager2没有设置适配器");
            return;
        }
        
        // 设置页面数量
        int itemCount = viewPager.getAdapter().getItemCount();
        Log.d(TAG, "setupWithViewPager: ViewPager2有 " + itemCount + " 个页面");
        setPageCount(itemCount);
        
        // 注册页面变化回调
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                Log.d(TAG, "onPageSelected: " + position);
                setCurrentPage(position);
            }
            
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // 平滑动画效果
                animateDotTransition(position, positionOffset);
            }
        });
        
        // 设置当前页面
        setCurrentPage(viewPager.getCurrentItem());
        
        // 确保可见性
        setVisibility(VISIBLE);
        
        // 添加整个View的点击事件
        setOnClickListener(v -> {
            // 计算点击位置对应的页面索引
            int touchX = (int) v.getX();
            int dotWidth = 2 * dotRadius + dotSpacing;
            int startX = (getWidth() - (pageCount * dotWidth - dotSpacing)) / 2;
            
            // 计算点击的是哪个点
            int clickedDot = (touchX - startX) / dotWidth;
            if (clickedDot >= 0 && clickedDot < pageCount && viewPager != null) {
                viewPager.setCurrentItem(clickedDot, true);
            }
        });
    }
    
    private void animateDotTransition(int position, float positionOffset) {
        // 只有在有效范围内才处理
        if (position < 0 || position + 1 >= pageCount) return;
        
        // 当前点和下一个点的索引
        int currentIndex = position;
        int nextIndex = position + 1;
        
        // 计算当前点和下一个点的缩放和颜色
        float currentScale = 1.0f + (1.0f - positionOffset) * 0.2f; // 最大放大到1.2倍
        float nextScale = 1.0f + positionOffset * 0.2f;
        
        for (int i = 0; i < pageCount; i++) {
            if (i == currentIndex) {
                dotScales[i] = currentScale;
                dotColors[i] = evaluateColor(positionOffset, selectedColor, unselectedColor);
            } else if (i == nextIndex) {
                dotScales[i] = nextScale;
                dotColors[i] = evaluateColor(positionOffset, unselectedColor, selectedColor);
            } else {
                dotScales[i] = 1.0f;
                dotColors[i] = unselectedColor;
            }
        }
        
        invalidate();
    }
    
    // 更新点的颜色（带动画效果）
    private void updateDotColor(int oldPosition, int newPosition) {
        if (oldPosition < 0 || oldPosition >= pageCount || 
            newPosition < 0 || newPosition >= pageCount) return;
            
        ValueAnimator colorAnimation = ValueAnimator.ofFloat(0, 1);
        colorAnimation.setDuration(300);
        colorAnimation.setInterpolator(new OvershootInterpolator(1.5f));
        
        final int oldColor = dotColors[oldPosition];
        final int newColor = selectedColor;
        final float oldScale = dotScales[oldPosition];
        final float newScale = 1.2f; // 选中点放大到1.2倍
        
        colorAnimation.addUpdateListener(animator -> {
            float fraction = animator.getAnimatedFraction();
            
            // 旧位置点：从选中状态到未选中状态
            dotColors[oldPosition] = evaluateColor(fraction, oldColor, unselectedColor);
            dotScales[oldPosition] = oldScale + (1.0f - oldScale) * fraction;
            
            // 新位置点：从未选中状态到选中状态
            dotColors[newPosition] = evaluateColor(fraction, dotColors[newPosition], newColor);
            dotScales[newPosition] = 1.0f + (newScale - 1.0f) * fraction;
            
            invalidate();
        });
        
        colorAnimation.start();
    }
    
    private int evaluateColor(float fraction, int startColor, int endColor) {
        return (int) new ArgbEvaluator().evaluate(fraction, startColor, endColor);
    }
    
    private int dpToPx(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Log.d(TAG, String.format("onSizeChanged: %dx%d -> %dx%d", oldw, oldh, w, h));
    }
    
    /**
     * 手动设置页面指示器，不依赖ViewPager2
     * @param totalPages 总页数
     * @param currentPage 当前页面索引
     */
    public void setupManually(int totalPages, int currentPage) {
        Log.d(TAG, "setupManually: 总页数=" + totalPages + ", 当前页=" + currentPage);
        this.viewPager = null; // 不依赖ViewPager2
        setPageCount(totalPages);
        setCurrentPage(currentPage);
        
        // 确保可见性
        setVisibility(VISIBLE);
    }
}