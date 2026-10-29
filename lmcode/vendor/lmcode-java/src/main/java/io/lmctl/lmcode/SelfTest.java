package io.lmctl.lmcode;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Small dependency-free smoke suite used by the JVM and native launchers. */
public final class SelfTest {
  private SelfTest() {}

  public static void main(String[] args) {
    int failures = runTests();
    if (failures != 0) throw new AssertionError("SelfTest failures: " + failures);
  }

  /** Returns the number of failed checks; zero is a successful self-test. */
  public static int runTests() {
    int checks = 0, failures = 0;
    try { checks++; EntryChecks.main(new String[0]); } catch (Throwable e) { failures++; e.printStackTrace(System.err); }
    try { checks++; strings(); } catch (Throwable e) { failures++; e.printStackTrace(System.err); }
    try { checks++; formatting(); } catch (Throwable e) { failures++; e.printStackTrace(System.err); }
    try { checks++; registry(); } catch (Throwable e) { failures++; e.printStackTrace(System.err); }
    try { checks++; collections(); } catch (Throwable e) { failures++; e.printStackTrace(System.err); }
    try { checks++; vmCaps(); } catch (Throwable e) { failures++; e.printStackTrace(System.err); }
    try { checks++; malformedWhile(); } catch (Throwable e) { failures++; e.printStackTrace(System.err); }
    try { checks++; FileContractChecks.run(); } catch (Throwable e) { failures++; e.printStackTrace(System.err); }
    try { checks++; CollectionContractChecks.run(); } catch (Throwable e) { failures++; e.printStackTrace(System.err); }
    System.out.println(failures == 0 ? "PASS " + checks : "FAIL " + failures + "/" + checks);
    return failures;
  }

  private static void strings() {
    byte[] raw = {(byte)0xff, 0, (byte)0x80};
    VmString s = new VmString(raw); raw[0] = 0;
    require(s.bytes()[0] == (byte)0xff, "VmString constructor must copy bytes");
    byte[] copy = s.bytes(); copy[1] = 2;
    require(s.bytes()[1] == 0 && s.length() == 3, "VmString.bytes must be defensive");
    require(s.toString().length() > 0, "invalid UTF-8 must remain representable");
    require(new VmString(new byte[]{'a', 0, 'b'}).equals(new VmString(new byte[]{'a', 0, 'b'})), "NUL bytes compare by value");
    Registry registry=new Registry();Context context=new Context();Registry.Binding match=registry.resolve("fn str_match(string, string) -> bool");
    Registry.Binding validFloat=registry.resolve("fn str_is_float(string) -> bool");Registry.Binding toFloat=registry.resolve("fn str_to_float(string) -> float");
    for(String zero:List.of("+.0","-.0","0.e-999",".000e999"))require(validFloat.callback.call(context,Value.none(),new Value[]{Value.ofString(zero)}).asBool(),"decimal zero grammar: "+zero);
    require(Double.doubleToRawLongBits(toFloat.callback.call(context,Value.none(),new Value[]{Value.ofString("-.0")}).asFloat())==Long.MIN_VALUE,"negative fractional zero sign");
    require(!validFloat.callback.call(context,Value.none(),new Value[]{Value.ofString("1e-999")}).asBool(),"nonzero underflow is invalid");

    VmString pattern=new VmString(new byte[]{'[',127,'-',(byte)129,']'});
    for(int i=0;i<256;i++)require(match.callback.call(context,Value.none(),new Value[]{Value.ofString(new VmString(new byte[]{(byte)i})),Value.ofString(pattern)}).asBool()==(i>=127&&i<=129),"glob ranges use unsigned bytes");
    require(!match.callback.call(context,Value.none(),new Value[]{Value.ofString("["),Value.ofString("[abc")}).asBool(),"unterminated class leaves remaining literal tokens");
    require(match.callback.call(context,Value.none(),new Value[]{Value.ofString("[abc"),Value.ofString("[abc")}).asBool(),"unterminated class is a literal bracket");
  }

  private static void formatting() {
    require(Formatting.formatG15(-0.0).equals("-0"), "negative zero formatting");
    require(Formatting.formatG15(Double.NaN).equals("nan"), "NaN formatting");
    require(Formatting.formatG15(Double.MIN_VALUE).equals("4.94065645841247e-324"), "subnormal formats exact binary64 digits");
    require(Formatting.formatG15(-Double.MIN_VALUE).equals("-4.94065645841247e-324"), "negative subnormal formatting");
    require(Formatting.formatG15(Double.POSITIVE_INFINITY).equals("inf"), "infinity formatting");
    require(Formatting.formatG15(1e-5).equals("1e-05"), "small scientific formatting");
    require(Formatting.formatG15(1e-4).equals("0.0001"), "fixed notation threshold");
    require(Formatting.formatG15(1e15).equals("1e+15"), "large scientific threshold");
    require(Formatting.formatG15(1.234567890123456).equals("1.23456789012346"), "15 digit rounding");
  }

