## feature

1. bundle的动态更新

### 注意

atlas更新不像hotpatch的立即生效，而是需要用户在patch成功安装之后，重启应用才能生效

## 接口Api

com.taobao.atlas.update.AtlasUpdater#update

1. 更新的接口

		/**
		 * 更新主入口
		 * @param updateInfo  更新的基础信息
		 * @param patchFile   tpatch包
		 * @throws MergeException
		 * @throws BundleException
		 */
		public static void update(UpdateInfo updateInfo, File patchFile) throws MergeException, BundleException

2. 更新的基础信息    

	    /**
	     * 当前的客户端版本
	     */
	    public String baseVersion;
	    /**
	     * 更新后的客户端版本
	     */
	    public String updateVersion;

	    /**
	     * 更新的模块列表信息
	     */
	    public List<Item> updateBundles;

	    public File workDir = new File(RuntimeVariables.androidApplication.getCacheDir(), "atlas_update");

	    /**
	     * 更新的模块信息
	     */
	    public static class Item implements Serializable {
	        /**
	         * 是不是主dex
	         */
	        public boolean isMainDex;
	        /**
	         * bundle 的名称
	         */
	        public String name;
	        /**
	         * bundle 版本信息
	         */
	        public String version;
	        /**
	         * bundle 的代码仓库对应的版本
	         */
	        public String srcVersion;
	        /**
	         * 依赖的 bundle 列表
	         */
	        public List<String> dependency;
	    }

## 更新过程

1. 解压patch包
2. 合并patch包和原始apk中的bundle（在独立进程进行）
3. 将合并后新的bundle进行重新的安装
4. 更新bundle的列表信息
5. 等待用户启动重新加载