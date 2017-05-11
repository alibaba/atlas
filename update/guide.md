####动态部署的限制
1. **AndroidManifest保持不变**
 	
 	新增Activity的功能暂时关闭（下个大版本开放）
 	所以新增Activity，service等如果有需求在大版本里面先预留解决，如果要修改theme 通过代码设置来解决;receiver可以先考虑采用动态注册的方式
 	
2. **window级别使用的资源保持不变**
	Activity切换的动画文件、通知栏使用的logo等如果修改了也不会生效，不过不会产生问题，使用的还是原来的样式
   