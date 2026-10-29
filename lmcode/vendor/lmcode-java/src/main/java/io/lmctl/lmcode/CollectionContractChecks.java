package io.lmctl.lmcode;

import java.util.List;

/** Focused collection contract checks shared by the JVM and native self-tests. */
public final class CollectionContractChecks {
  private CollectionContractChecks() {}

  public static int run() {
    mapRemovalKeepsLiveInsertionOrder();
    floatListsUseNumericEquality();
    handlesExpireAtRunExit();
    v2ContainersAndJsonContracts();
    return 4;
  }

  private static void mapRemovalKeepsLiveInsertionOrder() {
    Registry r = new Registry(true);
    Context c = new Context();
    r.enterRun(c);
    try {
      Value map = call(r, "fn str_map() -> StrMap", c, Value.none());
      call(r, "method StrMap.set(string, string) -> StrMap", c, map, s("a"), s("one"));
      call(r, "method StrMap.set(string, string) -> StrMap", c, map, s("b"), s("two"));
      call(r, "method StrMap.set(string, string) -> StrMap", c, map, s("a"), s("updated"));
      require(call(r, "method StrMap.key_at(int) -> string", c, map, i(0)).asString().equals(s("a").asString()), "map update retains position");
      require(call(r, "method StrMap.remove(string) -> bool", c, map, s("a")).asBool(), "map remove reports true");
      call(r, "method StrMap.set(string, string) -> StrMap", c, map, s("c"), s("three"));
      require(call(r, "method StrMap.key_at(int) -> string", c, map, i(0)).asString().equals(s("b").asString()), "remaining key moves to live rank zero");
      require(call(r, "method StrMap.key_at(int) -> string", c, map, i(1)).asString().equals(s("c").asString()), "new key appends after surviving keys");
    } finally { r.exitRun(c); }
  }

  private static void floatListsUseNumericEquality() {
    Registry r = new Registry(true);
    Context c = new Context();
    r.enterRun(c);
    try {
      Value list = call(r, "fn float_list() -> FloatList", c, Value.none());
      call(r, "method FloatList.add(float) -> FloatList", c, list, f(-0.0));
      require(call(r, "method FloatList.contains(float) -> bool", c, list, f(0.0)).asBool(), "signed zero compares numerically");
      require(call(r, "method FloatList.index_of(float) -> int", c, list, f(0.0)).asInt() == 0, "signed zero index uses numeric equality");
      call(r, "method FloatList.add(float) -> FloatList", c, list, f(Double.NaN));
      require(!call(r, "method FloatList.contains(float) -> bool", c, list, f(Double.NaN)).asBool(), "NaN does not compare equal");
    } finally { r.exitRun(c); }
  }

  private static void handlesExpireAtRunExit() {
    Registry r = new Registry(true);
    Context c = new Context(); Value list;
    r.enterRun(c);
    try { list = call(r, "fn str_list() -> StrList", c, Value.none()); }
    finally { r.exitRun(c); }
    boolean expired = false;
    try { call(r, "method StrList.len() -> int", c, list); }
    catch (VmException e) { expired = "RUN_HOST".equals(e.code); }
    require(expired, "collection handles expire at run exit");
  }

