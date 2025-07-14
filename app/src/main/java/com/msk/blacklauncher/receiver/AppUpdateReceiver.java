package com.msk.blacklauncher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AppUpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "AppUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        Log.d(TAG, "接收到应用更新广播: " + action);
        
        // 由于主要处理逻辑已经在WorkspaceFragment中实现，
        // 这里只需要转发广播到应用主Activity
        try {
            Intent forwardIntent = new Intent("com.msk.blacklauncher.APP_UPDATED");
            forwardIntent.putExtra("original_action", action);
            forwardIntent.putExtra("original_data", intent.getData());
            forwardIntent.setPackage(context.getPackageName());
            context.sendBroadcast(forwardIntent);
            
            Log.d(TAG, "已转发应用更新广播");
        } catch (Exception e) {
            Log.e(TAG, "转发广播失败: " + e.getMessage());
        }
    }
}
