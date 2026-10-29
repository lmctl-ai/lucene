# Lucene and PyLucene examples in LMScript

32 standalone examples, organized from basic indexing to engine customization.
Each `.lms` file contains an embedded `_lmcode_` program, an original fixture,
assertions of the expected behavior, and the online reference that inspired it.
These are adaptations to the exposed API, not claims of line-for-line ports.

## Run

From the repository root, with JDK 21+, Python 3 and a DSLB v6-capable LMScript
(tested with 0.2.444):

```sh
./lmcode/build.sh
lmscript lmcode/examples/gallery/01_index_files.lms
lmscript lmcode/examples/gallery/25_facet_drilldown.lms
lmscript lmcode/examples/gallery/30_sam_ranking.lms
```

Run the entire gallery using LMScript:

```sh
lmscript --no-preflight lmcode/examples/run-gallery.lms
```

Run the automated regression suite (original integration checks plus every
example through normal preflight and execution):

```sh
./lmcode/test.sh
# Gallery only, after a build:
python3 lmcode/tests/gallery.py
```

Every example takes no arguments, needs no external accounts, and prints one
JSON result. Everything runs offline after dependencies are installed. Most
examples use in-memory indexes. Example 01 uses the three bundled files in
[data](data) and persists an idempotently updated index under
`lmcode/build/gallery/file-index`. Compiled programs also go under
`lmcode/build/gallery`. Examples create their output directory themselves.
Run one gallery process at a time because compiled output paths are shared.

For an index path and search expression supplied by the caller, use the existing
[index.lms](../index.lms) and [query.lms](../query.lms) examples.

## Catalog

The expected column is enforced by `assert_true` checks inside each example;
exact floating-point score strings are deliberately not pinned. Click an online
reference to compare its Java/Python workflow with the corresponding `.lms`.

