package com.msk.blacklauncher.Utils;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppLayoutManager {
    private static final String TAG = "AppLayoutManager";
    private static final String PREFS_NAME = "app_layout_prefs";
    private static final String KEY_SETTINGS_APPS = "settings_apps";
    private static final String KEY_OFFICE_APPS = "office_apps";
    private static final String KEY_APPS_APPS = "apps_apps";

    // 保存卡片中的应用包名
    public static void saveAppsForCard(Context context, String cardKey, List<String> packageNames) {
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        Set<String> packageSet = new HashSet<>(packageNames);
        editor.putStringSet(cardKey, packageSet);
        editor.apply();
    }

    // 获取卡片中保存的应用包名
    public static List<String> getAppsForCard(Context context, String cardKey) {
        if (context == null) return new ArrayList<>();

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> packageSet = prefs.getStringSet(cardKey, new HashSet<>());

        return new ArrayList<>(packageSet);
    }


    public static void removeAppFromCard(Context context, String cardKey, String packageName) {
        if (context == null || packageName == null) return;

        List<String> apps = getAppsForCard(context, cardKey);
        Log.d("AppLayoutManager", "尝试删除应用，当前列表: " + apps.toString());

        if (apps.remove(packageName)) {
            saveAppsForCard(context, cardKey, apps);
            Log.d("AppLayoutManager", "已从 " + cardKey + " 中删除应用: " + packageName);
            Log.d("AppLayoutManager", "删除后列表: " + apps.toString());
        } else {
            Log.w("AppLayoutManager", "应用未在列表中: " + packageName);
        }
    }

    // 获取常量
    public static String getSettingsCardKey() {
        return KEY_SETTINGS_APPS;
    }

    public static String getOfficeCardKey() {
        return KEY_OFFICE_APPS;
    }

    public static String getAppsCardKey() {
        return KEY_APPS_APPS;
    }
}