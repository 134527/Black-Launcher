package com.msk.blacklauncher.view;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.msk.blacklauncher.R;
import com.msk.blacklauncher.adapter.ShortcutAdapter;
import com.msk.blacklauncher.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import android.app.Activity;
import android.widget.FrameLayout;
import android.app.ActivityManager;
import android.content.ComponentName;
import java.lang.reflect.Field;
import java.util.Map;

public class ShortcutMenuView {
    private final Context context;
    private final String packageName;
    private final String appName;
    private final Drawable appIcon;
    private final List<ShortcutInfo> shortcuts;
    private final PopupWindow popupWindow;
    private final View rootView;
    private final boolean isSystemApp;
    private static final String TAG = "ShortcutMenuView";
    private static final int DRAG_START_THRESHOLD = 10; // 拖拽开始阈值（像素）
    private View anchorView;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;
    private DragStartListener dragStartListener;

    private OnDismissListener onDismissListener;
    
    // 添加静态变量用于跟踪所有活跃的菜单实例
    private static final List<ShortcutMenuView> activeMenus = new ArrayList<>();

    public interface OnDismissListener {
        void onDismiss();
    }

    public interface DragStartListener {
        void onDragStart(View view, String packageName);
    }
    
    /**
     * 关闭所有活跃的快捷菜单
     */
    public static void dismissActiveMenus() {
        Log.d(TAG, "关闭所有活跃的快捷菜单: " + activeMenus.size() + " 个");
        
        // 为避免ConcurrentModificationException，创建副本进行操作
        List<ShortcutMenuView> menus = new ArrayList<>(activeMenus);
        for (ShortcutMenuView menu : menus) {
            if (menu != null && menu.isShowing()) {
                menu.dismiss();
            }
        }
        
        // 清空列表
        activeMenus.clear();
    }

    public ShortcutMenuView(Context context, String packageName, String appName, 
                           Drawable appIcon, List<ShortcutInfo> shortcuts) {
        this.context = context;
        this.packageName = packageName;
        this.appName = appName;
        this.appIcon = appIcon;
        this.shortcuts = shortcuts;

        // 检查是否为系统应用
        PackageManager pm = context.getPackageManager();
        boolean isSysApp = false;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            isSysApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception ignored) {}
        isSystemApp = isSysApp;

        // 初始化视图
        LayoutInflater inflater = LayoutInflater.from(context);
        rootView = inflater.inflate(R.layout.item_shortcut_menu, null);
        
        // 设置全屏标志，确保弹窗不会中断全屏状态
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            
            // 设置UI标志
            activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
        
        setupUI();
        
        // 创建PopupWindow
        popupWindow = new PopupWindow(
                rootView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false // 设置为false避免焦点问题导致退出全屏
        );
        
        // 设置背景以便支持阴影和圆角，但不设置具体背景防止退出全屏
        popupWindow.setElevation(24);
        popupWindow.setBackgroundDrawable(null);
        popupWindow.setOutsideTouchable(true);
        
