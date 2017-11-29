package android.taobao.atlas.remote;

/**
 * Created by guanjie on 2017/10/25.
 */

public interface IRemoteContext {
    void registerHostTransactor(IRemote transactor);
    String  getTargetBundle();
    IRemote getRemoteTarget();
    IRemote getHostTransactor();
}
