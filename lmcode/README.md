# Lucene from LMScript and lmcode

Build Apache Lucene from this fork, then operate its Java objects from a typed
`_lmcode_` block inside LMScript. The host exposes a focused indexing/search API
and SAM callbacks that override Lucene analysis and scoring behavior.

The fork is based on Lucene 10.5.2. The Java SDK is pinned in [sdk.lock](sdk.lock);
this integration uses DSLB v6. It runs on a JVM; it is not a GraalVM native binary.

## Build and try

Requirements: JDK 21+, Python 3, and `lmscript` with DSLB v6 entries
(tested with `@lmctl-ai/lmctl` 0.2.444). Gradle downloads its own wrapper and
dependencies. Run all commands from the repository root:

```sh
./lmcode/build.sh
lmscript lmcode/examples/basic.lms
lmscript lmcode/examples/rules.lms
```

The first example indexes three documents, runs a text query, and filters by
numeric year. The second shows custom analysis and a rule that changes ranking.
Each example prints a JSON result with stored fields, document IDs and scores.

The [32-example gallery](examples/gallery/README.md) expands the official Lucene
and PyLucene examples into runnable `.lms` files: phrase/fuzzy/wildcard queries,
stemming, token positions, term vectors, lifecycle, sorting, pagination, facets,
vector KNN, and SAM customization. Every example has assertions and source links.

```sh
lmscript --no-preflight lmcode/examples/run-gallery.lms
```

Persistent indexing and querying in separate processes:

```sh
lmscript lmcode/examples/index.lms /tmp/lmlucene-demo
lmscript lmcode/examples/query.lms /tmp/lmlucene-demo 'lucene AND rules'
```

Indexing uses exact-ID updates, so rerunning it keeps two documents. Choose a
new path for an isolated demonstration. Query syntax is Lucene QueryParser syntax.

The build produces `lmcode/build/lmlucene.jar` plus Lucene dependency JARs in
`lmcode/build/lib`. `lmcode/lmlucene` is the launcher. These Lucene JARs are built
from this checkout, not fetched as precompiled Maven artifacts.

## How compilation and execution fit together

```lmscript
program = _lmcode_ schema="lmcode/lucene.dsl.json";
let directory = memory_directory();
let analyzer = standard_analyzer();
let writer = index_writer(directory, writer_config(analyzer));
writer.add(document().text("body", "Hello Lucene", true));
let reader = writer.reader();
let searcher = index_searcher(reader);
searcher.search(term_query("body", "lucene"), 10).emit("matches");
end _lmcode_
savedsl(program, "lmcode/build/hello.dslb");
r = system lmcode/lmlucene --dsl lmcode/build/hello.dslb;
assert(r.code == 0);
print(r.stdout);
```

Save this snippet in the repository root. Schema paths are source-relative;
process paths are relative to the working directory. `_lmcode_` is the embedded
typed language; the surrounding LMScript saves the compiled bytecode and runs
the Java host. The host verifies the schema hash and imports before executing.
The standard lmcode catalog is implicitly available inside the block.

Use `cli="lmlucene"` in place of `schema="..."` to discover the schema from the
host on PATH. `lmlucene --dsl-schema --json` returns the exact schema bytes.

## SAM rules override Lucene behavior

```text
@analysis
fn normalize(token: string) -> string implements TokenTransform {
  let result = token;
  if (token == "automobile") { result = "car"; }
  return result;
}
@similarity
fn title_weight(stats: CollectionStats) -> float implements FieldWeight {
  let weight = 1.0;
  if (stats.field() == "title") { weight = 10.0; }
  return weight;
}
let analyzer = rule_analyzer("normalize");
# After creating the reader and searcher:
searcher.bm25(1.2, 0.75).similarity_rule("title_weight");
```

These definitions belong inside `_lmcode_`; see the executable
[rules.lms](examples/rules.lms). The host selects the annotated entry by function
name, checks its SAM signature, and calls it from actual Java Lucene overrides:

- `TokenTransform(string) -> string` runs from a `TokenFilter` after
  StandardAnalyzer. It replaces token text and preserves offsets/positions.
  Apply the same analyzer at indexing and query time. Empty replacements fail.
