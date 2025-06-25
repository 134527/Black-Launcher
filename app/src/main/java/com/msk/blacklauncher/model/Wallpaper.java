package com.msk.blacklauncher.model;

import android.graphics.Bitmap;
import android.net.Uri;

/**
 * 壁纸数据模型类
 */
public class Wallpaper {
    private String id;
    private String name;
    private Uri imageUri;
    private Bitmap thumbnail;
    private String category;
    private boolean isAsset; // 是否为应用内资源

    public Wallpaper(String id, String name, Uri imageUri, String category) {
        this.id = id;
        this.name = name;
        this.imageUri = imageUri;
        this.category = category;
    }

    public Wallpaper(String id, String name, Uri imageUri, String category, boolean isAsset) {
        this.id = id;
        this.name = name;
        this.imageUri = imageUri;
        this.category = category;
        this.isAsset = isAsset;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Uri getImageUri() {
        return imageUri;
    }

    public void setImageUri(Uri imageUri) {
        this.imageUri = imageUri;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Bitmap thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isAsset() {
        return isAsset;
    }

    public void setAsset(boolean asset) {
        isAsset = asset;
    }
} 