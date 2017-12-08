package android.taobao.atlas.remote;


/**
 * Created by guanjie on 2017/10/24.
 */

public interface IRemote extends IRemoteTransactor{
    IRemoteContext remoteContext = null;
    RemoteActivityManager.EmbeddedActivity realHost = null;
}
