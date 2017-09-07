#Atlas

##Native Android apps, without the installation

An evolution in app development, Atlas split your app code to many bundle-modules which is decouple with each other, And the Apk File built by Atlas plugin is also different from the default apk. It may has some small apk in libs or assets which we called them bundles. Every bundle has it's own dex and res just like default apk. Usually a bundle means an independent individual. 

Atlas allows Android users to run your bundles instantly, without installation. Android users experience what they love about apps—fast and beautiful user interfaces, high performance, and great capabilities—with just a tap.

##Access any component from local bundle

<div style="height:450px">
    <div style="height:100px; float:left">
        <div><img src="img/access1.png" width='200' ></div>
   </div>
    <div style="float:left; margin-left:220px;margin-top:-60px" />
        Users can get to your flagship Android experience just like normal Android application. each single click or any other gestures may start a component from another bundles smoothly with no aware. any component(Activity，Service，Receiver，ContentProvider) from bundles can be started from Intent—including search, social media, messaging without needing to install your bundles first. starting component in bundle from external app is also supported.
    </div>
</div>


##Access remote component from remote bundle
When Compiling the final Apk，Atlas support packaging part of bundles to apk with the others be stored on the server if you want. Usually the frequency of these bundles being used is lower. So we can reduce the apk size through this behavior,and users can use it when they want. users also could be no aware that they are using a remote bundle If the bundle is small enough and can be downloaded very quickly,And the waiting dialog is no need on that condition.
<div style="height:460px">
    <div style="height:100px; float:left">
        <div><img src="img/access2.png" width='200' ></div>
        <div style="float:left; margin-left:220px;margin-top:-300px" />→</div>
    </div>
  <div style="height:100px; float: left;margin-left:20px">
        <div><img src="img/access3.png"  width='200'></div>
   <div style="float:left; margin-left:220px;margin-top:-300px" />→</div>
    </div>
    <div style="height:100px; float:left;margin-left:20px">
        <div><img src="img/access4.png" width='200'></div>
    </div>
</div>

##Update bundle without the entire Apk upgrade

##Compatible with all versions higher than 4.0
<div style="height:260px">
    <div style="height:0px;float:left; margin-right:400px;margin-top:20px" />
        Atlas supports the latest Android devices from Android 4.0 (API level 14) through Android O, compatible with all device that available on the market.
    </div>
   <div style="float:right;">
        <div><img src="img/support.png" width='380' ></div>
   </div>
</div>

##Start to Modularize your application
Atlas functionality is an upgrade to your existing Android app, not a new, separate app