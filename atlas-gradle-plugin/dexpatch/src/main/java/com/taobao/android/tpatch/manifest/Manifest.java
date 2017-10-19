package com.taobao.android.tpatch.manifest;


import org.apache.commons.lang3.StringUtils;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/***
 *
 *
 */
@XmlRootElement(name= "manifest")
@XmlAccessorType(XmlAccessType.FIELD)
public class Manifest {

	@XmlAttribute(name = "package")
	public String packageName;
	@XmlAttribute(name = "versionCode", namespace = "http://schemas.android.com/apk/res/android")
	public int versionCode;

	@XmlAttribute(name = "versionName", namespace = "http://schemas.android.com/apk/res/android")
	public String versionName;

	@XmlElement(name ="uses-sdk")
	public UsesSdk usesSdk;

	@XmlElements({@XmlElement(name="permission", type=Permission.class)})
	public List<Permission> permissions;

	@XmlElements({@XmlElement(name="users-permission", type=UsePermission.class)})
	public List<UsePermission> usePermissions;

	@XmlElement(name ="application")
	private Application application;

	public Application getApplication(){
		if(application == null){
			application=new Application();
		}
		return application;
	}
	@Override
	public String toString() {
		return "Manifest [\n packageName=" + packageName + ", versionCode=" + versionCode + ", versionName="
				+ versionName + ",\n usesSdk=" + usesSdk + "\n    permissions=\n\t" + permissions
				+ "\n    usePermissions=\n\t" + usePermissions + "\n application=" + application + "\n]";
	}

	public static class UsesSdk {
		@XmlAttribute(name = "minSdkVersion", namespace = "http://schemas.android.com/apk/res/android")
		public int minSdkVersion;
		@XmlAttribute(name = "maxSdkVersion", namespace = "http://schemas.android.com/apk/res/android")
		public int maxSdkVersion;
		@XmlAttribute(name = "targetSdkVersion", namespace = "http://schemas.android.com/apk/res/android")
		public int targetSdkVersion;

		@Override
		public String toString() {
			return "[minSdkVersion=" + minSdkVersion + ", maxSdkVersion=" + maxSdkVersion + ", targetSdkVersion="
					+ targetSdkVersion + "]";
		}

	}

	private static class NameBase {
		@XmlAttribute(name = "name", namespace = "http://schemas.android.com/apk/res/android")
		public String name;

		@Override
		public String toString() {
			return "\n\t" + name;
		}
	}

	public static class UsePermission extends NameBase {
	}

	public static class Permission extends NameBase {
		@XmlAttribute(name = "protectionLevel", namespace = "http://schemas.android.com/apk/res/android")
		public String protectionLevel;

		@Override
		public String toString() {
			return "name=" + name + ",protectionLevel=" + protectionLevel;
		}
	}

	public static class MetaData {
		@XmlAttribute(name = "name", namespace = "http://schemas.android.com/apk/res/android")
		public String name;
		@XmlAttribute(name = "value", namespace = "http://schemas.android.com/apk/res/android")
		public String value;

		public MetaData(){
			
		}
		public MetaData(String name, String value) {
			super();
			this.name = name;
			this.value = value;
		}

		@Override
		public String toString() {
			return "\n\t\t" + name + "=" + value;
		}

		public String getValue(){
			return value;
		}
	}

	public static class AndroidBase {
		@XmlAttribute(name = "theme", namespace = "http://schemas.android.com/apk/res/android")
		public String theme;
		@XmlAttribute(name = "label", namespace = "http://schemas.android.com/apk/res/android")
		public String label;
		@XmlAttribute(name = "icon", namespace = "http://schemas.android.com/apk/res/android")
		public String icon;
		@XmlAttribute(name = "name", namespace = "http://schemas.android.com/apk/res/android")
		public String name;

		@XmlElements({@XmlElement(name="meta-data", type=MetaData.class)})
		public List<MetaData> metadatas;

		public int getTheme() {
			return getInteger(theme);
		}
		public int getIcon() {
			return getInteger(icon);
		}

		public int getLabel() {
			return getInteger(label);
		}

		private int getInteger(String str) {
			if (str == null || str.trim().length() == 0) {
				return 0;
			}
			return Integer.parseInt(str.replace("@", ""));
		}
		public MetaData getMetaData(String name){
			if(metadatas!=null){
				for(MetaData data:metadatas){
					if(StringUtils.equals(data.name, name)){
						return data;
					}
				}
			}
			return new MetaData(name, null);
		}
		public List<MetaData> getMetaDatas(){
			return metadatas;
		}
	}

	public static class Application extends AndroidBase {
		@XmlAttribute(name = "hardwareAccelerated", namespace = "http://schemas.android.com/apk/res/android")
		public boolean hardwareAccelerated;
		@XmlAttribute(name = "allowBackup", namespace = "http://schemas.android.com/apk/res/android")
		public boolean allowBackup;

		@XmlElements({@XmlElement(name="activity", type=Activity.class)})
		public List<Activity> activitys;
		@XmlElements({@XmlElement(name="provider", type=Provider.class)})
		public List<Provider> providers;
		@XmlElements({@XmlElement(name="receiver", type=Receiver.class)})
		public List<Receiver> receivers;
		@XmlElements({@XmlElement(name="service", type=Service.class)})
		public List<Service> services;

