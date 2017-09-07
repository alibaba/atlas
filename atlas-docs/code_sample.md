#Code Sample



## Access any component from local bundle

Users can get to your flagship Android experience just like normal Android application. each single click or any other gestures may start a component from another bundles smoothly with no aware. any component(Activity，Service，Receiver，ContentProvider) from bundles can be started from Intent—including search, social media, messaging without needing to install your bundles first. starting component in bundle from external app is also supported.


__Demo path__: `atlas/atlas-demo/demo_case/base_case`

### Step 1. Create bundle

```
├── app
│   ├── build.gradle
│   ├── libs
│   └── src
│       └── main
├── components
│   └── bundle
│       ├── build.gradle
│       ├── libs
│       └── src
├── build.gradle
├── gradle.properties
├── gradlew
└── settings.gradle
```

### Step 2. Config bundleCompile

open build.gradle

```
└── app
    └── build.gradle
```
add bundleCompile

```
bundleCompile project(':components:bundle')
``` 

### Step 3. Start bundle activity

code in DemoActivity

```
startActivity(
	new Intent().setClassName(getBaseContext(), "com.taobao.atlas.bundle.BundleActivity")
);
```

## Access remote component from remote bundle
When Compiling the final Apk，Atlas support packaging part of bundles to apk with the others be stored on the server if you want. Usually the frequency of these bundles being used is lower. So we can reduce the apk size through this behavior,and users can use it when they want. users also could be no aware that they are using a remote bundle If the bundle is small enough and can be downloaded very quickly,And the waiting dialog is no need on that condition.

__Demo path__: `atlas/atlas-demo/demo_case/bundle_remote_case`

### Step 1. Create bundle

```
├── app
│   ├── build.gradle
│   ├── libs
│   ├── proguard-rules.pro
│   └── src
├── componets
│   ├── base
│   │   ├── build.gradle
│   │   ├── libs
│   │   └── src
│   └── remotebundle
│       ├── build.gradle
│       ├── libs
│       └── src
├── gradle.properties
├── gradlew
├── build.gradle
├── settings.gradle
└── push_helper.sh

```

### Step 2. Config RemoteBundle


```
└── app
    └── build.gradle
```


```
//compile remotebundle
dependencies {
    bundleCompile project(':componets:remotebundle')
}

atlas {
	tBuildConfig {
		//set bundle is out Of Apk
		outOfApkBundles = ['remotebundle']
	}
}
```


### Step 3. Trigger of load remoteBundle


- when to load remote bundle (Activity not found)
- where to load remote bundle (`/sdcard/Android/data/pkg/cache/`)

```
public class DemoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        Atlas.getInstance().setClassNotFoundInterceptorCallback(new ClassNotFoundInterceptorCallback() {
            @Override
            public Intent returnIntent(Intent intent) {
            	//...
                File remoteBundleFile = new File(activity.getExternalCacheDir(), "lib" + bundleName.replace(".", "_") + ".so");
                String path = remoteBundleFile.getAbsolutePath();
                PackageInfo info = activity.getPackageManager().getPackageArchiveInfo(path, 0);
                Atlas.getInstance().installBundle(info.packageName, new File(path));
                activity.startActivities(new Intent[]{intent});
                return intent;
            }
        });
    }
}
```

### Step 4. load remote bundle

1. build
	
	```
	./gradlew clean assembleDebug
	```
	```
	app
	└── outputs
	    ├── apk
	    │	└── app-debug.apk
	    └── remote-bundles-debug
			└── libcom_taobao_atlas_remote_remotebundle.so
	```
2. push remotebundle to devices
	
	```
	./push_helper.sh
	```

Remotebundle is out of apk,so you need to download from server
> In demo ,we just push it to `/sdcard/Android/data/pkg/cache/`,and client load from the same path

### Step 5. Start remoteBundle

```
//com.taobao.atlas.remote.base.MainActivity

startActivity(new Intent().setClassName(
	getBaseContext(), "com.taobao.atlas.remote.remotebundle.RemoteBundleActivity"
));
```


## Usage for bundle communication

Two ways for bundle communicatio

1. aidl (recommend)
2. bundle dependencies

### Aidl (recommend)

We recommend use aidl for bundle communication. 

- Implement the aidl within each bundle
- Provide the interface to the caller

### Bundle dependencies

- Bundle dependencies can be dangerous if used improperly
- Bundle dependencies is against the encapsulation of the Bundle itself


`demo path: atlas/atlas-demo/demo_case/base_case`

#### Tree of Componets

```
└── componets
	   ├── firstbundle
	   │   ├── build.gradle
	   │   ├── bundleBaseInfoFile.json
	   │   ├── libs
	   │   └── src
	   └── publicbundle
	       ├── build.gradle
	       ├── libs
	       └── src
```

#### Config


1. Edit build.gradle (firstbundle)

	```
	dependencies {
		providedCompile project(':componets:publicbundle')
	}
	```

2. Edit bundleBaseInfoFile.json(firstbundle)

	```
	{
  		"name": "firstbundle",
  		"dependency": ["com.taobao.atlas.complex.publicbundle"]
	}
	```
	means `Firstbundle` depend on `PublicBundle`
	
3. Call method in Firstbundle
	
	```
	//com.taobao.atlas.complex.firstbundle
	
	 private void intiView() {
        TextView content = (TextView) findViewById(R.id.text);
        //LibUtils is publicbundle class
        content.setText(LibUtils.getShowText());
    }
	```

