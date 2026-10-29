package io.lmctl.lmcode;
import java.util.*;
/** Explicit schema declaration normalization; the bytecode loader still binds exact bytes. */
public final class SchemaSignatures {
 private SchemaSignatures(){}
 public static List<String> from(byte[] bytes){Map<String,Object> schema=Json.object(Json.parse(bytes));List<String> out=new ArrayList<>();
  Json.object(schema.getOrDefault("functions",Map.of())).forEach((name,overloads)->add(out,"fn "+name,overloads,null));
  Json.object(schema.getOrDefault("types",Map.of())).forEach((type,body)->Json.object(Json.object(body).getOrDefault("methods",Map.of())).forEach((name,overloads)->add(out,"method "+type+"."+name,overloads,type)));
  Json.object(schema.getOrDefault("context",Map.of())).forEach((name,type)->out.add("context "+name+": "+type));Collections.sort(out);return List.copyOf(out);
 }
 private static void add(List<String> out,String name,Object raw,String type){for(Object overload:Json.array(raw)){Map<String,Object> method=Json.object(overload);List<String> params=Json.array(method.getOrDefault("params",List.of())).stream().map(String.class::cast).toList();String returns=(String)method.getOrDefault("returns","void");if(returns.equals("self")&&type!=null)returns=type;out.add(name+"("+String.join(", ",params)+") -> "+returns);}}
}
