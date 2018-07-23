
package com.taobao.android.builder.hook.dex;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_AtlasDexArchiveMerger_AtlasDexRefMergingStrategy_MethodEvaluated extends AtlasDexArchiveMerger.AtlasDexRefMergingStrategy.MethodEvaluated {

  private final String declaringClass;
  private final String name;
  private final String protoShorty;
  private final String protoReturnType;
  private final String protoParameterTypes;

  AutoValue_AtlasDexArchiveMerger_AtlasDexRefMergingStrategy_MethodEvaluated(
      String declaringClass,
      String name,
      String protoShorty,
      String protoReturnType,
      String protoParameterTypes) {
    if (declaringClass == null) {
      throw new NullPointerException("Null declaringClass");
    }
    this.declaringClass = declaringClass;
    if (name == null) {
      throw new NullPointerException("Null name");
    }
    this.name = name;
    if (protoShorty == null) {
      throw new NullPointerException("Null protoShorty");
    }
    this.protoShorty = protoShorty;
    if (protoReturnType == null) {
      throw new NullPointerException("Null protoReturnType");
    }
    this.protoReturnType = protoReturnType;
    if (protoParameterTypes == null) {
      throw new NullPointerException("Null protoParameterTypes");
    }
    this.protoParameterTypes = protoParameterTypes;
  }

  @com.android.annotations.NonNull

  @Override
  public String declaringClass() {
    return declaringClass;
  }

  @com.android.annotations.NonNull

  @Override
  public String name() {
    return name;
  }

  @com.android.annotations.NonNull

  @Override
  public String protoShorty() {
    return protoShorty;
  }

  @com.android.annotations.NonNull

  @Override
  public String protoReturnType() {
    return protoReturnType;
  }

  @com.android.annotations.NonNull

  @Override
  public String protoParameterTypes() {
    return protoParameterTypes;
  }

  @Override
  public String toString() {
    return "MethodEvaluated{"
        + "declaringClass=" + declaringClass + ", "
        + "name=" + name + ", "
        + "protoShorty=" + protoShorty + ", "
        + "protoReturnType=" + protoReturnType + ", "
        + "protoParameterTypes=" + protoParameterTypes
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof AtlasDexArchiveMerger.AtlasDexRefMergingStrategy.MethodEvaluated) {
      AtlasDexArchiveMerger.AtlasDexRefMergingStrategy.MethodEvaluated that = (AtlasDexArchiveMerger.AtlasDexRefMergingStrategy.MethodEvaluated) o;
      return (this.declaringClass.equals(that.declaringClass()))
           && (this.name.equals(that.name()))
           && (this.protoShorty.equals(that.protoShorty()))
           && (this.protoReturnType.equals(that.protoReturnType()))
           && (this.protoParameterTypes.equals(that.protoParameterTypes()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= declaringClass.hashCode();
    h *= 1000003;
    h ^= name.hashCode();
    h *= 1000003;
    h ^= protoShorty.hashCode();
    h *= 1000003;
    h ^= protoReturnType.hashCode();
    h *= 1000003;
    h ^= protoParameterTypes.hashCode();
    return h;
  }

}
