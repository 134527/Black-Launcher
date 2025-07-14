package com.msk.blacklauncher.view;


import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.BlurMaskFilter;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import androidx.core.view.ViewCompat;
import androidx.viewpager2.widget.ViewPager2;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.os.Process;
import android.view.Menu;
import android.widget.PopupMenu;
import android.content.pm.ApplicationInfo;
import android.provider.Settings;
import android.net.Uri;
import android.content.Intent;
import android.os.Build;
import android.graphics.drawable.Drawable;
import android.content.pm.PackageManager;
import android.widget.Toast;
import android.view.HapticFeedbackConstants;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.view.Gravity;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.os.Handler;
import android.os.Looper;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.view.ShortcutMenuView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 * Created by chenqiao on 2016/10/26.
 */
public class CellLayout extends ViewGroup implements View.OnDragListener {

    private static final String TAG = "CellLayout";
    private int columns = 9;
    private int rows = 4;
    private int per_cell_width;
    private int per_cell_height;
    private ArrayList<Cell> cells;
    private ArrayList<Cell> needRemoveCells;
    private boolean[][] cellHolds;
    private int highLightColumn = -1;
    private int highLightRow = -1;
    private boolean highLightValid = false;
    private float lastDragX = -1;
    private float lastDragY = -1;
    private boolean isDragging = false;
    private long lastDragEventTime = 0;
    private static final long DRAG_FEEDBACK_INTERVAL = 200; // ms
    
    // 添加拖拽到边缘时创建页面的节流控制
    private static final long EDGE_PAGE_CREATION_THROTTLE = 1200; // ms，减少等待时间
    private long lastEdgePageCreationAttemptTime = 0;
    
    // 添加防止连续创建页面的时间间隔
    private static final long PAGE_CREATION_INTERVAL = 2500; // 减少到2.5秒防止创建多个页面
    private long lastPageCreationTime = 0;
    
    // 添加变量追踪最后一次页面切换时间
    private long lastPageSwitchTime = 0;
    private static final long PAGE_SWITCH_INTERVAL = 800; // 页面切换间隔
    
    // 悬停预览相关变量
    private int lastHoverColumn = -1;
    private int lastHoverRow = -1;
    private long hoverStartTime = 0;
    private static final long HOVER_PREVIEW_DELAY = 300; // 悬停多久后显示预览效果(毫秒)
    private boolean isPreviewActive = false;
    private ArrayList<View> previewAnimatedViews = new ArrayList<>();
    
    // 缓存当前拖拽的单元格
    private Cell currentDragCell = null;
    private int dragSourceIndex = -1;

    // 缓存对象，避免频繁创建
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF highlightRect = new RectF();
    
    // 动态高亮效果相关
    private float highlightAlpha = 0f;
    private float targetHighlightAlpha = 0f;
    private long lastHighlightUpdateTime = 0;
    private static final long HIGHLIGHT_ANIMATION_DURATION = 150; // ms
    
    // 触摸反馈节流控制
    private static final long EDGE_FEEDBACK_THROTTLE = 800; // ms
    private long lastEdgeFeedbackTime = 0;

    private PopupMenu currentPopupMenu; // 当前弹出的快捷菜单

    // 添加静态变量用于标记我们是否正在拖拽中
    private static boolean isInDragging = false;

    public CellLayout(Context context) {
        this(context, null);
    }

