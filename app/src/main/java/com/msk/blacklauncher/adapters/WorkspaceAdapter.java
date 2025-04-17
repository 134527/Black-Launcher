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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.view.CellLayout;

import java.util.List;

/**
 * 工作区页面适配器，处理多页应用布局和拖拽逻辑
 */
public class WorkspaceAdapter extends RecyclerView.Adapter<WorkspaceAdapter.PageViewHolder> {
    private static final String TAG = "WorkspaceAdapter";
    
    // 视图保持器
    public static class PageViewHolder extends RecyclerView.ViewHolder {
        public CellLayout cellLayout;
        public int pageIndex;
        
        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
            cellLayout = itemView.findViewById(R.id.workspace_grid);
        }
    }
    
    private Context context;
    private CellLayout.OnCellOverflowListener cellOverflowListener;
    
    public WorkspaceAdapter(CellLayout.OnCellOverflowListener listener) {
        this.cellOverflowListener = listener;
    }
    
    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 缓存上下文
        if (context == null) {
            context = parent.getContext();
        }
        
        // 加载页面布局
        View itemView = LayoutInflater.from(context).inflate(
                R.layout.item_workspace_page, parent, false);
        
        PageViewHolder holder = new PageViewHolder(itemView);
        
        // 设置拖拽事件监听器
        setupDragListener(holder);
        
        return holder;
    }
    
    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        // 保存页面索引
        holder.pageIndex = position;
        
        // 设置页面标识
        holder.itemView.setTag("page_" + position);
        
        // 设置CellLayout
        if (holder.cellLayout != null) {
            // 初始化CellLayout
            holder.cellLayout.setColumns(9); // 设置与WorkspaceFragment相同的列数
            holder.cellLayout.setRows(4);    // 设置与WorkspaceFragment相同的行数
            
            // 清空现有单元格
            holder.cellLayout.clearCells();
            
            // 设置单元格溢出监听器
            holder.cellLayout.setOnCellOverflowListener(cellOverflowListener);
            
            // 让Fragment负责填充单元格
            if (cellOverflowListener != null) {
                // 在UI线程中异步调用，确保视图准备就绪
                new Handler(Looper.getMainLooper()).post(() -> {
                    cellOverflowListener.setupWorkspacePage(position);
                });
            }
        }
    }
    
    @Override
    public int getItemCount() {
        // 返回页面数量，通常从Fragment获取
        // 由于我们在此类不维护页面数据，页面数量由外部提供
        return 10; // 最大支持10页，实际使用时按需显示
    }
    
    /**
     * 设置页面拖拽监听器
     */
    private void setupDragListener(PageViewHolder holder) {
        // 页面视图拖拽监听器
        holder.itemView.setOnDragListener((v, event) -> {
            if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED) {
                Log.d(TAG, "拖拽进入页面: " + holder.pageIndex);
                
                // 可以在这里添加页面切换逻辑以支持跨页面拖拽
                // 例如，当拖拽进入边缘区域时自动切换到相邻页面
                
                return true;
            }
            return false;
        });
    }
} 