package io.lmctl.lmcode;

import java.io.OutputStream;

/** The byte oriented, dependency free lmcode standard catalog. */
public final class Stdlib {
  private Stdlib() {}
  public static void register(Registry r) {
    r.beginBuiltinRegistration();
    try { StdString.register(r); StdMath.register(r); StdCollections.register(r, true); StdContainers.register(r); StdJson.register(r); StdFile.register(r); }
    finally { r.endBuiltinRegistration(); }
  }
  public static void registerTesting(Registry r, OutputStream out) {
    StdTesting.register(r, out);
  }
  static void beginRun(Context c) { StdCollections.beginRun(c); }
  static void endRun(Context c) { StdCollections.endRun(c); }
  public static Value makeStrList(Context c, java.util.Collection<VmString> items) {
    return StdCollections.obj(c, "StrList", new java.util.ArrayList<>(items));
  }
  public static Value makeIntList(Context c, java.util.Collection<Long> items) {
    return StdCollections.obj(c, "IntList", new java.util.ArrayList<>(items));
  }
  public static Value makeFloatList(Context c, java.util.Collection<Double> items) {
    return StdCollections.obj(c, "FloatList", new java.util.ArrayList<>(items));
  }
  public static Value makeStrMap(Context c, java.util.Map<VmString, VmString> entries) {
    return StdCollections.obj(c, "StrMap", new java.util.LinkedHashMap<>(entries));
  }
  /** Read-only host snapshot; valid while its originating collection arena is live. */
  public static java.util.List<Value> collectionValues(Value collection) {
    Object value=StdCollections.unwrap(collection);
    if(!(value instanceof java.util.List<?> list))throw VmException.host("expected a standard list");
    java.util.ArrayList<Value> out=new java.util.ArrayList<>();
    for(Object element:list)out.add(switch(collection.objectType()){case "StrList"->Value.ofString((VmString)element);case "IntList"->Value.ofInt((Long)element);case "FloatList"->Value.ofFloat((Double)element);default->throw VmException.host("expected a standard list");});
    return java.util.List.copyOf(out);
  }
  public static java.util.Map<VmString,VmString> mapEntries(Value collection) {
    Object value=StdCollections.unwrap(collection);
    if(!(value instanceof java.util.Map<?,?> map))throw VmException.host("expected a standard map");
    java.util.LinkedHashMap<VmString,VmString> out=new java.util.LinkedHashMap<>();
    map.forEach((k,v)->out.put((VmString)k,(VmString)v));return java.util.Collections.unmodifiableMap(out);
  }
}
