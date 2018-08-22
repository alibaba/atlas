package com.taobao.android.builder.tasks.incremental;

import java.util.List;

public class MuppBundleInfo {
  /**
   * activities : ["com.taobao.tao.rate.ui.commit.MainRateActivity","com.taobao.tao.rate.ui.commit.MainRateLoadingActivity","com.taobao.tao.rate.ui.photo.PhotoPreviewActivity","com.taobao.tao.rate.ui.photo.UploadPhotoPreviewActivity","com.taobao.tao.rate.ui.commit.AppendRateActivity","com.taobao.tao.rate.ui.commit.RateEditActivity","com.taobao.tao.rate.ui.myrate.MyRateActivity","com.taobao.tao.rate.ui.shoprate.RateShopDetailActivity","com.taobao.tao.rate.ui.videodetail.VideoDetailActivity","com.taobao.tao.rate.ui.ratedetail.RateDetailActivity","com.taobao.tao.rate.ui.photo.TakePhotoActivity","com.taobao.tao.rate.ui.ShowCommandActivity","com.taobao.tao.rate.ui.commit.NavRateSucessActivity","com.taobao.tao.rate.ui.RateVideoActivity","com.taobao.socialsdk.activity.CommentListActivity","com.taobao.socialsdk.activity.ImageDisplayActivity","com.taobao.socialsdk.activity.HistroyActivity","com.taobao.socialsdk.activity.CustomCommentListActivity","com.taobao.android.social.activity.CommentListActivity","com.taobao.android.social.activity.CommentAllListActivity","com.taobao.android.social.activity.CommentDetailActivity","com.taobao.android.social.activity.CommentDialogActivity","com.taobao.android.social.activity.CommentReplyActivity","com.taobao.ugc.framework.UGCContainerActivity","com.taobao.ugc.activity.UGCPreviewActivity","com.taobao.ugc.framework.UGCLoadingActivity","com.taobao.ugc.activity.AnonymousActivity","com.taobao.tao.flexbox.layoutmanager.preview.ComponentPreviewActivity","com.taobao.tao.flexbox.layoutmanager.container.ContainerActivity","com.taobao.tao.flexbox.layoutmanager.container.CommonContainerActivity","com.taobao.tao.flexbox.layoutmanager.log.TNodeActivity","com.taobao.ugc.kit.activity.PreviewActivity","com.taobao.ugc.mini.activity.MiniPublishActivity","com.taobao.ugc.mini.activity.MiniPopupActivity"]
   * applicationName : com.taobao.tao.rate.RateApplication
   * artifactId : trade_rate
   * contentProviders : []
   * dependency : ["com.taobao.speech","com.taobao.dynamic","com.taobao.taobao.home","com.taobao.avplayer","com.taobao.relationship","com.taobao.interact.publish","com.taobao.detail.rate"]
   * desc :
   * isInternal : true
   * isMBundle : true
   * md5 :
   * name :
   * pkgName : com.taobao.trade.rate
   * receivers : []
   * remoteFragments : {}
   * remoteTransactors : {}
   * remoteViews : {}
   * services : ["com.taobao.socialsdk.SocialService","com.taobao.social.sdk.jsbridge.SocialJsBridgeService","com.taobao.tao.flexbox.layoutmanager.log.TNodeLogService","com.taobao.ugc.mini.service.MicroPublishService"]
   * size : 0
   * unique_tag : 30e9zutzuifi3
   * version : 7.11.23.75486@2.2.5.25
   * packageUrl : https://appdownload.alicdn.com/null
   * url : https://appdownload.alicdn.com/null
   */

  private String applicationName;
  private String artifactId;
  private String desc;
  private boolean isInternal;
  private boolean isMBundle;
  private String md5;
  private String name;
  private String pkgName;
  private RemoteFragmentsBean remoteFragments;
  private RemoteTransactorsBean remoteTransactors;
  private RemoteViewsBean remoteViews;
  private int size;
  private String unique_tag;
  private String version;
  private String packageUrl;
  private String url;
  private List<String> activities;
  private List<?> contentProviders;
  private List<String> dependency;
  private List<?> receivers;
  private List<String> services;

  public String getApplicationName() {
    return applicationName;
  }

  public void setApplicationName(String applicationName) {
    this.applicationName = applicationName;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  public String getDesc() {
    return desc;
  }

  public void setDesc(String desc) {
    this.desc = desc;
  }

  public boolean isIsInternal() {
    return isInternal;
  }

  public void setIsInternal(boolean isInternal) {
    this.isInternal = isInternal;
  }

  public boolean isIsMBundle() {
    return isMBundle;
  }

  public void setIsMBundle(boolean isMBundle) {
    this.isMBundle = isMBundle;
  }

  public String getMd5() {
    return md5;
  }

  public void setMd5(String md5) {
    this.md5 = md5;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPkgName() {
    return pkgName;
  }

  public void setPkgName(String pkgName) {
    this.pkgName = pkgName;
  }

  public RemoteFragmentsBean getRemoteFragments() {
    return remoteFragments;
  }

  public void setRemoteFragments(RemoteFragmentsBean remoteFragments) {
    this.remoteFragments = remoteFragments;
  }

  public RemoteTransactorsBean getRemoteTransactors() {
    return remoteTransactors;
  }

  public void setRemoteTransactors(RemoteTransactorsBean remoteTransactors) {
    this.remoteTransactors = remoteTransactors;
  }

  public RemoteViewsBean getRemoteViews() {
    return remoteViews;
  }

  public void setRemoteViews(RemoteViewsBean remoteViews) {
    this.remoteViews = remoteViews;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  public String getUnique_tag() {
    return unique_tag;
  }

  public void setUnique_tag(String unique_tag) {
    this.unique_tag = unique_tag;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getPackageUrl() {
    return packageUrl;
  }

  public void setPackageUrl(String packageUrl) {
    this.packageUrl = packageUrl;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public List<String> getActivities() {
    return activities;
  }

  public void setActivities(List<String> activities) {
    this.activities = activities;
  }

  public List<?> getContentProviders() {
    return contentProviders;
  }

  public void setContentProviders(List<?> contentProviders) {
    this.contentProviders = contentProviders;
  }

  public List<String> getDependency() {
    return dependency;
  }

  public void setDependency(List<String> dependency) {
    this.dependency = dependency;
  }

  public List<?> getReceivers() {
    return receivers;
  }

  public void setReceivers(List<?> receivers) {
    this.receivers = receivers;
  }

  public List<String> getServices() {
    return services;
  }

  public void setServices(List<String> services) {
    this.services = services;
  }

  public static class RemoteFragmentsBean {
  }

  public static class RemoteTransactorsBean {
  }

  public static class RemoteViewsBean {
  }
}
