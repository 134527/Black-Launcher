package com.msk.blacklauncher.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.view.CellLayout;

import java.util.List;

public class WorkspacePagerAdapter extends RecyclerView.Adapter<WorkspacePagerAdapter.PageViewHolder> {
    private static final String TAG = "WorkspacePagerAdapter";

    private final Context context;
    private final List<List<CellLayout.Cell>> pages;
    private final int columns;
    private final int rows;
    private final OnCellActionListener cellActionListener;

    public interface OnCellActionListener {
        void onCellSwapped(CellLayout.Cell draggingCell, int fromPage, int toPage, int targetColumn, int targetRow);
        void saveAppPositions();
        void setupWorkspacePage(int pageIndex);
        boolean onCellOverflow(CellLayout.Cell overflowCell, int pageIndex);
    }

    // 页面ViewHolder
    public static class PageViewHolder extends RecyclerView.ViewHolder {
        public CellLayout cellLayout;

        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
            cellLayout = itemView.findViewById(R.id.workspace_grid);
        }
    }

    public WorkspacePagerAdapter(Context context, List<List<CellLayout.Cell>> pages, int columns, int rows) {
        this(context, pages, columns, rows, null);
    }

    public WorkspacePagerAdapter(Context context, List<List<CellLayout.Cell>> pages, int columns, int rows,
                                 OnCellActionListener listener) {
        this.context = context;
        this.pages = pages;
        this.columns = columns;
        this.rows = rows;
        this.cellActionListener = listener;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View pageView = LayoutInflater.from(context).inflate(R.layout.item_workspace_page, parent, false);
        pageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        return new PageViewHolder(pageView);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        // 配置CellLayout
        if (holder.cellLayout != null) {
            holder.cellLayout.setColumns(columns);
            holder.cellLayout.setRows(rows);

            // 清空现有视图
            holder.cellLayout.removeAllViews();

            // 设置溢出监听器
            holder.cellLayout.setOnCellOverflowListener(overflowCell -> {
                Log.d(TAG, "单元格溢出: " + (overflowCell != null ? overflowCell.getTag() : "null"));
                if (cellActionListener != null) {
                    return cellActionListener.onCellOverflow(overflowCell, position);
                }
                return false;
            });

            // 获取页面单元格
            if (position < pages.size()) {
                List<CellLayout.Cell> pageCells = pages.get(position);
                if (pageCells != null) {
                    // 添加单元格
                    for (CellLayout.Cell cell : pageCells) {
                        if (cell != null && !cell.getTag().equals("empty") && cell.getContentView() != null) {
                            try {
                                holder.cellLayout.addCell(cell);
                            } catch (Exception e) {
                                Log.e(TAG, "添加单元格失败", e);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }
}