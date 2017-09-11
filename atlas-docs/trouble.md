# Troubleshoot Android Atlas Apps development issues
This page describes common problems or issues that can arise when developing Android Atlas Apps and how to fix them. If you encounter other challenges while developing your atlas app, report us in github https://github.com/alibaba/atlas.


##Run the Atlas App APK

here are some issue when you develop atlas app,this may cause crash when you run. some issues are astricted in osgi framework.

<table><tbody>
    <tr>
        <th>Error message</th><th>Description</th><th>Solution</th>
    </tr>
    <tr>
        <td><font style='font-size: small;'>java.lang.ClassNotFoundException: Can't find class com.taobao.tao.msgcenter.manager in BundleClassLoader: com.taobao.wangxin</font></td><td><font style='font-size: small;'>class is not found in bundle com.taobao.wangxin</font></td><td style='font-size: small;'>first you should check in bundle dex,class is existed, also you should check the class in other bundle,if class is in other bundle dex, add bundle dependency can solve this problem</td>
    </tr>
    <tr>
        <td><font style='font-size: small;'>android.content.res.Resources$NotFoundException: Resource ID #0x39030039</font></td><td><font style='font-size: small;'>resourceid can not found in bundle in package 0x39</font></td><td style='font-size: small;'>in atlas framework activity animation ,remoteview, widget ,popupwindow resource which resource will be found in WindowManagerService,you should put these resouce in maindex,otherwise may cause resource not found</td>
    </tr>
</table>

