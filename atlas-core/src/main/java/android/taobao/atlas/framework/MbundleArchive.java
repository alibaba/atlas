package android.taobao.atlas.framework;

import android.taobao.atlas.framework.bundlestorage.BundleArchive;
import android.taobao.atlas.framework.bundlestorage.BundleArchiveRevision;
import android.taobao.atlas.hack.Hack;
import android.taobao.atlas.runtime.RuntimeVariables;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.List;

/**
 * MbundleArchive
 *
 * @author zhayu.ll
 * @date 18/7/10
 */
public class MbundleArchive extends BundleArchive {

    @Override
    public boolean isDexOpted() {
        return true;
    }

    @Override
    public void optDexFile() {
//        super.optDexFile();
    }

    @Override
    public Class<?> findClass(String className, ClassLoader cl) throws ClassNotFoundException {
        return cl.loadClass(className);
    }

    @Override
    public File findLibrary(String fileName) {
        try {
            return (File) Hack.into(ClassLoader.class).method("findLibrary",String.class).invoke(Framework.getSystemClassLoader(),fileName);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (Hack.HackDeclaration.HackAssertionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<URL> getResources(String resName) throws IOException {
        try {
            return (List<URL>) Hack.into(ClassLoader.class).method("getResources",String.class).invoke(Framework.getSystemClassLoader(),resName);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (Hack.HackDeclaration.HackAssertionException e) {
            e.printStackTrace();
        }
        return null;    }

    public MbundleArchive(String location) {
        super(location);
    }

    @Override
    public BundleArchiveRevision getCurrentRevision() {
        return null;
    }

    @Override
    public File getArchiveFile() {
        return new File(RuntimeVariables.sApkPath);
    }

    @Override
    public File getBundleDir() {
        return new File(RuntimeVariables.sApkPath).getParentFile();
    }
}
