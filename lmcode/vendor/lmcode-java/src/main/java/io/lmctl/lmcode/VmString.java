package io.lmctl.lmcode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
/** Immutable raw bytes; text conversion is an explicit UTF-8 operation. */
public final class VmString {
 private final byte[] data;
 public VmString(byte[] bytes) { data=bytes.clone(); }
 public static VmString fromString(String text) { return new VmString(text.getBytes(StandardCharsets.UTF_8)); }
 public byte[] bytes() { return data.clone(); }
 byte[] raw() { return data; }
 public int length() { return data.length; }
 public static VmString concat(VmString a,VmString b) { long n=(long)a.length()+b.length(); if(n>Integer.MAX_VALUE-8)throw VmException.run("string memory limit exceeded");byte[] out=Arrays.copyOf(a.data,(int)n);System.arraycopy(b.data,0,out,a.length(),b.length());return new VmString(out); }
 @Override public boolean equals(Object obj) { return obj instanceof VmString other && Arrays.equals(data,other.data); }
 @Override public int hashCode() { return Arrays.hashCode(data); }
 @Override public String toString() { return new String(data,StandardCharsets.UTF_8); }
}
