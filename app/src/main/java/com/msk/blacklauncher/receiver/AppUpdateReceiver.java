package com.msk.blacklauncher.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.msk.blacklauncher.MainActivity;

public class AppUpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        throw new UnsupportedOperationException("Not yet implemented");
       /* if (intent != null && (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction()) ||
                Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()))) {

            if (context instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) context;
//                mainActivity.refreshAppList();
            }
        }*/
    }
    }
