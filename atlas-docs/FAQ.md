#Frequently asked questions

Atlas is designed to maintain a lot of Android development habits. So for Android development, the cost of migration is very low.


##General


###What is the relationship between the base bundle and the other bundles?


In Atlas, The base bundle is equivalent to the Android Framework, the other bundls is equivalent to Android apps, apps can call the Framework Api or use the Framework resources. The base and the other bundles also same. If multiple bundles use the same resources, then this resources can put into the base bundle, so is the same function apis or common sdk. Multiple other bundles depend on the base bundle at the same time, thus avoiding duplication of resources. 

Atlas suggest the other bundles should call base bundle，does not suggest one of the other bundles call another。


###What is awb？

awb(Android Wireless Bundle) is the format Atlas invented, its internal structure just same like aar, only more than some unique properties of the bundle file. It will be compiled into the final package's so.


### About the bundle entry class application class 
First to declear is that the application in the bundle is not the final running application on the android system. The reason why Atlas design the bundle application as the bundle entry class is to keep everyone's android development habits.


the application class in bundle, Atlas will call the application onCreate() function when the bundle installation, you can write some initialization code. Be careful not to use "this" code in the bundle application, because it is not effective. To use the application in the system, use this.getApplicationContext().


### It will be add when there was more FAQ







