package io.lmctl.lmcode;
import java.util.*;
import java.io.ByteArrayOutputStream;
final class StdCollections {
 static final int LIMIT=65536;
 static final class Arena {int count; boolean alive=true;}
 static final class CollectionRef { final Arena arena; final Object value; CollectionRef(Arena a,Object v){arena=a;value=v;} Object get(){if(!arena.alive)throw VmException.host("collection is no longer live");return value;} }
 static Arena arena(Context c){return (Arena)c.state().computeIfAbsent("lmcode.std.collections",k->new Arena());}
 static void beginRun(Context c){c.state().remove("lmcode.std.collections");} static void endRun(Context c){Object x=c.state().remove("lmcode.std.collections");if(x instanceof Arena a)a.alive=false;}
 static void register(Registry r,boolean own){if(own)r.markDefaultCollections();
  r.function("str_list",List.of(),"StrList",(c,s,a)->obj(c,"StrList",new ArrayList<VmString>()));
  r.function("int_list",List.of(),"IntList",(c,s,a)->obj(c,"IntList",new ArrayList<Long>()));
  r.function("float_list",List.of(),"FloatList",(c,s,a)->obj(c,"FloatList",new ArrayList<Double>()));
  r.function("str_map",List.of(),"StrMap",(c,s,a)->obj(c,"StrMap",new LinkedHashMap<VmString,VmString>()));
  r.function("str_split",List.of("string","string"),"StrList",(c,s,a)->{VmString sep=a[1].asString();if(sep.length()==0)throw VmException.host("str_split: separator must not be empty");byte[]v=a[0].asString().bytes(),q=sep.bytes();c.charge((long)v.length+q.length);ArrayList<VmString>x=new ArrayList<>();int at=0;for(int i=0;i<=v.length-q.length;){boolean hit=true;for(int j=0;j<q.length;j++)if(v[i+j]!=q[j])hit=false;if(hit){x.add(new VmString(Arrays.copyOfRange(v,at,i)));at=i+q.length;i=at;}else i++;}x.add(new VmString(Arrays.copyOfRange(v,at,v.length)));return obj(c,"StrList",x);});
  listMethods(r,"StrList","string",x->((List<VmString>)x),v->v.asString(),v->Value.ofString((VmString)v)); listMethods(r,"IntList","int",x->((List<Long>)x),v->v.asInt(),v->Value.ofInt((Long)v)); listMethods(r,"FloatList","float",x->((List<Double>)x),v->v.asFloat(),v->Value.ofFloat((Double)v));
  r.method("StrMap","len",List.of(),"int",(c,self,a)->Value.ofInt(((LinkedHashMap<VmString,VmString>)unwrap(self)).size()));
  r.method("StrMap","has",List.of("string"),"bool",(c,self,a)->Value.ofBool(((LinkedHashMap<VmString,VmString>)unwrap(self)).containsKey(a[0].asString())));
  r.method("StrMap","get",List.of("string"),"string",(c,self,a)->{VmString v=((LinkedHashMap<VmString,VmString>)unwrap(self)).get(a[0].asString());if(v==null)throw VmException.host("StrMap.get: missing key");return Value.ofString(v);});
  r.method("StrMap","get_or",List.of("string","string"),"string",(c,self,a)->Value.ofString(((LinkedHashMap<VmString,VmString>)unwrap(self)).getOrDefault(a[0].asString(),a[1].asString())));
  r.method("StrMap","set",List.of("string","string"),"StrMap",(c,self,a)->{c.charge(64+a[0].asString().length()+a[1].asString().length());((LinkedHashMap<VmString,VmString>)unwrap(self)).put(a[0].asString(),a[1].asString());return self;});
  r.method("StrMap","key_at",List.of("int"),"string",(c,self,a)->{var m=(LinkedHashMap<VmString,VmString>)unwrap(self);return Value.ofString(new ArrayList<>(m.keySet()).get(idx(a[0].asInt(),m.size(),"StrMap.key_at")));});
  r.method("StrMap","remove",List.of("string"),"bool",(c,self,a)->Value.ofBool(((LinkedHashMap<VmString,VmString>)unwrap(self)).remove(a[0].asString())!=null));
  r.method("StrMap","clear",List.of(),"StrMap",(c,self,a)->{((Map<?,?>)unwrap(self)).clear();return self;});
  r.method("StrMap","keys",List.of(),"StrList",(c,self,a)->obj(c,"StrList",new ArrayList<>(((LinkedHashMap<VmString,VmString>)unwrap(self)).keySet())));
  r.method("StrMap","values",List.of(),"StrList",(c,self,a)->obj(c,"StrList",new ArrayList<>(((LinkedHashMap<VmString,VmString>)unwrap(self)).values())));
 }
  interface L<T>{List<T> get(Object x);} interface G<T>{T get(Value v);} interface P<T>{Value put(T x);}
 static <T>void listMethods(Registry r,String type,String elem,L<T>g,G<T>get,P<T>put){r.method(type,"len",List.of(),"int",(c,s,a)->Value.ofInt(g.get(unwrap(s)).size()));r.method(type,"at",List.of("int"),elem,(c,s,a)->{List<T>x=g.get(unwrap(s));return put.put(x.get(idx(a[0].asInt(),x.size(),type+".at")));});r.method(type,"add",List.of(elem),type,(c,s,a)->{c.charge(32);g.get(unwrap(s)).add(get.get(a[0]));return s;});r.method(type,"set",List.of("int",elem),type,(c,s,a)->{List<T>x=g.get(unwrap(s));x.set(idx(a[0].asInt(),x.size(),type+".set"),get.get(a[1]));return s;});r.method(type,"contains",List.of(elem),"bool",(c,s,a)->Value.ofBool(find(g.get(unwrap(s)),get.get(a[0]),type)>=0));r.method(type,"index_of",List.of(elem),"int",(c,s,a)->Value.ofInt(find(g.get(unwrap(s)),get.get(a[0]),type)));r.method(type,"clear",List.of(),type,(c,s,a)->{g.get(unwrap(s)).clear();return s;});if(type.equals("StrList"))r.method(type,"join",List.of("string"),"string",(c,s,a)->{var x=(List<VmString>)unwrap(s);byte[] sep=a[0].asString().bytes();long size=(long)Math.max(0,x.size()-1)*sep.length;for(VmString v:x)size+=v.length();if(size>Integer.MAX_VALUE)throw VmException.host("StrList.join: result too large");c.charge(size);ByteArrayOutputStream b=new ByteArrayOutputStream((int)size);for(int i=0;i<x.size();i++){if(i>0)b.writeBytes(sep);b.writeBytes(x.get(i).bytes());}return Value.ofString(new VmString(b.toByteArray()));});}
 static Object unwrap(Value v){Object x=v.asObject();if(x==null)throw VmException.host(v.objectType()+": null handle");return x instanceof CollectionRef r?r.get():x;}
 static Value borrow(Value self,String t,Object o){Object x=self.asObject();if(x instanceof CollectionRef r){r.get();return Value.object(t,new CollectionRef(r.arena,o));}return Value.object(t,o);}
 static <T> int find(List<T>x,T v,String type){for(int i=0;i<x.size();i++){Object q=x.get(i);boolean same=type.equals("FloatList")?Double.compare((Double)q,(Double)v)==0||((Double)q).doubleValue()==((Double)v).doubleValue():Objects.equals(q,v);if(type.equals("FloatList"))same=((Double)q).doubleValue()==((Double)v).doubleValue();if(same)return i;}return -1;}
 static int idx(long i,int n,String m){if(i<0||i>=n)throw VmException.host(m+": index out of range: "+i);return(int)i;} static Value obj(Context c,String t,Object o){Arena a=arena(c);if(a.count++>=LIMIT)throw VmException.host(t+" object limit exceeded (65536)");c.charge(64);return Value.object(t,new CollectionRef(a,o));}
}
