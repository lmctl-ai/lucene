package io.lmctl.lmcode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

final class Reader {
  private final byte[] bytes; int pos;
  Reader(byte[] bytes) { this.bytes = bytes; }
  boolean done() { return pos == bytes.length; }
  private void need(int n) {
    if (n < 0 || pos < 0 || n > bytes.length - pos)
      throw VmException.badBytecode("bytecode is truncated: wanted " + n + " byte(s) at offset " + pos + ", file has " + bytes.length);
  }
  int u8() { need(1); return bytes[pos++] & 255; }
  int u16() { need(2); int v=((bytes[pos]&255)<<8)|(bytes[pos+1]&255); pos+=2; return v; }
  long u32() { need(4); long v=((long)(bytes[pos]&255)<<24)|((long)(bytes[pos+1]&255)<<16)|((long)(bytes[pos+2]&255)<<8)|(bytes[pos+3]&255L); pos+=4; return v; }
  int i32() { return (int)u32(); }
  long i64() { need(8); long v=ByteBuffer.wrap(bytes,pos,8).order(ByteOrder.BIG_ENDIAN).getLong(); pos+=8; return v; }
  double f64() { return Double.longBitsToDouble(i64()); }
  byte[] bytes(int n) { need(n); byte[] v=Arrays.copyOfRange(bytes,pos,pos+n); pos+=n; return v; }
}
