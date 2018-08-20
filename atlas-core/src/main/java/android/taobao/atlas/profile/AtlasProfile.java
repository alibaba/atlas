package android.taobao.atlas.profile;

import java.util.HashMap;
import java.util.Map;
/**
 * AtlasProfiler
 *
 * @author zhayu.ll
 * @date 18/7/4
 */
public class AtlasProfile
{
    private static final Map<String, AtlasProfile> cacheMap = new HashMap();
    private String tag;
    private long start;

    private AtlasProfile(String tag)
    {
        this.tag = tag;
    }

    public static  AtlasProfile profile(String tag) {
        synchronized (cacheMap) {
            if (cacheMap.containsKey(tag)) {
                return (AtlasProfile) cacheMap.get(tag);
            }
            AtlasProfile atlasProfile = new AtlasProfile(tag);
            cacheMap.put(tag, atlasProfile);
            return atlasProfile;
        }
    }

    public void start()
    {
        this.start = System.currentTimeMillis();
    }

    public String stop()
    {
        return String.format("%s |cost: %sms",this.tag,String.valueOf(System.currentTimeMillis() - this.start));
    }
}