        // 特殊设置防止系统UI出现
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        
        // 设置触摸拦截，避免触发系统UI显示
        rootView.setOnTouchListener((v, event) -> {
            // 处理拖拽检测逻辑
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 记录初始触摸点
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isDragging = false;
                    // 每次触摸都强制全屏
                    ensureFullScreenMode();
                    return false; // 不消费事件，让子视图能处理点击
                    
                case MotionEvent.ACTION_MOVE:
                    // 计算移动距离
                    float deltaX = Math.abs(event.getRawX() - initialTouchX);
                    float deltaY = Math.abs(event.getRawY() - initialTouchY);
                    float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                    
                    // 每次移动都保持全屏
                    ensureFullScreenMode();
                    
                    // 如果移动距离超过阈值，认为是拖拽操作
                    if (!isDragging && distance > DRAG_START_THRESHOLD && dragStartListener != null && anchorView != null) {
                        isDragging = true;
                        Log.d(TAG, "开始拖拽操作，关闭快捷菜单");
                        dismiss();
                        
                        // 通知监听器开始拖拽
                        dragStartListener.onDragStart(anchorView, packageName);
                        return true; // 消费此事件
                    }
                    return false;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 触摸结束后再次强制全屏
                    ensureFullScreenMode();
                    return false;
                    
                default:
                    return false;
            }
        });
        
        // 设置消失监听器
        popupWindow.setOnDismissListener(() -> {
            if (onDismissListener != null) {
                onDismissListener.onDismiss();
            }
            
            // 从活跃菜单列表中移除自己
            activeMenus.remove(this);
            
            // 菜单关闭后强制一次全屏
            ensureFullScreenMode();
        });
    }

    private void setupUI() {
        // 获取视图引用
        RecyclerView recyclerView = rootView.findViewById(R.id.shortcuts_list);
        LinearLayout noShortcutsLayout = rootView.findViewById(R.id.no_shortcuts_layout);
        View shortcutsDivider = rootView.findViewById(R.id.shortcuts_divider);
        LinearLayout buttonsLayout = rootView.findViewById(R.id.buttons_layout);
        
        // 常规按钮（有快捷方式时显示）
        LinearLayout shareButton = rootView.findViewById(R.id.button_share);
        LinearLayout appInfoButton = rootView.findViewById(R.id.button_app_info);
        LinearLayout uninstallButton = rootView.findViewById(R.id.button_uninstall);
        
        // 无快捷方式时的行布局按钮
        LinearLayout appInfoRow = rootView.findViewById(R.id.app_info_row);
        LinearLayout uninstallRow = rootView.findViewById(R.id.uninstall_row);
        LinearLayout shareRow = rootView.findViewById(R.id.share_row);
        
        // 根据是否有快捷方式决定显示哪种布局
        if (shortcuts != null && !shortcuts.isEmpty()) {
            // 有快捷方式，使用标准布局
            recyclerView.setVisibility(View.VISIBLE);
            noShortcutsLayout.setVisibility(View.GONE);
            shortcutsDivider.setVisibility(View.VISIBLE);
            buttonsLayout.setVisibility(View.VISIBLE);
            
            // 设置快捷方式列表
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
            
            LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            ShortcutAdapter adapter = new ShortcutAdapter(shortcuts, launcherApps, shortcutInfo -> {
                try {
                    UserHandle userHandle = Process.myUserHandle();
                    launcherApps.startShortcut(packageName, shortcutInfo.getId(), null, null, userHandle);
                    dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            
            recyclerView.setAdapter(adapter);
            
            // 设置常规布局的按钮点击事件
            setupStandardButtons(shareButton, appInfoButton, uninstallButton);
        } else {
            // 无快捷方式，使用行列表布局
            recyclerView.setVisibility(View.GONE);
            noShortcutsLayout.setVisibility(View.VISIBLE);
            shortcutsDivider.setVisibility(View.GONE);
            buttonsLayout.setVisibility(View.GONE);
            
            // 设置行布局按钮点击事件
            setupRowButtons(shareRow, appInfoRow, uninstallRow);
        }
    }
    
    /**
     * 卸载应用的公共方法，改进版
     */
    private void uninstallApp() {
        try {
            Log.d(TAG, "开始卸载应用: " + packageName);
            
            // 先关闭菜单
            dismiss();
            
            // 使用一个短延迟确保PopupWindow完全关闭
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    // 获取activity上下文以确保正确启动
                    Context activityContext = context;
                    if (!(context instanceof Activity) && context.getApplicationContext() != null) {
                        // 尝试从应用上下文找到当前活动的Activity
                        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                        if (am != null) {
                            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                            if (!tasks.isEmpty()) {
                                ComponentName topActivity = tasks.get(0).topActivity;
                                if (topActivity != null && topActivity.getPackageName().equals(context.getPackageName())) {
                                    // 尝试使用反射获取当前活动的Activity
                                    try {
                                        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                                        Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
                                        Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
                                        activitiesField.setAccessible(true);
                                        
                                        // 实现细节可能因Android版本而异
                                        Map<Object, Object> activities = (Map<Object, Object>) activitiesField.get(activityThread);
                                        if (activities != null) {
                                            for (Object activityRecord : activities.values()) {
                                                Field activityField = activityRecord.getClass().getDeclaredField("activity");
                                                activityField.setAccessible(true);
                                                Activity activity = (Activity) activityField.get(activityRecord);
                                                if (activity != null) {
                                                    activityContext = activity;
                                                    break;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "反射获取Activity失败: " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                    
                    // 尝试多种方式卸载应用
                    boolean success = false;
                    
                    // 方法1: 标准卸载Intent
                    try {
                        Intent intent = new Intent(Intent.ACTION_DELETE);
                        intent.setData(Uri.parse("package:" + packageName));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        
                        // 如果有Activity上下文，使用startActivityForResult
                        if (activityContext instanceof Activity) {
                            ((Activity) activityContext).startActivityForResult(intent, 1001);
                            success = true;
                        } else {
                            activityContext.startActivity(intent);
                            success = true;
                        }
                        
                        Log.d(TAG, "方法1: 标准卸载Intent已发送");
                    } catch (Exception e) {
                        Log.e(TAG, "方法1失败: " + e.getMessage());
                    }
                    
                    // 如果方法1失败，尝试方法2
                    if (!success) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                            intent.setData(Uri.parse("package:" + packageName));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activityContext.startActivity(intent);
                            success = true;
                            Log.d(TAG, "方法2: ACTION_UNINSTALL_PACKAGE Intent已发送");
                        } catch (Exception e) {
                            Log.e(TAG, "方法2失败: " + e.getMessage());
                        }
                    }
                    
                    // 如果方法2失败，尝试方法3: 通过应用商店卸载
                    if (!success) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse("market://details?id=" + packageName));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activityContext.startActivity(intent);
                            success = true;
                            Log.d(TAG, "方法3: 应用商店卸载Intent已发送");
                        } catch (Exception e) {
                            Log.e(TAG, "方法3失败: " + e.getMessage());
                        }
                    }
                    
                    // 最后尝试打开应用详情页面
                    if (!success) {
                        openAppDetails();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "卸载应用时出错: " + e.getMessage());
                    openAppDetails();
                }
            }, 150); // 150ms延迟确保PopupWindow已经完全关闭
        } catch (Exception e) {
            Log.e(TAG, "卸载启动失败: " + e.getMessage());
            try {
                openAppDetails();
            } catch (Exception ex) {
                Log.e(TAG, "打开应用详情也失败: " + ex.getMessage());
            }
        }
    }
    
    /**
     * 打开应用详情页面
     */
    private void openAppDetails() {
        try {
            Log.d(TAG, "尝试打开应用详情页: " + packageName);
            Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            settingsIntent.setData(Uri.parse("package:" + packageName));
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settingsIntent);
            dismiss();
        } catch (Exception ex) {
            Log.e(TAG, "打开应用详情失败: " + ex.getMessage());
        }
    }

    /**
     * 设置标准布局按钮的点击事件
     */
    private void setupStandardButtons(LinearLayout shareButton, LinearLayout appInfoButton, LinearLayout uninstallButton) {
        // 分享按钮
        shareButton.setOnClickListener(v -> {
            try {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, 
                        "Check out this app: " + appName + " - " + packageName);
                sendIntent.setType("text/plain");
                sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(sendIntent, "分享应用"));
                dismiss();
            } catch (Exception e) {
                Log.e(TAG, "分享应用失败", e);
                dismiss();
            }
        });
        
        // 应用信息按钮
        appInfoButton.setOnClickListener(v -> {
            openAppDetails();
        });
        
        // 卸载按钮 - 系统应用不显示
        if (isSystemApp) {
            uninstallButton.setVisibility(View.GONE);
        } else {
            // 确保卸载按钮可见
            uninstallButton.setVisibility(View.VISIBLE);
            
            // 增加日志输出以便调试
            uninstallButton.setOnClickListener(v -> {
                Log.d(TAG, "卸载按钮被点击");
                try {
                    uninstallApp();
                } catch (Exception e) {
                    Log.e(TAG, "卸载应用失败: " + e.getMessage());
                    dismiss();
                }
            });
        }
    }
    
    /**
     * 设置行布局按钮的点击事件
     */
    private void setupRowButtons(LinearLayout shareRow, LinearLayout appInfoRow, LinearLayout uninstallRow) {
        // 分享按钮行
        shareRow.setOnClickListener(v -> {
            try {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, 
                        "Check out this app: " + appName + " - " + packageName);
                sendIntent.setType("text/plain");
                sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(sendIntent, "分享应用"));
                dismiss();
            } catch (Exception e) {
                Log.e(TAG, "分享应用失败", e);
                dismiss();
            }
        });

        // 应用信息按钮行
        appInfoRow.setOnClickListener(v -> {
            openAppDetails();
        });
        
        // 卸载按钮行 - 系统应用不显示
        if (isSystemApp) {
            uninstallRow.setVisibility(View.GONE);
        } else {
            // 确保卸载按钮行可见
            uninstallRow.setVisibility(View.VISIBLE);
            
            // 增加日志输出以便调试
            uninstallRow.setOnClickListener(v -> {
                Log.d(TAG, "卸载按钮行被点击");
                try {
                    uninstallApp();
                } catch (Exception e) {
                    Log.e(TAG, "卸载应用失败: " + e.getMessage());
                    dismiss();
                }
            });
        }
    }

    private void updatePointerPosition(View anchor, int popupX, int popupY) {
        if (rootView == null) return;
        
        ImageView menuPointer = rootView.findViewById(R.id.menu_pointer);
        if (menuPointer == null) return;
        
        // 获取锚点位置和中心坐标
        int[] location = ViewUtils.getViewLocationOnScreen(anchor);
        int[] centerCoords = ViewUtils.getViewCenterCoordinates(anchor);
        int anchorCenterX = centerCoords[0];
        int anchorCenterY = centerCoords[1];
        
        // 获取弹窗尺寸
        int popupWidth = rootView.getMeasuredWidth();
        
        // 计算指示器相对于弹窗的位置，使其正对图标中心
        int pointerCenterX = anchorCenterX - popupX;
        
        // 转换为左侧边距
        int pointerLeftMargin = pointerCenterX - menuPointer.getWidth() / 2;
        
        // 确保指示器不会在边缘
        int minPointerMargin = 20;
        int maxPointerMargin = popupWidth - menuPointer.getWidth() - minPointerMargin;
        pointerLeftMargin = Math.max(minPointerMargin, Math.min(pointerLeftMargin, maxPointerMargin));
        
        // 动态调整指示器显示
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) menuPointer.getLayoutParams();
        if (params != null) {
            // 基于图标中心点确定弹窗位置
            boolean isAboveAnchor = popupY + rootView.getMeasuredHeight() <= anchorCenterY;
            
            if (isAboveAnchor) {
                // 弹窗在图标上方，指示器在底部指向下方
                params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
                menuPointer.setImageResource(R.drawable.ic_pointer_down_symmetric);
                params.bottomMargin = -8; // 增大负边距，让尖角露出更多
            } else {
                // 弹窗在图标下方，指示器在顶部指向上方
                params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                menuPointer.setImageResource(R.drawable.ic_pointer_up_symmetric);
                params.topMargin = -8; // 增大负边距，让尖角露出更多
            }
            
            // 水平位置调整
            params.leftMargin = pointerLeftMargin;
            menuPointer.setLayoutParams(params);
            
            // 确保指示器可见
            menuPointer.setVisibility(View.VISIBLE);
            
            // 设置动画效果，让尖角出现更平滑
            menuPointer.setAlpha(0f);
            menuPointer.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
            
            // 记录日志帮助调试
            Log.d(TAG, "指示器位置: 左边距=" + pointerLeftMargin + 
                  ", 图标中心X=" + anchorCenterX + 
                  ", 弹窗X=" + popupX + 
                  ", 在上方=" + isAboveAnchor);
        }
    }

    public void show(View anchor) {
        // 先关闭其他所有活跃菜单
        dismissActiveMenus();
        
        // 将自己添加到活跃菜单列表
        activeMenus.add(this);
        
        // 尝试找到真正的图标视图
        View iconAnchor = ViewUtils.findActualIconView(anchor);
        if (iconAnchor == null) iconAnchor = anchor;
        
        // 保存锚点视图引用
        this.anchorView = anchor; // 保存原始锚点用于拖拽回调
        
        // 确保应用保持全屏状态（仅调用一次）
        ensureFullScreenMode();
        
        // 测量PopupWindow大小
        rootView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        
        int popupWidth = rootView.getMeasuredWidth();
        int popupHeight = rootView.getMeasuredHeight();
        
        // 获取锚点位置和尺寸
        int[] location = ViewUtils.getViewLocationOnScreen(iconAnchor);
        int[] centerCoords = ViewUtils.getViewCenterCoordinates(iconAnchor);
        int anchorCenterX = centerCoords[0];
        int anchorCenterY = centerCoords[1];
        
        // 计算X位置，使菜单居中显示在图标正上方/下方
        int x = anchorCenterX - popupWidth / 2;
        
        // 确保不超出屏幕边缘
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        if (x < 0) x = 0;
        if (x + popupWidth > screenWidth) x = screenWidth - popupWidth;
        
        // 计算Y位置，使菜单显示在图标上方，为指示器留出空间
        int y = location[1] - popupHeight - 10; // 初始尝试在图标上方显示，留出10dp的空间
        
        // 如果上方空间不足，则显示在图标下方
        if (y < 0) {
            y = location[1] + iconAnchor.getHeight() + 10; // 在图标下方显示，留出10dp的空间
        }
        
        // 计算并更新指示器位置
        final int finalX = x;
        final int finalY = y;
        final View finalIconAnchor = iconAnchor;

        try {
            // 使用FLAG_NOT_FOCUSABLE特性，避免获取焦点导致系统UI显示
            popupWindow.setFocusable(false);
            popupWindow.setTouchable(true);
            
            // 显示PopupWindow
            popupWindow.showAtLocation(anchor, 0, finalX, finalY);
            
            // 在PopupWindow显示后更新指示器位置
            new Handler(Looper.getMainLooper()).post(() -> {
                updatePointerPosition(finalIconAnchor, finalX, finalY);
                
                // 只在弹窗显示后确保一次全屏状态
                ensureFullScreenMode();
            });
        } catch (Exception e) {
            Log.e(TAG, "显示快捷菜单失败", e);
        }
        
        // 应用进入动画
        CardView cardView = rootView.findViewById(R.id.shortcut_menu_card);
        if (cardView != null) {
            cardView.setScaleX(0.9f); // 稍微调整初始缩放值
            cardView.setScaleY(0.9f);
            cardView.setAlpha(0f);
            
            cardView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(150) // 减少动画时间
                    .start();
        }
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public void setOnDismissListener(OnDismissListener listener) {
        this.onDismissListener = listener;
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    public void setDragStartListener(DragStartListener listener) {
        this.dragStartListener = listener;
    }

    /**
     * 确保应用处于全屏模式
     */
    private void ensureFullScreenMode() {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            
            try {
                // 设置窗口标志，强制全屏
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                              WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                
                // 设置系统UI可见性 - 使用最完整的全屏标志组合
                View decorView = activity.getWindow().getDecorView();
                int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                
                decorView.setSystemUiVisibility(uiOptions);
            } catch (Exception e) {
                Log.e(TAG, "全屏模式设置失败", e);
            }
        }
    }
} 