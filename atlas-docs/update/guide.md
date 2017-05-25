####动态部署的限制
1. **AndroidManifest**
 	已有Activity的manifest信息暂时不支持更改
 	
2. **window级别使用的资源保持不变**
	Activity切换的动画文件、通知栏使用的logo等如果修改了也不会生效，不过不会产生问题，使用的还是原来的样式
   