- `FieldWeight(CollectionStats) -> float` runs from `Similarity.scorer` and
  scales the base scorer. Statistics expose `field`, `doc_count`, `total_terms`,
  `doc_freq` (maximum across phrase terms), and `boost`. Return a finite,
  nonnegative factor. This design preserves Lucene's score monotonicity rules.

Changing `bm25` replaces the current similarity; calling `similarity_rule` wraps
the current similarity. Each callback gets fresh locals and its own SDK context.
The host loads a second copy of the bytecode into an independent registry to
avoid active-registry reentry and preserve the caller's collection arena.
Callbacks cannot recursively open or operate Lucene resources. They can use
lmcode's standard library, including its I/O functions: this is trusted code.

## CLI schema, following lmaws

The host publishes two distinct schemas:

| Command | Purpose |
|---|---|
| `--schema` / `--cli-schema` | `cli.schema.v1`: the `run` operation for LMScript imports |
| `--dsl-schema --json` | The object API compiled into `_lmcode_` bytecode |
| `--dsl-signatures` | Canonical registered host + standard signatures |

```sh
export PATH="$PWD/lmcode:$PATH"
lmscript lmcode/examples/schema-cli.lms
lmlucene run --program lmcode/build/basic.dslb
lmlucene run --program lmcode/build/basic.dslb --dryrun
lmlucene run --input-json '{"op":"run","params":{"program":"lmcode/build/basic.dslb"}}'
```

`--input-file FILE` accepts the same JSON document. `run` is classified as write
because its bytecode may mutate indexes or files. `--readonly` refuses execution;
`--dryrun` validates bytecode/schema/imports without running the body or callbacks.
The published development CLI schema is [cli.schema.dev.json](cli.schema.dev.json).
No released/latest channel or package installation is claimed.

Stdout contains exactly one `{ok,result}` or `{ok:false,phase,code,message,...}`
JSON envelope. `TopDocs.emit(label)` adds results to `result.searches`, retaining
repeated stored fields as arrays. Script `print` writes to stderr. Exit codes:
0 success, 2 CLI usage (or LMScript compile failure), 3 load/runtime/policy failure.
Token streams, facets, and reported explanations appear in `result.details`.

## API and lifecycle

The API follows the object workflow in PyLucene's
[IndexFiles.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/IndexFiles.py)
and [SearchFiles.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/SearchFiles.py).
See [the API mapping](../durable-memory/references.md) and
[lucene.dsl.json](lucene.dsl.json) for every exposed signature.

Call `writer.commit()` or `writer.close()` to persist writes. Automatic cleanup
rolls back unclosed writers, including after failure, and closes other resources
in reverse acquisition order. Explicitly committed data remains committed even
if a later operation fails. Readers are immutable snapshots; create a new reader
to see subsequent updates. Stored strings must be valid UTF-8 at the Java boundary.

The API includes analyzed/keyword/stored/numeric/vector fields, in-memory and
filesystem directories, updates/deletes, near-real-time readers, parsed/term/
prefix/Boolean/range queries, exact hit counts, stored documents, scores, and
explanations. The gallery adds English/whitespace analyzers, token inspection,
stored term vectors, doc-value sorting, score pagination, flat sorted-set facets,
drilldown and filtered Euclidean KNN. It does not bind all of PyLucene:
highlighting, taxonomy hierarchies and arbitrary subclass overrides remain outside
the current surface. See [binding details](../durable-memory/gallery.md).

## Tests

```sh
./lmcode/test.sh
# Equivalent orchestration through LMScript:
lmscript --no-preflight lmcode/test.lms
```

Integration tests use real Lucene and LMScript, temporary indexes, both SAM
hooks, ranking/score assertions, callback arena isolation, persistence, snapshots,
rollback/lock cleanup, schema/registry parity, malformed bytecode, type errors,
located runtime failures, CLI JSON inputs and policy controls, then execute all
32 gallery examples. Set `LMSCRIPT_BIN`
to test against a specific compiler executable.

Lucene source retains its Apache-2.0 license. The pinned SDK is MIT licensed;
see [its license](vendor/lmcode-java/LICENSE). PyLucene is a design reference,
not a runtime dependency.
