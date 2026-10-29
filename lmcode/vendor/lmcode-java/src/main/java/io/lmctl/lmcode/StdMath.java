package io.lmctl.lmcode;
final class StdMath {
 static void register(Registry r){
  r.function("math_abs",java.util.List.of("int"),"int",(c,s,a)->Value.ofInt(Math.abs(a[0].asInt())));
  r.function("math_abs",java.util.List.of("float"),"float",(c,s,a)->Value.ofFloat(Math.abs(a[0].asFloat())));
  r.function("math_min",java.util.List.of("int","int"),"int",(c,s,a)->Value.ofInt(Math.min(a[0].asInt(),a[1].asInt())));
  r.function("math_max",java.util.List.of("int","int"),"int",(c,s,a)->Value.ofInt(Math.max(a[0].asInt(),a[1].asInt())));
  r.function("math_min",java.util.List.of("float","float"),"float",(c,s,a)->Value.ofFloat(min(a[0].asFloat(),a[1].asFloat())));
  r.function("math_max",java.util.List.of("float","float"),"float",(c,s,a)->Value.ofFloat(max(a[0].asFloat(),a[1].asFloat())));
  for(String n: new String[]{"floor","ceil","round"}){ final String q=n; r.function("math_"+q,java.util.List.of("int"),"int",(c,s,a)->Value.ofInt(a[0].asInt())); r.function("math_"+q,java.util.List.of("float"),"float",(c,s,a)->Value.ofFloat(q.equals("floor")?Math.floor(a[0].asFloat()):q.equals("ceil")?Math.ceil(a[0].asFloat()):round(a[0].asFloat()))); }
  r.function("math_pow",java.util.List.of("int","int"),"int",(c,s,a)->Value.ofInt(powI(a[0].asInt(),a[1].asInt())));
  r.function("math_pow",java.util.List.of("float","float"),"float",(c,s,a)->Value.ofFloat(powF(a[0].asFloat(),a[1].asFloat())));
  r.function("math_sqrt",java.util.List.of("int"),"int",(c,s,a)->Value.ofInt(sqrtI(a[0].asInt())));
  r.function("math_sqrt",java.util.List.of("float"),"float",(c,s,a)->Value.ofFloat(Math.sqrt(a[0].asFloat())));
 }
 static double min(double a,double b){return Double.isNaN(a)||Double.isNaN(b)?Double.NaN:(b<a?b:a);} static double max(double a,double b){return Double.isNaN(a)||Double.isNaN(b)?Double.NaN:(b>a?b:a);} static double round(double x){return x>=0?Math.floor(x+.5):Math.ceil(x-.5);}
 static double powF(double a,double e){if(e==0.0||a==1.0||(a==-1.0&&Double.isInfinite(e)))return 1.0;if(Double.isNaN(a)||Double.isNaN(e))return Double.NaN;return Math.pow(a,e);}
 static long powI(long b,long e){if(e<0)throw VmException.host("math_pow: integer exponent must be nonnegative");long z=1;while(e!=0){if((e&1)!=0)z*=b;e>>>=1;if(e!=0)b*=b;}return z;}
 static long sqrtI(long n){if(n<0)throw VmException.host("math_sqrt: integer argument must be nonnegative");long lo=0,hi=3037000499L;while(lo<=hi){long m=lo+(hi-lo)/2;if(m==0||m<=n/m)lo=m+1;else hi=m-1;}return hi;}
}
