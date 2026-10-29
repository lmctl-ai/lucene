package io.lmctl.lmcode;
import java.util.*;
/** Explicit typed callbacks; no reflection or dynamic loading. */
public final class Registry {
 @FunctionalInterface public interface Callback { Value call(Context context,Value self,Value[] arguments); }
 public static final class Binding { public final boolean method; public final String type,name,returns;public final List<String> parameters;public final Callback callback;
 Binding(String t,String n,List<String> p,String r,Callback cb){method=t!=null;type=t;name=n;parameters=List.copyOf(p);returns=r;callback=Objects.requireNonNull(cb);}
 public boolean method(){return method;} public String type(){return type;} public String name(){return name;} public List<String> parameters(){return parameters;} public String returns(){return returns;} public Callback callback(){return callback;}
 public String canonical(){return (method?"method "+type+".":"fn ")+name+"("+String.join(", ",parameters)+") -> "+returns;}}
 private final Map<String,Binding> bindings=new HashMap<>();private final Map<String,String> contexts=new HashMap<>();private final Set<String> builtins=new HashSet<>();private boolean registeringBuiltins,defaultCollections,active;
 public Registry(){this(true);} public Registry(boolean includeStd){if(includeStd)Stdlib.register(this);}
 private Registry add(String type,String name,List<String> params,String returns,Callback callback){Binding b=new Binding(type,name,params,returns,callback);String canonical=b.canonical();
 if(registeringBuiltins&&builtins.contains(canonical))return this;
 for(Binding old:bindings.values())if(Objects.equals(old.type,type)&&old.name.equals(name)&&old.parameters.equals(params))throw new VmException("LOAD_UNBOUND","registry: "+(type==null?"function":"method")+" '"+(type==null?name:type+"."+name)+"("+String.join(", ",params)+") -> "+returns+"' is registered twice");
 bindings.put(canonical,b);if(registeringBuiltins)builtins.add(canonical);return this;}
 public Registry function(String name,List<String> params,String returns,Callback cb){return add(null,name,params,returns,cb);} public Registry method(String type,String name,List<String> params,String returns,Callback cb){return add(type,name,params,returns,cb);}
 public Registry context(String name,String type){if(contexts.putIfAbsent(name,type)!=null)throw new VmException("LOAD_UNBOUND","registry: context '"+name+"' is declared twice");return this;}
 public String contextType(String name){return contexts.get(name);} public Binding resolve(String canonical){return bindings.get(canonical);}
 public String declaredFor(String canonical){int par=canonical.indexOf('(');if(par<0)return "";String prefix=canonical.substring(0,par+1);return String.join(", ",bindings.keySet().stream().filter(x->x.startsWith(prefix)).sorted().toList());}
 public List<String> signatures(){List<String> out=new ArrayList<>(bindings.keySet());contexts.forEach((n,t)->out.add("context "+n+": "+t));Collections.sort(out);return List.copyOf(out);}
 public List<String> stdSignatures(){return builtins.stream().sorted().toList();} public List<String> hostSignatures(){return signatures().stream().filter(s->!builtins.contains(s)).toList();}
 public void checkSchemaSignatures(Collection<String> signatures){Set<String> wanted=new TreeSet<>(signatures);wanted.addAll(builtins);Set<String> actual=new TreeSet<>(signatures());if(!actual.equals(wanted)){Set<String> missing=new TreeSet<>(wanted);missing.removeAll(actual);Set<String> extra=new TreeSet<>(actual);extra.removeAll(wanted);throw new VmException("LOAD_UNBOUND","registry schema signatures differ: missing ["+String.join(", ",missing)+"], extra ["+String.join(", ",extra)+"]");}}
 public void checkSchemaSignatures(byte[] schema){checkSchemaSignatures(SchemaSignatures.from(schema));}
 public boolean matchesSchemaSignatures(Collection<String> s){try{checkSchemaSignatures(s);return true;}catch(VmException e){return false;}}
 public void beginBuiltinRegistration(){registeringBuiltins=true;} public void endBuiltinRegistration(){registeringBuiltins=false;} public void markDefaultCollections(){defaultCollections=true;}
 synchronized void enterRun(Context ctx){if(active)throw VmException.host("registry run is already active");active=true;try{if(defaultCollections)Stdlib.beginRun(ctx);}catch(RuntimeException e){active=false;throw e;}}
 synchronized void exitRun(Context ctx){try{if(defaultCollections)Stdlib.endRun(ctx);}finally{active=false;}}
}
