package com.msk.blacklauncher.adapters;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.view.CellLayout;

import java.util.List;

/**
 * 工作区页面适配器，处理多页应用布局和拖拽逻辑
 */
public class WorkspaceAdapter extends RecyclerView.Adapter<WorkspaceAdapter.WorkspacePageHolder> {
    private static final String TAG = "WorkspaceAdapter";
    
    private CellLayout.OnCellOverflowListener cellOverflowListener;
    private boolean isDragging = false;
    private ViewPager2 parentViewPager;
    
    private boolean isScrolling = false;
    private long lastScrollTime = 0;
    private static final long SCROLL_THROTTLE_MS = 500; // 500 milliseconds
    private static final long SCROLL_DELAY_MS = 500; // 500 milliseconds
    
    public WorkspaceAdapter(CellLayout.OnCellOverflowListener listener) {
        this.cellOverflowListener = listener;
        Log.d(TAG, "WorkspaceAdapter已创建，确保指示器默认为GONE");
    }
    
    @NonNull
    @Override
    public WorkspacePageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_workspace_page, parent, false);
        
        // 查找父ViewPager2用于页面切换操作
        if (parent.getParent() instanceof ViewPager2) {
            parentViewPager = (ViewPager2) parent.getParent();
        }
        
        return new WorkspacePageHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull WorkspacePageHolder holder, int position) {
        // 设置页面标签，用于在拖动时识别当前页面
        holder.itemView.setTag("page_" + position);
        
        // 获取CellLayout并设置监听器
        CellLayout cellLayout = holder.itemView.findViewById(R.id.workspace_grid);
        cellLayout.setOnCellOverflowListener(cellOverflowListener);
        
        // 设置drag indicator指示器
        View leftIndicator = holder.itemView.findViewById(R.id.drag_indicator_left);
        View rightIndicator = holder.itemView.findViewById(R.id.drag_indicator_right);
        
        // 左侧指示器仅在页面索引大于0时显示，否则隐藏
        // 右侧指示器仅在页面索引小于最大页面索引时显示，否则隐藏
        // 初始状态都隐藏，拖拽时才显示
        leftIndicator.setVisibility(View.GONE);
        rightIndicator.setVisibility(View.GONE);
        
        // 设置拖拽监听器
        setupDragListener(holder, position);
        
        // 通知监听器设置此页面
        if (cellOverflowListener != null) {
            cellOverflowListener.setupWorkspacePage(position);
        }
    }
    
    @Override
    public int getItemCount() {
        // 从Fragment中获取页面数量
        if (cellOverflowListener != null) {
            return cellOverflowListener.getPageCount();
        }
        return 0;
    }
    
    /**
     * 设置页面拖拽监听器
     */
    private void setupDragListener(WorkspacePageHolder holder, int position) {
        // 获取边缘指示器
        View leftIndicator = holder.itemView.findViewById(R.id.drag_indicator_left);
        View rightIndicator = holder.itemView.findViewById(R.id.drag_indicator_right);
        
        // 页面视图拖拽监听器
        holder.itemView.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    isDragging = true;
                    
                    // 设置左侧指示器，非第一页可见
                    if (position > 0) {
                        leftIndicator.setVisibility(View.VISIBLE);
                        leftIndicator.setAlpha(0.5f);
                    } else {
                        leftIndicator.setVisibility(View.GONE);
                    }
                    
                    // 显示右侧指示器，当有下一页或可创建新页面时
                    if (position < getItemCount() - 1 || (cellOverflowListener != null && cellOverflowListener.canCreateNewPage())) {
                        rightIndicator.setVisibility(View.VISIBLE);
                        rightIndicator.setAlpha(0.5f);
                    } else {
                        rightIndicator.setVisibility(View.GONE);
                    }
                    return true;
                
                case DragEvent.ACTION_DRAG_ENTERED:
                    Log.d(TAG, "拖拽进入页面: " + position);
                    
                    // 播放页面进入动画
                    playPageEnterAnimation(holder.itemView);
                    
                    // 确保CellLayout能接收拖拽
                    CellLayout cellLayout = holder.itemView.findViewById(R.id.workspace_grid);
                    if (cellLayout != null) {
                        // 确保CellLayout有正确的监听器
                        cellLayout.setOnDragListener(cellLayout);
                        
                        // 如果是跨页面拖拽，确保单元格布局已经准备好
                        if (cellOverflowListener != null) {
                            cellOverflowListener.setupWorkspacePage(position);
                        }
                    }
                    
                    return true;
                
                case DragEvent.ACTION_DRAG_LOCATION:
                    // 处理边缘区域检测，实现页面自动切换
                    handleEdgeDetection(event, holder.itemView, position);
                    return true;
                
                case DragEvent.ACTION_DRAG_EXITED:
                    Log.d(TAG, "拖拽离开页面: " + position);
                    return true;
                
                case DragEvent.ACTION_DROP:
                    Log.d(TAG, "放置在页面: " + position);
                    // 隐藏指示器
                    leftIndicator.setVisibility(View.GONE);
                    rightIndicator.setVisibility(View.GONE);
                    isDragging = false;
                    // 重置滚动状态，防止下次拖拽时出现问题
                    isScrolling = false;
                    return false; // 让CellLayout处理实际的放置操作
                
                case DragEvent.ACTION_DRAG_ENDED:
                    Log.d(TAG, "拖拽结束");
                    // 隐藏所有指示器
                    leftIndicator.setVisibility(View.GONE);
                    rightIndicator.setVisibility(View.GONE);
                    isDragging = false;
                    // 重置滚动相关状态
                    isScrolling = false;
                    
                    // 记录日志以便追踪问题
                    boolean success = event.getResult();
                    Log.d(TAG, "拖拽结果: " + (success ? "成功" : "失败"));
                    
                    return true;
            }
            return false;
        });
    }
    
    /**
     * 处理拖拽到边缘区域的逻辑
     */
    private void handleEdgeDetection(DragEvent event, View itemView, int position) {
        if (parentViewPager == null) return;
        
        try {
            // 获取拖拽位置相对于页面的百分比
            float x = event.getX();
            float pageWidth = itemView.getWidth();
            float positionPercent = x / pageWidth;
            
            // 定义边缘区域大小（百分比）
            float edgeThreshold = 0.15f;  
            
            // 同时检测左侧和右侧边缘
            boolean isRightEdge = positionPercent > (1 - edgeThreshold) && position < getItemCount() - 1;
            boolean isLeftEdge = positionPercent < edgeThreshold && position > 0;
            
            // 获取边缘指示器
            View leftIndicator = itemView.findViewById(R.id.drag_indicator_left);
            View rightIndicator = itemView.findViewById(R.id.drag_indicator_right);
            
            // 记录当前时间用于节流控制
            long currentTime = System.currentTimeMillis();
            
            // 根据位置和页面状态设置指示器可见性
            // 左侧指示器只在不是第一页时显示
            if (position > 0) {
                leftIndicator.setVisibility(View.VISIBLE);
                leftIndicator.setAlpha(0.5f);
            } else {
                leftIndicator.setVisibility(View.GONE);
            }
            
            // 右侧指示器只在有下一页或可以创建新页面时显示
            if (position < getItemCount() - 1 || (cellOverflowListener != null && cellOverflowListener.canCreateNewPage())) {
                rightIndicator.setVisibility(View.VISIBLE);
                rightIndicator.setAlpha(0.5f);
            } else {
                rightIndicator.setVisibility(View.GONE);
            }
            
            // 静态变量控制页面切换状态
            if (isScrolling && currentTime - lastScrollTime < SCROLL_THROTTLE_MS) {
                return; // 如果正在滚动中且未超过阈值时间，则不触发
            }
            
            // 更新指示器状态并处理页面切换
            if (isRightEdge) {
                // 增强右侧指示器
                rightIndicator.setBackgroundColor(Color.argb(100, 100, 180, 255));
                rightIndicator.animate().alpha(0.9f).scaleX(1.2f).setDuration(100).start();
                
                // 检查当前是否已经在动画切换中
                if (parentViewPager.getCurrentItem() == position && !isScrolling) {
                    // 设置滚动状态和时间
                    isScrolling = true;
                    lastScrollTime = currentTime;
                    
                    try {
                        // 切换到下一页，使用平滑动画
                        parentViewPager.setCurrentItem(position + 1, true);
                        
                        // 记录日志
                        Log.d(TAG, "拖拽到右侧边缘，切换到页面: " + (position + 1));
                    } catch (Exception e) {
                        Log.e(TAG, "切换页面失败: " + e.getMessage());
                        isScrolling = false;
                    }
                    
                    // 延迟恢复滚动状态，防止连续快速切换
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        isScrolling = false;
                    }, SCROLL_DELAY_MS);
                }
            } else if (isLeftEdge) {
                // 增强左侧指示器
                leftIndicator.setBackgroundColor(Color.argb(100, 100, 180, 255));
                leftIndicator.animate().alpha(0.9f).scaleX(1.2f).setDuration(100).start();
                
                // 检查当前是否已经在动画切换中
                if (parentViewPager.getCurrentItem() == position && !isScrolling) {
                    // 设置滚动状态和时间
                    isScrolling = true;
                    lastScrollTime = currentTime;
                    
                    try {
                        // 切换到上一页，使用平滑动画
                        parentViewPager.setCurrentItem(position - 1, true);
                        
                        // 记录日志
                        Log.d(TAG, "拖拽到左侧边缘，切换到页面: " + (position - 1));
                    } catch (Exception e) {
                        Log.e(TAG, "切换页面失败: " + e.getMessage());
                        isScrolling = false;
                    }
                    
                    // 延迟恢复滚动状态，防止连续快速切换
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        isScrolling = false;
                    }, SCROLL_DELAY_MS);
                }
            } else {
                // 重置指示器
                if (leftIndicator.getVisibility() == View.VISIBLE) {
                    leftIndicator.setBackgroundColor(Color.argb(50, 255, 255, 255));
                    leftIndicator.animate().alpha(0.5f).scaleX(1.0f).setDuration(100).start();
                }
                
                if (rightIndicator.getVisibility() == View.VISIBLE) {
                    rightIndicator.setBackgroundColor(Color.argb(50, 255, 255, 255));
                    rightIndicator.animate().alpha(0.5f).scaleX(1.0f).setDuration(100).start();
                }
            }
        } catch (Exception e) {
            // 捕获并记录任何异常，防止崩溃
            Log.e(TAG, "边缘检测异常: " + e.getMessage());
            isScrolling = false;
        }
    }
    
    /**
     * 播放页面进入动画
     */
    private void playPageEnterAnimation(View pageView) {
        // 设置初始状态，不缩放
        pageView.setScaleX(1.0f);
        pageView.setScaleY(1.0f);
        pageView.setAlpha(0.9f);
        
        // 只创建透明度动画，保持大小不变
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 1.0f);
        
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(pageView, alpha);
        animator.setDuration(200);
        animator.start();
    }
    
    static class WorkspacePageHolder extends RecyclerView.ViewHolder {
        public WorkspacePageHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
} 