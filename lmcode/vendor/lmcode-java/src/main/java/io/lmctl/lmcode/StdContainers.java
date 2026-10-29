package io.lmctl.lmcode;

import java.util.*;

final class StdContainers {
  private StdContainers() {}

  static final class QueueState<T> {
    final ArrayList<T> items = new ArrayList<>();
    int head = 0;
    int size() { return items.size() - head; }
    boolean empty() { return head == items.size(); }
    void push(T v) { items.add(v); }
    T peek(String type) {
      if (empty()) throw VmException.host(type + ".peek: empty");
      return items.get(head);
    }
    T pop(String type) {
      if (empty()) throw VmException.host(type + ".pop: empty");
      T v = items.set(head, null);
      head++;
      if (head > 64 && head * 2 >= items.size()) {
        items.subList(0, head).clear();
        head = 0;
      }
      return v;
    }
    T at(long i, String type) {
      int idx = StdCollections.idx(i, size(), type + ".at");
      return items.get(head + idx);
    }
    void clear() {
      items.clear();
      head = 0;
    }
  }

  static final class PqItem<T> {
    final T value;
    final double priority;
    final long sequence;
    PqItem(T v, double p, long s) { value = v; priority = p; sequence = s; }
  }

  static final class PqState<T> {
    final PriorityQueue<PqItem<T>> items = new PriorityQueue<>((a, b) -> {
      if (a.priority < b.priority) return -1;
      if (a.priority > b.priority) return 1;
      return Long.compare(a.sequence, b.sequence);
    });
    long nextSequence = 0;
    void push(T value, double priority, String type) {
      if (!Double.isFinite(priority)) throw VmException.host(type + ".push: priority must be finite");
      if (nextSequence == Long.MAX_VALUE) throw VmException.host(type + ".push: insertion sequence exhausted");
      long seq = nextSequence;
      items.add(new PqItem<>(value, priority, seq));
      nextSequence = seq + 1;
    }
    T peek(String type) {
      PqItem<T> top = items.peek();
      if (top == null) throw VmException.host(type + ".peek: empty");
      return top.value;
    }
    T pop(String type) {
      PqItem<T> top = items.poll();
      if (top == null) throw VmException.host(type + ".pop: empty");
      return top.value;
    }
    void clear() {
      items.clear();
      nextSequence = 0;
    }
  }

  interface G<T> { T get(Value v); }
  interface P<T> { Value put(T x); }
  interface S<T> { long bytes(T x); }

  static void register(Registry r) {
    r.function("int_map", List.of(), "IntMap", (c, s, a) -> StdCollections.obj(c, "IntMap", new LinkedHashMap<VmString, Long>()));
    r.function("float_map", List.of(), "FloatMap", (c, s, a) -> StdCollections.obj(c, "FloatMap", new LinkedHashMap<VmString, Double>()));
    r.function("int_stack", List.of(), "IntStack", (c, s, a) -> StdCollections.obj(c, "IntStack", new ArrayList<Long>()));
    r.function("float_stack", List.of(), "FloatStack", (c, s, a) -> StdCollections.obj(c, "FloatStack", new ArrayList<Double>()));
    r.function("str_stack", List.of(), "StrStack", (c, s, a) -> StdCollections.obj(c, "StrStack", new ArrayList<VmString>()));
    r.function("int_queue", List.of(), "IntQueue", (c, s, a) -> StdCollections.obj(c, "IntQueue", new QueueState<Long>()));
    r.function("float_queue", List.of(), "FloatQueue", (c, s, a) -> StdCollections.obj(c, "FloatQueue", new QueueState<Double>()));
    r.function("str_queue", List.of(), "StrQueue", (c, s, a) -> StdCollections.obj(c, "StrQueue", new QueueState<VmString>()));
    r.function("int_pq", List.of(), "IntPQ", (c, s, a) -> StdCollections.obj(c, "IntPQ", new PqState<Long>()));
    r.function("float_pq", List.of(), "FloatPQ", (c, s, a) -> StdCollections.obj(c, "FloatPQ", new PqState<Double>()));
    r.function("str_pq", List.of(), "StrPQ", (c, s, a) -> StdCollections.obj(c, "StrPQ", new PqState<VmString>()));

    mapMethods(r, "IntMap", "int", Value::asInt, Value::ofInt, x -> 8L);
    mapMethods(r, "FloatMap", "float", Value::asFloat, Value::ofFloat, x -> 8L);
    stackMethods(r, "IntStack", "int", Value::asInt, Value::ofInt, x -> 8L);
    stackMethods(r, "FloatStack", "float", Value::asFloat, Value::ofFloat, x -> 8L);
    stackMethods(r, "StrStack", "string", Value::asString, Value::ofString, x -> x.length());
    queueMethods(r, "IntQueue", "int", Value::asInt, Value::ofInt, x -> 8L);
    queueMethods(r, "FloatQueue", "float", Value::asFloat, Value::ofFloat, x -> 8L);
    queueMethods(r, "StrQueue", "string", Value::asString, Value::ofString, x -> x.length());
    pqMethods(r, "IntPQ", "int", Value::asInt, Value::ofInt, x -> 8L);
    pqMethods(r, "FloatPQ", "float", Value::asFloat, Value::ofFloat, x -> 8L);
    pqMethods(r, "StrPQ", "string", Value::asString, Value::ofString, x -> x.length());
  }