    public CellLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CellLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        cells = new ArrayList<>();
        cellHolds = new boolean[rows][columns];
        needRemoveCells = new ArrayList<>();
        setOnDragListener(this);
        // 启用硬件加速
        setLayerType(LAYER_TYPE_HARDWARE, null);
        // 初始化画笔
        initPaints();
        // 启用绘制
        setWillNotDraw(false);
    }

    private void initPaints() {
        // 高亮背景画笔
        highlightPaint.setColor(Color.argb(70, 180, 210, 255));
        highlightPaint.setStyle(Paint.Style.FILL);
        
        // 边框画笔
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        
        // 发光效果画笔
        glowPaint.setColor(Color.argb(180, 150, 200, 255));
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(6);
        // 使用硬件加速兼容的模糊效果
        glowPaint.setMaskFilter(new BlurMaskFilter(15, BlurMaskFilter.Blur.OUTER));
    }

    // 在 CellLayout 中添加设置单元格可拖拽的方法
    private void setupCellForDrag(Cell cell)  {
        if (cell == null || cell.getContentView() == null) return;

        View contentView = cell.getContentView();

        // 设置长按启动快捷方式或应用选项菜单
        contentView.setOnLongClickListener(v -> {
            // 立即显示快捷菜单
            showAppShortcuts(v, cell.getTag());
            return true;
        });
        
        // 添加触摸监听，用于检测长按后的移动
        contentView.setOnTouchListener((v, event) -> {
            // 如果菜单正在显示，并且用户开始移动
            if (currentPopupMenu != null && event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                // 计算移动距离
                float moveDistance = (float) Math.sqrt(
                    Math.pow(event.getX() - v.getWidth()/2f, 2) + 
                    Math.pow(event.getY() - v.getHeight()/2f, 2));
                    
                // 如果移动距离超过阈值，取消菜单并开始拖拽
                if (moveDistance > v.getWidth() * 0.15f) {
                    // 取消当前菜单
                    if (currentPopupMenu != null) {
                        currentPopupMenu.dismiss();
                        currentPopupMenu = null;
                    }
                    
                    // 关闭所有ShortcutMenuView活跃菜单
                    ShortcutMenuView.dismissActiveMenus();
                    
                    // 开始拖拽
                    isInDragging = true;
                    startDragFromCell(cell);
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 不再在onDraw中绘制高亮效果，已移至dispatchDraw方法
    }

    /**
     * 清除所有单元格但保持布局结构
     */
    public void clearCells() {
        // 清除所有视图
        removeAllViews();

        // 清除单元格列表但不破坏结构
        if (cells != null) {
            cells.clear();
        } else {
            cells = new ArrayList<>();
        }

        // 重置单元格占用状态
        cellHolds = new boolean[rows][columns];

        Log.d(TAG, "已清除所有单元格");
    }

    /**
     * 获取所有单元格
     */
    public List<Cell> getCells() {
        return cells;
    }

    public void addCell(Cell cell) {
        if (cell == null) {
            Log.e(TAG, "尝试添加空单元格");
            return;
        }

        // 记录单元格
        cells.add(cell);

        // 对非空单元格，添加视图并设置拖拽
        if (cell.getContentView() != null) {
            View contentView = cell.getContentView();

            // 从父视图中移除(如果有)
            if (contentView.getParent() != null) {
                ((ViewGroup)contentView.getParent()).removeView(contentView);
            }

            // 设置视图可见性
            boolean isEmpty = "empty".equals(cell.getTag());
            contentView.setVisibility(isEmpty ? View.INVISIBLE : View.VISIBLE);

            // 设置布局参数
            contentView.setLayoutParams(new LayoutParams(
                    per_cell_width, per_cell_height
            ));

            // 添加到布局
            addView(contentView);

            // 只为非空单元格设置拖拽
            if (!isEmpty) {
                setupCellForDrag(cell);
            }
        }
    }

    int childWidthSpec, childHeightSpec, childExpectCellWidthNum, childExpectCellHeightNum;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        removeAllViews();
        per_cell_width = getMeasuredWidth() / columns;
        per_cell_height = getMeasuredHeight() / rows;
        // 获取到getMeasuredWidth后，进行一次cell的测量
        for (Cell cell : cells) {
            initCell(cell);
        }
    }

    private void initCell(Cell cell) {
        View child = cell.getContentView();
        
        // 检查视图是否为null
        if (child == null) {
            // 如果内容视图为空，不进行处理
            return;
        }

        // 检查视图是否已有父视图
        ViewParent parent = child.getParent();
        if (parent != null && parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(child);
        }

        // 现在安全地添加视图
        addView(child);

        childWidthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST);
        childHeightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST);
        measureChild(child, childWidthSpec, childHeightSpec);

        //计算出cell要占据几格
        childExpectCellWidthNum = (int) Math.ceil(child.getMeasuredWidth() / (per_cell_width * 1.0f));
        childExpectCellHeightNum = (int) Math.ceil(child.getMeasuredHeight() / (per_cell_height * 1.0f));
        cell.setWidthNum(childExpectCellWidthNum);
        cell.setHeightNum(childExpectCellHeightNum);
    }

     @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;

        // 计算单元格尺寸
        per_cell_width = width / columns;
        per_cell_height = height / rows;

        Log.d("CellLayout", "计算单元格尺寸: 宽度=" + per_cell_width + ", 高度=" + per_cell_height);

        // 检测尺寸是否合理
        if (per_cell_width <= 0 || per_cell_height <= 0) {
            Log.e("CellLayout", "单元格尺寸计算错误，使用默认值");
            per_cell_width = Math.max(100, width / columns);
            per_cell_height = Math.max(100, height / rows);
        }

        // 布局子视图
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);

            // 获取单元格位置
            int cellIndex = -1;
            for (int j = 0; j < cells.size(); j++) {
                if (cells.get(j) != null && cells.get(j).getContentView() == child) {
                    cellIndex = j;
                    break;
                }
            }

            if (cellIndex != -1) {
                // 计算行列位置
                int row = cellIndex / columns;
                int col = cellIndex % columns;

                // 计算实际位置
                int cellLeft = col * per_cell_width;
                int cellTop = row * per_cell_height;
                int cellRight = cellLeft + per_cell_width;
                int cellBottom = cellTop + per_cell_height;

                // 布局子视图
                child.layout(cellLeft, cellTop, cellRight, cellBottom);
            }
        }
    }
    private Point findLeftAndTop(Cell cell) {
        Point result = new Point(-1, -1);
        boolean isEnough;
        for (int row = 0; row <= rows - cell.getHeightNum(); row++) {
            for (int column = 0; column <= columns - cell.getWidthNum(); column++) {
                isEnough = checkIsEnough(cellHolds, column, row, cell.getWidthNum(), cell.getHeightNum());
                if (isEnough) {
                    fillCellLayout(column, row, cell.getWidthNum(), cell.getHeightNum());
                    result.set(row, column);
                    return result;
                }
            }
        }
        return result;
    }

    private boolean checkIsEnough(boolean[][] myCellHolds, int startX, int startY, int width, int height) {
        if (startX < 0 || startY < 0 ||
                startX + width > columns || startY + height > rows) {
            return false;
        }
        for (int i = startX; i < startX + width; i++) {
            for (int j = startY; j < startY + height; j++) {
                if (myCellHolds[j][i]) {
                    return false;
                }
            }
        }
        return true;
    }

    public void setColumns(int columns) {
        this.columns = columns;
        initCellHolds();
        requestLayout();
    }

    public void setRows(int rows) {
        this.rows = rows;
        initCellHolds();
        requestLayout();
    }
    private void initCellHolds() {
        cellHolds = new boolean[rows][columns];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                cellHolds[i][j] = false;
            }
        }
    }
    private void fillCellLayout(int startX, int startY, int width, int height) {
        if (startX + width > columns || startY + height > rows) {
            return;
        }
        for (int i = startX; i < startX + width; i++) {
            for (int j = startY; j < startY + height; j++) {
                cellHolds[j][i] = true;
            }
        }
    }

    private boolean[][] tempCellHolds;


    @Override
    public boolean onDrag(View v, DragEvent event) {
        try {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // 隐藏正在显示的快捷菜单
                    if (currentPopupMenu != null) {
                        currentPopupMenu.dismiss();
                        currentPopupMenu = null;
                    }
                    
                    // 关闭所有ShortcutMenuView活跃菜单
                    ShortcutMenuView.dismissActiveMenus();
                    
                    // 拖动开始
                    isDragging = true;
                    isInDragging = true;
                    lastDragX = event.getX();
                    lastDragY = event.getY();
                    
                    // 获取并缓存当前拖拽的单元格
                    Object localState = event.getLocalState();
                    if (localState instanceof Cell) {
                        currentDragCell = (Cell) localState;
                        // 查找拖拽源单元格的索引
                        for (int i = 0; i < cells.size(); i++) {
                            if (cells.get(i) == currentDragCell || 
                                (cells.get(i).getTag() != null && 
                                 currentDragCell.getTag() != null &&
                                 cells.get(i).getTag().equals(currentDragCell.getTag()))) {
                                dragSourceIndex = i;
                                break;
                            }
                        }
                    }
                    
                    // 完全重置和禁用高亮状态
                    highLightColumn = -1;
                    highLightRow = -1;
                    highlightAlpha = 0f;
                    highLightValid = false; // 禁用高亮
                    lastHighlightUpdateTime = System.currentTimeMillis();
                    
                    // 立即强制重绘以清除任何高亮框
                    invalidate();
                    
                    // 重置悬停预览状态
                    lastHoverColumn = -1;
                    lastHoverRow = -1;
                    hoverStartTime = 0;
                    isPreviewActive = false;
                    cancelPreviewAnimations();
                    

                    
                    Log.d(TAG, "拖动开始 - X: " + lastDragX + ", Y: " + lastDragY);
                    return true;

                case DragEvent.ACTION_DRAG_ENTERED:
                    // 拖动进入视图区域
                    Log.d(TAG, "拖动进入视图区域");
                    
                    // 播放页面进入动画
                    playPageEnterAnimation(v);
                    
                    return true;

                case DragEvent.ACTION_DRAG_LOCATION:
                    // 拖动定位 - 处理高亮和边缘滚动
                    
                    float x = event.getX();
                    float y = event.getY();
                    
                    // 记录最后的拖动位置
                    lastDragX = x;
                    lastDragY = y;
                    
                    // 计算当前高亮位置
                    int highlightCol = Math.min(columns - 1, Math.max(0, (int)(x / per_cell_width)));
                    int highlightRow = Math.min(rows - 1, Math.max(0, (int)(y / per_cell_height)));
                    
                    // 在拖拽状态下，只更新位置但强制禁用高亮
                    highLightColumn = highlightCol;
                    highLightRow = highlightRow;
                    // 确保拖拽状态下高亮始终无效
                    highLightValid = false;
                    
                    // 记录当前悬停的位置（用于DROP时交换图标）
                    if (highlightCol != lastHoverColumn || highlightRow != lastHoverRow) {
                        // 取消之前的预览效果，确保图标恢复可见
                        if (isPreviewActive) {
                            // 保存是否需要恢复图标可见性
                            final boolean needRestoreVisibility = isPreviewActive;
                            // 取消预览动画
                            cancelPreviewAnimations();
                            isPreviewActive = false;
                            
                            // 确保取消预览后图标恢复可见状态
                            if (needRestoreVisibility && cells != null) {
                                for (Cell cell : cells) {
                                    if (cell != null && cell.getContentView() != null && 
                                        !cell.getTag().equals("empty")) {
                                        
                                        View iconView = cell.getContentView();
                                        iconView.setVisibility(View.VISIBLE);
                                        iconView.setAlpha(1.0f);
                                        iconView.setScaleX(1.0f);
                                        iconView.setScaleY(1.0f);
                                        iconView.setTranslationX(0);
                                        iconView.setTranslationY(0);
                                    }
                                }
                            }
                        }
                        
                        // 关闭所有ShortcutMenuView活跃菜单
                        ShortcutMenuView.dismissActiveMenus();
                        
                        // 记录新的悬停位置
                        lastHoverColumn = highlightCol;
                        lastHoverRow = highlightRow;
                    }
                    
                    // 检测是否靠近屏幕边缘 - 优化的边缘检测逻辑
                    int edgeSize = (int)(Math.min(per_cell_width, per_cell_height) * 0.7f); // 减小边缘区域大小，提高灵敏度
                    boolean isNearLeftEdge = x < edgeSize;
                    boolean isNearRightEdge = x > getWidth() - edgeSize;
                    
                    // 获取当前时间，用于节流控制
                    long currentTime = System.currentTimeMillis();
                    
                    // 获取当前页面索引
                    int currentPage = -1;
                    ViewPager2 viewPager = getMainActivityViewPager();
                    if (viewPager != null) {
                        currentPage = viewPager.getCurrentItem();
                    }
                    
                    // 检查当前页面是否已满，如果已满则降低创建新页面的门槛
                    boolean isCurrentPageFull = isPageFull();
                    if (isCurrentPageFull) {
                        // 如果当前页面已满，减小边缘区域大小使创建更容易触发
                        edgeSize = (int)(Math.min(per_cell_width, per_cell_height) * 0.9f);
                        isNearRightEdge = x > getWidth() - edgeSize;
                    }
                    
                    // 增加对左侧边缘检测的响应度
                    if (currentPage > 0) {
                        // 如果不是第一页，减小左侧边缘区域大小提高灵敏度
                        int leftEdgeSize = (int)(Math.min(per_cell_width, per_cell_height) * 0.6f);
                        isNearLeftEdge = x < leftEdgeSize;
                    }
                    
                    // 检测边缘滑动逻辑
                    if (isNearLeftEdge && currentPage > 0) {
                        // 处理左侧边缘检测 - 滑动到前一个页面
                        float leftEdgeProgress = 1.0f - (x / edgeSize); // 值从0到1
                        leftEdgeProgress = Math.min(1.0f, Math.max(0.0f, leftEdgeProgress));
                        
                        // 检查时间间隔以避免频繁切换
                        boolean canSwitchPage = (currentTime - lastPageSwitchTime) > PAGE_SWITCH_INTERVAL;
                        

                        
                        if (canSwitchPage && leftEdgeProgress > 0.7f) {
                            // 切换到前一个页面
                            lastPageSwitchTime = currentTime;
                            
                            if (viewPager != null) {
                                viewPager.setCurrentItem(currentPage - 1, true);
                                Log.d(TAG, "拖拽到左侧边缘，切换到页面: " + (currentPage - 1));
                                
                                // 拖拽的Cell也应该移动到前一页
                                if (event.getLocalState() instanceof Cell) {
                                    Cell dragCell = (Cell) event.getLocalState();
                                    String packageName = dragCell.getTag();
                                    
                                    if (packageName != null && !packageName.contains(":cross_page")) {
                                        // 标记为跨页面拖拽，并记录源页面
                                        dragCell.setTag(packageName + ":cross_page:" + currentPage + ":" + currentTime);
                                        Log.d(TAG, "标记跨页面拖拽到前一页: " + dragCell.getTag());
                                        
                                        // 保存原始页面信息
                                        dragCell.setExpectColumnIndex(0);
                                        dragCell.setExpectRowIndex(currentPage);
                                    }
                                }
                            }
                        }
                    }
                    // 检测是否靠近屏幕右边缘 - 改进边缘创建页面逻辑
                    else if (isNearRightEdge && cellOverflowListener != null) {
                        // 根据拖动位置在边缘区域的深入程度计算创建页面的紧急度
                        float edgeProgress = (x - (getWidth() - edgeSize)) / edgeSize; // 值从0到1
                        edgeProgress = Math.min(1.0f, Math.max(0.0f, edgeProgress));
                        
                        // 根据紧急度和页面是否已满动态调整节流时间
                        float throttleMultiplier = isCurrentPageFull ? 0.5f : (1.0f - edgeProgress * 0.7f);
                        long throttleTime = (long)(EDGE_PAGE_CREATION_THROTTLE * throttleMultiplier);
                        
                        // 降低节流时间，使响应更快
                        throttleTime = Math.min(throttleTime, isCurrentPageFull ? 500 : 800); // 当页面已满时更快响应
                        
                        // 判断是否可以尝试创建新页面（根据紧急度动态节流控制）
                        boolean canCreatePage = (currentTime - lastEdgePageCreationAttemptTime) > throttleTime;
                        
                        // 添加防止连续创建页面的判断
                        boolean notRecentlyCreatedPage = (currentTime - lastPageCreationTime) > PAGE_CREATION_INTERVAL;
                        
                        // 确保页面切换间隔足够
                        boolean canSwitchPage = (currentTime - lastPageSwitchTime) > PAGE_SWITCH_INTERVAL;
                        

                        
                        if (canCreatePage && notRecentlyCreatedPage && canSwitchPage) {
                            // 更新尝试创建页面的时间
                            lastEdgePageCreationAttemptTime = currentTime;
                            lastPageSwitchTime = currentTime;
                            
                            // 获取MainActivity中的ViewPager2
                            ViewPager2 viewPager2 = getMainActivityViewPager();
                            int currentPage2 = -1;
                            
                            if (viewPager2 != null) {
                                currentPage = viewPager2.getCurrentItem();
                                int pageCount = cellOverflowListener.getPageCount();
                                
                                // 检查是否允许创建新页面或者已经存在下一页
                                if (cellOverflowListener.canCreateNewPage() || (currentPage < pageCount - 1) || isCurrentPageFull) {
                                    // 获取当前拖拽的单元格
                                    localState = event.getLocalState();
                                    
                                    // 震动反馈 - 根据页面是否已满调整震动强度
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        try {
                                            VibrationEffect effect;
                                            if (isCurrentPageFull) {
                                                // 页面已满时使用更强烈的震动反馈
                                                effect = VibrationEffect.createOneShot(
                                                        50, // 稍长的振动
                                                        VibrationEffect.DEFAULT_AMPLITUDE
                                                );
                                            } else {
                                                effect = VibrationEffect.createOneShot(
                                                        40, // 标准振动
                                                        VibrationEffect.DEFAULT_AMPLITUDE
                                                );
                                            }
                                            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                                            if (vibrator != null) {
                                                vibrator.vibrate(effect);
                                            }
                                        } catch (Exception e) {
                                            Log.e(TAG, "震动反馈错误", e);
                                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                                        }
                                    } else {
                                        // 旧版本使用标准触感反馈
                                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                                    }
                                    
                                    // 显示创建页面的动画提示
                                    showPageCreationFeedback();
                                    
                                    int targetPageIndex;
                                    boolean isNewlyCreatedPage = false;
                                    
                                    // 判断是创建新页面还是使用已存在的下一页
                                    if (currentPage < pageCount - 1) {
                                        // 如果不是最后一页，直接使用下一页
                                        targetPageIndex = currentPage + 1;
                                        Log.d(TAG, "检测到已存在下一页，准备移动到页面 " + targetPageIndex);
                                    } else {
                                        // 如果是最后一页，创建新页面
                                        targetPageIndex = cellOverflowListener.createNewPage();
                                        isNewlyCreatedPage = true;
                                        Log.d(TAG, "创建新页面，索引为 " + targetPageIndex);
                                        
                                        // 更新最后创建页面的时间
                                        lastPageCreationTime = currentTime;
                                    }
                                    
                                    if (localState instanceof Cell) {
                                        // 为当前拖拽的单元格添加跨页标记
                                        Cell dragCell = (Cell) localState;
                                        String packageName = dragCell.getTag();
                                        
                                        // 标记为跨页面拖拽，并记录源页面
                                        if (packageName != null && !packageName.contains(":cross_page")) {
                                            // 添加当前时间戳防止重复创建
                                            dragCell.setTag(packageName + ":cross_page:" + currentPage + ":" + currentTime);
                                            Log.d(TAG, "标记跨页面拖拽: " + dragCell.getTag());
                                            
                                            // 如果当前拖动的视图为隐藏状态，确保它在目标页面可见
                                            if (dragCell.getContentView() != null) {
                                                dragCell.getContentView().setVisibility(View.VISIBLE);
                                                dragCell.getContentView().setAlpha(1.0f);
                                            }
                                            
                                            // 保存原始页面，便于拖放失败时返回
                                            dragCell.setExpectColumnIndex(0); // 保存当前页面索引到列索引
                                            dragCell.setExpectRowIndex(currentPage); // 保存当前页面索引到行索引
                                        }
                                    }
                                    
                                    // 平滑滚动到目标页面
                                    if (viewPager2 != null) {
                                        final boolean finalIsNewlyCreatedPage = isNewlyCreatedPage;
                                        
                                        // 使用更平滑的动画过渡到目标页面
                                        viewPager2.setCurrentItem(targetPageIndex, true);
                                        
                                        // 提供拖拽反馈
                                        if (localState instanceof Cell) {
                                            // 确保用户可以通过返回按钮返回到原始页面
                                            View rootView = viewPager2.getRootView();
                                            if (rootView.getContext() instanceof Activity) {
                                                Activity activity = (Activity) rootView.getContext();
                                                try {
                                                    // 根据页面是否已满显示不同提示
                                                    String toastMsg = isCurrentPageFull ? 
                                                        "当前页面已满，已自动创建新页面" : 
                                                        "您可以点击返回按钮回到原页面";
                                                    Toast.makeText(activity, toastMsg, Toast.LENGTH_SHORT).show();
                                                } catch (Exception e) {
                                                    Log.e(TAG, "显示返回提示失败", e);
                                                }
                                            }
                                        }
                                        
                                        // 通知WorkspaceFragment确保目标页面已设置
                                        if (cellOverflowListener != null) {
                                            // 延迟一小段时间确保页面已创建
                                            ViewPager2 finalViewPager = viewPager;
                                            int finalTargetPageIndex = targetPageIndex;
                                            int finalCurrentPage = currentPage;
                                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                                // 设置目标页面
                                                cellOverflowListener.setupWorkspacePage(finalTargetPageIndex);
                                                
                                                // 确保其他应用图标可以拖到目标页面中
                                                View targetPageView = getViewForPage(finalViewPager, finalTargetPageIndex);
                                                if (targetPageView != null) {
                                                    // 查找目标页面上的CellLayout
                                                    // 首先尝试使用R.id.workspace_grid
                                                    CellLayout targetPageCellLayout = targetPageView.findViewById(R.id.workspace_grid);
                                                    
                                                    // 如果找不到，尝试通用查找方法
                                                    if (targetPageCellLayout == null) {
                                                        targetPageCellLayout = findCellLayoutInViewGroup(targetPageView);
                                                    }
                                                    
                                                    if (targetPageCellLayout != null) {
                                                        // 确保目标页面的CellLayout也具有相同的监听器和配置
                                                        targetPageCellLayout.setOnCellOverflowListener(cellOverflowListener);
                                                        targetPageCellLayout.setOnDragListener(targetPageCellLayout);
                                                        targetPageCellLayout.setRows(rows);
                                                        targetPageCellLayout.setColumns(columns);
                                                        
                                                        // 如果是新创建的页面，需要填充空白单元格
                                                        if (finalIsNewlyCreatedPage) {
                                                            // 填充空白单元格
                                                            targetPageCellLayout.fillEmptyCells();
                                                            
                                                            // 重新加载单元格
                                                            List<CellLayout.Cell> pageCells = cellOverflowListener.getPageCells(finalTargetPageIndex);
                                                            if (pageCells != null) {
                                                                for (Cell cell : pageCells) {
                                                                    targetPageCellLayout.addCell(cell);
                                                                }
                                                            }
                                                            
                                                            // 如果是由于页面已满自动创建的，需要适当安排图标位置
                                                            if (isCurrentPageFull && localState instanceof Cell) {
                                                                // 尝试找到合适的空白位置
                                                                targetPageCellLayout.reservePositionForDragCell((Cell)localState);
                                                            }
                                                        }
                                                        
                                                        // 通知适配器数据已更改
                                                        cellOverflowListener.notifyPageAdapterChanged();
                                                        
                                                        Log.d(TAG, "已为目标页面设置CellOverflowListener和拖拽监听器，确保拖拽功能正常");
                                                    } else {
                                                        Log.e(TAG, "无法在目标页面找到CellLayout");
                                                    }
                                                } else {
                                                    Log.e(TAG, "无法获取目标页面视图");
                                                }
                                            }, 250); // 增加延迟时间确保页面完全创建
                                        }
                                    }
                                }
                            } else {
                                Log.e(TAG, "无法获取MainActivity中的ViewPager2");
                            }
                        }
                    }
                    

                    
                    // 检查时间间隔，避免频繁触发
                    if (currentTime - lastDragEventTime > DRAG_FEEDBACK_INTERVAL) {
                        lastDragEventTime = currentTime;
                        
                        // 如果靠近屏幕左右边缘，通知需要切换页面
                        if ((isNearLeftEdge || isNearRightEdge) && 
                            currentTime - lastEdgeFeedbackTime > EDGE_FEEDBACK_THROTTLE) {
                            lastEdgeFeedbackTime = currentTime;
                            
                            // 取消预览效果
                            if (isPreviewActive) {
                                cancelPreviewAnimations();
                                isPreviewActive = false;
                            }
                            
                            // 关闭所有ShortcutMenuView活跃菜单
                            ShortcutMenuView.dismissActiveMenus();
                        }
                    }
                    
                    return true;

                case DragEvent.ACTION_DRAG_EXITED:
                    // 拖动退出视图区域
                    Log.d(TAG, "拖动退出视图区域");
                    fadeOutHighlight();
                    
                    // 取消预览效果
                    if (isPreviewActive) {
                        cancelPreviewAnimations();
                        isPreviewActive = false;
                    }
                    

                    
                    return true;

                case DragEvent.ACTION_DROP:
                    // 处理放置操作
                    Log.d(TAG, "拖动放置");
                    
                    // 重置高亮状态
                    fadeOutHighlight();
                    isDragging = false;
                    
                    // 如果有预览效果，先取消
                    if (isPreviewActive) {
                        cancelPreviewAnimations();
                        isPreviewActive = false;
                    }
                    

                    
                    // 获取拖动中的数据对象（单元格）
                    localState = event.getLocalState();
                    Cell dragCell = null;
                    int fromPage = -1;
                    
                    if (localState instanceof Cell) {
                        dragCell = (Cell) localState;
                        
                        // 检查是否是跨页面拖拽并提取源页面
                        String tag = dragCell.getTag();
                        boolean isCrossPageDrag = tag != null && tag.contains(":cross_page");
                        
                        if (isCrossPageDrag) {
                            // 提取源页面索引 - 修复格式为 packageName:cross_page:pageIndex:timestamp
                            String[] parts = tag.split(":");
                            if (parts.length >= 3) {
                                try {
                                    // 正确提取页面索引 (在cross_page后的第一个部分)
                                    if (parts.length >= 4 && "cross_page".equals(parts[parts.length - 3])) {
                                        fromPage = Integer.parseInt(parts[parts.length - 2]);
                                    } else {
                                        // 兼容旧格式
                                        fromPage = Integer.parseInt(parts[parts.length - 1]);
                                    }
                                } catch (NumberFormatException e) {
                                    Log.e(TAG, "解析源页面索引失败", e);
                                }
                            }
                            
                            // 还原标签
                            dragCell.setTag(tag.substring(0, tag.indexOf(":cross_page")));
                        } else {
                            // 非跨页面拖拽，这里执行图标位置交换
                            // 计算目标位置
                            int targetColumn = Math.min(columns - 1, Math.max(0, (int)(event.getX() / per_cell_width)));
                            int targetRow = Math.min(rows - 1, Math.max(0, (int)(event.getY() / per_cell_height)));
                            int targetIndex = targetRow * columns + targetColumn;
                            
                            // 如果有拖拽源单元格，且目标位置索引有效，直接交换
                            if (dragSourceIndex >= 0 && targetIndex >= 0 && 
                                targetIndex < cells.size() && targetIndex != dragSourceIndex) {
                                
                                Cell targetCell = cells.get(targetIndex);
                                
                                // 根据目标单元格类型执行交换
                                if (targetCell.getTag().equals("empty")) {
                                    // 空白单元格 - 交换位置
                                    swapCells(dragSourceIndex, targetIndex);
                                    Log.d(TAG, "放置时交换：应用图标与空白单元格: " + dragSourceIndex + " <-> " + targetIndex);
                                } else {
                                    // 非空单元格 - 交换位置
                                    swapCells(dragSourceIndex, targetIndex);
                                    Log.d(TAG, "放置时交换：应用图标与其他图标: " + dragSourceIndex + " <-> " + targetIndex);
                                }
                            }
                        }
                    }
                    
                    // 在拖拽结束时恢复视图可见性，不使用动画
                    if (dragCell != null && dragCell.getContentView() != null) {
                        View contentView = dragCell.getContentView();
                        contentView.setVisibility(View.VISIBLE);
                        contentView.setAlpha(1.0f);
                        contentView.setScaleX(1.0f);
                        contentView.setScaleY(1.0f);
                    }
                    
                    // 处理跨页面拖拽的情况
                    if (dragCell != null && fromPage >= 0 && cellOverflowListener != null) {
                                // 获取当前页面索引
                                ViewParent parent = getParent();
                                int currentPage2 = -1;
                                ViewPager2 viewPager3 = null;
                                
                                while (parent != null && !(parent.getParent() instanceof ViewPager2)) {
                                    parent = parent.getParent();
                                }
                                
                                if (parent != null) {
                                    ViewGroup viewPagerContent = (ViewGroup) parent;
                                    viewPager3 = (ViewPager2) viewPagerContent.getParent();
                                    currentPage = viewPager3.getCurrentItem();
                            
                            // 计算目标位置
                            int targetColumn = Math.min(columns - 1, Math.max(0, (int)(event.getX() / per_cell_width)));
                            int targetRow = Math.min(rows - 1, Math.max(0, (int)(event.getY() / per_cell_height)));
                            int targetIndex = targetRow * columns + targetColumn;
                                    
                                    // 添加日志以确认当前页面和源页面
                                    Log.d(TAG, "跨页面拖拽: 从页面 " + fromPage + " 到页面 " + currentPage + 
                                          " 位置(" + targetColumn + "," + targetRow + ")");
                                    
                                    // 先调用监听器处理跨页面拖拽
                                    cellOverflowListener.onCellSwapped(dragCell, fromPage, currentPage, targetColumn, targetRow);
                                    
                                    // 使用新方法处理跨页面拖拽
                                    handleCrossPageDrop(dragCell, targetIndex, fromPage, currentPage);
                                    
                                    // 检查源页面是否为空，如果是空的且不是第一页，删除源页面
                                    if (fromPage > 0 && fromPage != currentPage) {
                                        // 创建final副本以在lambda中使用
                                        final int finalFromPage = fromPage;
                                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                            // 通过接口检查是否需要移除空页面
                                            if (cellOverflowListener != null) {
                                                // 检查源页面是否为空
                                                boolean isSourceEmpty = cellOverflowListener.isPageEmpty(finalFromPage);
                                                
                                                if (isSourceEmpty) {
                                                    Log.d(TAG, "源页面" + finalFromPage + "为空，准备删除...");
                                                    // 通知WorkspaceFragment删除空页面
                                                    cellOverflowListener.removePage(finalFromPage);
                                        } else {
                                                    // 如果源页面不为空，只需要更新页面
                                                    cellOverflowListener.setupWorkspacePage(finalFromPage);
                                                }
                                            }
                                        }, 300); // 增加延迟确保UI已稳定
                                }
                            }
                        }
                        
                        // 通知更新保存位置
                        if (cellOverflowListener != null) {
                            cellOverflowListener.saveAppPositions();
                    }
                    
                    // 在处理完拖放后，确保所有图标可见
                    ensureAllIconsVisible();
                    
                    return true;

                case DragEvent.ACTION_DRAG_ENDED:
                    // 拖动操作结束
                    Log.d(TAG, "拖动结束");
                    
                    // 检查是否成功放置
                    boolean success = event.getResult();
                    Log.d(TAG, "拖拽结果: " + (success ? "成功" : "失败"));
                    
                    // 重置高亮和拖动状态
                    fadeOutHighlight();
                    isDragging = false;
                    isInDragging = false;
                    
                    // 取消预览效果
                    if (isPreviewActive) {
                        cancelPreviewAnimations();
                        isPreviewActive = false;
                    }
                    
                    // 确保所有图标可见
                    ensureAllIconsVisible();
                    

                    
                    // 检查是否是跨页面拖拽且失败的情况，需要返回到原始页面
                    if (!success && currentDragCell != null) {
                        // 获取拖拽的单元格
                        dragCell = currentDragCell;
                        String tag = dragCell.getTag();
                        
                        // 检查是否是跨页面拖拽
                        boolean isCrossPageDrag = tag != null && tag.contains(":cross_page");
                        
                        if (isCrossPageDrag) {
                            // 恢复原始标签
                            dragCell.setTag(tag.substring(0, tag.indexOf(":cross_page")));
                            
                            // 获取原始页面索引（之前保存在expectRowIndex中）
                            int originalPage = dragCell.getExpectRowIndex();
                            
                            // 获取ViewPager并返回原始页面
                            ViewParent parent = getParent();
                            ViewPager2 viewPager4 = null;
                            
                            while (parent != null && !(parent.getParent() instanceof ViewPager2)) {
                                parent = parent.getParent();
                            }
                            
                            if (parent != null && originalPage >= 0) {
                                ViewGroup viewPagerContent = (ViewGroup) parent;
                                viewPager4 = (ViewPager2) viewPagerContent.getParent();
                                
                                // 返回原始页面
                                Log.d(TAG, "拖拽失败，返回原始页面: " + originalPage);
                                viewPager4.setCurrentItem(originalPage, true);
                                
                                // 确保单元格视图可见
                                if (dragCell.getContentView() != null) {
                                    dragCell.getContentView().setVisibility(View.VISIBLE);
                                }
                                
                                // 弹出提示
                                Context context = viewPager4.getContext();
                                if (context != null) {
                                    Toast.makeText(context, "拖拽未完成，已返回原始页面", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                    
                    // 确保所有临时隐藏的视图恢复可见
                    for (Cell cell : cells) {
                        if (cell.getContentView() != null && !cell.getTag().equals("empty")) {
                            View contentView = cell.getContentView();
                            if (contentView.getVisibility() != View.VISIBLE) {
                            contentView.setVisibility(View.VISIBLE);
                            }
                            
                            // 确保视图属性重置，使用动画效果
                            contentView.animate()
                                .alpha(1.0f)
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .translationX(0)
                                .translationY(0)
                                .setInterpolator(new DecelerateInterpolator())
                                .setDuration(150)
                                .withEndAction(() -> {
                                    // 确保动画结束后视图状态正确
                                    contentView.setVisibility(View.VISIBLE);
                                    contentView.setAlpha(1.0f);
                                    contentView.setTranslationX(0);
                                    contentView.setTranslationY(0);
                                    contentView.setScaleX(1.0f);
                                    contentView.setScaleY(1.0f);
                                })
                                .start();
                        }
                    }
                    
                    // 重置拖拽缓存
                    currentDragCell = null;
                    dragSourceIndex = -1;
                    
                    // 请求重绘
                    invalidate();
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "拖拽处理错误: " + e.getMessage(), e);
            // 重置状态
            fadeOutHighlight();
            isDragging = false;
            
            // 取消预览效果
            if (isPreviewActive) {
                cancelPreviewAnimations();
                isPreviewActive = false;
            }
            
            // 确保所有图标可见
            ensureAllIconsVisible();
            

            
            invalidate();
        }

        return false;
    }

    /**
     * 平滑淡出高亮效果
     */
    private void fadeOutHighlight() {
        if (highLightValid && targetHighlightAlpha > 0) {
            // 设置目标alpha为0，启动淡出动画
            targetHighlightAlpha = 0f;
            lastHighlightUpdateTime = System.currentTimeMillis();
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            // 直接重置状态
            resetHighlight();
        }
    }

    private void resetHighlight() {
        highLightColumn = -1;
        highLightRow = -1;
        highLightValid = false;
        highlightAlpha = 0f;
        targetHighlightAlpha = 0f;
        invalidate();
    }

    private void moveCells(int sourceIndex, int targetIndex) {
        // 确保索引有效
        if (sourceIndex < 0 || sourceIndex >= cells.size() ||
                targetIndex < 0 || targetIndex >= cells.size()) {
            Log.e(TAG, "无效的单元格索引: 源=" + sourceIndex + ", 目标=" + targetIndex);
            return;
        }

        if (sourceIndex == targetIndex) {
            return; // 相同位置不需要移动
        }

        // 获取源单元格和目标单元格
        Cell sourceCell = cells.get(sourceIndex);
        Cell targetCell = cells.get(targetIndex);

        Log.d(TAG, "移动单元格: 从" + sourceIndex + "到" + targetIndex +
                ", 源Tag=" + sourceCell.getTag() + ", 目标Tag=" + targetCell.getTag());

        // 特别处理目标单元格为空的情况
        if (targetCell.getTag().equals("empty")) {
            // 直接交换源单元格和目标单元格
            cells.set(sourceIndex, targetCell);  // 源位置设为空
            cells.set(targetIndex, sourceCell);  // 目标位置设为源单元格

            // 确保视图状态正确
            if (sourceCell.getContentView() != null) {
                sourceCell.getContentView().setVisibility(View.VISIBLE);
                sourceCell.getContentView().setAlpha(1.0f);
            }

            // 重新布局
            requestLayout();
            return;
        }

        // 以下是非空目标单元格的处理逻辑，改进为智能排序和填空
        // 创建临时数组保存当前状态
        ArrayList<Cell> tempCells = new ArrayList<>(cells);
        
        // 记录哪些视图需要应用动画
        ArrayList<Integer> cellsToAnimate = new ArrayList<>();
        
        // 创建一个排序后的单元格列表，去除空单元格
        ArrayList<Cell> sortedCells = new ArrayList<>();
        for (Cell cell : cells) {
            if (!cell.getTag().equals("empty")) {
                sortedCells.add(cell);
            }
        }
        
        // 将源单元格移动到目标位置
        if (sortedCells.contains(sourceCell)) {
            sortedCells.remove(sourceCell);
        }
        
        // 计算目标排序位置
        int targetSortedIndex = 0;
        for (int i = 0; i < targetIndex; i++) {
            if (!cells.get(i).getTag().equals("empty")) {
                targetSortedIndex++;
            }
        }
        
        // 在目标位置插入源单元格
        if (targetSortedIndex <= sortedCells.size()) {
            sortedCells.add(targetSortedIndex, sourceCell);
        } else {
            sortedCells.add(sourceCell);
        }
        
        // 清空当前单元格列表并重新填充
        for (int i = 0; i < cells.size(); i++) {
            // 使用安全的方式创建空单元格
            Cell emptyCell = new Cell("empty", null);
            // 设置默认尺寸以避免测量问题
            emptyCell.setWidthNum(1);
            emptyCell.setHeightNum(1);
            cells.set(i, emptyCell);
        }
        
        // 重新填充单元格，首先放置非空单元格
        for (int i = 0; i < sortedCells.size(); i++) {
            cells.set(i, sortedCells.get(i));
            
            // 如果这个单元格在移动过程中发生了变化，添加到动画列表
            if (i != tempCells.indexOf(sortedCells.get(i))) {
                cellsToAnimate.add(i);
            }
        }
        
        // 应用图标滑动动画
        applySlideAnimations(tempCells, cellsToAnimate, sourceIndex, targetIndex);
        
        // 通知单元格变更
        if (onCellsChangedListener != null) {
            onCellsChangedListener.onCellsChanged();
        }
        
        // 重新布局
        requestLayout();
    }
    
    /**
     * 应用图标移动效果（简化版，无动画）
     */
    private void applySlideAnimations(ArrayList<Cell> originalCells, ArrayList<Integer> cellsToAnimate, 
                                     int sourceIndex, int targetIndex) {
        // 直接设置所有图标的最终位置，不使用动画
        for (int i = 0; i < cellsToAnimate.size(); i++) {
            Integer currentIndex = cellsToAnimate.get(i);
            Cell cell = cells.get(currentIndex);
            if (cell.getContentView() == null || cell.getTag().equals("empty")) {
                continue;
            }
            
            View iconView = cell.getContentView();
            
            // 取消所有正在进行的动画
            iconView.clearAnimation();
            iconView.animate().cancel();
            
            // 直接设置最终状态
            iconView.setTranslationX(0);
            iconView.setTranslationY(0);
            iconView.setAlpha(1.0f);
            iconView.setScaleX(1.0f);
            iconView.setScaleY(1.0f);
            iconView.setRotation(0);
            iconView.setZ(0); // 重置Z轴高度
            iconView.setLayerType(LAYER_TYPE_NONE, null); // 重置硬件加速
        }
    }

    // 修改跨页面拖拽代码
    /**
     * 处理跨页面拖放逻辑
     * @param dragCell 拖拽的单元格
     * @param targetIndex 目标位置索引
     * @param fromPage 源页面索引
     * @param currentPage 当前页面索引
     */
    private void handleCrossPageDrop(Cell dragCell, int targetIndex, int fromPage, int currentPage) {
        if (dragCell == null || cellOverflowListener == null) return;
        
        try {
            // 确保视图可见
            if (dragCell.getContentView() != null) {
                View contentView = dragCell.getContentView();
                contentView.setVisibility(View.VISIBLE);
                contentView.setAlpha(1.0f);
                
                // 使用弹性动画效果增强交互体验
                contentView.setScaleX(0.8f);
                contentView.setScaleY(0.8f);
                contentView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(250)
                    .setInterpolator(new OvershootInterpolator(0.8f))
                    .start();
            }
            
            // 确保原标签已恢复（移除跨页面标记）
            String tag = dragCell.getTag();
            if (tag != null && tag.contains(":cross_page")) {
                dragCell.setTag(tag.substring(0, tag.indexOf(":cross_page")));
            }
            
            // 检查目标页面是否已满
            boolean isTargetPageFull = isPageFull(2); // 需要至少2个空位
            
            if (isTargetPageFull) {
                // 如果目标页面已满，尝试找到更多空间
                Log.w(TAG, "目标页面空间不足，尝试处理页面溢出");
                
                // 震动反馈提示空间不足
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VibrationEffect effect = VibrationEffect.createOneShot(
                                70, VibrationEffect.DEFAULT_AMPLITUDE);
                        Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                        if (vibrator != null) {
                            vibrator.vibrate(effect);
                        }
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "震动反馈错误", e);
                }
                
                // 显示提示
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context, "当前页面空间不足，尝试放置到下一页", Toast.LENGTH_SHORT).show();
                }
                
                // 尝试处理溢出（创建或切换到下一页）
                boolean handled = handlePageOverflow(dragCell);
                
                if (handled) {
                    // 如果成功处理了溢出，返回
                    return;
                }
                
                // 如果处理失败，尝试挤一挤放进去
                Log.d(TAG, "无法创建新页面，尝试在当前页面放置");
            }
            
            // 如果是空白位置直接放置，否则尝试使用moveCells
            Cell targetCell = targetIndex < cells.size() ? cells.get(targetIndex) : null;
            if (targetCell == null || targetCell.getTag().equals("empty")) {
                // 确保单元格大小正确
                dragCell.setWidthNum(1);
                dragCell.setHeightNum(1);
                
                // 安全检查
                if (targetIndex >= 0 && targetIndex < cells.size()) {
                    cells.set(targetIndex, dragCell);
                    Log.d(TAG, "成功放置跨页面应用到空白位置: " + targetIndex);
                } else {
                    // 如果目标索引无效，寻找任意空位
                    int emptyIndex = -1;
                    for (int i = 0; i < cells.size(); i++) {
                        if (cells.get(i).getTag().equals("empty")) {
                            emptyIndex = i;
                            break;
                        }
                    }
                    
                    if (emptyIndex >= 0) {
                        cells.set(emptyIndex, dragCell);
                        Log.d(TAG, "成功放置跨页面应用到空白位置: " + emptyIndex);
                    } else {
                        // 没有空位，尝试挤到第一个位置
                        Log.w(TAG, "没有空白位置，尝试放置到第一个位置");
                        dragCell.setWidthNum(1);
                        dragCell.setHeightNum(1);
                        
                        // 先备份第一个位置的图标
                        Cell firstCell = cells.get(0);
                        
                        // 设置到第一个位置
                        cells.set(0, dragCell);
                        
                        // 尝试为原来第一个位置的图标找空间
                        if (!firstCell.getTag().equals("empty")) {
                            // 如果之前的图标不是空白，尝试找空间放置
                            boolean handledFirstCell = handlePageOverflow(firstCell);
                            
                            // 如果处理失败，只能放回到最后一个位置
                            if (!handledFirstCell) {
                                for (int i = cells.size() - 1; i >= 0; i--) {
                                    if (cells.get(i).getTag().equals("empty")) {
                                        cells.set(i, firstCell);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 目标位置已有图标，尝试交换
                Log.d(TAG, "目标位置有图标，尝试交换位置");
                
                // 先查找空位
                int emptyIndex = -1;
                for (int i = 0; i < cells.size(); i++) {
                    if (cells.get(i).getTag().equals("empty")) {
                        emptyIndex = i;
                        break;
                    }
                }
                
                if (emptyIndex >= 0) {
                    // 如果有空位，先放到空位然后再移动到目标位置
                    dragCell.setWidthNum(1);
                    dragCell.setHeightNum(1);
                    cells.set(emptyIndex, dragCell);
                    
                    // 然后移动到目标位置
                    moveCells(emptyIndex, targetIndex);
                    
                    // 使用动画效果增强交换体验
                    View targetView = targetCell.getContentView();
                    if (targetView != null) {
                        targetView.animate()
                            .alpha(0.5f)
                            .scaleX(0.9f)
                            .scaleY(0.9f)
                            .setDuration(150)
                            .withEndAction(() -> {
                                targetView.animate()
                                    .alpha(1.0f)
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(200)
                                    .start();
                            })
                            .start();
                    }
                    
                    Log.d(TAG, "已将跨页面应用放置到位置 " + targetIndex);
                } else {
                    // 如果没有空位，尝试直接交换两个单元格
                    Cell temp = cells.get(targetIndex);
                    cells.set(targetIndex, dragCell);
                    
                    // 尝试为被交换出来的单元格找空间
                    // 优先放到源页面
                    boolean handledDisplacedCell = false;
                    
                    if (fromPage != currentPage) {
                        // 暂存当前页面索引
                        temp.setExpectRowIndex(currentPage);
                        
                        // 尝试处理页面溢出（放回源页面）
                        handledDisplacedCell = cellOverflowListener.requestReturnToPage(temp, fromPage);
                    }
                    
                    // 如果无法放回源页面，尝试找下一页
                    if (!handledDisplacedCell) {
                        handledDisplacedCell = handlePageOverflow(temp);
                    }
                    
                    // 如果处理失败，尝试强制放置在当前页面
                    if (!handledDisplacedCell) {
                        Log.w(TAG, "无法为交换出的图标找到位置，尝试在当前页面找空间");
                        
                        // 简单放置在最后一个位置
                        for (int i = cells.size() - 1; i >= 0; i--) {
                            // 避免覆盖刚刚放置的图标
                            if (i != targetIndex && cells.get(i).getTag().equals("empty")) {
                                cells.set(i, temp);
                                handledDisplacedCell = true;
                                break;
                            }
                        }
                    }
                    
                    if (!handledDisplacedCell) {
                        Log.e(TAG, "严重错误：无法为交换出的图标找到任何位置");
                        // 显示错误提示
                        Toast.makeText(getContext(), 
                            "无法找到足够空间放置图标，请清理部分图标", 
                            Toast.LENGTH_SHORT).show();
                    }
                }
            }
            
            // 请求重新布局
            requestLayout();
            invalidate();
            
            // 如果是跨页面操作，更新两个页面
            if (fromPage != currentPage) {
                cellOverflowListener.notifyPageDataChanged(fromPage);
                cellOverflowListener.notifyPageDataChanged(currentPage);
            }
            
            // 保存应用位置
            cellOverflowListener.saveAppPositions();
            
        } catch (Exception e) {
            Log.e(TAG, "处理跨页面拖拽图标放置失败: " + e.getMessage(), e);
        }
    }

    // 在CellLayout类中添加
    private OnCellsChangedListener onCellsChangedListener;

    // 在初始化工作区时调用此方法填充空白单元格
    public void fillEmptyCells() {
        int totalCells = rows * columns;
        int currentSize = cells.size();

        // 如果当前单元格数量少于总单元格数，添加空白单元格
        for (int i = currentSize; i < totalCells; i++) {
            // 创建空白单元格并添加
            Cell emptyCell = new Cell("empty", null);
            // 设置默认尺寸以避免测量问题
            emptyCell.setWidthNum(1);
            emptyCell.setHeightNum(1);
            cells.add(emptyCell);
        }

        Log.d(TAG, "已填充空白单元格：总计 " + totalCells + " 个单元格");
    }

    public interface OnCellsChangedListener {
        void onCellsChanged();
    }

    public void setOnCellsChangedListener(OnCellsChangedListener listener) {
        this.onCellsChangedListener = listener;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        // 只有在highLightValid为true且不在拖拽状态时才绘制高亮效果
        if (highLightValid && !isDragging && !isInDragging && highLightColumn >= 0 && highLightRow >= 0) {
            // 计算动画插值
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - lastHighlightUpdateTime;
            
            if (targetHighlightAlpha > highlightAlpha) {
                // 淡入
                highlightAlpha = Math.min(targetHighlightAlpha, 
                                         highlightAlpha + (elapsed / (float)HIGHLIGHT_ANIMATION_DURATION));
            } else if (targetHighlightAlpha < highlightAlpha) {
                // 淡出
                highlightAlpha = Math.max(targetHighlightAlpha,
                                         highlightAlpha - (elapsed / (float)HIGHLIGHT_ANIMATION_DURATION));
            }
            
            // 更新时间戳
            lastHighlightUpdateTime = currentTime;
            
            // 如果高亮还在显示，继续动画
            if (highlightAlpha > 0) {
                // 计算单元格区域
                float cellLeft = highLightColumn * per_cell_width;
                float cellTop = highLightRow * per_cell_height;
                
                // 计算图标的大小（比单元格略小）
                float iconSize = Math.min(per_cell_width, per_cell_height) * 0.85f;
                
                // 计算图标居中后的区域
                float iconLeft = cellLeft + (per_cell_width - iconSize) / 2;
                float iconTop = cellTop + (per_cell_height - iconSize) / 2;
                float iconRight = iconLeft + iconSize;
                float iconBottom = iconTop + iconSize;
                
                // 存储高亮区域
                highlightRect.set(iconLeft, iconTop, iconRight, iconBottom);
                
                // 应用当前alpha值
                int alpha = (int)(70 * highlightAlpha);
                highlightPaint.setAlpha(alpha);
                
                // 绘制圆角矩形高亮背景
                float cornerRadius = 20f;
                canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, highlightPaint);
                
                // 绘制内边框
                alpha = (int)(255 * highlightAlpha);
                borderPaint.setAlpha(alpha);
                canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, borderPaint);
                
                // 应用模糊发光效果
                alpha = (int)(180 * highlightAlpha);
                glowPaint.setAlpha(alpha);
                canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, glowPaint);
                
                // 请求继续动画
                if (highlightAlpha != targetHighlightAlpha) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
            }
        }
    }
    public static class Cell {
        private String tag;
        private View contentView;
        private int widthNum;//横向占据的格数
        private int heightNum;//纵向占据的格数
        private int expectColumnIndex = -1, expectRowIndex = -1;//计算出的可摆放的位置

        public Cell(String tag, View view) {
            this.tag = tag;
            this.contentView = view;
           /* this.contentView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    DragShadowBuilder builder = new DragShadowBuilder(v);
                    v.setVisibility(View.INVISIBLE);
                    v.startDrag(null, builder, Cell.this, 0);
                    return true;
                }
            });*/
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public View getContentView() {
            return contentView;
        }

        public void setContentView(View contentView) {
            this.contentView = contentView;
        }

        public int getWidthNum() {
            return widthNum;
        }

        public void setWidthNum(int widthNum) {
            this.widthNum = widthNum;
        }

        public void setHeightNum(int heightNum) {
            this.heightNum = heightNum;
        }

        public int getHeightNum() {
            return heightNum;
        }

        public int getExpectColumnIndex() {
            return expectColumnIndex;
        }

        public void setExpectColumnIndex(int expectColumnIndex) {
            this.expectColumnIndex = expectColumnIndex;
        }

        public int getExpectRowIndex() {
            return expectRowIndex;
        }

        public void setExpectRowIndex(int expectRowIndex) {
            this.expectRowIndex = expectRowIndex;
        }

    }

     private void swapCells(int sourceIndex, int targetIndex)  {
        // 确保索引有效
        if (sourceIndex < 0 || sourceIndex >= cells.size() ||
                targetIndex < 0 || targetIndex >= cells.size()) {
            Log.e(TAG, "交换单元格时索引无效: 源=" + sourceIndex + ", 目标=" + targetIndex);
            return;
        }

        if (sourceIndex == targetIndex) {
            return; // 相同位置不需要交换
        }

        Log.d(TAG, "交换单元格: " + sourceIndex + " <-> " + targetIndex);

        // 简单交换两个单元格
        Cell sourceCell = cells.get(sourceIndex);
        Cell targetCell = cells.get(targetIndex);

        cells.set(sourceIndex, targetCell);
        cells.set(targetIndex, sourceCell);

        // 确保视图正确显示
        if (sourceCell.getContentView() != null) {
            sourceCell.getContentView().setVisibility(View.VISIBLE);
            sourceCell.getContentView().setAlpha(1.0f);
        }

        if (targetCell.getContentView() != null) {
            targetCell.getContentView().setVisibility(
                    targetCell.getTag().equals("empty") ? View.INVISIBLE : View.VISIBLE
            );
        }

        // 重新布局
        requestLayout();
    }
    // 添加一个监听器接口，当没有空间放置应用图标时调用
    public interface OnCellOverflowListener {
        /**
         * 当 CellLayout 无法为 Cell 找到足够空间时调用
         * @param overflowCell 无法放置的Cell
         * @param pageIndex 当前页面索引
         * @return 如果返回true，表示已处理溢出；如果返回false，CellLayout将移除此Cell
         */
        boolean onCellOverflow(Cell overflowCell, int pageIndex);
        
        /**
         * 当单元格交换时调用
         * @param draggingCell 被拖动的单元格
         * @param fromPage 源页面索引
         * @param toPage 目标页面索引
         * @param targetColumn 目标列
         * @param targetRow 目标行
         */
        void onCellSwapped(Cell draggingCell, int fromPage, int toPage, int targetColumn, int targetRow);
        
        /**
         * 保存所有应用位置
         */
        void saveAppPositions();
        
        /**
         * 设置工作区页面
         * @param pageIndex 页面索引
         */
        void setupWorkspacePage(int pageIndex);
        
        /**
         * 获取页面总数
         * @return 页面总数
         */
        int getPageCount();
        
        /**
         * 创建新页面
         * @return 新页面索引
         */
        int createNewPage();
        
        /**
         * 检查是否可以创建新页面
         * @return true如果允许创建新页面
         */
        boolean canCreateNewPage();
        
        /**
         * 当拖拽到页面边缘，请求创建新页面并移动项目到该页面
         * @param cellToMove 被拖动的单元格
         * @param fromPageIndex 源页面索引
         * @return 新页面的索引，如果创建失败则返回-1
         */
        int requestNewPageAndMoveItem(Cell cellToMove, int fromPageIndex);
        
        /**
         * 检查指定页面是否为空（只包含空白单元格）
         * @param pageIndex 页面索引
         * @return 如果页面为空返回true
         */
        boolean isPageEmpty(int pageIndex);
        
        /**
         * 移除指定的页面
         * @param pageIndex 要移除的页面索引
         */
        void removePage(int pageIndex);
        
        /**
         * 获取指定页面的单元格列表
         * @param pageIndex 页面索引
         * @return 单元格列表，如果页面不存在则返回null
         */
        List<Cell> getPageCells(int pageIndex);
        
        /**
         * 通知页面适配器数据已更改
         * 用于刷新ViewPager2
         */
        void notifyPageAdapterChanged();
        
        /**
         * 请求将单元格返回到指定页面
         * @param cellToMove 需要移动的单元格
         * @param targetPageIndex 目标页面索引
         * @return 如果成功处理返回true
         */
        boolean requestReturnToPage(Cell cellToMove, int targetPageIndex);
        
        /**
         * 通知指定页面的数据已更改
         * @param pageIndex 页面索引
         */
        void notifyPageDataChanged(int pageIndex);
    }

    private OnCellOverflowListener cellOverflowListener;

    public void setOnCellOverflowListener(OnCellOverflowListener listener) {
        this.cellOverflowListener = listener;
    }



    private void playPageEnterAnimation(View pageView) {
        // 直接设置最终状态，不使用动画
        pageView.setScaleX(1.0f);
        pageView.setScaleY(1.0f);
        pageView.setAlpha(1.0f);
    }

    /**
     * 显示预览动画效果
     * @param column 目标列
     * @param row 目标行
     */
    private void showPreviewAnimation(int column, int row) {
        if (currentDragCell == null || dragSourceIndex < 0) {
            return;
        }
        
        // 确保关闭所有菜单
        ShortcutMenuView.dismissActiveMenus();
        
        // 标记预览状态为活跃
        isPreviewActive = true;
        
        // 计算目标索引
        int targetIndex = row * columns + column;
        
        // 如果目标是空白单元格或拖拽源单元格，不显示预览
        if (targetIndex == dragSourceIndex || 
            targetIndex >= cells.size() || 
            cells.get(targetIndex).getTag().equals("empty")) {
            return;
        }
        
        Log.d(TAG, "显示预览动画: 源=" + dragSourceIndex + ", 目标=" + targetIndex);
        
        // 创建临时数组保存当前状态
        ArrayList<Cell> tempCells = new ArrayList<>(cells);
        
        // 记录需要预览动画的单元格
        ArrayList<Integer> cellsToAnimate = new ArrayList<>();
        
        if (targetIndex > dragSourceIndex) {
            // 向后移动: 目标位置比源位置大
            for (int i = dragSourceIndex + 1; i <= targetIndex; i++) {
                if (!tempCells.get(i).getTag().equals("empty")) {
                    cellsToAnimate.add(i);
                }
            }
        } else {
            // 向前移动: 目标位置比源位置小
            for (int i = targetIndex; i < dragSourceIndex; i++) {
                if (!tempCells.get(i).getTag().equals("empty")) {
                    cellsToAnimate.add(i);
                }
            }
        }
        
        // 应用预览动画
        applyPreviewAnimations(tempCells, cellsToAnimate, dragSourceIndex, targetIndex);
    }
    
    /**
     * 应用预览动画效果
     */
    private void applyPreviewAnimations(ArrayList<Cell> originalCells, ArrayList<Integer> cellsToAnimate, 
                                       int sourceIndex, int targetIndex) {
        // 设置动画基础参数
        long baseDuration = 200; // 预览动画的持续时间
        final float previewAlpha = 0.7f; // 预览状态下的透明度
        
        // 清除之前的预览视图列表
        previewAnimatedViews.clear();
        
        for (int i = 0; i < cellsToAnimate.size(); i++) {
            Integer originalIndex = cellsToAnimate.get(i);
            Cell cell = originalCells.get(originalIndex);
            if (cell.getContentView() == null || cell.getTag().equals("empty")) {
                continue;
            }
            
            View iconView = cell.getContentView();
            previewAnimatedViews.add(iconView);
            
            // 找出此单元格在预览位置的坐标
            int newIndex;
            if (targetIndex > sourceIndex) {
                // 向后移动
                newIndex = originalIndex - 1;
            } else {
                // 向前移动
                newIndex = originalIndex + 1;
            }
            
            // 计算原位置和新位置的坐标差
            int fromColumn = originalIndex % columns;
            int fromRow = originalIndex / columns;
            int toColumn = newIndex % columns;
            int toRow = newIndex / columns;
            
            float fromX = fromColumn * per_cell_width;
            float fromY = fromRow * per_cell_height;
            float toX = toColumn * per_cell_width;
            float toY = toRow * per_cell_height;
            
            // 保存原始位置信息到视图标签中，便于恢复
            int[] originalPosition = new int[] {fromColumn, fromRow};
            iconView.setTag(R.id.preview_original_position, originalPosition);
            
            // 添加延迟效果
            long delay = i * 20;
            
            // 设置硬件加速提高动画性能
            iconView.setLayerType(LAYER_TYPE_HARDWARE, null);
            
            // 保存原始Z轴高度
            float originalZ = iconView.getZ();
            
            // 应用预览动画
            iconView.animate()
                .translationX(toX - fromX)
                .translationY(toY - fromY)
                .alpha(previewAlpha)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setStartDelay(delay)
                .setDuration(baseDuration)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }
    
    /**
     * 取消预览动画效果，将图标恢复到原始位置
     */
    private void cancelPreviewAnimations() {
        // 循环恢复所有预览中的视图
        for (View view : previewAnimatedViews) {
            if (view != null) {
                // 取消所有可能正在进行的动画
                view.clearAnimation();
                view.animate().cancel();
                
                // 直接设置视图状态，不使用动画
                view.setVisibility(View.VISIBLE);
                view.setAlpha(1.0f);
                view.setScaleX(1.0f);
                view.setScaleY(1.0f);
                view.setTranslationX(0);
                view.setTranslationY(0);
                
                // 移除标签
                view.setTag(R.id.preview_original_position, null);
            }
        }
        
        // 清空预览视图列表
        previewAnimatedViews.clear();
    }

    /**
     * 弹出应用快捷方式或应用选项菜单
     */
    private void showAppShortcuts(View anchor, String packageName) {
        if ("empty".equals(packageName) || isInDragging) {
            return;
        }
        
        // 如果正在拖拽，不显示菜单
        if (isDragging) {
            return;
        }
        
        Context context = anchor.getContext();
        LauncherApps launcherApps = null;
        List<ShortcutInfo> shortcuts = null;
        
        // 获取应用快捷方式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            ShortcutQuery query = new ShortcutQuery();
            query.setPackage(packageName);
            query.setQueryFlags(ShortcutQuery.FLAG_MATCH_DYNAMIC
                                | ShortcutQuery.FLAG_MATCH_MANIFEST
                                | ShortcutQuery.FLAG_MATCH_PINNED);
            try {
                shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle());
            } catch (Exception e) {
                shortcuts = null;
            }
        }
        
        // 获取应用信息
        String appName = "";
        Drawable appIcon = null;
        
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            appName = pm.getApplicationLabel(ai).toString();
            appIcon = pm.getApplicationIcon(packageName);
        } catch (Exception e) {
            // 如果无法获取应用信息，使用默认值
            appName = packageName;
            appIcon = context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
        }
        
        // 隐藏之前的菜单
        if (currentPopupMenu != null) {
            currentPopupMenu.dismiss();
            currentPopupMenu = null;
        }
        
        // 创建自定义快捷菜单
        ShortcutMenuView menuView = new ShortcutMenuView(
                context, 
                packageName, 
                appName, 
                appIcon, 
                shortcuts
        );
        
        // 设置监听器
        menuView.setOnDismissListener(() -> {
            // 菜单消失时的回调
            if (currentPopupMenu != null) {
                currentPopupMenu = null;
            }
            
            // 恢复视图可见性，但不在拖拽开始时恢复
            if (anchor.getVisibility() == View.INVISIBLE && !isDragging) {
                anchor.setVisibility(View.VISIBLE);
            }
        });
        
        // 设置拖拽开始监听器
        menuView.setDragStartListener(new ShortcutMenuView.DragStartListener() {
            @Override
            public void onDragStart(View view, String pkgName) {
                // 在开始拖拽前取消菜单
                menuView.dismiss();
                // 开始拖拽
                startDragFromShortcutMenu(view, pkgName);
            }
        });
        
        // 显示新菜单
        menuView.show(anchor);
        currentPopupMenu = new PopupMenu(context, anchor); // 保存菜单引用
    }
    
    /**
     * 从快捷菜单发起拖拽操作
     */
    private void startDragFromShortcutMenu(View view, String packageName) {
        // 查找对应的Cell
        Cell targetCell = null;
        for (Cell cell : cells) {
            if (cell.getTag().equals(packageName)) {
                targetCell = cell;
                break;
            }
        }
        
        if (targetCell == null || targetCell.getContentView() == null) {
            Log.e(TAG, "找不到要拖拽的单元格: " + packageName);
            return;
        }
        
        // 设置拖拽标志
        isDragging = true;
        
        // 创建拖拽数据
        ClipData dragData = new ClipData(
                packageName,
                new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN },
                new ClipData.Item(packageName)
        );
        
        // 创建拖拽阴影
        View contentView = targetCell.getContentView();
        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(contentView) {
            @Override
            public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
                super.onProvideShadowMetrics(outShadowSize, outShadowTouchPoint);
                
                // 增加拖拽阴影尺寸，使其更为明显
                outShadowSize.x = (int)(outShadowSize.x * 1.1f);
                outShadowSize.y = (int)(outShadowSize.y * 1.1f);
                
                // 调整拖拽阴影的触摸点为中心
                outShadowTouchPoint.set(outShadowSize.x / 2, outShadowSize.y / 2);
            }
            
            @Override
            public void onDrawShadow(Canvas canvas) {
                // 增强拖拽时的阴影效果
                canvas.save();
                
                // 移除缩放效果，保持原始大小
                // float scale = 0.95f;
                // canvas.scale(scale, scale, canvas.getWidth()/2f, canvas.getHeight()/2f);
                
                // 平移效果，使阴影稍微上移
                canvas.translate(0, -12);
                
                // 绘制原始视图作为阴影
                super.onDrawShadow(canvas);
                
                canvas.restore();
            }
        };
        
        // 开始拖拽操作
        contentView.startDragAndDrop(
                dragData,
                shadowBuilder,
                targetCell, // 传递Cell对象作为本地状态
                View.DRAG_FLAG_OPAQUE
        );
        
        // 临时隐藏原视图
        contentView.setVisibility(View.INVISIBLE);
        
        // 设置当前拖拽的单元格
        currentDragCell = targetCell;
        
        // 查找拖拽源单元格的索引
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i) == targetCell) {
                dragSourceIndex = i;
                break;
            }
        }
        
        // 显示拖拽指示器
        ViewParent parent = getParent();

    }

    /**
     * 从单元格直接开始拖拽
     */
    private void startDragFromCell(Cell cell) {
        if (cell == null || cell.getContentView() == null) return;
        
        View contentView = cell.getContentView();
        String packageName = cell.getTag();
        
        // 设置拖拽标志
        isDragging = true;
        isInDragging = true;
        
        // 完全清除高亮
        highLightValid = false;
        highlightAlpha = 0f;
        targetHighlightAlpha = 0f;
        
        // 立即强制重绘以清除任何高亮框
        invalidate();
        
        // 创建拖拽数据
        ClipData dragData = new ClipData(
                packageName,
                new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN },
                new ClipData.Item(packageName)
        );
        
        // 创建拖拽阴影
        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(contentView) {
            @Override
            public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
                super.onProvideShadowMetrics(outShadowSize, outShadowTouchPoint);
                
                // 增加拖拽阴影尺寸，使其更为明显
                outShadowSize.x = (int)(outShadowSize.x * 1.1f);
                outShadowSize.y = (int)(outShadowSize.y * 1.1f);
                
                // 调整拖拽阴影的触摸点为中心
                outShadowTouchPoint.set(outShadowSize.x / 2, outShadowSize.y / 2);
            }
            
            @Override
            public void onDrawShadow(Canvas canvas) {
                // 增强拖拽时的阴影效果
                canvas.save();
                
                // 移除缩放效果，保持原始大小
                // float scale = 0.95f;
                // canvas.scale(scale, scale, canvas.getWidth()/2f, canvas.getHeight()/2f);
                
                // 平移效果，使阴影稍微上移
                canvas.translate(0, -12);
                
                // 绘制原始视图作为阴影
                super.onDrawShadow(canvas);
                
                canvas.restore();
            }
        };
        
        // 开始拖拽操作
        contentView.startDragAndDrop(
                dragData,
                shadowBuilder,
                cell, // 传递Cell对象作为本地状态
                View.DRAG_FLAG_OPAQUE
        );
        
        // 临时隐藏原视图
        contentView.setVisibility(View.INVISIBLE);
        
        // 设置当前拖拽的单元格
        currentDragCell = cell;
        
        // 查找拖拽源单元格的索引
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i) == cell) {
                dragSourceIndex = i;
                break;
            }
        }
        
        // 显示拖拽指示器
        ViewParent parent = getParent();

    }

    /**
     * 判断当前页面是否已满（没有足够空白单元格）
     * @param minEmptyCellsRequired 最低需要的空白单元格数量，默认为1
     * @return 如果页面已满返回true
     */
    private boolean isPageFull(int minEmptyCellsRequired) {
        if (cells == null) return false;
        
        int emptyCellsCount = 0;
        for (Cell cell : cells) {
            // 统计空白单元格数量
            if (cell != null && "empty".equals(cell.getTag())) {
                emptyCellsCount++;
                // 如果达到所需最小空白数，则页面不满
                if (emptyCellsCount >= minEmptyCellsRequired) {
                    return false;
                }
            }
        }
        
        // 如果空白单元格少于所需数量，则页面已满
        return emptyCellsCount < minEmptyCellsRequired;
    }
    
    /**
     * 判断当前页面是否已满（默认要求至少1个空白单元格）
     * @return 如果页面已满返回true
     */
    private boolean isPageFull() {
        return isPageFull(1);
    }

    /**
     * 处理页面溢出情况，自动创建或切换到新页面
     * @param overflowCell 溢出的单元格
     * @return 是否成功处理
     */
    private boolean handlePageOverflow(Cell overflowCell) {
        if (overflowCell == null || cellOverflowListener == null) {
            return false;
        }
        
        // 获取当前页面索引
        int currentPage = -1;
        ViewPager2 viewPager = getMainActivityViewPager();
        
        if (viewPager != null) {
            currentPage = viewPager.getCurrentItem();
            int pageCount = cellOverflowListener.getPageCount();
            
            Log.d(TAG, "处理页面溢出：当前页面 " + currentPage + "，总页数 " + pageCount);
            
            // 记录当前时间用于节流控制
            long currentTime = System.currentTimeMillis();
            
            // 判断是创建新页面还是使用已存在的下一页
            int targetPageIndex;
            if (currentPage < pageCount - 1) {
                // 如果不是最后一页，直接使用下一页
                targetPageIndex = currentPage + 1;
                Log.d(TAG, "页面溢出：使用已存在的下一页 " + targetPageIndex);
            } else {
                // 如果是最后一页，创建新页面
                if (currentTime - lastPageCreationTime > PAGE_CREATION_INTERVAL && 
                    cellOverflowListener.canCreateNewPage()) {
                    targetPageIndex = cellOverflowListener.createNewPage();
                    Log.d(TAG, "页面溢出：创建新页面，索引为 " + targetPageIndex);
                    
                    // 更新最后创建页面的时间
                    lastPageCreationTime = currentTime;
                } else {
                    // 如果创建失败或不允许创建，返回false
                    Log.d(TAG, "页面溢出：无法创建新页面，间隔时间不足或不允许创建");
                    return false;
                }
            }
            
            // 标记为跨页面拖拽，并记录源页面
            String packageName = overflowCell.getTag();
            if (packageName != null && !packageName.contains(":cross_page")) {
                overflowCell.setTag(packageName + ":cross_page:" + currentPage + ":" + currentTime);
                Log.d(TAG, "标记跨页面拖拽: " + overflowCell.getTag());
                
                // 保存原始页面，便于拖放失败时返回
                overflowCell.setExpectColumnIndex(0); // 保存当前页面索引到列索引
                overflowCell.setExpectRowIndex(currentPage); // 保存当前页面索引到行索引
            }
            
            // 如果当前拖动的视图为隐藏状态，确保它在目标页面可见
            if (overflowCell.getContentView() != null) {
                overflowCell.getContentView().setVisibility(View.VISIBLE);
                overflowCell.getContentView().setAlpha(1.0f);
            }
            
            // 使用监听器通知溢出处理
            cellOverflowListener.onCellOverflow(overflowCell, currentPage);
            
            // 更新页面切换时间
            lastPageSwitchTime = currentTime;
            
            // 平滑切换到目标页面
            viewPager.setCurrentItem(targetPageIndex, true);
            
            // 通知设置目标页面
            final int finalTargetPageIndex = targetPageIndex;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // 确保目标页面已正确设置
                cellOverflowListener.setupWorkspacePage(finalTargetPageIndex);
                
                // 查找目标页面并设置拖拽监听
                View targetPageView = getViewForPage(viewPager, finalTargetPageIndex);
                if (targetPageView != null) {
                    CellLayout targetPageCellLayout = findCellLayoutInViewGroup(targetPageView);
                    if (targetPageCellLayout != null) {
                        // 确保目标页面的CellLayout也具有相同的监听器和配置
                        targetPageCellLayout.setOnCellOverflowListener(cellOverflowListener);
                        targetPageCellLayout.setRows(rows);
                        targetPageCellLayout.setColumns(columns);
                        
                        // 预留位置
                        targetPageCellLayout.reservePositionForDragCell(overflowCell);
                    }
                }
                
                // 通知适配器数据已更改
                cellOverflowListener.notifyPageAdapterChanged();
            }, 300);
            
            return true;
        }
        
        return false;
    }

    /**
     * 显示页面创建的视觉反馈
     */
    private void showPageCreationFeedback() {
        // 尝试获取Activity上下文
        Context context = getContext();
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            
            // 创建一个自定义视图作为页面创建反馈
            FrameLayout feedbackContainer = new FrameLayout(context);
            feedbackContainer.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            feedbackContainer.setBackgroundResource(android.R.drawable.toast_frame);
            feedbackContainer.setPadding(24, 16, 24, 16);
            
            // 添加文本视图
            TextView textView = new TextView(context);
            textView.setText("创建新页面");
            textView.setTextSize(16);
            textView.setTextColor(Color.WHITE);
            
            // 添加一个简单的图标
            ImageView iconView = new ImageView(context);
            iconView.setImageResource(android.R.drawable.ic_menu_add);
            iconView.setColorFilter(Color.WHITE);
            
            // 水平布局容器
            LinearLayout contentLayout = new LinearLayout(context);
            contentLayout.setOrientation(LinearLayout.HORIZONTAL);
            contentLayout.setGravity(Gravity.CENTER_VERTICAL);
            contentLayout.addView(iconView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            
            // 添加间距
            Space space = new Space(context);
            LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(12, 0);
            contentLayout.addView(space, spaceParams);
            
            // 添加文本
            contentLayout.addView(textView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            
            feedbackContainer.addView(contentLayout);
            
            // 添加到Activity的根视图
            ViewGroup rootView = (ViewGroup) activity.getWindow().getDecorView().findViewById(android.R.id.content);
            rootView.addView(feedbackContainer);
            
            // 设置初始状态
            feedbackContainer.setAlpha(0f);
            feedbackContainer.setScaleX(0.8f);
            feedbackContainer.setScaleY(0.8f);
            
            // 计算位置（屏幕右侧中间位置）
            feedbackContainer.setTranslationX(rootView.getWidth() - feedbackContainer.getWidth() - 32);
            feedbackContainer.setTranslationY(rootView.getHeight() / 2f - feedbackContainer.getHeight() / 2f);
            
            // 应用出现动画
            feedbackContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .setInterpolator(new OvershootInterpolator(0.8f))
                    .withEndAction(() -> {
                        // 保持显示一段时间后自动消失
                        feedbackContainer.animate()
                                .alpha(0f)
                                .scaleX(0.8f)
                                .scaleY(0.8f)
                                .setStartDelay(1200)
                                .setDuration(200)
                                .withEndAction(() -> rootView.removeView(feedbackContainer))
                                .start();
                    })
                    .start();
        } else {
            // 退回到使用Toast的方式
            Toast.makeText(context, "创建新页面", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 更新边缘指示器的视觉反馈
     * @param view 父视图
     * @param progress 靠近边缘的进度 (0-1)
     */

    
    /**
     * 添加脉动动画
     */
    private void addPulseAnimation(View view) {
        // 检查是否已经有脉动动画
        if (view.getTag() != null && "pulsing".equals(view.getTag())) {
            return; // 已经有脉动动画
        }
        
        // 标记视图正在进行脉动动画
        view.setTag("pulsing");
        
        // 创建脉动动画
        Animation pulse = new AlphaAnimation(1.0f, 0.7f);
        pulse.setDuration(400);
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setRepeatMode(Animation.REVERSE);
        
        // 应用动画
        view.startAnimation(pulse);
    }
    
    /**
     * 混合两种颜色
     */
    private int blendColors(int color1, int color2, float ratio) {
        final float inverseRatio = 1f - ratio;
        
        float a = (Color.alpha(color1) * inverseRatio) + (Color.alpha(color2) * ratio);
        float r = (Color.red(color1) * inverseRatio) + (Color.red(color2) * ratio);
        float g = (Color.green(color1) * inverseRatio) + (Color.green(color2) * ratio);
        float b = (Color.blue(color1) * inverseRatio) + (Color.blue(color2) * ratio);
        
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }

    /**
     * 获取指定页面索引的View
     */
    private View getViewForPage(ViewPager2 viewPager, int pageIndex) {
        if (viewPager == null) return null;
        
        try {
            // 获取ViewPager2内部的RecyclerView
            Field recyclerViewField = ViewPager2.class.getDeclaredField("mRecyclerView");
            recyclerViewField.setAccessible(true);
            RecyclerView recyclerView = (RecyclerView) recyclerViewField.get(viewPager);
            
            if (recyclerView != null) {
                // 尝试查找指定页面的视图
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View child = recyclerView.getChildAt(i);
                    RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                    if (holder != null && holder.getAdapterPosition() == pageIndex) {
                        return child;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取页面视图失败: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * 拖动结束时确保所有图标可见
     */
    private void ensureAllIconsVisible() {
        if (cells != null) {
            for (Cell cell : cells) {
                if (cell != null && cell.getContentView() != null && 
                    !cell.getTag().equals("empty")) {
                    
                    View iconView = cell.getContentView();
                    // 取消所有可能正在进行的动画
                    iconView.clearAnimation();
                    iconView.animate().cancel();
                    
                    // 立即设置图标可见，不使用动画
                    iconView.setVisibility(View.VISIBLE);
                    iconView.setAlpha(1.0f);
                    iconView.setScaleX(1.0f);
                    iconView.setScaleY(1.0f);
                    iconView.setTranslationX(0);
                    iconView.setTranslationY(0);
                    
                    // 移除所有临时标签
                    iconView.setTag(R.id.preview_original_position, null);
                }
            }
            
            // 请求重新布局和绘制
            requestLayout();
            invalidate();
        }
    }

    /**
     * 获取MainActivity中的ViewPager2实例
     * @return ViewPager2实例，如果找不到返回null
     */
    private ViewPager2 getMainActivityViewPager() {
        try {
            // 从上下文中获取Activity
            Context context = getContext();
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                
                // 尝试通过不同可能的ID查找ViewPager2
                ViewPager2 viewPager = null;
                
                // 尝试查找常见的ViewPager2 ID
                int[] possibleIds = {
                    activity.getResources().getIdentifier("viewpager", "id", activity.getPackageName()),
                    activity.getResources().getIdentifier("view_pager", "id", activity.getPackageName()),
                    activity.getResources().getIdentifier("main_viewpager", "id", activity.getPackageName()),
                    activity.getResources().getIdentifier("workspace_pager", "id", activity.getPackageName())
                };
                
                for (int id : possibleIds) {
                    if (id != 0) {
                        viewPager = activity.findViewById(id);
                        if (viewPager != null) {
                            Log.d(TAG, "通过ID找到ViewPager2: " + id);
                            // 注册页面变化回调，用于记录页面滑动日志
                            registerPageChangeCallback(viewPager);
                            return viewPager;
                        }
                    }
                }
                
                // 如果通过ID未找到，回退到原始方法查找ViewPager2
                ViewParent parent = getParent();
                while (parent != null && !(parent.getParent() instanceof ViewPager2)) {
                    parent = parent.getParent();
                }
                
                if (parent != null) {
                    ViewGroup viewPagerContent = (ViewGroup) parent;
                    viewPager = (ViewPager2) viewPagerContent.getParent();
                    // 注册页面变化回调，用于记录页面滑动日志
                    registerPageChangeCallback(viewPager);
                    return viewPager;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取MainActivity的ViewPager2失败: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    // 页面变化回调对象，防止重复注册
    private ViewPager2.OnPageChangeCallback pageChangeCallback;
    
    /**
     * 注册ViewPager2页面变化回调，用于记录页面滑动日志
     * @param viewPager ViewPager2实例
     */
    private void registerPageChangeCallback(ViewPager2 viewPager) {
        if (viewPager == null || pageChangeCallback != null) return;
        
        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                Log.d(TAG, "页面滑动中: 位置=" + position + ", 偏移=" + positionOffset);
            }
            
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Log.d(TAG, "页面选择: 位置=" + position);
            }
            
            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                String stateString;
                switch (state) {
                    case ViewPager2.SCROLL_STATE_IDLE:
                        stateString = "空闲";
                        break;
                    case ViewPager2.SCROLL_STATE_DRAGGING:
                        stateString = "拖拽中";
                        break;
                    case ViewPager2.SCROLL_STATE_SETTLING:
                        stateString = "settling";
                        break;
                    default:
                        stateString = "未知状态";
                }
                Log.d(TAG, "页面滑动状态改变: " + stateString);
            }
        };
        
        viewPager.registerOnPageChangeCallback(pageChangeCallback);
        Log.d(TAG, "已注册ViewPager2页面变化回调");
    }
    
    /**
     * 当CellLayout被回收时，取消注册页面变化回调
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        
        // 取消注册页面变化回调
        if (pageChangeCallback != null) {
            ViewPager2 viewPager = getMainActivityViewPager();
            if (viewPager != null) {
                viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
                Log.d(TAG, "已取消注册ViewPager2页面变化回调");
            }
            pageChangeCallback = null;
        }
    }

    /**
     * 在视图组中递归查找CellLayout
     * @return 找到的CellLayout，如果没有找到则返回null
     */
    private CellLayout findCellLayoutInViewGroup(View view) {
        // 如果视图本身就是CellLayout，直接返回
        if (view instanceof CellLayout) {
            return (CellLayout) view;
        }
        
        // 如果是ViewGroup，遍历其子视图
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                // 递归搜索
                CellLayout result = findCellLayoutInViewGroup(child);
                if (result != null) {
                    return result;
                }
            }
        }
        
        // 没有找到
        return null;
    }
    
    /**
     * 为拖拽的单元格预留位置
     * @param dragCell 被拖拽的单元格
     */
    public void reservePositionForDragCell(Cell dragCell) {
        if (dragCell == null || cells == null || cells.isEmpty()) {
            return;
        }
        
        // 查找第一个空白单元格位置
        int emptyIndex = -1;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).getTag().equals("empty")) {
                emptyIndex = i;
                break;
            }
        }
        
        if (emptyIndex != -1) {
            // 找到空位，预留位置
            Log.d(TAG, "为拖拽的单元格预留位置，索引: " + emptyIndex);
            
            // 设置高亮预留区域
            int reserveRow = emptyIndex / columns;
            int reserveColumn = emptyIndex % columns;
            
            // 设置高亮区域
            highLightRow = reserveRow;
            highLightColumn = reserveColumn;
            highLightValid = true;
            
            // 启用高亮动画
            targetHighlightAlpha = 1.0f;
            highlightAlpha = 0.3f;
            lastHighlightUpdateTime = System.currentTimeMillis();
            
            // 强制重绘
            invalidate();
            
            // 延迟取消高亮
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                fadeOutHighlight();
            }, 1500);
        }
    }


    // 检查是否处于拖拽状态
    public static boolean isInDragging() {
        return isInDragging;
    }

    // 设置拖拽状态
    public static void setDraggingState(boolean dragging) {
        isInDragging = dragging;
        Log.d("CellLayout", "设置全局拖拽状态: " + dragging);
    }

}