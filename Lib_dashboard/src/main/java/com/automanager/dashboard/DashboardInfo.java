package com.automanager.dashboard;
import android.os.Parcel;
import android.os.Parcelable;

public class DashboardInfo implements Parcelable {
    public String title;
    public byte[] thumb;
    public boolean withCustomConfig;
    /** 默认排序权重：DbLocalFragment 未手动排序时按 rank 降序展示（越大越靠前） */
    public int rank;
    // 后续扩展字段直接在这里增加

    public DashboardInfo(){}

    protected DashboardInfo(Parcel in) {
        title = in.readString();
        thumb = in.createByteArray();
        withCustomConfig = in.readInt() != 0;
        rank = in.readInt();
    }

    public static final Creator<DashboardInfo> CREATOR = new Creator<DashboardInfo>() {
        @Override
        public DashboardInfo createFromParcel(Parcel in) {
            return new DashboardInfo(in);
        }
        @Override
        public DashboardInfo[] newArray(int size) {
            return new DashboardInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeByteArray(thumb);
        dest.writeInt(withCustomConfig ? 1 : 0);
        dest.writeInt(rank);
    }
}