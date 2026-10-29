package io.lmctl.lmcode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
public final class Reports {
 private Reports(){}
 private static void printlnLf(String line){byte[] b=(line+"\n").getBytes(StandardCharsets.UTF_8);System.out.write(b,0,b.length);System.out.flush();}
 public static byte[] escapeBytes(VmString s){return escapeBytes(s.bytes());}
 public static byte[] escapeBytes(byte[] raw){ByteArrayOutputStream b=new ByteArrayOutputStream(raw.length+2);b.write('"');for(byte v:raw){int c=v&255;switch(c){case '"'->{b.write('\\');b.write('"');}case '\\'->{b.write('\\');b.write('\\');}case '\n'->{b.write('\\');b.write('n');}case '\r'->{b.write('\\');b.write('r');}case '\t'->{b.write('\\');b.write('t');}default->{if(c<32)b.writeBytes(String.format(java.util.Locale.ROOT,"\\u%04x",c).getBytes(StandardCharsets.US_ASCII));else b.write(c);}}}b.write('"');return b.toByteArray();}
 public static String escape(VmString s){return escape(s.toString());}
 public static String escape(String s){StringBuilder b=new StringBuilder("\"");for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){case '"'->b.append("\\\"");case '\\'->b.append("\\\\");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");default->{if(c<32)b.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}}}return b.append('"').toString();}
 public static int ok(byte[] resultJson){ByteArrayOutputStream b=new ByteArrayOutputStream(resultJson.length+32);b.writeBytes("{\"ok\": true, \"result\": ".getBytes(StandardCharsets.US_ASCII));b.writeBytes(resultJson);b.writeBytes("}\n".getBytes(StandardCharsets.US_ASCII));byte[] out=b.toByteArray();System.out.write(out,0,out.length);System.out.flush();return 0;}
 public static int ok(String resultJson){printlnLf("{\"ok\": true, \"result\": "+resultJson+"}");return 0;}
 public static String errorJson(String phase,VmException e){return "{\"ok\": false, \"phase\": "+escape(phase)+", \"code\": "+escape(e.code)+", \"message\": "+escape(e.getMessage())+(e.line==null?"":", \"line\": "+e.line+(e.column==null?"":", \"column\": "+e.column))+"}";}
 public static int error(String phase,VmException e){printlnLf(errorJson(phase,e));return e.code.equals("APP_FAIL")?4:3;}
 public static int appFailure(String message){return error("app",new VmException("APP_FAIL",message));}
}