  @SuppressWarnings("unchecked")
  private static <T> void mapMethods(Registry r, String type, String elem, G<T> get, P<T> put, S<T> size) {
    r.method(type, "len", List.of(), "int", (c, self, a) -> Value.ofInt(((LinkedHashMap<VmString, T>) StdCollections.unwrap(self)).size()));
    r.method(type, "has", List.of("string"), "bool", (c, self, a) -> Value.ofBool(((LinkedHashMap<VmString, T>) StdCollections.unwrap(self)).containsKey(a[0].asString())));
    r.method(type, "get", List.of("string"), elem, (c, self, a) -> {
      T v = ((LinkedHashMap<VmString, T>) StdCollections.unwrap(self)).get(a[0].asString());
      if (v == null) throw VmException.host(type + ".get: missing key");
      return put.put(v);
    });
    r.method(type, "get_or", List.of("string", elem), elem, (c, self, a) -> {
      T v = ((LinkedHashMap<VmString, T>) StdCollections.unwrap(self)).get(a[0].asString());
      return v == null ? a[1] : put.put(v);
    });
    r.method(type, "set", List.of("string", elem), type, (c, self, a) -> {
      VmString k = a[0].asString();
      T v = get.get(a[1]);
      c.charge(64L + k.length() + size.bytes(v));
      ((LinkedHashMap<VmString, T>) StdCollections.unwrap(self)).put(k, v);
      return self;
    });
    r.method(type, "key_at", List.of("int"), "string", (c, self, a) -> {
      var m = (LinkedHashMap<VmString, T>) StdCollections.unwrap(self);
      int idx = StdCollections.idx(a[0].asInt(), m.size(), type + ".key_at");
      return Value.ofString(new ArrayList<>(m.keySet()).get(idx));
    });
    r.method(type, "remove", List.of("string"), "bool", (c, self, a) -> Value.ofBool(((LinkedHashMap<VmString, T>) StdCollections.unwrap(self)).remove(a[0].asString()) != null));
    r.method(type, "clear", List.of(), type, (c, self, a) -> {
      ((LinkedHashMap<VmString, T>) StdCollections.unwrap(self)).clear();
      return self;
    });
    String listType = type.equals("IntMap") ? "IntList" : "FloatList";
    r.method(type, "keys", List.of(), "StrList", (c, self, a) -> StdCollections.obj(c, "StrList", new ArrayList<>(((LinkedHashMap<VmString, T>) StdCollections.unwrap(self)).keySet())));
    r.method(type, "values", List.of(), listType, (c, self, a) -> StdCollections.obj(c, listType, new ArrayList<>(((LinkedHashMap<VmString, T>) StdCollections.unwrap(self)).values())));
  }