  private static void registry() {
    Registry r = new Registry(false);
    r.function("f", List.of("int"), "int", (c,s,a) -> a[0]);
    r.function("f", List.of("float"), "float", (c,s,a) -> a[0]);
    require(r.resolve("fn f(int) -> int") != null && r.resolve("fn f(float) -> float") != null, "typed overload lookup");
    boolean duplicate = false;
    try { r.function("f", List.of("int"), "string", (c,s,a) -> Value.ofString("bad")); }
    catch (VmException e) { duplicate = "LOAD_UNBOUND".equals(e.code); }
    require(duplicate, "duplicate signature with wrong return is rejected");
    Registry std = new Registry(true);
    List<String> before = std.signatures();
    std.checkSchemaSignatures(std.stdSignatures());
    Stdlib.register(std);
    require(before.equals(std.signatures()), "default standard registration is idempotent");
  }

  private static void collections() {
    Registry r = new Registry(true); Context c = new Context();
    Value map;
    r.enterRun(c);
    try {
      Registry.Binding b = r.resolve("fn str_map() -> StrMap");
      map = b.callback.call(c, Value.none(), new Value[0]);
      require(map.kind == Value.Kind.OBJ, "str_map returns an object");
      require(c.state().containsKey("lmcode.std.collections"), "collection arena is live during run");
    } finally { r.exitRun(c); }
    require(!c.state().containsKey("lmcode.std.collections"), "collection arena is cleared after run");
    boolean expired = false;
    try { r.resolve("method StrMap.len() -> int").callback.call(c, map, new Value[0]); }
    catch (VmException e) { expired = "RUN_HOST".equals(e.code); }
    require(expired, "collection handles cannot outlive their run");
  }

  private static void vmCaps() {
    Program p = Program.load(loopBytecode(), new byte[0], new Registry(false));
    Context instruction = new Context(); instruction.instructionLimit = 3;
    Throwable instructionFailure = runBounded(p, instruction);
    boolean limited = instructionFailure instanceof VmException e && "RUN_ERROR".equals(e.code);
    require(limited, "instruction cap stops a real looping bytecode program");
    Context memory = new Context(); memory.memoryLimitBytes = 0;
    Throwable memoryFailure = runBounded(p, memory);
    boolean memoryLimited = memoryFailure instanceof VmException e && "RUN_ERROR".equals(e.code);
    require(memoryLimited, "memory cap is enforced by real VM execution");
    Registry std = new Registry(true);
    Context tiny = new Context(); tiny.memoryLimitBytes = 8;
    boolean allocationLimited = false;
    try { std.resolve("fn str_repeat(string, int) -> string").callback.call(tiny, Value.none(), new Value[]{Value.ofString("abcd"), Value.ofInt(4)}); }
    catch (VmException e) { allocationLimited = "RUN_ERROR".equals(e.code); }
    require(allocationLimited, "string allocation is preflighted against the memory cap");
  }

  private static Throwable runBounded(Program p, Context context) {
    AtomicReference<Throwable> result = new AtomicReference<>();
    Thread t = new Thread(() -> { try { p.run(context, new Registry(false)); } catch (Throwable e) { result.set(e); } }, "lmcode-self-test");
    t.setDaemon(true);
    t.start();
    try { t.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return e; }
    return t.isAlive() ? new AssertionError("VM did not stop within self-test timeout") : result.get();
  }

  private static byte[] loopBytecode() {
    return codeBytecode(new byte[]{52,3,1,53,0,0,0,5,54,(byte)255,(byte)255,(byte)255,(byte)243,17});
  }
  private static void malformedWhile() {
    byte[] code={52,1,0,0,0,0,0,0,0,5,3,0,53,0,0,0,6,16,54,(byte)255,(byte)255,(byte)255,(byte)233,17};
    boolean rejected=false;try{Program.load(codeBytecode(code),new byte[0],new Registry(false));}catch(VmException e){rejected=e.code.equals("LOAD_BAD_BYTECODE")&&e.getMessage().contains("WHILE_TEST must leave");}
    require(rejected,"while condition must leave exactly its loop-head depth");
  }
  private static byte[] codeBytecode(byte[] code) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream d = new DataOutputStream(bytes);
      d.writeBytes("DSLB"); d.writeShort(6);
      d.write(MessageDigest.getInstance("SHA-256").digest(new byte[0]));
      d.writeInt(0); d.writeShort(0); d.writeShort(0); d.writeShort(0); d.writeShort(1);
      d.writeInt(code.length); d.write(code); d.writeInt(0); d.writeShort(0); d.flush();
      return bytes.toByteArray();
    } catch (Exception e) { throw new AssertionError(e); }
  }

  private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
