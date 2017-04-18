package android.app;

import android.content.IContentProvider;
import android.content.pm.ProviderInfo;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by guanjie on 2017/4/18.
 */

public class ContentProviderHolder implements Parcelable {

    public ContentProviderHolder(ProviderInfo info){

    }

    public ProviderInfo info;
    public IContentProvider provider;
    public IBinder connection;

    protected ContentProviderHolder(Parcel in) {
        info = in.readParcelable(ProviderInfo.class.getClassLoader());
        connection = in.readStrongBinder();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(info, flags);
        dest.writeStrongBinder(connection);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ContentProviderHolder> CREATOR = new Creator<ContentProviderHolder>() {
        @Override
        public ContentProviderHolder createFromParcel(Parcel in) {
            return new ContentProviderHolder(in);
        }

        @Override
        public ContentProviderHolder[] newArray(int size) {
            return new ContentProviderHolder[size];
        }
    };
}
