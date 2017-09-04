# Overview of Atlas Framework
Atlas provides a unique way to for code decoupling.Atlas uses a new concept called bundle(just like bundle in osgi). A bundle module is similar to Android app module.It can almost include everything needed to build app. It become an small apk after compiling,at last compiling step we changed the Modify the extension name(.apk) of bundle to '.so' and stored it to the final shell apk. We usually called the shell apk **Base bundle** that contains other bundles 


why we need bundle?  