  private static void v2ContainersAndJsonContracts() {
    Registry r = new Registry(true);
    require(r.stdSignatures().size() == 181, "expected 181 std signatures, got " + r.stdSignatures().size());
    Context c = new Context();
    Value borrowedJson;
    r.enterRun(c);
    try {
      Value pq = call(r, "fn int_pq() -> IntPQ", c, Value.none());
      call(r, "method IntPQ.push(int, float) -> IntPQ", c, pq, i(10), f(0.0));
      call(r, "method IntPQ.push(int, float) -> IntPQ", c, pq, i(20), f(-0.0));
      require(call(r, "method IntPQ.pop() -> int", c, pq).asInt() == 10, "signed zero priorities tie in insertion order");
      require(call(r, "method IntPQ.pop() -> int", c, pq).asInt() == 20, "second tied item pops second");
      @SuppressWarnings("unchecked")
      StdContainers.PqState<Long> state = (StdContainers.PqState<Long>) StdCollections.unwrap(pq);
      state.nextSequence = Long.MAX_VALUE - 1;
      call(r, "method IntPQ.push(int, float) -> IntPQ", c, pq, i(30), f(1.0));
      boolean exhausted = false;
      try {
        call(r, "method IntPQ.push(int, float) -> IntPQ", c, pq, i(40), f(1.0));
      } catch (VmException e) {
        exhausted = "RUN_HOST".equals(e.code) && "IntPQ.push: insertion sequence exhausted".equals(e.getMessage());
      }
      require(exhausted && state.items.size() == 1, "exhausted sequence fails without mutating PQ");
      call(r, "method IntPQ.clear() -> IntPQ", c, pq);
      require(state.nextSequence == 0 && state.items.isEmpty(), "PQ clear resets sequence to zero");

      int beforeJson = StdCollections.arena(c).count;
      boolean parseFailed = false;
      try {
        call(r, "fn json_parse(string) -> Json", c, s("[1,"));
      } catch (VmException e) {
        parseFailed = "RUN_HOST".equals(e.code);
      }
      require(parseFailed && StdCollections.arena(c).count == beforeJson, "failed json_parse does not consume an arena slot");
      Value doc = call(r, "fn json_parse(string) -> Json", c, s("{\"\\u0061\":[1,-0]}"));
      require(StdCollections.arena(c).count == beforeJson + 1, "json_parse root consumes one arena slot");
      borrowedJson = call(r, "method Json.get(string) -> Json", c, doc, s("a"));
      Value child0 = call(r, "method Json.at(int) -> Json", c, borrowedJson, i(0));
      require(call(r, "method Json.as_int() -> int", c, child0).asInt() == 1, "borrowed Json child reads int");
      require(StdCollections.arena(c).count == beforeJson + 1, "Json navigation borrows without allocating arena slots");
      Value keys = call(r, "method Json.keys() -> StrList", c, doc);
      require(StdCollections.arena(c).count == beforeJson + 2, "Json.keys allocates one StrList slot");
      require(call(r, "method StrList.at(int) -> string", c, keys, i(0)).asString().equals(s("a").asString()), "decoded key in keys()");
      require(call(r, "method Json.to_json() -> string", c, doc).asString().equals(s("{\"\\u0061\":[1,-0]}").asString()), "to_json preserves raw spellings");

      Value raw = call(r, "fn str_hex_decode(string) -> string", c, s("00ff41"));
      require(call(r, "fn str_hex_encode(string) -> string", c, raw).asString().equals(s("00ff41").asString()), "hex encode is lowercase");
      Value b64 = call(r, "fn str_base64_encode(string) -> string", c, raw);
      require(call(r, "fn str_base64_decode(string) -> string", c, b64).asString().equals(raw.asString()), "base64 roundtrip preserves raw bytes");
      for (String invalidB64 : List.of("Zg", "Zg== ", "AB==", "Zh==")) {
        boolean badB64 = false;
        try {
          call(r, "fn str_base64_decode(string) -> string", c, s(invalidB64));
        } catch (VmException e) {
          badB64 = "RUN_HOST".equals(e.code) && "str_base64_decode: invalid encoding".equals(e.getMessage());
        }
        require(badB64, "strict base64 rejects: " + invalidB64);
      }
      Value imap = call(r, "fn int_map() -> IntMap", c);
      call(r, "method IntMap.set(string, int) -> IntMap", c, imap, s("x"), i(7));
      call(r, "method IntMap.set(string, int) -> IntMap", c, imap, s("y"), i(8));
      Value ik = call(r, "method IntMap.keys() -> StrList", c, imap);
      Value iv = call(r, "method IntMap.values() -> IntList", c, imap);
      call(r, "method IntMap.clear() -> IntMap", c, imap);
      call(r, "method IntList.set(int, int) -> IntList", c, iv, i(0), i(99));
      require(call(r, "method StrList.len() -> int", c, ik).asInt() == 2, "IntMap.keys snapshot is independent of map clear");
      require(call(r, "method IntList.at(int) -> int", c, iv, i(0)).asInt() == 99, "IntMap.values snapshot is mutable IntList");
    } finally { r.exitRun(c); }
    boolean borrowedExpired = false;
    try {
      call(r, "method Json.kind() -> string", c, borrowedJson);
    } catch (VmException e) {
      borrowedExpired = "RUN_HOST".equals(e.code);
    }
    require(borrowedExpired, "borrowed Json child handles expire at run exit");
  }

  private static Value call(Registry r, String signature, Context c, Value... args) {
    Registry.Binding b = r.resolve(signature);
    require(b != null, "missing collection signature: " + signature);
    Value self = Value.none();
    Value[] actual = args;
    if (b.method) {
      require(args.length > 0, "method call needs self: " + signature);
      self = args[0];
      actual = java.util.Arrays.copyOfRange(args, 1, args.length);
    }
    return b.callback.call(c, self, actual);
  }
  private static Value i(long x) { return Value.ofInt(x); }
  private static Value f(double x) { return Value.ofFloat(x); }
  private static Value s(String x) { return Value.ofString(x); }
  private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
