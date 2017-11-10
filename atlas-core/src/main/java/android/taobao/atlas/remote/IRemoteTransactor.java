package android.taobao.atlas.remote;

import android.os.Bundle;

/**
 * Created by guanjie on 2017/10/25.
 */

public interface IRemoteTransactor {

    Bundle call(String commandName, Bundle args, IResponse callback);

    <T> T getRemoteInterface(Class<T> interfaceClass);

    interface IResponse{
        void OnResponse(Bundle bundle);
    }
}
