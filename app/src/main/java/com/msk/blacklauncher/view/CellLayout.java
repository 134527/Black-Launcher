package com.msk.blacklauncher.view;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 * Created by chenqiao on 2016/10/26.
 */
public class CellLayout extends ViewGroup implements View.OnDragListener {

    private int columns = 6;
    private int rows = 4;
    private int per_cell_width;
    private int per_cell_height;
    private ArrayList<Cell> cells;
    private ArrayList<Cell> needRemoveCells;
    private boolean[][] cellHolds;
    private int highLightColumn = -1;
    private int highLightRow = -1;
    private boolean highLightValid = false;

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
        // 启用绘制
        setWillNotDraw(false);
    }


    public void addCell(Cell cell) {
        cells.add(cell);
        if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0) {
            //如果界面已经显示了，那么立刻进行一次位置计算
            initCell(cell);
            Point p = findLeftAndTop(cell);
            cell.setExpectRowIndex(p.x);
            cell.setExpectColumnIndex(p.y);
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
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // 计算单元格尺寸
        per_cell_width = (r - l) / columns;
        per_cell_height = (b - t) / rows;

        // 清空现有占位信息
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                cellHolds[i][j] = false;
            }
        }

        // 整理需要移除的单元格
        needRemoveCells.clear();

        // 排序单元格 - 优先处理已有位置的单元格
        List<Cell> orderedCells = new ArrayList<>(cells);
        orderedCells.sort((c1, c2) -> {
            boolean hasPos1 = c1.getExpectColumnIndex() >= 0 && c1.getExpectRowIndex() >= 0;
            boolean hasPos2 = c2.getExpectColumnIndex() >= 0 && c2.getExpectRowIndex() >= 0;
            return hasPos1 == hasPos2 ? 0 : (hasPos1 ? -1 : 1);
        });

        // 逐个布局单元格
        for (Cell cell : orderedCells) {
            if (cell == null || cell.getContentView() == null) {
                needRemoveCells.add(cell);
                continue;
            }

            // 确定单元格位置
            int locateX, locateY;

            if (cell.getExpectColumnIndex() >= 0 && cell.getExpectRowIndex() >= 0) {
                // 使用期望位置
                locateX = Math.min(cell.getExpectColumnIndex(), columns - 1);
                locateY = Math.min(cell.getExpectRowIndex(), rows - 1);

                // 检查位置是否可用
                if (!checkIsEnough(cellHolds, locateX, locateY, cell.getWidthNum(), cell.getHeightNum())) {
                    // 尝试找新位置
                    Point p = findLeftAndTop(cell);
                    if (p.x == -1 || p.y == -1) {
                        // 处理溢出
                        if (cellOverflowListener != null && cellOverflowListener.onCellOverflow(cell)) {
                            needRemoveCells.add(cell);
                            continue;
                        } else {
                            // 作为备用方案，尽量放在左上角
                            locateX = 0;
                            locateY = 0;
                        }
                    } else {
                        locateX = p.x;
                        locateY = p.y;
                        // 更新期望位置
                        cell.setExpectColumnIndex(locateX);
                        cell.setExpectRowIndex(locateY);
                    }
                }
            } else {
                // 查找新位置
                Point p = findLeftAndTop(cell);
                if (p.x == -1 || p.y == -1) {
                    // 报告溢出
                    if (cellOverflowListener != null && cellOverflowListener.onCellOverflow(cell)) {
                        needRemoveCells.add(cell);
                        continue;
                    } else {
                        // 备用方案
                        locateX = 0;
                        locateY = 0;
                    }
                } else {
                    locateX = p.x;
                    locateY = p.y;
                    // 设置期望位置
                    cell.setExpectColumnIndex(locateX);
                    cell.setExpectRowIndex(locateY);
                }
            }

            // 标记位置为已占用
            fillCellLayout(locateX, locateY, cell.getWidthNum(), cell.getHeightNum());

            // 计算实际布局位置和尺寸
            int nowL = locateX * per_cell_width;
            int nowT = locateY * per_cell_height;
            int cellWidth = Math.min(cell.getWidthNum() * per_cell_width, getWidth() - nowL);
            int cellHeight = Math.min(cell.getHeightNum() * per_cell_height, getHeight() - nowT);

            // 设置视图大小和位置
            View contentView = cell.getContentView();
            ViewGroup.LayoutParams params = contentView.getLayoutParams();
            if (params == null) {
                params = new ViewGroup.LayoutParams(cellWidth, cellHeight);
                contentView.setLayoutParams(params);
            } else {
                params.width = cellWidth;
                params.height = cellHeight;
            }

            // 布局视图
            contentView.layout(nowL, nowT, nowL + cellWidth, nowT + cellHeight);
        }

        // 移除需要移除的单元格
        for (Cell cell : needRemoveCells) {
            if (cell != null && cell.getContentView() != null) {
                removeView(cell.getContentView());
            }
            cells.remove(cell);
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
        Cell cell = (Cell) event.getLocalState();
        if (cell == null) return false;

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                // 深拷贝当前布局状态
                tempCellHolds = new boolean[rows][];
                for (int i = 0; i < rows; i++) {
                    tempCellHolds[i] = cellHolds[i].clone();
                }
                // 清空拖动 Cell 原位置
                if (cell.getExpectColumnIndex() >= 0 && cell.getExpectRowIndex() >= 0) {
                    for (int i = cell.getExpectColumnIndex();
                         i < cell.getExpectColumnIndex() + cell.getWidthNum(); i++) {
                        for (int j = cell.getExpectRowIndex();
                             j < cell.getExpectRowIndex() + cell.getHeightNum(); j++) {
                            if (j < rows && i < columns) {
                                tempCellHolds[j][i] = false;
                            }
                        }
                    }
                }
                break;
            case DragEvent.ACTION_DRAG_LOCATION:
                // 计算当前悬停位置
                int col = (int) (event.getX() / per_cell_width);
                int row = (int) (event.getY() / per_cell_height);

                // 防止越界
                col = Math.max(0, Math.min(col, columns - 1));
                row = Math.max(0, Math.min(row, rows - 1));

                // 更新高亮区域
                if (col != highLightColumn || row != highLightRow) {
                    highLightColumn = col;
                    highLightRow = row;

                    // 检查位置是否有效
                    highLightValid = checkIsEnough(cellHolds, col, row, cell.getWidthNum(), cell.getHeightNum());
                    invalidate();
                }
                break;

            case DragEvent.ACTION_DROP:
                // 确保拖拽中的视图不为null
                if (cell.getContentView() == null) {
                    return false;
                }

                int targetCol = (int) (event.getX() / per_cell_width);
                int targetRow = (int) (event.getY() / per_cell_height);

                // 确保目标位置在边界内
                targetCol = Math.max(0, Math.min(targetCol, columns - cell.getWidthNum()));
                targetRow = Math.max(0, Math.min(targetRow, rows - cell.getHeightNum()));

                // 使用交换方法处理放置
                swapCells(cell, targetCol, targetRow);
                cell.getContentView().setVisibility(View.VISIBLE);

                // 清除高亮
                highLightColumn = -1;
                highLightRow = -1;
                invalidate();
                break;

            case DragEvent.ACTION_DRAG_ENDED:
                if (!event.getResult()) {
                    // 拖拽取消，恢复原状态
                    cell.getContentView().setVisibility(View.VISIBLE);
                }
                // 清除高亮
                highLightColumn = -1;
                highLightRow = -1;
                invalidate();
                break;
        }
        return true;
    }

    // 添加安全清除原位置的方法
    private void clearOriginalPosition(Cell cell) {
        int startX = cell.getExpectColumnIndex();
        int startY = cell.getExpectRowIndex();
        int width = cell.getWidthNum();
        int height = cell.getHeightNum();

        // 检查位置是否有效
        if (startX < 0 || startY < 0 || startX + width > columns || startY + height > rows) {
            return;
        }

        // 清空原位置
        for (int i = startX; i < startX + width; i++) {
            for (int j = startY; j < startY + height; j++) {
                tempCellHolds[j][i] = false;
            }
        }
    }
    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (highLightColumn != -1 && highLightRow != -1) {
            Paint paint = new Paint();
            paint.setColor(highLightValid ? Color.argb(100, 0, 255, 0) : Color.argb(100, 255, 0, 0));
            int left = highLightColumn * per_cell_width;
            int top = highLightRow * per_cell_height;
            int right = left + (per_cell_width * childExpectCellWidthNum); // 使用拖拽中的Cell尺寸
            int bottom = top + (per_cell_height * childExpectCellHeightNum);
            canvas.drawRect(left, top, right, bottom, paint);
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
            this.contentView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    DragShadowBuilder builder = new DragShadowBuilder(v);
                    v.setVisibility(View.INVISIBLE);
                    v.startDrag(null, builder, Cell.this, 0);
                    return true;
                }
            });
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

    public void swapCells(Cell draggingCell, int targetColumnIndex, int targetRowIndex) {
        // 保存原位置
        int originalColumnIndex = draggingCell.getExpectColumnIndex();
        int originalRowIndex = draggingCell.getExpectRowIndex();

        // 查找目标位置是否已有Cell
        Cell targetCell = null;
        for (Cell cell : cells) {
            if (cell.getExpectColumnIndex() == targetColumnIndex &&
                    cell.getExpectRowIndex() == targetRowIndex) {
                targetCell = cell;
                break;
            }
        }

        // 清除拖动的Cell原位置
        for (int i = originalColumnIndex; i < originalColumnIndex + draggingCell.getWidthNum(); i++) {
            for (int j = originalRowIndex; j < originalRowIndex + draggingCell.getHeightNum(); j++) {
                if (j < rows && i < columns) {
                    cellHolds[j][i] = false;
                }
            }
        }

        // 如果目标位置有Cell，将其移动到原位置
        if (targetCell != null) {
            // 清除目标Cell原位置
            for (int i = targetCell.getExpectColumnIndex();
                 i < targetCell.getExpectColumnIndex() + targetCell.getWidthNum(); i++) {
                for (int j = targetCell.getExpectRowIndex();
                     j < targetCell.getExpectRowIndex() + targetCell.getHeightNum(); j++) {
                    if (j < rows && i < columns) {
                        cellHolds[j][i] = false;
                    }
                }
            }

            // 将目标Cell放到拖动Cell的原位置
            targetCell.setExpectColumnIndex(originalColumnIndex);
            targetCell.setExpectRowIndex(originalRowIndex);

            // 填充目标Cell的新位置
            fillCellLayout(originalColumnIndex, originalRowIndex,
                    targetCell.getWidthNum(), targetCell.getHeightNum());
        }

        // 填充拖动的Cell的新位置
        draggingCell.setExpectColumnIndex(targetColumnIndex);
        draggingCell.setExpectRowIndex(targetRowIndex);
        fillCellLayout(targetColumnIndex, targetRowIndex,
                draggingCell.getWidthNum(), draggingCell.getHeightNum());

        // 重新布局
        requestLayout();
    }
    // 添加一个监听器接口，当没有空间放置应用图标时调用
    public interface OnCellOverflowListener {
        /**
         * 当 CellLayout 无法为 Cell 找到足够空间时调用
         * @param overflowCell 无法放置的Cell
         * @return 如果返回true，表示已处理溢出；如果返回false，CellLayout将移除此Cell
         */
        boolean onCellOverflow(Cell overflowCell);
    }

    private OnCellOverflowListener cellOverflowListener;

    public void setOnCellOverflowListener(OnCellOverflowListener listener) {
        this.cellOverflowListener = listener;
    }


}