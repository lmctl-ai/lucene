# API references and implementation decisions

## References consulted

- [lmaws memory](https://github.com/lmctl-ai/lmaws/blob/main/durable-memory/index.md):
  schema-published CLI operations, explicit access classification, structured
  results, and dry-run/read-only policy. Its TypeScript server is not copied into
  this Java host. We implement a small flags/JSON operation surface, with an actual
  LMScript schema-import test; GraphQL inputs are not advertised.
- [PyLucene overview](https://lucene.apache.org/pylucene/): wraps Java Lucene,
  rather than reimplementing search.
- [PyLucene IndexFiles.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/IndexFiles.py):
  Directory, Analyzer, IndexWriterConfig, IndexWriter, Document and fields.
- [PyLucene SearchFiles.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/SearchFiles.py):
  DirectoryReader, IndexSearcher, QueryParser, TopDocs and stored fields.
- Lucene 10.5.2 source in this repository: `Similarity.java` specifies the scoring
  monotonicity requirements; `AnalyzerWrapper`, `TokenFilter`, and `CharTermAttribute`
  provide token adaptation without changing position/offset attributes.
- [lmcode-java](https://github.com/lmctl-ai/lmcode-java): SDK API and DSLB v6 entries.
- [lmcode-java-sample](https://github.com/lmctl-ai/lmcode-java-sample): schema,
  registry and host-owned objects; annotated SAM entry dispatch.

## Mapping

| Java/PyLucene concept | lmcode binding |
|---|---|
| FSDirectory / ByteBuffersDirectory | `fs_directory(path)` / `memory_directory()` |
| StandardAnalyzer / KeywordAnalyzer | `standard_analyzer()` / `keyword_analyzer()` |
| AnalyzerWrapper / TokenFilter override | `rule_analyzer(name)`, `TokenTransform` SAM |
| Document + TextField/StringField/StoredField/LongPoint | `document().text/keyword/stored/number` |
| IndexWriterConfig | `writer_config(analyzer).open_mode(...).ram_mb(...)` |
| IndexWriter | `index_writer(...).add/update/delete/commit/reader/close/rollback` |
| DirectoryReader | `directory_reader(directory)` and `writer.reader()` |
| IndexSearcher | `index_searcher(reader).search/count/explain` |
| BM25Similarity / Similarity override | `searcher.bm25(k1,b).similarity_rule(name)` |
| QueryParser + query objects | `parse_query`, `term_query`, `prefix_query`, `long_range`, `boolean_query` |
| TopDocs / ScoreDoc / storedFields | `hits.total/len/at`, `hit.score/doc_id/document` |

The binding covers text indexing/search and two engine extension points.
The [gallery expansion](gallery.md) adds flat facets, vector KNN, sorting,
pagination and term-vector inspection. Highlighting, custom collectors, taxonomy
hierarchies and arbitrary Lucene subclass overrides remain future API additions.

## Limits and ownership

Lucene requires valid Unicode text; lmcode strings are arbitrary bytes. The
boundary rejects invalid UTF-8 instead of silently replacing bytes. Host objects
remain JVM-owned; SDK collections remain owned by their execution arena.
Missing stored fields return an empty string; use `Document.has` to distinguish
missing from stored-empty. `TopDocs.emit` retains repeated stored fields as arrays.

Search is single-threaded and uses exact hit counting (`ScoreMode.COMPLETE`).
The hit limit is 1..1,000,000. The host does not expose an executor, so callback
registries are never shared by concurrent Lucene search threads. The SDK's default
instruction/allocation budgets apply separately to each body or callback; they
are not a total Lucene memory or wall-clock budget. A callback context is fresh
for each call, while its declared function and immutable bytecode are reused.

Readers are immutable snapshots; reopen after updates. Keep dependent resources
open while using readers, searchers and hits. Lifecycle mistakes are located host
errors. IndexWriterConfig is single-use, as in Java Lucene.
