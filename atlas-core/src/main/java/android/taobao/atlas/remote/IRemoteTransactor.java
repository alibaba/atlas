package android.taobao.atlas.remote;

import android.os.Bundle;

import java.io.Serializable;

/**
 * Created by guanjie on 2017/10/25.
 */

public interface IRemoteTransactor extends Serializable{

    Bundle call(String commandName, Bundle args, IResponse callback);

    <T> T getRemoteInterface(Class<T> interfaceClass,Bundle args);

    interface IResponse{
        void OnResponse(Bundle bundle);
    }
}
