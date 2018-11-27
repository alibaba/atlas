-keep class com.example.android.unsplash.transition.TextResize {
  public protected *;
}
-keep public class com.example.android.unsplash.ui.grid.GridMarginDecoration {
  public protected *;
}
-keep public class com.example.android.unsplash.ui.grid.PhotoAdapter {
  public protected *;
}
-keep public class com.example.android.unsplash.ui.grid.OnItemSelectedListener {
  public protected *;
}
-keep public class com.example.android.unsplash.ui.grid.PhotoViewHolder {
  public protected *;
}
-keep public class com.example.android.unsplash.ui.DetailSharedElementEnterCallback {
  public protected *;
}
-keep public class com.example.android.unsplash.ui.pager.DetailViewPagerAdapter {
  public protected *;
}
-keep public class com.example.android.unsplash.ui.TransitionCallback {
  public protected *;
}
-keep, includedescriptorclasses public class com.example.android.unsplash.data.PhotoService** {
  public protected *;
}
-keep, includedescriptorclasses public class android.support.v4.view.ViewPager {
  public protected *;
}
-keep public class android.support.v4.view.PagerAdapter {
  public protected *;
}
-keep public class android.support.v7.widget.RecyclerView** {
  public protected *;
}
-keep, includedescriptorclasses class android.support.v7.widget.GridLayoutManager {
  public protected *;
}

-keepattributes *Annotation*,Signature

-dontwarn com.google.appengine.api.urlfetch.**
-dontwarn com.squareup.okhttp.**
-dontwarn okio.BufferedSink
-dontwarn rx.**

-keep public class com.google.gson.** {
  public protected *;
}
-keep class retrofit.** { *; }