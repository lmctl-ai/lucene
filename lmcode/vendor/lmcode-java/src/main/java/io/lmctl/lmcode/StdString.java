package io.lmctl.lmcode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class StdString {
  static final int MAX=64*1024*1024;
  private static VmString s(Value v){return v.asString();}
  static void register(Registry r){
    r.function("str_len", java.util.List.of("string"), "int", (c,self,a)->Value.ofInt(s(a[0]).length()));
    r.function("str_starts_with",java.util.List.of("string","string"),"bool",(c,x,a)->Value.ofBool(bytes(s(a[0]),s(a[1]))));
    r.function("str_ends_with",java.util.List.of("string","string"),"bool",(c,x,a)->Value.ofBool(ends(s(a[0]),s(a[1]))));
    r.function("str_contains",java.util.List.of("string","string"),"bool",(c,x,a)->Value.ofBool(index(s(a[0]),s(a[1]))>=0));
    r.function("str_find",java.util.List.of("string","string"),"int",(c,x,a)->Value.ofInt(index(s(a[0]),s(a[1]))));
    r.function("str_lower",java.util.List.of("string"),"string",(c,x,a)->Value.ofString(lower(s(a[0]))));
    r.function("str_upper",java.util.List.of("string"),"string",(c,x,a)->Value.ofString(upper(s(a[0]))));
    r.function("str_trim",java.util.List.of("string"),"string",(c,x,a)->Value.ofString(trim(s(a[0]))));
    r.function("str_substr",java.util.List.of("string","int","int"),"string",(c,x,a)->Value.ofString(substr(s(a[0]),a[1].asInt(),a[2].asInt())));
    r.function("str_repeat",java.util.List.of("string","int"),"string",(c,x,a)->Value.ofString(repeat(c,s(a[0]),a[1].asInt())));
    r.function("str_replace",java.util.List.of("string","string","string"),"string",(c,x,a)->Value.ofString(replace(c,s(a[0]),s(a[1]),s(a[2]))));
    r.function("str_is_int",java.util.List.of("string"),"bool",(c,x,a)->Value.ofBool(parseInt(s(a[0]))!=null));
    r.function("str_to_int",java.util.List.of("string"),"int",(c,x,a)->Value.ofInt(orZero(parseInt(s(a[0])))));
    r.function("str_is_float",java.util.List.of("string"),"bool",(c,x,a)->Value.ofBool(parseFloat(s(a[0]))!=null));
    r.function("str_to_float",java.util.List.of("string"),"float",(c,x,a)->Value.ofFloat(orZeroD(parseFloat(s(a[0])))));
    r.function("str_match",java.util.List.of("string","string"),"bool",(c,x,a)->Value.ofBool(glob(b(s(a[0])),b(s(a[1])))));
    r.function("str_base64_encode",java.util.List.of("string"),"string",(c,x,a)->Value.ofString(base64Encode(c,s(a[0]))));
    r.function("str_base64_decode",java.util.List.of("string"),"string",(c,x,a)->Value.ofString(base64Decode(c,s(a[0]))));
    r.function("str_hex_encode",java.util.List.of("string"),"string",(c,x,a)->Value.ofString(hexEncode(c,s(a[0]))));
    r.function("str_hex_decode",java.util.List.of("string"),"string",(c,x,a)->Value.ofString(hexDecode(c,s(a[0]))));
  }
  private static final byte[] B64="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] HEX="0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
  private static VmString base64Encode(Context c,VmString x){
    byte[] in=b(x);long groups=(in.length/3L)+(in.length%3!=0?1L:0L);
    if(groups>Integer.MAX_VALUE/4L)throw VmException.host("str_base64_encode: size overflow");
    int outLen=(int)(groups*4L);c.charge(outLen);byte[] out=new byte[outLen];int o=0;
    for(int i=0;i<in.length;i+=3){
      int rem=in.length-i,b0=in[i]&0xff,b1=rem>1?(in[i+1]&0xff):0,b2=rem>2?(in[i+2]&0xff):0;
      out[o++]=B64[(b0>>>2)&63];out[o++]=B64[((b0&3)<<4)|(b1>>>4)];
      out[o++]=rem>1?B64[((b1&15)<<2)|(b2>>>6)]:(byte)'=';out[o++]=rem>2?B64[b2&63]:(byte)'=';
    }
    return new VmString(out);
  }
  private static int b64Val(int c){
    if(c>='A'&&c<='Z')return c-'A';if(c>='a'&&c<='z')return c-'a'+26;if(c>='0'&&c<='9')return c-'0'+52;
    return c=='+'?62:c=='/'?63:-1;
  }
  private static VmString base64Decode(Context c,VmString x){
    byte[] in=b(x);if((in.length&3)!=0)throw VmException.host("str_base64_decode: invalid encoding");
    int outCap=(in.length/4)*3;if(in.length>0){if(in[in.length-1]=='=')outCap--;if(in[in.length-2]=='=')outCap--;}
    c.charge(Math.max(0,outCap));byte[] out=new byte[Math.max(0,outCap)];int o=0;
    for(int i=0;i<in.length;i+=4){
      boolean last=(i+4==in.length);int c0=in[i]&0xff,c1=in[i+1]&0xff,c2=in[i+2]&0xff,c3=in[i+3]&0xff;
      int a=b64Val(c0),b=b64Val(c1);if(a<0||b<0)throw VmException.host("str_base64_decode: invalid encoding");
      if(c2=='='){if(!last||c3!='='||(b&15)!=0)throw VmException.host("str_base64_decode: invalid encoding");out[o++]=(byte)((a<<2)|(b>>>4));continue;}
      int d=b64Val(c2);if(d<0)throw VmException.host("str_base64_decode: invalid encoding");
      out[o++]=(byte)((a<<2)|(b>>>4));out[o++]=(byte)((b<<4)|(d>>>2));
      if(c3=='='){if(!last||(d&3)!=0)throw VmException.host("str_base64_decode: invalid encoding");continue;}
      int e=b64Val(c3);if(e<0)throw VmException.host("str_base64_decode: invalid encoding");
      out[o++]=(byte)((d<<6)|e);
    }
    return o==out.length?new VmString(out):new VmString(Arrays.copyOf(out,o));
  }
  private static VmString hexEncode(Context c,VmString x){
    byte[] in=b(x);if(in.length>Integer.MAX_VALUE/2)throw VmException.host("str_hex_encode: size overflow");
    int outLen=in.length*2;c.charge(outLen);byte[] out=new byte[outLen];
    for(int i=0;i<in.length;i++){int v=in[i]&0xff;out[i*2]=HEX[v>>>4];out[i*2+1]=HEX[v&15];}
    return new VmString(out);
  }
  private static int hexVal(int c){
    if(c>='0'&&c<='9')return c-'0';if(c>='a'&&c<='f')return c-'a'+10;if(c>='A'&&c<='F')return c-'A'+10;return -1;
  }
  private static VmString hexDecode(Context c,VmString x){
    byte[] in=b(x);if((in.length&1)!=0)throw VmException.host("str_hex_decode: invalid encoding");
    int outLen=in.length/2;c.charge(outLen);byte[] out=new byte[outLen];
    for(int i=0;i<in.length;i+=2){
      int hi=hexVal(in[i]&0xff),lo=hexVal(in[i+1]&0xff);
      if(hi<0||lo<0)throw VmException.host("str_hex_decode: invalid encoding");
      out[i>>>1]=(byte)((hi<<4)|lo);
    }
    return new VmString(out);
  }
  private static byte[] b(VmString x){return x.bytes();}
  private static boolean bytes(VmString a,VmString b){return starts(b(a),b(b));}
  private static boolean starts(byte[]a,byte[]b){return a.length>=b.length&&Arrays.equals(Arrays.copyOf(a,b.length),b);}
  private static boolean ends(VmString a,VmString b){byte[]x=b(a),y=b(b);if(x.length<y.length)return false;return Arrays.equals(Arrays.copyOfRange(x,x.length-y.length,x.length),y);}
  private static int index(VmString a,VmString n){byte[]x=b(a),y=b(n); outer:for(int i=0;i<=x.length-y.length;i++){for(int j=0;j<y.length;j++)if(x[i+j]!=y[j])continue outer;return i;}return -1;}
  private static VmString lower(VmString x){return map(x,false);} private static VmString upper(VmString x){return map(x,true);}
  private static VmString map(VmString x,boolean up){byte[]a=b(x).clone();for(int i=0;i<a.length;i++)if(up?a[i]>='a'&&a[i]<='z':a[i]>='A'&&a[i]<='Z')a[i]=(byte)(a[i]+(up?-32:32));return new VmString(a);}
  private static VmString trim(VmString x){byte[]a=b(x);int i=0,j=a.length;while(i<j&&ws(a[i]))i++;while(j>i&&ws(a[j-1]))j--;return new VmString(Arrays.copyOfRange(a,i,j));}
  private static boolean ws(byte c){return c==' '||(c>=9&&c<=13);}
  private static VmString substr(VmString x,long st,long n){byte[]a=b(x);if(st<0)st=0;if(n<=0||st>=a.length)return new VmString(new byte[0]);long end=n>Long.MAX_VALUE-st?Long.MAX_VALUE:st+n;int i=(int)st,j=(int)Math.min((long)a.length,end);return new VmString(Arrays.copyOfRange(a,i,j));}
  private static VmString repeat(Context c,VmString x,long n){byte[]a=b(x);if(n<0)throw VmException.host("str_repeat: count must be nonnegative and result at most 67108864 bytes");if(a.length==0)return new VmString(new byte[0]);if(n>MAX/a.length)throw VmException.host("str_repeat: count must be nonnegative and result at most 67108864 bytes");long bytes=(long)a.length*n;c.charge(bytes);byte[]o=new byte[(int)bytes];for(int i=0;i<n;i++)System.arraycopy(a,0,o,i*a.length,a.length);return new VmString(o);}
  private static VmString replace(Context c,VmString x,VmString f,VmString t){byte[]a=b(x),q=b(f),z=b(t);if(q.length==0)return x;long count=0;for(int at=0;at<=a.length-q.length;){int k=indexAt(a,at,q);if(k<0)break;count++;at=k+q.length;}long out=(long)a.length+count*((long)z.length-q.length);if(out<0||out>MAX)throw VmException.host("str_replace: result must be at most 67108864 bytes");c.charge(out);java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream((int)out);int at=0;while(at<=a.length){int k=indexAt(a,at,q);if(k<0){o.write(a,at,a.length-at);break;}o.write(a,at,k-at);o.writeBytes(z);at=k+q.length;}return new VmString(o.toByteArray());}
  private static int indexAt(byte[]a,int at,byte[]q){outer:for(int i=at;i<=a.length-q.length;i++){for(int j=0;j<q.length;j++)if(a[i+j]!=q[j])continue outer;return i;}return -1;}
  private static Long parseInt(VmString x){try{String z=new String(b(x),StandardCharsets.US_ASCII);if(!z.matches("[+-]?[0-9]+"))return null;return Long.parseLong(z);}catch(Exception e){return null;}}
  private static Double parseFloat(VmString x){try{String z=new String(b(x),StandardCharsets.US_ASCII);if(!z.matches("[+-]?(([0-9]+(\\.[0-9]*)?)|(\\.[0-9]+))([eE][+-]?[0-9]+)?"))return null;double d=Double.parseDouble(z);if(!Double.isFinite(d))return null;if(d==0){int e=z.indexOf('e');if(e<0)e=z.indexOf('E');String significand=e<0?z:z.substring(0,e);for(int i=0;i<significand.length();i++)if(significand.charAt(i)>='1'&&significand.charAt(i)<='9')return null;}return d;}catch(Exception e){return null;}}
  private static long orZero(Long x){return x==null?0:x;} private static double orZeroD(Double x){return x==null?0:x;}
  private static boolean glob(byte[] text,byte[] pat){
    int t=0,p=0,star=-1,mark=0;long[] work={0};int[] next={0};
    while(t<text.length){step(work);if(p<pat.length&&pat[p]=='*'){star=p++;mark=t;}
      else if(p<pat.length&&matchToken(pat,p,text[t]&255,next,work)){p=next[0];t++;}
      else if(star>=0){p=star+1;t=++mark;}else return false;}
    while(p<pat.length&&pat[p]=='*'){step(work);p++;}return p==pat.length;
  }
  private static void step(long[] work){if(work[0]==(1L<<27))throw VmException.host("str_match: pattern exceeds the 2^27 work limit");work[0]++;}
  private static boolean matchToken(byte[] p,int i,int c,int[] next,long[] work){
    if(p[i]=='\\'&&i+1<p.length){next[0]=i+2;return(p[i+1]&255)==c;}
    if(p[i]=='?'){next[0]=i+1;return true;}
    if(p[i]=='['){int q=i+1;boolean negate=q<p.length&&p[q]=='!';if(negate)q++;int first=q;boolean hit=false;
      while(q<p.length&&(p[q]!=']'||q==first)){step(work);int lo=p[q++]&255;if(lo=='\\'&&q<p.length)lo=p[q++]&255;
        if(q+1<p.length&&p[q]=='-'&&p[q+1]!=']'){q++;int hi=p[q++]&255;if(hi=='\\'&&q<p.length)hi=p[q++]&255;if(lo<=hi&&c>=lo&&c<=hi)hit=true;}
        else if(lo==c)hit=true;}
      if(q<p.length){next[0]=q+1;return hit!=negate;}
    }
    next[0]=i+1;return(p[i]&255)==c;
  }
}
