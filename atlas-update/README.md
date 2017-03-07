## Feature

1. Bundle dynamic update

### Note

Unlike hotpatch takes effect immediately, Atlas bundle update requires the user to restart the application after the patch installed.

## Interface Api

com.taobao.atlas.update.AtlasUpdater#update

1. Update interface

		/**
		 * Update the main entrance
		 * @param updateInfo  The basis information of update
		 * @param patchFile   tpatch package
		 * @throws MergeException
		 * @throws BundleException
		 */
		public static void update(UpdateInfo updateInfo, File patchFile) throws MergeException, BundleException

2. The basis information of update  

	    /**
	     * the current version of the client
	     */
	    public String baseVersion;
	    /**
	     * the updated version of the client
	     */
	    public String updateVersion;

	    /**
	     * update module information list
	     */
	    public List<Item> updateBundles;

	    public File workDir = new File(RuntimeVariables.androidApplication.getCacheDir(), "atlas_update");

	    /**
	     * update module information
	     */
	    public static class Item implements Serializable {
	        /**
	         * is the main dex
	         */
	        public boolean isMainDex;
	        /**
	         * the name of the bundle
	         */
	        public String name;
	        /**
	         * the bundle version information
	         */
	        public String version;
	        /**
	         * git tag version of the bundle
	         */
	        public String srcVersion;
	        /**
	         * depend on the bundle list
	         */
	        public List<String> dependency;
	    }

## Update process

1. Unzip the patch package
2. Merger patch of the original apk package and bundle (in a separate process)
3. Install The combined new bundle
4. Update the bundle list information
5. Wait for the user starts to reload