| Example | Topic | Expected observation | Online reference |
|---|---|---|---|
| [01_index_files.lms](01_index_files.lms) | Index and search a small text-file corpus | three persisted documents; two Lucene matches | [IndexFiles.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/IndexFiles.py) |
| [02_fielded_search.lms](02_fielded_search.lms) | Search titles and contents separately | only id 1 | [SearchFiles.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/SearchFiles.py) |
| [03_exact_vs_analyzed.lms](03_exact_vs_analyzed.lms) | Exact identifiers versus analyzed text | exact SKU preserves case; analyzed text contains token abc | [IndexFiles.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/IndexFiles.py) |
| [04_phrase_query.lms](04_phrase_query.lms) | Exact phrase search | matching ids 1 | [package-summary.html](https://lucene.apache.org/core/10_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html) |
| [05_phrase_slop.lms](05_phrase_slop.lms) | Phrase proximity with slop | matching ids 1, 2 | [package-summary.html](https://lucene.apache.org/core/10_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html) |
| [06_boolean_filters.lms](06_boolean_filters.lms) | Boolean MUST, FILTER and MUST_NOT clauses | only id 1 | [package-summary.html](https://lucene.apache.org/core/10_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html) |
| [07_wildcard_prefix.lms](07_wildcard_prefix.lms) | Single-character wildcard and direct prefix queries | matching ids 1, 2 | [package-summary.html](https://lucene.apache.org/core/10_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html) |
| [08_fuzzy_query.lms](08_fuzzy_query.lms) | One-edit typo tolerance | matching ids 1, 2 | [package-summary.html](https://lucene.apache.org/core/10_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html) |
| [09_regexp_query.lms](09_regexp_query.lms) | Regular-expression terms | matching ids 1, 2 | [package-summary.html](https://lucene.apache.org/core/10_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html) |
| [10_lexical_ranges.lms](10_lexical_ranges.lms) | Lexical date ranges on keyword fields | inclusive range finds two; exclusive range finds one | [package-summary.html](https://lucene.apache.org/core/10_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html) |
| [11_numeric_ranges.lms](11_numeric_ranges.lms) | Numeric point ranges for prices | only mid; text range syntax does not query LongPoint data | [package-summary.html](https://lucene.apache.org/core/10_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html) |
| [12_boosted_query.lms](12_boosted_query.lms) | Boost one term to change ranking | matching ids 1, 2 | [package-summary.html](https://lucene.apache.org/core/10_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html) |
| [13_english_stemming.lms](13_english_stemming.lms) | English stemming and stop words | run matches both documents and the is removed | [PorterStemmerAnalyzer.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/PorterStemmerAnalyzer.py) |
| [14_token_offsets.lms](14_token_offsets.lms) | Inspect terms, positions and offsets | hello at 0..5 and lucene at 7..13 | [TermPositionVector.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/TermPositionVector.py) |
| [15_analyzer_choices.lms](15_analyzer_choices.lms) | Compare standard, whitespace and keyword analyzers | three distinct tokenization policies | [PorterStemmerAnalyzer.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/PorterStemmerAnalyzer.py) |
| [16_term_vectors.lms](16_term_vectors.lms) | Read stored term frequencies, positions and offsets | red frequency 2 at positions 0 and 2; blue frequency 1 | [TermPositionVector.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/TermPositionVector.py) |
| [17_repeated_fields.lms](17_repeated_fields.lms) | Repeated fields, stored-only data and absent values | one hit with tag array [java, search] | [IndexFiles.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/IndexFiles.py) |
| [18_update_delete.lms](18_update_delete.lms) | Update by ID and delete by exact term | one updated document, then zero live documents | [IndexWriter.java](https://github.com/apache/lucene/blob/releases/lucene/10.5.2/lucene/core/src/java/org/apache/lucene/index/IndexWriter.java) |
| [19_reader_snapshots.lms](19_reader_snapshots.lms) | Near-real-time readers are immutable snapshots | old reader sees 1; new reader sees 2 without commit | [IndexSearcher.html](https://lucene.apache.org/core/10_5_0/core/org/apache/lucene/search/IndexSearcher.html) |
| [20_commit_rollback.lms](20_commit_rollback.lms) | Explicit commit survives later rollback | only committed document remains | [IndexWriter.java](https://github.com/apache/lucene/blob/releases/lucene/10.5.2/lucene/core/src/java/org/apache/lucene/index/IndexWriter.java) |
| [21_search_after.lms](21_search_after.lms) | Paginate scores with searchAfter | pages of 2, 2, 1; all five IDs exactly once | [SearchFiles.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/SearchFiles.py) |
| [22_numeric_sort.lms](22_numeric_sort.lms) | Sort results by numeric doc values | low first ascending; high first descending | [IndexSearcher.html](https://lucene.apache.org/core/10_5_0/core/org/apache/lucene/search/IndexSearcher.html) |
| [23_keyword_sort.lms](23_keyword_sort.lms) | Sort results by keyword doc values | apple, mango, zebra | [IndexSearcher.html](https://lucene.apache.org/core/10_5_0/core/org/apache/lucene/search/IndexSearcher.html) |
| [24_facet_counts.lms](24_facet_counts.lms) | Count author facets across all or matching documents | global Ada=2, Ben=1; query-relative Ada=1, Ben=1 | [SimpleSortedSetFacetsExample.java](https://github.com/apache/lucene/blob/releases/lucene/10.5.2/lucene/demo/src/java/org/apache/lucene/demo/facet/SimpleSortedSetFacetsExample.java) |
| [25_facet_drilldown.lms](25_facet_drilldown.lms) | Filter by a facet and recalculate counts | two 2026 documents; refining author Ada leaves id 3 | [FacetExample.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/FacetExample.py) |
| [26_multivalued_facets.lms](26_multivalued_facets.lms) | Count multiple tags without double-counting documents | java=1, search=2; two documents in the dimension | [SimpleSortedSetFacetsExample.java](https://github.com/apache/lucene/blob/releases/lucene/10.5.2/lucene/demo/src/java/org/apache/lucene/demo/facet/SimpleSortedSetFacetsExample.java) |
| [27_vector_knn.lms](27_vector_knn.lms) | Nearest-neighbor search over explicit float vectors | near then middle, using Euclidean similarity | [IndexFiles.java](https://github.com/apache/lucene/blob/releases/lucene/10.5.2/lucene/demo/src/java/org/apache/lucene/demo/IndexFiles.java) |
| [28_filtered_knn.lms](28_filtered_knn.lms) | Apply an eligibility filter inside KNN retrieval | near then far; excludes private middle | [KnnFloatVectorQuery.html](https://lucene.apache.org/core/10_5_0/core/org/apache/lucene/search/KnnFloatVectorQuery.html) |
| [29_sam_normalization.lms](29_sam_normalization.lms) | Override analysis with a domain vocabulary rule | both spellings match; emitted tokens are car and color | [PorterStemmerAnalyzer.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/PorterStemmerAnalyzer.py) |
| [30_sam_ranking.lms](30_sam_ranking.lms) | Use a field-statistics SAM rule to change ranking | baseline favors body; SAM weighting ranks title first | [Similarity.java](https://github.com/apache/lucene/blob/releases/lucene/10.5.2/lucene/core/src/java/org/apache/lucene/search/similarities/Similarity.java) |
| [31_bm25_explain.lms](31_bm25_explain.lms) | Inspect BM25 length normalization and explanations | short text wins by default; b=0 produces equal scores | [BM25Similarity.java](https://github.com/apache/lucene/blob/releases/lucene/10.5.2/lucene/core/src/java/org/apache/lucene/search/similarities/BM25Similarity.java) |
| [32_manual_search.lms](32_manual_search.lms) | Search a miniature manual-page catalog | find(1), with stored description | [mansearch.py](https://svn.apache.org/repos/asf/lucene/pylucene/trunk/samples/mansearch.py) |

## Learning paths

- **Index and query:** 01–03, then 04–12 for the query parser and explicit query objects.
- **Understand analysis:** 13–16 compare analyzers and inspect stored token data.
- **Manage data:** 17–20 cover field storage, updates, snapshots and commits.
- **Return useful results:** 21–23 paginate and sort; 24–26 aggregate and drill down.
- **Vector search:** 27–28 use real Lucene KNN with explicit fixture vectors.
- **Customize the engine:** 29–30 override analysis and weighting through lmcode SAM entries.
- **Explain and apply:** 31 explains BM25; 32 builds a small manual-page search application.

## How the upstream examples were adapted

- PyLucene `IndexFiles.py` / `SearchFiles.py`: Java object workflow, filesystem
  persistence, stored fields and parsed queries. We use a small bundled corpus
  and idempotent updates instead of relying on user directories or an input loop.
- PyLucene `PorterStemmerAnalyzer.py`: analyzer extension and stemming concepts.
  Example 13 uses the built-in EnglishAnalyzer (its filter ordering differs from
  the historical custom analyzer); example 29 demonstrates the actual SAM bridge.
- PyLucene `TermPositionVector.py`: term-frequency, position and offset inspection.
  The `.lms` version stores vectors explicitly and validates repeated-token offsets.
- PyLucene `FacetExample.py` and Java `SimpleSortedSetFacetsExample.java`: facet
  counts and drilldown. These examples use flat sorted-set doc-values facets;
  taxonomy trees and DrillSideways are not part of this binding.
- Lucene `IndexFiles.java` and `KnnFloatVectorQuery`: indexing and retrieving
  vectors. Examples 27–28 supply transparent 2D vectors rather than requiring an
  embedding dictionary or model; they do not demonstrate semantic embeddings.
- PyLucene `mansearch.py`: separate searchable metadata from stored descriptions.
  Example 32 uses a miniature catalog rather than crawling installed man pages.

New binding details and constraints are recorded in
[the durable-memory guide](../../../durable-memory/gallery.md). Sources are
recorded in [manifest.json](manifest.json), with fetched-source hashes in
[sources.json](sources.json). The source examples are Apache-licensed; this
repository retains the Apache license. The examples here use original fixtures.
