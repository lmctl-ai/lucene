package io.lmctl.lmcode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class StdJson {
  private StdJson() {}

  enum Kind { NULL, BOOL, INT, FLOAT, STRING, ARRAY, OBJECT }

  static final class Member {
    final VmString key;
    final VmString rawKey;
    final Node value;
    Member(VmString key, VmString rawKey, Node value) {
      this.key = key;
      this.rawKey = rawKey;
      this.value = value;
    }
  }

  static final class Node {
    Kind kind = Kind.NULL;
    boolean booleanValue;
    long integerValue;
    double floatValue;
    VmString text;
    VmString raw;
    ArrayList<Node> array;
    ArrayList<Member> object;
  }

  private static final byte[] LIT_NULL = {'n', 'u', 'l', 'l'};
  private static final byte[] LIT_TRUE = {'t', 'r', 'u', 'e'};
  private static final byte[] LIT_FALSE = {'f', 'a', 'l', 's', 'e'};

  private static void fail(String reason, int pos) {
    throw VmException.host("json_parse: " + reason + " at byte " + pos);
  }

  private static void appendUtf8(ByteArrayOutputStream out, int cp) {
    if (cp <= 0x7f) {
      out.write(cp);
    } else if (cp <= 0x7ff) {
      out.write(0xc0 | (cp >> 6));
      out.write(0x80 | (cp & 0x3f));
    } else if (cp <= 0xffff) {
      out.write(0xe0 | (cp >> 12));
      out.write(0x80 | ((cp >> 6) & 0x3f));
      out.write(0x80 | (cp & 0x3f));
    } else {
      out.write(0xf0 | (cp >> 18));
      out.write(0x80 | ((cp >> 12) & 0x3f));
      out.write(0x80 | ((cp >> 6) & 0x3f));
      out.write(0x80 | (cp & 0x3f));
    }
  }

  static void utf8(byte[] s, int base) {
    for (int i = 0; i < s.length;) {
      int c = s[i] & 0xff;
      if (c < 128) {
        i++;
        continue;
      }
      int n = 0;
      int cp = 0;
      if (c >= 194 && c <= 223) {
        n = 2;
        cp = c & 31;
      } else if (c >= 224 && c <= 239) {
        n = 3;
        cp = c & 15;
      } else if (c >= 240 && c <= 244) {
        n = 4;
        cp = c & 7;
      } else {
        fail("invalid UTF-8", base + i);
      }
      if (i + n > s.length) fail("invalid UTF-8", base + i);
      for (int j = 1; j < n; j++) {
        if ((s[i + j] & 192) != 128) fail("invalid UTF-8", base + i);
      }
      for (int j = 1; j < n; j++) {
        cp = (cp << 6) | (s[i + j] & 63);
      }
      if ((n == 3 && cp < 2048) || (n == 4 && cp < 65536) || cp > 0x10ffff || (cp >= 0xd800 && cp <= 0xdfff)) {
        fail("invalid UTF-8", base + i);
      }
      i += n;
    }
  }

  static final class Parser {
    final byte[] s;
    int p = 0;

    Parser(byte[] s) { this.s = s; }

    void ws() {
      while (p < s.length && (s[p] == ' ' || s[p] == '\t' || s[p] == '\r' || s[p] == '\n')) p++;
    }

    boolean take(byte c) {
      if (p < s.length && s[p] == c) {
        p++;
        return true;
      }
      return false;
    }

    void lit(byte[] w) {
      if (p + w.length > s.length) fail("invalid literal", p);
      for (int i = 0; i < w.length; i++) {
        if (s[p + i] != w[i]) fail("invalid literal", p);
      }
      p += w.length;
    }

    int hex(int at) {
      int c = 0;
      for (int i = 0; i < 4; i++) {
        int h = s[at + i] & 0xff;
        int v = (h >= '0' && h <= '9') ? h - '0'
              : (h >= 'a' && h <= 'f') ? h - 'a' + 10
              : (h >= 'A' && h <= 'F') ? h - 'A' + 10
              : -1;
        if (v < 0) fail("invalid Unicode escape", at + i);
        c = (c << 4) | v;
      }
      return c;
    }

    byte[] str() {
      if (!take((byte) '"')) fail("expected string", p);
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      while (p < s.length) {
        int c = s[p++] & 0xff;
        if (c == '"') return o.toByteArray();
        if (c < 32) fail("control byte in string", p - 1);
        if (c != '\\') {
          o.write(c);
          continue;
        }
        if (p >= s.length) fail("unterminated escape", p);
        int e = s[p++] & 0xff;
        switch (e) {
          case '"', '\\', '/' -> o.write(e);
          case 'b' -> o.write('\b');
          case 'f' -> o.write('\f');
          case 'n' -> o.write('\n');
          case 'r' -> o.write('\r');
          case 't' -> o.write('\t');
          case 'u' -> {
            if (p + 4 > s.length) fail("invalid Unicode escape", p);
            int cp = hex(p);
            p += 4;
            if (cp >= 0xd800 && cp <= 0xdbff) {
              if (p + 6 > s.length || s[p] != '\\' || s[p + 1] != 'u')
                fail("unpaired high surrogate", p);
              int lo = hex(p + 2);
              if (lo < 0xdc00 || lo > 0xdfff)
                fail("unpaired high surrogate", p);
              p += 6;
              cp = 0x10000 + ((cp - 0xd800) << 10) + (lo - 0xdc00);
            } else if (cp >= 0xdc00 && cp <= 0xdfff) {
              fail("unpaired low surrogate", p - 4);
            }
            appendUtf8(o, cp);
          }
          default -> fail("invalid escape", p - 1);
        }
      }
      fail("unterminated string", p);
      return new byte[0];
    }

    void num(Node o) {
      int st = p;
      if (s[p] == '-') p++;
      if (p >= s.length || s[p] < '0' || s[p] > '9') fail("invalid number", p);
      if (s[p] == '0') p++;
      else while (p < s.length && s[p] >= '0' && s[p] <= '9') p++;
      boolean fl = false;
      if (p < s.length && s[p] == '.') {
        fl = true;
        p++;
        if (p >= s.length || s[p] < '0' || s[p] > '9') fail("invalid number", p);
        while (p < s.length && s[p] >= '0' && s[p] <= '9') p++;
      }
      if (p < s.length && (s[p] == 'e' || s[p] == 'E')) {
        fl = true;
        p++;
        if (p < s.length && (s[p] == '+' || s[p] == '-')) p++;
        if (p >= s.length || s[p] < '0' || s[p] > '9') fail("invalid number", p);
        while (p < s.length && s[p] >= '0' && s[p] <= '9') p++;
      }
      o.raw = new VmString(Arrays.copyOfRange(s, st, p));
      String rawAscii = new String(s, st, p - st, StandardCharsets.US_ASCII);
      if (!fl) {
        try {
          o.integerValue = Long.parseLong(rawAscii);
        } catch (NumberFormatException e) {
          fail("integer out of range", st);
        }
        o.kind = Kind.INT;
      } else {
        double v;
        try {
          v = Double.parseDouble(rawAscii);
        } catch (NumberFormatException e) {
          v = Double.NaN;
        }
        if (!Double.isFinite(v)) fail("nonfinite number", st);
        o.floatValue = v;
        o.kind = Kind.FLOAT;
      }
    }

    Node val(int d) {
      ws();
      if (p >= s.length) fail("expected value", p);
      int st = p;
      Node o = new Node();
      byte b = s[p];
      if (b == 'n') {
        lit(LIT_NULL);
        o.kind = Kind.NULL;
      } else if (b == 't') {
        lit(LIT_TRUE);
        o.kind = Kind.BOOL;
        o.booleanValue = true;
      } else if (b == 'f') {
        lit(LIT_FALSE);
        o.kind = Kind.BOOL;
      } else if (b == '"') {
        o.kind = Kind.STRING;
        byte[] decoded = str();
        utf8(decoded, st);
        o.text = new VmString(decoded);
      } else if (b == '[') {
        if (++d > 256) fail("maximum depth exceeded", p);
        o.kind = Kind.ARRAY;
        o.array = new ArrayList<>();
        p++;
        ws();
        if (!take((byte) ']')) {
          for (;;) {
            o.array.add(val(d));
            ws();
            if (take((byte) ']')) break;
            if (!take((byte) ',')) fail("expected comma", p);
            ws();
          }
        }
      } else if (b == '{') {
        if (++d > 256) fail("maximum depth exceeded", p);
        o.kind = Kind.OBJECT;
        o.object = new ArrayList<>();
        p++;
        ws();
        HashSet<VmString> keys = new HashSet<>();
        if (!take((byte) '}')) {
          for (;;) {
            ws();
            int kp = p;
            if (p >= s.length || s[p] != '"') fail("object key must be a string", p);
            byte[] kBytes = str();
            utf8(kBytes, kp);
            VmString k = new VmString(kBytes);
            if (!keys.add(k)) fail("duplicate object key", kp);
            int keyEnd = p;
            ws();
            if (!take((byte) ':')) fail("expected colon", p);
            VmString rawKey = new VmString(Arrays.copyOfRange(s, kp, keyEnd));
            o.object.add(new Member(k, rawKey, val(d)));
            ws();
            if (take((byte) '}')) break;
            if (!take((byte) ',')) fail("expected comma", p);
            ws();
          }
        }
      } else if (b == '-' || (b >= '0' && b <= '9')) {
        num(o);
      } else {
        fail("unexpected value", p);
      }
      if (o.kind == Kind.STRING) {
        o.raw = new VmString(Arrays.copyOfRange(s, st, p));
      }
      return o;
    }
  }

  private static void emit(Node n, ByteArrayOutputStream out) {
    switch (n.kind) {
      case NULL -> out.writeBytes(LIT_NULL);
      case BOOL -> out.writeBytes(n.booleanValue ? LIT_TRUE : LIT_FALSE);
      case INT, FLOAT, STRING -> out.writeBytes(n.raw.bytes());
      case ARRAY -> {
        out.write('[');
        for (int i = 0; i < n.array.size(); i++) {
          if (i > 0) out.write(',');
          emit(n.array.get(i), out);
        }
        out.write(']');
      }
      case OBJECT -> {
        out.write('{');
        for (int i = 0; i < n.object.size(); i++) {
          if (i > 0) out.write(',');
          Member m = n.object.get(i);
          out.writeBytes(m.rawKey.bytes());
          out.write(':');
          emit(m.value, out);
        }
        out.write('}');
      }
    }
  }

  private static String kindName(Node n) {
    return switch (n.kind) {
      case NULL -> "null";
      case BOOL -> "bool";
      case INT -> "int";
      case FLOAT -> "float";
      case STRING -> "string";
      case ARRAY -> "array";
      case OBJECT -> "object";
    };
  }

  private static Node node(Value self) {
    return (Node) StdCollections.unwrap(self);
  }

  static void register(Registry r) {
    r.function("json_parse", List.of("string"), "Json", (c, self, a) -> {
      byte[] s = a[0].asString().bytes();
      c.charge(64L + s.length * 2L);
      utf8(s, 0);
      if (s.length >= 3 && (s[0] & 0xff) == 0xef && (s[1] & 0xff) == 0xbb && (s[2] & 0xff) == 0xbf) {
        fail("BOM is not allowed", 0);
      }
      Parser p = new Parser(s);
      Node parsed = p.val(0);
      p.ws();
      if (p.p != s.length) fail("trailing input", p.p);
      return StdCollections.obj(c, "Json", parsed);
    });
    r.method("Json", "kind", List.of(), "string", (c, self, a) -> Value.ofString(kindName(node(self))));
    r.method("Json", "len", List.of(), "int", (c, self, a) -> {
      Node n = node(self);
      if (n.kind == Kind.ARRAY) return Value.ofInt(n.array.size());
      if (n.kind == Kind.OBJECT) return Value.ofInt(n.object.size());
      throw VmException.host("Json.len: expected array or object");
    });
    r.method("Json", "at", List.of("int"), "Json", (c, self, a) -> {
      Node n = node(self);
      if (n.kind != Kind.ARRAY) throw VmException.host("Json.at: expected array");
      long idx = a[0].asInt();
      if (idx < 0 || idx >= n.array.size()) throw VmException.host("Json.at: index out of range: " + idx);
      return StdCollections.borrow(self, "Json", n.array.get((int) idx));
    });
    r.method("Json", "get", List.of("string"), "Json", (c, self, a) -> {
      Node n = node(self);
      if (n.kind != Kind.OBJECT) throw VmException.host("Json.get: expected object");
      VmString key = a[0].asString();
      for (Member m : n.object) {
        if (m.key.equals(key)) return StdCollections.borrow(self, "Json", m.value);
      }
      throw VmException.host("Json.get: missing key");
    });
    r.method("Json", "has", List.of("string"), "bool", (c, self, a) -> {
      Node n = node(self);
      if (n.kind != Kind.OBJECT) throw VmException.host("Json.has: expected object");
      VmString key = a[0].asString();
      for (Member m : n.object) {
        if (m.key.equals(key)) return Value.ofBool(true);
      }
      return Value.ofBool(false);
    });
    r.method("Json", "keys", List.of(), "StrList", (c, self, a) -> {
      Node n = node(self);
      if (n.kind != Kind.OBJECT) throw VmException.host("Json.keys: expected object");
      ArrayList<VmString> keys = new ArrayList<>(n.object.size());
      for (Member m : n.object) keys.add(m.key);
      return StdCollections.obj(c, "StrList", keys);
    });
    r.method("Json", "as_string", List.of(), "string", (c, self, a) -> {
      Node n = node(self);
      if (n.kind != Kind.STRING) throw VmException.host("Json.as_string: expected string");
      return Value.ofString(n.text);
    });
    r.method("Json", "as_int", List.of(), "int", (c, self, a) -> {
      Node n = node(self);
      if (n.kind != Kind.INT) throw VmException.host("Json.as_int: expected int");
      return Value.ofInt(n.integerValue);
    });
    r.method("Json", "as_float", List.of(), "float", (c, self, a) -> {
      Node n = node(self);
      if (n.kind == Kind.FLOAT) return Value.ofFloat(n.floatValue);
      if (n.kind == Kind.INT) return Value.ofFloat((double) n.integerValue);
      throw VmException.host("Json.as_float: expected number");
    });
    r.method("Json", "as_bool", List.of(), "bool", (c, self, a) -> {
      Node n = node(self);
      if (n.kind != Kind.BOOL) throw VmException.host("Json.as_bool: expected bool");
      return Value.ofBool(n.booleanValue);
    });
    r.method("Json", "to_json", List.of(), "string", (c, self, a) -> {
      Node n = node(self);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      emit(n, out);
      byte[] bytes = out.toByteArray();
      c.charge(bytes.length);
      return Value.ofString(new VmString(bytes));
    });
  }
}