  @SuppressWarnings("unchecked")
  private static <T> void stackMethods(Registry r, String type, String elem, G<T> get, P<T> put, S<T> size) {
    r.method(type, "push", List.of(elem), type, (c, self, a) -> {
      T v = get.get(a[0]);
      c.charge(32L + size.bytes(v));
      ((ArrayList<T>) StdCollections.unwrap(self)).add(v);
      return self;
    });
    r.method(type, "pop", List.of(), elem, (c, self, a) -> {
      var s = (ArrayList<T>) StdCollections.unwrap(self);
      if (s.isEmpty()) throw VmException.host(type + ".pop: empty");
      return put.put(s.remove(s.size() - 1));
    });
    r.method(type, "peek", List.of(), elem, (c, self, a) -> {
      var s = (ArrayList<T>) StdCollections.unwrap(self);
      if (s.isEmpty()) throw VmException.host(type + ".peek: empty");
      return put.put(s.get(s.size() - 1));
    });
    r.method(type, "len", List.of(), "int", (c, self, a) -> Value.ofInt(((ArrayList<T>) StdCollections.unwrap(self)).size()));
    r.method(type, "empty", List.of(), "bool", (c, self, a) -> Value.ofBool(((ArrayList<T>) StdCollections.unwrap(self)).isEmpty()));
    r.method(type, "clear", List.of(), type, (c, self, a) -> {
      ((ArrayList<T>) StdCollections.unwrap(self)).clear();
      return self;
    });
    r.method(type, "at", List.of("int"), elem, (c, self, a) -> {
      var s = (ArrayList<T>) StdCollections.unwrap(self);
      int idx = StdCollections.idx(a[0].asInt(), s.size(), type + ".at");
      return put.put(s.get(s.size() - 1 - idx));
    });
  }

  @SuppressWarnings("unchecked")
  private static <T> void queueMethods(Registry r, String type, String elem, G<T> get, P<T> put, S<T> size) {
    r.method(type, "push", List.of(elem), type, (c, self, a) -> {
      T v = get.get(a[0]);
      c.charge(32L + size.bytes(v));
      ((QueueState<T>) StdCollections.unwrap(self)).push(v);
      return self;
    });
    r.method(type, "pop", List.of(), elem, (c, self, a) -> put.put(((QueueState<T>) StdCollections.unwrap(self)).pop(type)));
    r.method(type, "peek", List.of(), elem, (c, self, a) -> put.put(((QueueState<T>) StdCollections.unwrap(self)).peek(type)));
    r.method(type, "len", List.of(), "int", (c, self, a) -> Value.ofInt(((QueueState<T>) StdCollections.unwrap(self)).size()));
    r.method(type, "empty", List.of(), "bool", (c, self, a) -> Value.ofBool(((QueueState<T>) StdCollections.unwrap(self)).empty()));
    r.method(type, "clear", List.of(), type, (c, self, a) -> {
      ((QueueState<T>) StdCollections.unwrap(self)).clear();
      return self;
    });
    r.method(type, "at", List.of("int"), elem, (c, self, a) -> put.put(((QueueState<T>) StdCollections.unwrap(self)).at(a[0].asInt(), type)));
  }

  @SuppressWarnings("unchecked")
  private static <T> void pqMethods(Registry r, String type, String elem, G<T> get, P<T> put, S<T> size) {
    r.method(type, "push", List.of(elem, "float"), type, (c, self, a) -> {
      T v = get.get(a[0]);
      double prio = a[1].asFloat();
      var q = (PqState<T>) StdCollections.unwrap(self);
      q.push(v, prio, type);
      c.charge(48L + size.bytes(v));
      return self;
    });
    r.method(type, "peek", List.of(), elem, (c, self, a) -> put.put(((PqState<T>) StdCollections.unwrap(self)).peek(type)));
    r.method(type, "pop", List.of(), elem, (c, self, a) -> put.put(((PqState<T>) StdCollections.unwrap(self)).pop(type)));
    r.method(type, "len", List.of(), "int", (c, self, a) -> Value.ofInt(((PqState<T>) StdCollections.unwrap(self)).items.size()));
    r.method(type, "empty", List.of(), "bool", (c, self, a) -> Value.ofBool(((PqState<T>) StdCollections.unwrap(self)).items.isEmpty()));
    r.method(type, "clear", List.of(), type, (c, self, a) -> {
      ((PqState<T>) StdCollections.unwrap(self)).clear();
      return self;
    });
  }
}
