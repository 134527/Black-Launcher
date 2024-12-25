package com.msk.blacklauncher.model;

import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

public class AppModel implements Parcelable {
    private final String appName;
    private final Drawable appIcon;
    private final String packageName;

    public AppModel(String appName, Drawable appIcon, String packageName) {
        this.appName = appName;
        this.appIcon = appIcon;
        this.packageName = packageName;
    }




    // Getter methods
    public String getAppName() {
        return appName;
    }

    public Drawable getAppIcon() {
        return appIcon;
    }

    public String getPackageName() {
        return packageName;
    }

    // Parcelable implementation
    protected AppModel(Parcel in) {
        appName = in.readString();
        packageName = in.readString();
        appIcon = in.readParcelable(Drawable.class.getClassLoader());; // Drawable cannot be passed; handle this separately if needed.
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(appName);

        dest.writeString(packageName);
        // Drawable not written due to its complex nature.
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AppModel> CREATOR = new Creator<AppModel>() {
        @Override
        public AppModel createFromParcel(Parcel in) {
            return new AppModel(in);
        }

        @Override
        public AppModel[] newArray(int size) {
            return new AppModel[size];
        }
    };
}