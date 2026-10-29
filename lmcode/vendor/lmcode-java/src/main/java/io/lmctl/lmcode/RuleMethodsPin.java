package io.lmctl.lmcode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Regression pin for compiler rule methods (R47/R48): a schema may carry a top-level
  * "interfaces" map of SAM signatures, and annotated functions provide callable entries while preserving body behavior. Fixtures under src/test/resources/rule-methods were compiled with
  * an R47/R48 lmscript (LMSCRIPT_BIN); plain.lms keeps a blank line where annotated.lms has
  * the @PRICE_RULE annotation so source-map line numbers align. */
public final class RuleMethodsPin {
  private RuleMethodsPin() {}

  public static void main(String[] args) throws Exception {
    Path dir = Path.of("src/test/resources/rule-methods");
    byte[] schema = Files.readAllBytes(dir.resolve("schema.json"));
    byte[] withoutInterfaces = Files.readAllBytes(dir.resolve("schema-no-interfaces.json"));
    byte[] annotated = Files.readAllBytes(dir.resolve("annotated.dslb"));
    byte[] plain = Files.readAllBytes(dir.resolve("plain.dslb"));

    require(SchemaSignatures.from(schema).equals(List.of("fn note(string) -> void")),
        "interfaces must be tolerated without becoming signatures");

    List<String> out = new ArrayList<>();
    Registry registry = new Registry(false);
    registry.function("note", List.of("string"), "void", (c, s, a) -> { out.add(a[0].asString().toString()); return Value.none(); });

    Program.load(annotated, schema, registry).run(new Context(), registry);
    require(out.equals(List.of("adjust 41")), "annotated rule-method program output: " + out);
    out.clear();
    Program.load(plain, schema, registry).run(new Context(), registry);
    require(out.equals(List.of("adjust 41")), "unannotated program output: " + out);

    Program rules=Program.load(annotated,schema,registry);
    require(rules.entries().size()==1&&rules.entries().getFirst().annotation().equals("PRICE_RULE"),"annotation emits one entry");
    require(Program.load(plain,schema,registry).entries().isEmpty(),"plain functions are not entries");
    out.clear();rules.call(0,new Context(),registry,List.of(Value.ofInt(12)));
    require(out.equals(List.of("adjust 12")),"rule call initializes its parameter");

    boolean mismatch = false;
    try { Program.load(annotated, withoutInterfaces, registry); }
    catch (VmException e) { mismatch = "LOAD_SCHEMA_MISMATCH".equals(e.code); }
    require(mismatch, "interfaces join the schema sha256, so a schema without them must not load this bytecode");
    System.out.println("PASS rule-methods pin");
  }

  private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
