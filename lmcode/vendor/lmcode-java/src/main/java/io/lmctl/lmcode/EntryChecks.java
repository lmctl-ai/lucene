package io.lmctl.lmcode;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Independent wire fixtures: refusals must name the entry, not fail on a version/schema mismatch. */
public final class EntryChecks {
  static byte[] fixture(int version,int name,int nparams,int nlocals,int type,int hidden,String iface,byte[] code) {
    try {var bytes=new ByteArrayOutputStream();var d=new DataOutputStream(bytes);
      d.writeBytes("DSLB");d.writeShort(version);d.write(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
      var strings=List.of("increment","pair",iface,"int","string");d.writeInt(strings.size());
      for(String s:strings){byte[] b=s.getBytes(java.nio.charset.StandardCharsets.UTF_8);d.writeByte(1);d.writeInt(b.length);d.write(b);}
      d.writeShort(0);d.writeShort(0);d.writeShort(0);d.writeShort(0);d.writeInt(1);d.writeByte(17);d.writeInt(0);
      if(version==5)return bytes.toByteArray();
      d.writeShort(1);d.writeInt(name);d.writeInt(1);d.writeInt(2);d.writeShort(nparams);d.writeShort(nlocals);
      for(int i=0;i<nlocals;i++){d.writeInt(type);d.writeByte(hidden);}d.writeShort(2);d.writeInt(code.length);d.write(code);
      d.writeInt(1);d.writeInt(0);d.writeInt(7);d.writeShort(3);return bytes.toByteArray();
    }catch(Exception e){throw new AssertionError(e);}
  }
  static byte[] valid(){return fixture(6,0,1,1,3,0,"interface Step(int) -> int",new byte[]{4,0,0,17});}
  // WP11 fixes: duplicate entry names and STORE_LOCAL to a hidden non-loop local are refused.
  static byte[] duplicateName(){byte[] b=valid();int record=12+2+2+5+2+4+4+4+10,count=b.length-record-2;var out=new ByteArrayOutputStream();out.write(b,0,count);out.write(0);out.write(2);out.write(b,count+2,record);out.write(b,count+2,record);return out.toByteArray();}
  static byte[] hiddenStore(){byte[] b=fixture(6,0,1,2,3,0,"interface Step(int) -> int",new byte[]{4,0,0,5,0,1,4,0,0,17});int record=12+2+2+10+2+4+10+4+10;b[b.length-record+25]=1;return b;}
  static void refuse(String label,byte[] bytes,String text){try{Program.load(bytes,new byte[0],new Registry(false));throw new AssertionError(label+": accepted malformed entry");}catch(VmException e){check(e.code.equals("LOAD_BAD_BYTECODE")&&e.getMessage().contains(text),label+": "+e.getMessage());}System.out.println("PASS refusal "+label);}
  static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
  public static void main(String[] args){int failed=0;var checks=new LinkedHashMap<String,Runnable>();
    checks.put("v5",()->refuse("v5",fixture(5,0,1,1,3,0,"interface Step(int) -> int",new byte[]{4,0,0,17}),"unsupported bytecode version 5 (this runtime reads version 6)"));checks.put("duplicate name",()->refuse("duplicate name",duplicateName(),"duplicate entry name 'increment'"));checks.put("hidden store",()->refuse("hidden store",hiddenStore(),"writes compiler-owned loop local 1"));
    checks.put("constant",()->refuse("constant",fixture(6,99,1,1,3,0,"interface Step(int) -> int",new byte[]{4,0,0,17}),"constant index 99"));
    checks.put("interface",()->refuse("interface",fixture(6,0,1,1,3,0,"fn Step(int) -> int",new byte[]{4,0,0,17}),"entry 'increment': interface"));
    checks.put("params count",()->refuse("params count",fixture(6,0,0,1,3,0,"interface Step(int) -> int",new byte[]{4,0,0,17}),"entry 'increment' declares 0 parameter(s); interface Step(int) -> int has 1"));
    checks.put("locals count",()->refuse("locals count",fixture(6,0,1,0,3,0,"interface Step(int) -> int",new byte[]{17}),"nparams 1 exceeds nlocals 0"));
    checks.put("param type",()->refuse("param type",fixture(6,0,1,1,4,0,"interface Step(int) -> int",new byte[]{4,0,0,17}),"entry 'increment' parameter 1 has type string; the interface expects int"));
    checks.put("hidden param",()->refuse("hidden param",fixture(6,0,1,1,3,1,"interface Step(int) -> int",new byte[]{4,0,0,17}),"entry 'increment' parameter 1 is hidden"));
    checks.put("hidden flag",()->refuse("hidden flag",fixture(6,0,1,1,3,2,"interface Step(int) -> int",new byte[]{4,0,0,17}),"hidden flag must be 0 or 1"));
    checks.put("HALT value",()->refuse("HALT value",fixture(6,0,0,0,3,0,"interface Step() -> int",new byte[]{17}),"entry 'increment': HALT leaves 0 values"));
    checks.put("HALT void",()->refuse("HALT void",fixture(6,0,0,0,3,0,"interface Step() -> void",new byte[]{3,1,17}),"entry 'increment': HALT leaves 1 values"));
    checks.put("jump",()->refuse("jump",fixture(6,0,0,0,3,0,"interface Step() -> void",new byte[]{12,0,0,0,20,17}),"entry 'increment': jump at offset"));
    checks.put("underflow",()->refuse("underflow",fixture(6,0,0,0,3,0,"interface Step() -> void",new byte[]{16,17}),"entry 'increment': operand stack underflow"));
    checks.put("truncated",()->refuse("truncated",Arrays.copyOf(valid(),valid().length-1),"truncated"));
    checks.put("trailing",()->refuse("trailing",Arrays.copyOf(valid(),valid().length+1),"trailing byte"));
    for(var c:checks.entrySet())try{c.getValue().run();}catch(AssertionError e){failed++;System.out.println("FAIL "+e.getMessage());}
    if(failed!=0)throw new AssertionError(failed+" entry controls failed");
    Registry registry=new Registry(false);Program program=Program.load(valid(),new byte[0],registry);Context context=new Context();
    check(program.entries().size()==1&&program.entries().getFirst().name().equals("increment")&&program.entries().getFirst().params().equals(List.of("int")),"entry metadata");
    check(program.call(0,context,registry,List.of(Value.ofInt(41))).asInt()==41,"call before body initializes parameters");
    program.run(context,registry);
    check(program.call(0,context,registry,List.of(Value.ofInt(9))).asInt()==9,"calls use fresh locals");
    check(program.call(0,context,registry,List.of(Value.ofString("untyped"))).asString().toString().equals("untyped"),"R34 no argument/result kind check");
    try{program.call(0,context,registry,List.of());throw new AssertionError("arity accepted");}catch(VmException e){check(e.code.equals("RUN_HOST")&&e.getMessage().equals("call entry 'increment' expects 1 argument(s); found 0"),"arity text");}
    try{program.call(-1,context,registry,List.of());throw new AssertionError("index accepted");}catch(VmException e){check(e.code.equals("RUN_HOST"),"entry index refusal");}
    try{program.entries().clear();throw new AssertionError("mutable entries");}catch(UnsupportedOperationException expected){}
    try{program.entries().getFirst().params().clear();throw new AssertionError("mutable params");}catch(UnsupportedOperationException expected){}
    Program empty=Program.load(fixture(6,0,0,0,3,0,"interface Step() -> void",new byte[]{17}),new byte[0],registry);
    check(empty.call(0,context,registry,List.of()).kind==Value.Kind.NONE,"void result");
    Program bad=Program.load(fixture(6,0,0,0,3,0,"interface Step() -> int",new byte[]{1,0,0,0,0,0,0,0,1,1,0,0,0,0,0,0,0,0,22,17}),new byte[0],registry);
    try{bad.call(0,context,registry,List.of());throw new AssertionError("division accepted");}catch(VmException e){check(e.code.equals("RUN_ERROR")&&Objects.equals(e.line,7L)&&Objects.equals(e.column,3),"entry location");}
    context.instructionLimit=0;
    try{program.call(0,context,registry,List.of(Value.ofInt(1)));throw new AssertionError("budget ignored");}catch(VmException e){check(e.getMessage().contains("instruction budget"),"same execution budget");}
    check(ConformanceRunner.arguments("[9007199254740993]",List.of("int"))[0].asInt()==9007199254740993L,"exact int64 call argument");
    System.out.println("PASS entry API");
  }
}
