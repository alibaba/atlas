## 更新日志

1. 升级atlasplugin到2.3.3.beta1
2. 支持awb之间的依赖，具体参考 firstbundle的配置
	1. dependencies 里添加 provided依赖 

		 	providedCompile project(':publicbundle')
		 
	2. 申明bundle之间依赖关系

			➜  firstbundle git:(gradle/2.3.3) ✗ more bundleBaseInfoFile.json
			{
			  "name": "第一个bundle",
			  "dependency": ["com.taobao.publicBundle"]
			}
			
	3. 在 app中添加被依赖的bundle

			bundleCompile project(':publicbundle')
    		
3. 加入databinding的bundle，具体参见app的配置

	1. 添加databinding的bundle依赖

			bundleCompile project(':databindbundle')
			
	2. 配置需要开启的databinding列表

			atlas {
		
		    tBuildConfig {
		        dataBindingBundles = ['com.taobao.databindbundle']
		    	 ...
		    	 
    3. 注意同时开启app的databinding
		
			android {
			 	dataBinding {
	        		enabled = true
	    	 	}
	    		