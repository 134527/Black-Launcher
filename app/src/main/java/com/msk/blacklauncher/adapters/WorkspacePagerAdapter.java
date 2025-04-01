package com.msk.blacklauncher.adapters;

import android.content.Context;
import android.os.Handler;
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
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position)  {
        // 日志跟踪
        Log.d(TAG, "绑定页面: " + position);

        // 设置页面标记用于识别
        holder.itemView.setTag("page_" + position);

        // 配置CellLayout
        if (holder.cellLayout != null) {
            holder.cellLayout.setColumns(columns);
            holder.cellLayout.setRows(rows);

            // 清空现有视图和单元格，但保持布局结构
            holder.cellLayout.clearCells();

            // 获取页面单元格
            if (position < pages.size()) {
                List<CellLayout.Cell> pageCells = pages.get(position);
                if (pageCells != null) {
                    Log.d(TAG, "页面 " + position + " 有 " + pageCells.size() + " 个单元格");

                    // 添加所有单元格以维持格局
                    for (int i = 0; i < pageCells.size(); i++) {
                        CellLayout.Cell cell = pageCells.get(i);
                        if (cell != null) {
                            try {
                                // 为所有单元格(包括空单元格)添加到CellLayout
                                if (cell.getContentView() != null) {
                                    // 根据单元格类型设置可见性
                                    boolean isEmpty = "empty".equals(cell.getTag());
                                    cell.getContentView().setVisibility(isEmpty ?
                                            View.INVISIBLE : View.VISIBLE);

                                    // 确保非空单元格的子视图可见
                                    if (!isEmpty && cell.getContentView() instanceof ViewGroup) {
                                        ViewGroup container = (ViewGroup) cell.getContentView();
                                        for (int j = 0; j < container.getChildCount(); j++) {
                                            View childView = container.getChildAt(j);
                                            childView.setVisibility(View.VISIBLE);
                                        }
                                    }

                                    // 添加单元格
                                    holder.cellLayout.addCell(cell);

                                    if (!isEmpty) {
                                        Log.d(TAG, "成功添加非空单元格 " + i + ": " + cell.getTag());
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "添加单元格失败: " + cell.getTag(), e);
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