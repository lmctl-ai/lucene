package io.lmctl.lmcode;
import java.io.*;
final class StdTesting {
 static void register(Registry r,OutputStream out){OutputStream o=out==null?System.out:out;
  r.function("print",java.util.List.of("string"),"void",(c,s,a)->{try{o.write(a[0].asString().bytes());o.write('\n');o.flush();return Value.none();}catch(IOException e){throw VmException.host(e.getMessage());}});
  r.function("assert_close",java.util.List.of("float","float","float"),"void",(c,s,a)->{double x=a[0].asFloat(),y=a[1].asFloat(),t=a[2].asFloat();if(!(Math.abs(x-y)<=t))throw VmException.host("assert_close failed: "+Formatting.formatG15(x)+" vs "+Formatting.formatG15(y)+" (tol "+Formatting.formatG15(t)+")");return Value.none();});
 }
}
