package com.msk.blacklauncher.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

public class IconUtils {
    private static final String TAG = "IconUtils";

    /**
     * 设置统一的圆角图标，添加白色边框
     */
    public static void setRoundedIcon(Context context, ImageView imageView, Drawable drawable, float cornerRadius) {
        if (context == null || imageView == null || drawable == null) {
            Log.e(TAG, "参数为空，无法处理图标");
            return;
        }

        try {
            // 将dp转为像素
            float density = context.getResources().getDisplayMetrics().density;

            // 获取图标的位图
            Bitmap originalBitmap = drawableToBitmap(drawable);
            if (originalBitmap == null) {
                Log.e(TAG, "无法从drawable创建位图");
                imageView.setImageDrawable(drawable);
                return;
            }

            // 使用固定大小的正方形
            int size = (int)(80 * density); // 统一80dp大小

            // 创建最终的位图
            Bitmap outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(outputBitmap);

            // 背景为完全透明
            canvas.drawColor(Color.TRANSPARENT);

            // 先绘制白色圆角矩形作为背景
            Paint backgroundPaint = new Paint();
            backgroundPaint.setColor(Color.WHITE);
            backgroundPaint.setAntiAlias(true);

            // 确定圆角半径 - 使用较大的值实现更圆润的效果
            float cornerRadiusPx = size / 5.0f;

            // 绘制圆角矩形背景
            RectF backgroundRect = new RectF(0, 0, size, size);
            canvas.drawRoundRect(backgroundRect, cornerRadiusPx, cornerRadiusPx, backgroundPaint);

            // 为图标保留一点边距，使白色边框可见
            int padding = (int)(size * 0.1f);

            // 创建一个临时位图用于应用圆角裁剪
            Bitmap tempBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas tempCanvas = new Canvas(tempBitmap);

            // 在临时画布上绘制圆角矩形作为裁剪蒙版
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            maskPaint.setColor(Color.WHITE);

            // 这里是错误所在 - 需要定义maskRect
            RectF maskRect = new RectF(padding, padding, size - padding, size - padding);
            tempCanvas.drawRoundRect(maskRect, cornerRadiusPx - padding/2, cornerRadiusPx - padding/2, maskPaint);

            // 设置混合模式
            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

            // 绘制原始图标到临时画布，会应用圆角裁剪
            Rect srcRect = new Rect(0, 0, originalBitmap.getWidth(), originalBitmap.getHeight());
            RectF dstRect = new RectF(padding, padding, size - padding, size - padding);
            tempCanvas.drawBitmap(originalBitmap, srcRect, dstRect, maskPaint);

            // 将处理后的图标绘制到最终画布
            canvas.drawBitmap(tempBitmap, 0, 0, null);

            // 设置到ImageView
            imageView.setImageBitmap(outputBitmap);

            Log.d(TAG, "成功应用圆角图标效果");

        } catch (Exception e) {
            Log.e(TAG, "应用圆角图标时出错: " + e.getMessage(), e);
            // 出错时使用原始图标
            imageView.setImageDrawable(drawable);
        }
    }

    /**
     * 将Drawable转换为Bitmap
     */
    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        try {
            // 创建新的位图
            int width = Math.max(drawable.getIntrinsicWidth(), 1);
            int height = Math.max(drawable.getIntrinsicHeight(), 1);

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "drawable转bitmap失败: " + e.getMessage());
            return null;
        }
    }
}