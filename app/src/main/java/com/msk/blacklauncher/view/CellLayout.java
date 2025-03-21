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
        int nowL, nowT, locateX, locateY;
        int cellWidth, cellHeight;
        needRemoveCells.clear();
        for (Cell cell : cells) {
            if (cell.getExpectColumnIndex() >= 0 && cell.getExpectRowIndex() >= 0) {
                locateX = cell.getExpectColumnIndex();
                locateY = cell.getExpectRowIndex();
            } else {
                Point p = findLeftAndTop(cell);
                cell.setExpectRowIndex(p.x);
                cell.setExpectColumnIndex(p.y);
                locateX = cell.getExpectColumnIndex();
                locateY = cell.getExpectRowIndex();
                if (p.x == -1 || p.y == -1) {
                    Log.e("CellLayout", "onLayout: child is to large or to much children");
                    needRemoveCells.add(cell);
                    continue;
                }
            }
            nowL = locateX * per_cell_width;
            nowT = locateY * per_cell_height;
            cellWidth = cell.getWidthNum() * per_cell_width;
            cellHeight = cell.getHeightNum() * per_cell_height;
            //修改cell的layoutparam的大小，不然会导致cell的view中的gravity失效
            cell.getContentView().getLayoutParams().width = cellWidth;
            cell.getContentView().getLayoutParams().height = cellHeight;
            cell.getContentView().layout(nowL, nowT, nowL + cellWidth, nowT + cellHeight);
        }
        for (Cell needRemoveCell : needRemoveCells) {
            removeView(needRemoveCell.getContentView());
        }
        cells.removeAll(needRemoveCells);
//        Log.d("CellLayout", "onLayout: ==============================");
//        for (boolean[] cellHold : cellHolds) {
//            StringBuilder builder = new StringBuilder();
//            for (boolean b1 : cellHold) {
//                builder.append(b1 + " ");
//            }
//            Log.d("CellLayout", "onLayout: " + builder.toString());
//        }
//        Log.d("CellLayout", "onLayout: ==============================");
    }

    //查找足够空间放置cell
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

            case DragEvent.ACTION_DROP:
                // 计算目标位置
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

}