package com.taobao.atlas.dexmerge;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by lilong on 16/7/5.
 */
public class MergeObject implements Parcelable{
    public String originalFile;
    public String patchName;
    public String mergeFile;
    protected MergeObject(Parcel in) {
    }

    public static final Creator<MergeObject> CREATOR = new Creator<MergeObject>() {
        @Override
        public MergeObject createFromParcel(Parcel in) {

            return new MergeObject(in.readString(),in.readString(),in.readString());
        }

        @Override
        public MergeObject[] newArray(int size) {
            return new MergeObject[size];
        }
    };

    public MergeObject(String originalFile, String patchFile, String mergeFile) {
        this.originalFile = originalFile;
        this.patchName = patchFile;
        this.mergeFile = mergeFile;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(originalFile);
        dest.writeString(patchName);
        dest.writeString(mergeFile);
    }
}
