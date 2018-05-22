package android.taobao.atlas.startup.patch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * PatchMerger
 *
 * @author zhayu.ll
 * @date 18/5/10
 */
public abstract class PatchMerger {

    protected PatchVerifier patchVerifier;

    protected ExecutorService service = null;

    protected List<Future<Boolean>> futures = new ArrayList<>();

    public PatchMerger(PatchVerifier patchVerifier) {
        this.patchVerifier = patchVerifier;
        service = Executors.newFixedThreadPool(3);
    }

    public abstract boolean merge(File sourceFile, File patchFile, File newFile);

    public void sumitForMerge(final File sourceFile, final File patchFile, final File newFile){

        Future <Boolean>future = service.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {

                return merge(sourceFile,patchFile,newFile);
            }
        });
        futures.add(future);
    }

    public boolean waitForResult(){
        for (Future future:futures){
            try {
                Boolean flag = (Boolean) future.get(5000,TimeUnit.SECONDS);
                if (!flag){
                    return false;
                }
            } catch (Throwable e){
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

}