		@Override
		public String toString() {
			return "[name=" + name + ",\n\tallowBackup=" + allowBackup + ",hardwareAccelerated=" + hardwareAccelerated
					+ ",\n\t" + "theme=" + theme + ",label=" + label + ",icon=" + icon + ",\n\tmetadata=" + metadatas
					+ "\n\tactivitys=" + activitys + "\n\tproviders=" + providers + "\n\treceivers=" + receivers
					+ "\n\tservices=" + services + "\n\t]";
		}
	}

	public static class IntentFilter {
		@XmlElements({@XmlElement(name="action", type=NameBase.class)})
		public List<NameBase> actions;
		@XmlElements({@XmlElement(name="category", type=NameBase.class)})
		public List<NameBase> categorys;
		@XmlAttribute(name = "priority", namespace = "http://schemas.android.com/apk/res/android")
		public long priority;
		@XmlElements({@XmlElement(name="data", type=IntentData.class)})
		public List<IntentData> datas;

		private String getDatas() {
			if (datas == null) {
				return null;
			}
			int count = datas.size();
			int i = 0;
			StringBuffer stringBuffer = new StringBuffer("[\n\t\t\t");
			for (IntentData data : datas) {
				i++;
				stringBuffer.append(data);
				if (i != count) {
					stringBuffer.append(",\n\t\t\t");
				}
			}
			stringBuffer.append("]");
			return stringBuffer.toString();
		}

		@Override
		public String toString() {
			return "\n\t\tIntentFilter [priority=" + priority + ",\n\t\tactions=" + actions + ",\n\t\tcategorys="
					+ categorys + ",\n\t\tdatas=" + getDatas() + "\n\t\t]";
		}

	}

	public static class IntentData {
		@XmlAttribute(name = "scheme", namespace = "http://schemas.android.com/apk/res/android")
		public String scheme;
		@XmlAttribute(name = "host", namespace = "http://schemas.android.com/apk/res/android")
		public String host;
		@XmlAttribute(name = "path", namespace = "http://schemas.android.com/apk/res/android")
		public String path;

		@Override
		public String toString() {
			return "data[scheme=" + scheme + ",host=" + host + ",path=" + path + "]";
		}
	}

	public static class ContextBase extends AndroidBase {
		@XmlAttribute(name = "exported", namespace = "http://schemas.android.com/apk/res/android")
		public boolean exported = false;
		@XmlAttribute(name = "enabled", namespace = "http://schemas.android.com/apk/res/android")
		public boolean enabled = true;
		@XmlAttribute(name = "permission", namespace = "http://schemas.android.com/apk/res/android")
		public String permission;
		@XmlElements({@XmlElement(name="intent-filter", type=IntentFilter.class)})
		public List<IntentFilter> intentFilters;
	}

	public static class Service extends ContextBase {
		@Override
		public String toString() {
			return "\n\tService[name=" + name + ",enabled=" + enabled + ",exported=" + exported + ",\n\tpermission="
					+ permission + ",\n\t" + "theme=" + theme + ",label=" + label + ",icon=" + icon + ",\n\tmetadata="
					+ metadatas + "\n\t]";
		}
	}

	public static class Receiver extends ContextBase {
		@Override
		public String toString() {
			return "\n\tReceiver[name=" + name + ",enabled=" + enabled + ",exported=" + exported + ",\n\tpermission="
					+ permission + ",\n\t" + "theme=" + theme + ",label=" + label + ",icon=" + icon + ",\n\tmetadata="
					+ metadatas + "\n\t]";
		}
	}

	public static class Provider extends ContextBase {
		@Override
		public String toString() {
			return "\n\tProvider[name=" + name + ",enabled=" + enabled + ",exported=" + exported + ",\n\tpermission="
					+ permission + ",\n\t" + "theme=" + theme + ",label=" + label + ",icon=" + icon + ",\n\tmetadata="
					+ metadatas + "\n\t]";
		}
	}

	public static class Activity extends ContextBase {
		@XmlAttribute(name = "launchMode", namespace = "http://schemas.android.com/apk/res/android")
		public int launchMode;
		@XmlAttribute(name = "screenOrientation", namespace = "http://schemas.android.com/apk/res/android")
		public int screenOrientation;
		@XmlAttribute(name = "configChanges", namespace = "http://schemas.android.com/apk/res/android")
		public String configChanges;

		@Override
		public String toString() {
			return "\n\tActivity[name=" + name + ",enabled=" + enabled + ",exported=" + exported + ",\n\tpermission="
					+ ",launchMode=" + launchMode + ",permission=" + permission + ",enabled=" + enabled + ",exported="
					+ exported + ",configChanges=" + configChanges + ",screenOrientation=" + screenOrientation + ",\n\t"
					+ "theme=" + theme + ",label=" + label + ",icon=" + icon + ",\n\tmetadata=" + metadatas
					+ ",\n\tintentFilters=" + intentFilters + "\n\t]";
		}
	}
}
