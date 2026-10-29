# lmlucene durable memory

This checkout is a fork of Apache Lucene, based on tag `releases/lucene/10.5.2`
(`145d89cd30c38d25e39a0a2232cf00eee208b9cf`). Branch: `lmcode-host`.
The integration lives entirely in `lmcode/`; upstream Lucene implementations are unchanged.

Start with [the host guide](../lmcode/README.md), then:

- [Host.java](../lmcode/src/io/lmctl/lucene/Host.java): the actual Lucene bindings,
  schema generation, resource ownership and SAM adapters.
- [Main.java](../lmcode/src/io/lmctl/lucene/Main.java): bytecode and schema CLI protocols.
- [lucene.dsl.json](../lmcode/lucene.dsl.json): published host-only DSL schema.
- [cli.schema.dev.json](../lmcode/cli.schema.dev.json): lmaws-style operation schema.
- [Integration tests](../lmcode/tests/integration.py): real compiler and engine,
  callback isolation, schema parity, persistence and failure controls.
- [References and decisions](references.md): PyLucene API mapping and SDK constraints.
- [Example gallery](gallery.md): 32 online-source-inspired `.lms` examples and
  the analysis, term-vector, sorting, pagination, facet and KNN bindings they use.

## Important invariants

- lmcode is the typed `_lmcode_` block inside LMScript. LMScript compiles it;
  this Java host executes DSLB v6 through the pinned lmcode-java SDK.
- Schema and registry share one registration table. `--dsl-schema` output is
  byte-exact, deterministic and SHA-256-bound by the bytecode loader. Update
  the committed schema deliberately when registrations change; tests reject drift.
- The SDK refuses reentry into an active registry. Lucene callbacks execute a
  separately loaded copy of the program with a separate registry and a fresh
  context. Main-body stdlib collections must survive callbacks. Never reuse the
  body registry for `Program.call` from an analyzer or scorer.
- Rules are selected by function name and checked against their canonical SAM
  signature. Annotations make rule entries; they do not cause automatic dispatch.
- FieldWeight overrides `Similarity.scorer`, multiplying the base scorer by one
  finite nonnegative factor per scorer. This preserves frequency/norm monotonicity.
  Arbitrary per-document score replacement is deliberately not exposed.
- TokenTransform overrides `TokenFilter.incrementToken` after StandardAnalyzer;
  offsets and positions stay intact. Empty replacements are rejected. Multi-term
  query normalization retains StandardAnalyzer's normalizer.
- Callbacks may inspect their arguments and use lmcode stdlib; other Lucene host
  operations are refused during callbacks. Bytecode remains trusted, not sandboxed.
- Resources close in reverse order. Unclosed writers roll back; explicit
  `commit()` persists and `close()` commits as in Lucene. Prior commits survive
  later failures. Never claim execution is transactional across explicit commits.
- The CLI `run` operation is classified as write even for a query-only script,
  because trusted bytecode can call stdlib file operations. `--readonly` refuses
  it; `--dryrun` loads/verifies but executes neither body nor rule entries.
- Stdout is one JSON result envelope. Host `print` emits diagnostics on stderr.
  Emit search results using `TopDocs.emit(label)`.

## Build and verification

JDK 21+, Python 3, and a v6-capable LMScript (tested with 0.2.444):

```sh
./lmcode/test.sh
./gradlew :lucene:core:test --tests org.apache.lucene.index.TestIndexWriter --tests org.apache.lucene.search.TestIndexSearcher -Pgradle.ge=false --console=plain
```

The host build compiles Lucene from this checkout and vendors the Java SDK by
hash in `lmcode/sdk.lock`; no Maven artifact substitutes for the fork.
Build outputs remain ignored. No binary release is implied by a source push.

Verified locally on 2026-10-29, Linux arm64, OpenJDK 21.0.12.1 and LMScript
0.2.444: the source build, all 14 integration groups, all 32 gallery examples
through normal preflight, and the single LMScript gallery runner passed. The focused
upstream suites reported 129 passing tests and one skipped test, zero failures.
CI adds an Ubuntu x64 check; local results do not imply a completed CI run.
