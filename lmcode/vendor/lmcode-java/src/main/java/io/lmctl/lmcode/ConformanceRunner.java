package io.lmctl.lmcode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Executes the unchanged reference .dslb and .expect files on JVM or native. */
public final class ConformanceRunner {
 private ConformanceRunner(){}
 static final class Thing {long id;Thing(long n){id=n;}}
 static final class Stub implements AutoCloseable {
  final List<String> trace=new ArrayList<>();final Registry registry;final Context context=new Context();final ByteArrayOutputStream output=new ByteArrayOutputStream();final Path temp;
  Stub(boolean standard)throws IOException {
   temp=standard?Files.createTempDirectory("lmcode-java-vector-"):null;registry=new Registry(standard);
   Value root=thing(0),variable=Value.object("Var",new Thing(0));context.setObject("t","Trace",trace);if(!standard){context.set("root",root).set("v",variable).setObject("xs","IntList",new long[]{0,1,2,3});}
   registry.context("t","Trace");if(!standard)registry.context("root","Thing").context("v","Var").context("xs","IntList");
   registry.function("bytes",List.of("string"),"string",(c,s,a)->{String hex=a[0].asString().toString();if((hex.length()&1)!=0||!hex.matches("[0-9a-f]*"))throw VmException.host("bytes: expected lowercase hexadecimal pairs");return Value.ofString(new VmString(HexFormat.of().parseHex(hex)));});
   registry.method("Trace","hex",List.of("string"),"void",(c,s,a)->record("hex "+HexFormat.of().formatHex(a[0].asString().bytes())));
   if(standard){Stdlib.registerTesting(registry,output);registry.function("printed",List.of(),"string",(c,s,a)->Value.ofString(new VmString(output.toByteArray())));registry.function("test_temp_dir",List.of(),"string",(c,s,a)->Value.ofString(temp.toString()));registry.function("test_long_path",List.of(),"string",(c,s,a)->{try{Path p=temp.resolve("x".repeat(120)).resolve("y".repeat(120));Files.createDirectories(p);return Value.ofString(p.resolve("file.bin").toString());}catch(IOException e){throw VmException.host(e.getMessage());}});}
   else{registry.method("IntList","len",List.of(),"int",(c,s,a)->Value.ofInt(((long[])s.asObject()).length));registry.method("IntList","at",List.of("int"),"int",(c,s,a)->Value.ofInt(((long[])s.asObject())[Math.toIntExact(a[0].asInt())]));}
   registry.method("Trace","int",List.of("int"),"void",(c,s,a)->record("int "+a[0].asInt()));
   registry.method("Trace","float",List.of("float"),"void",(c,s,a)->record("float "+Formatting.formatG15(a[0].asFloat())));
   registry.method("Trace","str",List.of("string"),"void",(c,s,a)->record("str "+a[0].asString()));
   registry.method("Trace","bool",List.of("bool"),"void",(c,s,a)->record("bool "+a[0].asBool()));
   registry.method("Trace","thing",List.of("Thing"),"void",(c,s,a)->record("thing "+obj(a[0]).id));
   registry.method("Trace","int",List.of("float"),"void",(c,s,a)->record("float "+Formatting.formatG15(a[0].asFloat())));
   registry.method("Trace","status",List.of(),"Status",(c,s,a)->Value.ofString("OPTIMAL"));
   registry.method("Thing","len",List.of(),"int",(c,s,a)->{long id=obj(s).id;record("len "+id);return Value.ofInt(id);});
   registry.method("Thing","at",List.of("int"),"Thing",(c,s,a)->{long id=a[0].asInt();record("at "+id);return thing(id);});
   registry.method("Thing","id",List.of(),"int",(c,s,a)->Value.ofInt(obj(s).id));registry.method("Thing","next",List.of(),"Thing",(c,s,a)->thing(obj(s).id+1));
   registry.method("Thing","name",List.of(),"string",(c,s,a)->Value.ofString("thing-"+obj(s).id));registry.method("Thing","scaled",List.of("float"),"float",(c,s,a)->Value.ofFloat(obj(s).id*a[0].asFloat()));
   registry.method("Thing","plus",List.of("Thing"),"Thing",(c,s,a)->thing(obj(s).id+obj(a[0]).id));registry.method("Thing","minus",List.of("Thing"),"Thing",(c,s,a)->thing(obj(s).id-obj(a[0]).id));
   registry.method("Thing","times",List.of("int"),"Thing",(c,s,a)->thing(obj(s).id*a[0].asInt()));registry.method("Thing","neg",List.of(),"Thing",(c,s,a)->thing(-obj(s).id));registry.method("Thing","rminus",List.of("int"),"Thing",(c,s,a)->thing(a[0].asInt()-obj(s).id));
   registry.method("Thing","set",List.of("int"),"Thing",(c,s,a)->{obj(s).id=a[0].asInt();return s;});
   registry.method("Var","id",List.of(),"int",(c,s,a)->Value.ofInt(obj(s).id));registry.method("Var","expr",List.of(),"Thing",(c,s,a)->{long id=obj(s).id;record("expr "+id);return thing(id);});
   registry.function("var",List.of("int"),"Var",(c,s,a)->{long id=a[0].asInt();record("var "+id);return Value.object("Var",new Thing(id));});
   registry.function("twice",List.of("int"),"int",(c,s,a)->Value.ofInt(a[0].asInt()*2));registry.function("twice",List.of("float"),"float",(c,s,a)->Value.ofFloat(a[0].asFloat()*2));
   registry.function("thing",List.of("int"),"Thing",(c,s,a)->thing(a[0].asInt()));registry.function("bound",List.of("int"),"int",(c,s,a)->{record("bound "+a[0].asInt());return a[0];});registry.function("half",List.of("float"),"float",(c,s,a)->Value.ofFloat(a[0].asFloat()/2));
   registry.function("yes",List.of(),"bool",(c,s,a)->{record("yes");return Value.ofBool(true);});registry.function("bad_int",List.of(),"int",(c,s,a)->Value.ofString("x"));registry.function("fail",List.of("string"),"void",(c,s,a)->{throw VmException.host(a[0].asString().toString());});
  }
  Value record(String line){trace.add(line);return Value.none();}static Thing obj(Value v){return (Thing)v.asObject();}static Value thing(long n){return Value.object("Thing",new Thing(n));}
  @Override public void close()throws IOException {if(temp!=null)try(var files=Files.walk(temp)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
 static boolean match(String actual,String expected){if(!expected.startsWith("~ "))return actual.equals(expected);String body=expected.substring(2);int space=body.lastIndexOf(' ');if(space<0||!actual.startsWith(body.substring(0,space+1)))return false;try{double want=Double.parseDouble(body.substring(space+1)),got=Double.parseDouble(actual.substring(space+1));return Double.isFinite(want)&&Double.isFinite(got)&&Math.abs(got-want)<=1e-12*Math.max(Math.abs(want),1e-300);}catch(NumberFormatException e){return false;}}
 static long number(Object value){return new java.math.BigDecimal(value.toString()).longValueExact();}
 static IllegalArgumentException bad(String what,String type){return new IllegalArgumentException("conformance: bad call args: "+what+" does not match parameter type "+type);}
 static Value argument(Object raw,String type){
  // WP11 F5: a JSON kind that does not match the declared parameter type is a runner error, never a VM input.
  if(raw instanceof Map<?,?> map){Map<String,Object> object=Json.object(map);String objectType=String.valueOf(object.get("type"));if(!objectType.equals(type)||!type.equals("Thing"))throw bad("object of type "+objectType,type);return Value.object("Thing",new Thing(number(object.get("id"))));}
  if(type.equals("int")){if(!(raw instanceof Number)||raw.toString().matches(".*[.eE].*"))throw bad(String.valueOf(raw),type);return Value.ofInt(number(raw));}
  if(type.equals("float")){if(!(raw instanceof Number))throw bad(String.valueOf(raw),type);return Value.ofFloat(((Number)raw).doubleValue());}
  if(type.equals("bool")){if(!(raw instanceof Boolean))throw bad(String.valueOf(raw),type);return Value.ofBool((Boolean)raw);}
  if(!(raw instanceof String))throw bad(String.valueOf(raw),type);
  return switch(type){case "int"->Value.ofInt(number(raw));case "float"->Value.ofFloat(((Number)raw).doubleValue());case "bool"->Value.ofBool((Boolean)raw);case "string", "Status"->Value.ofString((String)raw);default->throw new IllegalArgumentException("unsupported entry argument type "+type);};
 }
 static Value[] arguments(String json,List<String> params){List<Object> raw=Json.array(Json.parseExactNumbers(json));if(raw.size()!=params.size())return raw.stream().map(x->Value.none()).toArray(Value[]::new);Value[] out=new Value[raw.size()];for(int i=0;i<out.length;i++)out[i]=argument(raw.get(i),params.get(i));return out;}
 static String result(Value value){return switch(value.kind()){case NONE->"void";case INT->"int "+value.asInt();case FLOAT->"float "+Formatting.formatG15(value.asFloat());case STR->"string "+value.asString();case BOOL->"bool "+value.asBool();case OBJ->{Object object=value.asObject();if(object instanceof Thing t)yield "thing "+t.id;yield "object";}};}
 static boolean traceMatches(List<String> actual,List<String> expected){if(actual.size()!=expected.size())return false;for(int i=0;i<actual.size();i++)if(!match(actual.get(i),expected.get(i)))return false;return true;}
 static Program.Entry entry(Program program,String name){for(Program.Entry e:program.entries())if(e.name().equals(name))return e;throw VmException.host("entry '"+name+"' is not defined");}
 static boolean entriesMatch(Program program,List<String> expected){List<Program.Entry> got=program.entries();if(got.size()!=expected.size())return false;for(int i=0;i<got.size();i++){Program.Entry e=got.get(i);if(!(e.name()+" "+e.annotation()+" "+e.iface()).equals(expected.get(i)))return false;}return true;}
 public static int run(Path dir)throws IOException {
  Map<String,Object> manifest=Json.object(Json.parse(Files.readAllBytes(dir.resolve("manifest.json"))));Map<String,String> schemas=new HashMap<>();for(Object item:Json.array(manifest.get("vectors"))){Map<String,Object> v=Json.object(item);schemas.put((String)v.get("name"),(String)v.getOrDefault("schema","schema.json"));}
  List<Path> expectations;try(var files=Files.list(dir)){expectations=files.filter(p->p.toString().endsWith(".expect")).sorted().toList();}if(expectations.isEmpty())throw new IOException("no vectors in "+dir);
  int failed=0;for(Path expectation:expectations){String name=expectation.getFileName().toString().replaceFirst("\\.expect$","");List<String> exp=Files.readAllLines(expectation,StandardCharsets.UTF_8);String kind=exp.getFirst();List<String> lines=exp.subList(1,exp.size()).stream().map(s->s.replace("\\n","\n")).toList();byte[] schema=Files.readAllBytes(dir.resolve(schemas.getOrDefault(name,"schema.json")));Map<String,Object> declarations=Json.object(Json.parse(schema));Map<String,Object> functions=Json.object(declarations.getOrDefault("functions",Map.of()));boolean standard=List.of("str_len","math_abs","str_list","print","file_read").stream().anyMatch(functions::containsKey);
   try(Stub stub=new Stub(standard)){boolean loaded=false,ran=false;String message="",code="";VmException caught=null;try{Program program=Program.load(Files.readAllBytes(dir.resolve(name+".dslb")),schema,stub.registry);loaded=true;
    if(kind.equals("entries")){ran=entriesMatch(program,lines);}
    else if(kind.equals("call")||kind.equals("call_error")||kind.equals("call_error_at")){
     String[] call=exp.get(1).split(" ",2);Program.Entry e=entry(program,call[0]);Value[] args=arguments(call.length==2?call[1]:"[]",e.params());
     program.run(stub.context,stub.registry);Value value=program.call(program.entries().indexOf(e),stub.context,stub.registry,Arrays.asList(args));ran=true;
     if(kind.equals("call")){String wanted=lines.size()>1?lines.get(1):"";List<String> wantedTrace=lines.size()>2?lines.subList(2,lines.size()):List.of();ran=("result "+result(value)).equals(wanted)&&traceMatches(stub.trace,wantedTrace);}
    } else {program.run(stub.context,stub.registry);ran=true;}
   }catch(VmException e){caught=e;message=e.getMessage();code=e.code;}catch(RuntimeException e){message=e.toString();}
    String exact=message;if(caught!=null&&caught.line!=null)exact=message.substring(("dsl line "+caught.line+(caught.column==null?"":":"+caught.column)+": ").length());boolean location=false;int locationLine=kind.equals("call_error_at")?2:1,locationColumn=kind.equals("call_error_at")?3:2;if(lines.size()>locationColumn)try{long l=Long.parseLong(lines.get(locationLine));int c=Integer.parseInt(lines.get(locationColumn));location=(l==0?caught==null||caught.line==null:caught!=null&&Objects.equals(caught.line,l))&&(c==0?caught==null||caught.column==null:caught!=null&&Objects.equals(caught.column,c));}catch(NumberFormatException ignored){}
    if(System.getenv("LMCODE_TRACE")!=null){for(String value:stub.trace)System.out.println("TRACE\t"+name+"\t"+value);if(!code.isEmpty())System.out.println("ERROR\t"+name+"\t"+code+"\t"+exact);}
    boolean ok=switch(kind){case "trace"->{boolean equal=ran&&traceMatches(stub.trace,lines);yield equal;}case "entries"->loaded&&ran;case "call"->loaded&&ran;case "call_error"->loaded&&!ran&&lines.size()>=3&&code.equals(lines.get(1))&&message.contains(lines.get(2));case "call_error_at"->loaded&&!ran&&lines.size()>=5&&code.equals(lines.get(1))&&location&&message.contains(lines.get(4));case "runtime_error"->loaded&&!ran&&message.contains(lines.getFirst());case "runtime_error_exact"->loaded&&!ran&&lines.size()==2&&code.equals(lines.get(0))&&exact.equals(lines.get(1));case "runtime_error_file"->{String path=lines.size()>=3?(lines.get(2).isEmpty()?stub.temp.toString():stub.temp+"/"+lines.get(2)):"";String prefix=lines.size()>=3?lines.get(1)+": "+path+": ":"";yield loaded&&!ran&&lines.size()==3&&code.equals(lines.get(0))&&exact.startsWith(prefix)&&!exact.substring(prefix.length()).isBlank();}case "runtime_error_at"->loaded&&!ran&&lines.size()>=4&&code.equals(lines.get(0))&&location&&message.contains(lines.get(3));case "load_error"->!loaded&&message.contains(lines.getFirst());default->false;};
    System.out.println((ok?"ok - ":"FAIL - ")+name+" ("+kind+")");if(!ok){failed++;System.out.println("    error: "+message+" code="+code);int mismatch=0;while(mismatch<Math.min(stub.trace.size(),lines.size())&&match(stub.trace.get(mismatch),lines.get(mismatch)))mismatch++;System.out.println("    trace lengths: "+stub.trace.size()+" / "+lines.size()+"; first mismatch "+mismatch);System.out.println("    got: "+(mismatch<stub.trace.size()?stub.trace.get(mismatch):"<missing>"));System.out.println("    want: "+(mismatch<lines.size()?lines.get(mismatch):"<missing>"));}
   }
  }System.out.println((failed==0?"PASS ":"FAIL ")+(expectations.size()-failed)+"/"+expectations.size()+" vectors");return failed==0?0:1;
 }
 public static void main(String[] args)throws IOException {if(args.length==0)throw new IllegalArgumentException("usage: ConformanceRunner DIRECTORY...");int failures=0;for(String arg:args)failures+=run(Path.of(arg));if(failures!=0)throw new AssertionError("conformance failed");}
}
