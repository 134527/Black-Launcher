package com.msk.blacklauncher.view;


import android.content.ClipData;
import android.content.ClipDescription;
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

    private static final String TAG = "CellLayout";
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

    // 在 CellLayout 中添加设置单元格可拖拽的方法
    private void setupCellForDrag(Cell cell)  {
        if (cell == null || cell.getContentView() == null) return;

        View contentView = cell.getContentView();

        // 设置长按启动拖拽
        contentView.setOnLongClickListener(v -> {
            // 空单元格不能拖拽
            if ("empty".equals(cell.getTag())) {
                return false;
            }

            ClipData dragData = new ClipData(
                    cell.getTag(),
                    new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN },
                    new ClipData.Item(cell.getTag())
            );

            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(contentView);

            // 开始拖拽操作
            contentView.startDragAndDrop(
                    dragData,
                    shadowBuilder,
                    cell, // 传递Cell对象作为本地状态
                    0
            );

            // 这里不设置Alpha值，以避免问题
            // contentView.setAlpha(0.5f);

            return true;
        });

        // 移除这个监听器以避免冲突
    /*
    contentView.setOnDragListener((v, event) -> {
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            contentView.setAlpha(1.0f);
        }
        return false;
    });
    */
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 如果有高亮单元格，绘制高亮效果
        if (highLightValid && highLightColumn >= 0 && highLightRow >= 0) {
            Paint paint = new Paint();
            paint.setColor(Color.argb(80, 0, 150, 255)); // 半透明蓝色
            paint.setStyle(Paint.Style.FILL);

            float left = highLightColumn * per_cell_width;
            float top = highLightRow * per_cell_height;
            float right = left + per_cell_width;
            float bottom = top + per_cell_height;

            canvas.drawRect(left, top, right, bottom, paint);

            // 添加边框
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.rgb(0, 120, 215));
            paint.setStrokeWidth(2);
            canvas.drawRect(left, top, right, bottom, paint);
        }
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
        // 获取拖拽的单元格索引
        int dragCellIndex = -1;
        Cell dragCell = null;

        if (event.getLocalState() != null && event.getLocalState() instanceof Cell) {
            dragCell = (Cell) event.getLocalState();
            String dragTag = dragCell.getTag();

            // 查找拖动的单元格在当前页的索引
            for (int i = 0; i < cells.size(); i++) {
                Cell currentCell = cells.get(i);
                if (currentCell != null && currentCell.getTag() != null &&
                        currentCell.getTag().equals(dragTag)) {
                    dragCellIndex = i;
                    break;
                }
            }
        }

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;

            case DragEvent.ACTION_DRAG_ENTERED:
                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                // 计算当前悬停位置对应的单元格
                int column = Math.min(columns - 1, Math.max(0, (int)(event.getX() / per_cell_width)));
                int row = Math.min(rows - 1, Math.max(0, (int)(event.getY() / per_cell_height)));

                highLightColumn = column;
                highLightRow = row;
                highLightValid = true;  // 设置为可放置
                invalidate();
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                highLightValid = false;
                invalidate();
                return true;

            case DragEvent.ACTION_DROP:
                if (dragCellIndex < 0) {
                    Log.e(TAG, "拖拽索引无效: " + dragCellIndex);
                    highLightColumn = -1;
                    highLightRow = -1;
                    highLightValid = false;
                    invalidate();
                    return false;
                }

                // 计算目标位置
                int targetColumn = Math.min(columns - 1, Math.max(0, (int)(event.getX() / per_cell_width)));
                int targetRow = Math.min(rows - 1, Math.max(0, (int)(event.getY() / per_cell_height)));
                int targetIndex = targetRow * columns + targetColumn;

                // 确保目标索引有效
                if (targetIndex >= 0 && targetIndex < cells.size()) {
                    Cell targetCell = cells.get(targetIndex);

                    // 判断目标单元格类型并采用不同处理方式
                    if (targetCell.getTag().equals("empty")) {
                        // 空白单元格 - 直接交换位置
                        swapCells(dragCellIndex, targetIndex);
                        Log.d(TAG, "将应用图标与空白单元格交换: " + dragCellIndex + " <-> " + targetIndex);
                    } else {
                        // 非空单元格 - 插入并移动
                        moveCells(dragCellIndex, targetIndex);
                        Log.d(TAG, "将应用图标移动至非空单元格: " + dragCellIndex + " -> " + targetIndex);
                    }
                }

                // 恢复被拖动单元格的视觉状态
                if (dragCell != null && dragCell.getContentView() != null) {
                    dragCell.getContentView().setAlpha(1.0f);
                    dragCell.getContentView().setVisibility(View.VISIBLE);
                }

                // 重置高亮状态
                highLightColumn = -1;
                highLightRow = -1;
                highLightValid = false;
                invalidate();
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                // 确保被拖动的单元格恢复正常
                if (dragCell != null && dragCell.getContentView() != null) {
                    dragCell.getContentView().setAlpha(1.0f);
                    dragCell.getContentView().setVisibility(View.VISIBLE);
                }

                // 重置高亮状态
                highLightColumn = -1;
                highLightRow = -1;
                highLightValid = false;
                invalidate();
                return true;
        }

        return false;
    }

    private void resetHighlight() {
        highLightColumn = -1;
        highLightRow = -1;
        highLightValid = false;
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

        // 以下是非空目标单元格的处理逻辑，保持原有实现
        // 创建临时数组保存当前状态
        ArrayList<Cell> tempCells = new ArrayList<>(cells);

        if (targetIndex > sourceIndex) {
            // 向后移动: 目标位置比源位置大
            for (int i = sourceIndex; i < targetIndex; i++) {
                cells.set(i, tempCells.get(i + 1));
            }
            cells.set(targetIndex, sourceCell);
        } else {
            // 向前移动: 目标位置比源位置小
            for (int i = sourceIndex; i > targetIndex; i--) {
                cells.set(i, tempCells.get(i - 1));
            }
            cells.set(targetIndex, sourceCell);
        }

        // 重新布局
        requestLayout();
    }

    // 在CellLayout类中添加
    private OnCellsChangedListener onCellsChangedListener;

    // 在初始化工作区时调用此方法填充空白单元格
    public void fillEmptyCells() {
        int totalCells = rows * columns;
        int currentSize = cells.size();

        // 如果当前单元格数量少于总单元格数，添加空白单元格
        for (int i = currentSize; i < totalCells; i++) {
            // 创建空白单元格
            View emptyView = new View(getContext());
            emptyView.setBackgroundColor(Color.TRANSPARENT);

            // 创建空白单元格并添加
            Cell emptyCell = new Cell("empty", emptyView);
            cells.add(emptyCell);

            // 添加到布局但设为不可见
            if (emptyView.getParent() != null) {
                ((ViewGroup)emptyView.getParent()).removeView(emptyView);
            }
            emptyView.setVisibility(View.INVISIBLE);
            addView(emptyView);
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

        // 只在有效的高亮情况下绘制
        if (highLightColumn >= 0 && highLightRow >= 0) {
            Paint paint = new Paint();

            // 始终使用绿色表示可放置，不再使用红色
            paint.setColor(Color.argb(80, 0, 200, 0));
            paint.setStyle(Paint.Style.FILL);

            // 计算高亮区域
            int left = highLightColumn * per_cell_width;
            int top = highLightRow * per_cell_height;
            int right = left + per_cell_width;
            int bottom = top + per_cell_height;

            // 绘制填充
            canvas.drawRect(left, top, right, bottom, paint);

            // 绘制边框
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.rgb(0, 150, 0));
            paint.setStrokeWidth(2);
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
         * @return 如果返回true，表示已处理溢出；如果返回false，CellLayout将移除此Cell
         */
        boolean onCellOverflow(Cell overflowCell);
    }

    private OnCellOverflowListener cellOverflowListener;

    public void setOnCellOverflowListener(OnCellOverflowListener listener) {
        this.cellOverflowListener = listener;
    }


}