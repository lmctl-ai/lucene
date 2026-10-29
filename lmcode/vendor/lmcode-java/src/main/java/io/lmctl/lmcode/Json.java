package io.lmctl.lmcode;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Small strict JSON reader for schema and manifest data, with no reflection. */
public final class Json {
 private final String s;private int p;private int depth;private final boolean exactNumbers;
 private Json(String s,boolean exactNumbers){this.s=s;this.exactNumbers=exactNumbers;}
 public static Object parse(byte[] bytes){return parse(new String(bytes,StandardCharsets.UTF_8));}
 public static Object parse(String s){return parse(s,false);}
 static Object parseExactNumbers(String s){return parse(s,true);}
 private static Object parse(String s,boolean exactNumbers){Json j=new Json(s,exactNumbers);Object v=j.value();j.ws();if(j.p!=s.length())throw new IllegalArgumentException("trailing JSON");return v;}
 private void ws(){while(p<s.length()&&" \t\r\n".indexOf(s.charAt(p))>=0)p++;}
 private char take(){if(p>=s.length())throw new IllegalArgumentException("truncated JSON");return s.charAt(p++);}
 private Object value(){ws();if(++depth>128)throw new IllegalArgumentException("JSON nesting limit");try{char c=take();return switch(c){case '{'->object();case '['->array();case '"'->string();case 't'->{literal("rue");yield Boolean.TRUE;}case 'f'->{literal("alse");yield Boolean.FALSE;}case 'n'->{literal("ull");yield null;}default->{int start=p-1;while(p<s.length()&&"0123456789.eE+-".indexOf(s.charAt(p))>=0)p++;String n=s.substring(start,p);if(!n.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw new IllegalArgumentException("invalid JSON number");if(exactNumbers)yield new java.math.BigDecimal(n);yield Double.valueOf(n);}};}finally{depth--;}}
 private void literal(String t){if(!s.startsWith(t,p))throw new IllegalArgumentException("invalid JSON literal");p+=t.length();}
 private Map<String,Object> object(){Map<String,Object> m=new LinkedHashMap<>();ws();if(p<s.length()&&s.charAt(p)=='}'){p++;return m;}while(true){ws();if(take()!='"')throw new IllegalArgumentException("JSON key");String k=string();ws();if(take()!=':')throw new IllegalArgumentException("JSON colon");Object v=value();if(m.containsKey(k))throw new IllegalArgumentException("duplicate JSON key");m.put(k,v);ws();char c=take();if(c=='}')return m;if(c!=',')throw new IllegalArgumentException("JSON comma");}}
 private List<Object> array(){List<Object> a=new ArrayList<>();ws();if(p<s.length()&&s.charAt(p)==']'){p++;return a;}while(true){a.add(value());ws();char c=take();if(c==']')return a;if(c!=',')throw new IllegalArgumentException("JSON comma");}}
 private String string(){StringBuilder b=new StringBuilder();while(true){char c=take();if(c=='"')return b.toString();if(c<32)throw new IllegalArgumentException("JSON control byte");if(c!='\\'){b.append(c);continue;}c=take();switch(c){case '"','\\','/'->b.append(c);case 'b'->b.append('\b');case 'f'->b.append('\f');case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'u'->{if(p+4>s.length())throw new IllegalArgumentException("JSON unicode");b.append((char)Integer.parseInt(s.substring(p,p+4),16));p+=4;}default->throw new IllegalArgumentException("JSON escape");}}}
 @SuppressWarnings("unchecked") public static Map<String,Object> object(Object value){if(!(value instanceof Map))throw new IllegalArgumentException("expected JSON object");return (Map<String,Object>)value;}
 @SuppressWarnings("unchecked") public static List<Object> array(Object value){if(!(value instanceof List))throw new IllegalArgumentException("expected JSON array");return (List<Object>)value;}
}
