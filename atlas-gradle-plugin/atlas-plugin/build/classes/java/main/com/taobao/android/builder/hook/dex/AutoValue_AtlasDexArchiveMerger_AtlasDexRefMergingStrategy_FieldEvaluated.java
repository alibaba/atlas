
package com.taobao.android.builder.hook.dex;

import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_AtlasDexArchiveMerger_AtlasDexRefMergingStrategy_FieldEvaluated extends AtlasDexArchiveMerger.AtlasDexRefMergingStrategy.FieldEvaluated {

  private final String declaringClass;
  private final String type;
  private final String name;

  AutoValue_AtlasDexArchiveMerger_AtlasDexRefMergingStrategy_FieldEvaluated(
      String declaringClass,
      String type,
      String name) {
    if (declaringClass == null) {
      throw new NullPointerException("Null declaringClass");
    }
    this.declaringClass = declaringClass;
    if (type == null) {
      throw new NullPointerException("Null type");
    }
    this.type = type;
    if (name == null) {
      throw new NullPointerException("Null name");
    }
    this.name = name;
  }

  @com.android.annotations.NonNull

  @Override
  public String declaringClass() {
    return declaringClass;
  }

  @com.android.annotations.NonNull

  @Override
  public String type() {
    return type;
  }

  @com.android.annotations.NonNull

  @Override
  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return "FieldEvaluated{"
        + "declaringClass=" + declaringClass + ", "
        + "type=" + type + ", "
        + "name=" + name
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof AtlasDexArchiveMerger.AtlasDexRefMergingStrategy.FieldEvaluated) {
      AtlasDexArchiveMerger.AtlasDexRefMergingStrategy.FieldEvaluated that = (AtlasDexArchiveMerger.AtlasDexRefMergingStrategy.FieldEvaluated) o;
      return (this.declaringClass.equals(that.declaringClass()))
           && (this.type.equals(that.type()))
           && (this.name.equals(that.name()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= declaringClass.hashCode();
    h *= 1000003;
    h ^= type.hashCode();
    h *= 1000003;
    h ^= name.hashCode();
    return h;
  }

}
