/* SPDX-License-Identifier: Apache-2.0 */
package io.lmctl.lucene;

import io.lmctl.lmcode.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.*;
import org.apache.lucene.facet.sortedset.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.BytesRef;

/** Typed handles to Lucene itself. The schema is generated from these same registrations. */
public final class Host implements AutoCloseable {
  public final Registry registry = new Registry();
  private final Map<String, Object> functions = new TreeMap<>();
  private final Map<String, Object> types = new TreeMap<>();
  private final List<Resource<?>> resources = new ArrayList<>();
  private final boolean callbackOnly;
  private Host callbacks;
  private Program rules;
  public long callbackCalls;
  private final List<Object> results = new ArrayList<>();
  private final List<Object> details = new ArrayList<>();

  @FunctionalInterface
  interface Body {
    Value call(Value self, Value[] args) throws Exception;
  }

  static final class Resource<T extends AutoCloseable> {
    final T value;
    boolean closed;

    Resource(T value) {
      this.value = value;
    }

    T get() {
      if (closed) throw VmException.host("resource is closed");
      return value;
    }

    void close() throws Exception {
      if (!closed) {
        value.close();
        closed = true;
      }
    }

    void cleanup() throws Exception {
      if (!closed) {
        // Uncommitted work is deliberately rolled back, including on a VM failure.
        if (value instanceof IndexWriter writer) writer.rollback();
        else value.close();
        closed = true;
      }
    }
  }

  record Search(IndexSearcher value, Resource<DirectoryReader> reader) {
    IndexSearcher get() {
      reader.get();
      return value;
    }
  }

  record Hits(TopDocs value, Search search) {}

  record Hit(ScoreDoc value, Search search) {}

  record Stats(String field, long docCount, long totalTerms, long docFreq, double boost) {}

  record Token(String term, int position, int start, int end) {}

  record Tokens(List<Token> items) {}

  record VectorTerm(String term, List<Token> positions) {}

  record VectorTerms(List<VectorTerm> items) {}

  record Counts(String dimension, long total, List<LabelAndValue> labels) {}

  public Host() {
    this(false);
  }

  private Host(boolean callbackOnly) {
    this.callbackOnly = callbackOnly;
    fn(
        "print",
        "void",
        List.of("string"),
        (s, a) -> {
          System.err.println(text(a[0]));
          return Value.none();
        });
    fn(
        "assert_true",
        "void",
        List.of("bool", "string"),
        (s, a) -> {
          if (!a[0].asBool()) throw VmException.host("assertion: " + text(a[1]));
          return Value.none();
        });
    fn(
        "memory_directory",
        "Directory",
        List.of(),
        (s, a) -> own("Directory", new ByteBuffersDirectory()));
    fn(
        "fs_directory",
        "Directory",
        List.of("string"),
        (s, a) -> own("Directory", FSDirectory.open(Path.of(text(a[0])))));
    fn(
        "standard_analyzer",
        "Analyzer",
        List.of(),
        (s, a) -> own("Analyzer", new StandardAnalyzer()));
    fn("keyword_analyzer", "Analyzer", List.of(), (s, a) -> own("Analyzer", new KeywordAnalyzer()));
    fn(
        "rule_analyzer",
        "Analyzer",
        List.of("string"),
        (s, a) -> {
          int entry = entry(text(a[0]), "interface TokenTransform(string) -> string");
          Analyzer base = new StandardAnalyzer();
          Analyzer wrapper =
              new AnalyzerWrapper(Analyzer.PER_FIELD_REUSE_STRATEGY) {
                @Override
                protected Analyzer getWrappedAnalyzer(String field) {
                  return base;
                }

                @Override
                protected TokenStreamComponents wrapComponents(
                    String field, TokenStreamComponents c) {
                  TokenStream filter =
                      new TokenFilter(c.getTokenStream()) {
                        final CharTermAttribute term = addAttribute(CharTermAttribute.class);

                        @Override
                        public boolean incrementToken() throws IOException {
                          if (!input.incrementToken()) return false;
                          String replacement = text(invoke(entry, Value.ofString(term.toString())));
                          if (replacement.isEmpty())
                            throw VmException.host("TokenTransform must return a nonempty token");
                          term.setEmpty().append(replacement);
                          return true;
                        }
                      };
                  return new TokenStreamComponents(c.getSource(), filter);
                }

                @Override
                public void close() {
                  try {
                    super.close();
                  } finally {
                    base.close();
                  }
                }
              };
          return own("Analyzer", wrapper);
        });
    fn("document", "Document", List.of(), (s, a) -> obj("Document", new Document()));
    method(
        "Document",
        "text",
        "Document",
        List.of("string", "string", "bool"),
        (s, a) -> {
          doc(s).add(new TextField(text(a[0]), text(a[1]), store(a[2])));
          return s;
        });
    method(
        "Document",
        "keyword",
        "Document",
        List.of("string", "string", "bool"),
        (s, a) -> {
          doc(s).add(new StringField(text(a[0]), text(a[1]), store(a[2])));
          return s;
        });
    method(
        "Document",
        "stored",
        "Document",
        List.of("string", "string"),
        (s, a) -> {
          doc(s).add(new StoredField(text(a[0]), text(a[1])));
          return s;
        });
    method(
        "Document",
        "number",
        "Document",
        List.of("string", "int", "bool"),
        (s, a) -> {
          doc(s).add(new LongPoint(text(a[0]), a[1].asInt()));
          if (a[2].asBool()) doc(s).add(new StoredField(text(a[0]), a[1].asInt()));
          return s;
        });
    method(
        "Document",
        "get",
        "string",
        List.of("string"),
        (s, a) -> Value.ofString(Objects.toString(doc(s).get(text(a[0])), "")));
    method(
        "Document",
        "has",
        "bool",
        List.of("string"),
        (s, a) -> Value.ofBool(doc(s).getField(text(a[0])) != null));
    fn(
        "writer_config",
        "WriterConfig",
        List.of("Analyzer"),
        (s, a) -> obj("WriterConfig", new IndexWriterConfig(resource(a[0], Analyzer.class).get())));
    method(
        "WriterConfig",
        "open_mode",
        "WriterConfig",
        List.of("OpenMode"),
        (s, a) -> {
          config(s).setOpenMode(IndexWriterConfig.OpenMode.valueOf(text(a[0])));
          return s;
        });
    method(
        "WriterConfig",
        "ram_mb",
        "WriterConfig",
        List.of("float"),
        (s, a) -> {
          config(s).setRAMBufferSizeMB(a[0].asFloat());
          return s;
        });
    fn(
        "index_writer",
        "IndexWriter",
        List.of("Directory", "WriterConfig"),
        (s, a) ->
            own(
                "IndexWriter",
                new IndexWriter(resource(a[0], Directory.class).get(), config(a[1]))));
    method(
        "IndexWriter",
        "add",
        "void",
        List.of("Document"),
        (s, a) -> {
          writer(s).addDocument(doc(a[0]));
          return Value.none();
        });
    method(
        "IndexWriter",
        "update",
        "void",
        List.of("string", "string", "Document"),
        (s, a) -> {
          writer(s).updateDocument(new Term(text(a[0]), text(a[1])), doc(a[2]));
          return Value.none();
        });
    method(
        "IndexWriter",
        "delete",
        "void",
        List.of("string", "string"),
        (s, a) -> {
          writer(s).deleteDocuments(new Term(text(a[0]), text(a[1])));
          return Value.none();
        });
    method(
        "IndexWriter",
        "commit",
        "void",
        List.of(),
        (s, a) -> {
          writer(s).commit();
          return Value.none();
        });
    method(
        "IndexWriter",
        "reader",
        "DirectoryReader",
        List.of(),
        (s, a) -> own("DirectoryReader", DirectoryReader.open(writer(s))));
    method(
        "IndexWriter",
        "rollback",
        "void",
        List.of(),
        (s, a) -> {
          resource(s, IndexWriter.class).cleanup();
          return Value.none();
        });
    fn(
        "directory_reader",
        "DirectoryReader",
        List.of("Directory"),
        (s, a) ->
            own("DirectoryReader", DirectoryReader.open(resource(a[0], Directory.class).get())));
    method(
        "DirectoryReader",
        "num_docs",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(resource(s, DirectoryReader.class).get().numDocs()));
    fn(
        "index_searcher",
        "IndexSearcher",
        List.of("DirectoryReader"),
        (s, a) -> {
          Resource<DirectoryReader> r = resource(a[0], DirectoryReader.class);
          return obj("IndexSearcher", new Search(new IndexSearcher(r.get()), r));
        });
    method(
        "IndexSearcher",
        "bm25",
        "IndexSearcher",
        List.of("float", "float"),
        (s, a) -> {
          search(s)
              .get()
              .setSimilarity(new BM25Similarity((float) a[0].asFloat(), (float) a[1].asFloat()));
          return s;
        });
    method(
        "IndexSearcher",
        "similarity_rule",
        "IndexSearcher",
        List.of("string"),
        (s, a) -> {
          String ruleName = text(a[0]);
          int entry = entry(ruleName, "interface FieldWeight(CollectionStats) -> float");
          Similarity base = search(s).get().getSimilarity();
          search(s)
              .get()
              .setSimilarity(
                  new Similarity() {
                    @Override
                    public long computeNorm(FieldInvertState state) {
                      return base.computeNorm(state);
                    }

                    @Override
                    public SimScorer scorer(
                        float boost, CollectionStatistics stats, TermStatistics... terms) {
                      long df = 0;
                      for (TermStatistics term : terms) df = Math.max(df, term.docFreq());
                      double factor =
                          invoke(
                                  entry,
                                  obj(
                                      "CollectionStats",
                                      new Stats(
                                          stats.field(),
                                          stats.docCount(),
                                          stats.sumTotalTermFreq(),
                                          df,
                                          boost)))
                              .asFloat();
                      if (!Double.isFinite(factor) || factor < 0 || factor > Float.MAX_VALUE)
                        throw VmException.host(
                            "FieldWeight must return a finite nonnegative float within float32"
                                + " range");
                      SimScorer original = base.scorer(boost, stats, terms);
                      return new SimScorer() {
                        @Override
                        public float score(float freq, long norm) {
                          float score = (float) (original.score(freq, norm) * factor);
                          if (!Float.isFinite(score) || score < 0)
                            throw VmException.host("similarity produced an invalid score");
                          return score;
                        }

                        @Override
                        public Explanation explain(Explanation freq, long norm) {
                          return Explanation.match(
                              score(freq.getValue().floatValue(), norm),
                              "lmcode FieldWeight " + ruleName + " (factor=" + factor + ")",
                              original.explain(freq, norm));
                        }
                      };
                    }
                  });
          return s;
        });
    method(
        "CollectionStats",
        "field",
        "string",
        List.of(),
        (s, a) -> Value.ofString(((Stats) s.asObject()).field()));
    method(
        "CollectionStats",
        "doc_count",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((Stats) s.asObject()).docCount()));
    method(
        "CollectionStats",
        "total_terms",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((Stats) s.asObject()).totalTerms()));
    method(
        "CollectionStats",
        "doc_freq",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((Stats) s.asObject()).docFreq()));
    method(
        "CollectionStats",
        "boost",
        "float",
        List.of(),
        (s, a) -> Value.ofFloat(((Stats) s.asObject()).boost()));
    fn(
        "parse_query",
        "Query",
        List.of("string", "string", "Analyzer"),
        (s, a) ->
            obj(
                "Query",
                new QueryParser(text(a[0]), resource(a[2], Analyzer.class).get())
                    .parse(text(a[1]))));
    fn(
        "term_query",
        "Query",
        List.of("string", "string"),
        (s, a) -> obj("Query", new TermQuery(new Term(text(a[0]), text(a[1])))));
    fn(
        "prefix_query",
        "Query",
        List.of("string", "string"),
        (s, a) -> obj("Query", new PrefixQuery(new Term(text(a[0]), text(a[1])))));
    fn("all_query", "Query", List.of(), (s, a) -> obj("Query", new MatchAllDocsQuery()));
    fn(
        "long_range",
        "Query",
        List.of("string", "int", "int"),
        (s, a) -> obj("Query", LongPoint.newRangeQuery(text(a[0]), a[1].asInt(), a[2].asInt())));
    fn(
        "boolean_query",
        "BooleanQuery",
        List.of(),
        (s, a) -> obj("BooleanQuery", new BooleanQuery.Builder()));
    method(
        "BooleanQuery",
        "add",
        "BooleanQuery",
        List.of("Query", "Occur"),
        (s, a) -> {
          ((BooleanQuery.Builder) s.asObject())
              .add(query(a[0]), BooleanClause.Occur.valueOf(text(a[1])));
          return s;
        });
    method(
        "BooleanQuery",
        "build",
        "Query",
        List.of(),
        (s, a) -> obj("Query", ((BooleanQuery.Builder) s.asObject()).build()));
    method(
        "IndexSearcher",
        "search",
        "TopDocs",
        List.of("Query", "int"),
        (s, a) -> {
          Search search = search(s);
          int limit = positive(a[1], "hit limit");
          TopDocs hits =
              search
                  .get()
                  .search(query(a[0]), new TopScoreDocCollectorManager(limit, Integer.MAX_VALUE));
          for (ScoreDoc hit : hits.scoreDocs)
            if (!Float.isFinite(hit.score) || hit.score < 0)
              throw VmException.host("query produced an invalid score");
          return obj("TopDocs", new Hits(hits, search));
        });
    method(
        "IndexSearcher",
        "count",
        "int",
        List.of("Query"),
        (s, a) -> Value.ofInt(search(s).get().count(query(a[0]))));
    method(
        "IndexSearcher",
        "explain",
        "string",
        List.of("Query", "int"),
        (s, a) ->
            Value.ofString(
                search(s).get().explain(query(a[0]), Math.toIntExact(a[1].asInt())).toString()));
    method(
        "TopDocs",
        "total",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((Hits) s.asObject()).value().totalHits.value()));
    method(
        "TopDocs",
        "len",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((Hits) s.asObject()).value().scoreDocs.length));
    method(
        "TopDocs",
        "at",
        "Hit",
        List.of("int"),
        (s, a) -> {
          Hits hits = (Hits) s.asObject();
          long i = a[0].asInt();
          if (i < 0 || i >= hits.value().scoreDocs.length)
            throw VmException.host("hit index out of range");
          return obj("Hit", new Hit(hits.value().scoreDocs[(int) i], hits.search()));
        });
    method(
        "Hit", "doc_id", "int", List.of(), (s, a) -> Value.ofInt(((Hit) s.asObject()).value().doc));
    method(
        "Hit",
        "score",
        "float",
        List.of(),
        (s, a) -> Value.ofFloat(((Hit) s.asObject()).value().score));
    method(
        "Hit",
        "document",
        "Document",
        List.of(),
        (s, a) -> {
          Hit hit = (Hit) s.asObject();
          return obj("Document", hit.search().get().storedFields().document(hit.value().doc));
        });
    // One JSON envelope on stdout; diagnostics/print go to stderr.
    method(
        "TopDocs",
        "emit",
        "void",
        List.of("string"),
        (s, a) -> {
          Hits hits = (Hits) s.asObject();
          List<Object> rows = new ArrayList<>();
          for (ScoreDoc hit : hits.value().scoreDocs) {
            Document document = hits.search().get().storedFields().document(hit.doc);
            Map<String, Object> fields = new TreeMap<>();
            for (IndexableField f : document.getFields()) {
              Object value = f.numericValue() != null ? f.numericValue() : f.stringValue();
              @SuppressWarnings("unchecked")
              List<Object> values =
                  (List<Object>) fields.computeIfAbsent(f.name(), k -> new ArrayList<>());
              values.add(value);
            }
            rows.add(Map.of("doc_id", hit.doc, "score", hit.score, "fields", fields));
          }
          results.add(
              Map.of("label", text(a[0]), "total", hits.value().totalHits.value(), "hits", rows));
          return Value.none();
        });
    registerGalleryBindings();
    for (String type : List.of("Directory", "Analyzer", "IndexWriter", "DirectoryReader"))
      method(
          type,
          "close",
          "void",
          List.of(),
          (s, a) -> {
            ((Resource<?>) s.asObject()).close();
            return Value.none();
          });
  }

  /** Additional Lucene APIs used by the runnable upstream-inspired gallery. */
  private void registerGalleryBindings() {
    fn("english_analyzer", "Analyzer", List.of(), (s, a) -> own("Analyzer", new EnglishAnalyzer()));
    fn(
        "whitespace_analyzer",
        "Analyzer",
        List.of(),
        (s, a) -> own("Analyzer", new WhitespaceAnalyzer()));
    fn(
        "report",
        "void",
        List.of("string", "string"),
        (s, a) -> {
          details.add(Map.of("label", text(a[0]), "value", text(a[1])));
          return Value.none();
        });
    method(
        "Analyzer",
        "tokens",
        "Tokens",
        List.of("string", "string"),
        (s, a) -> {
          List<Token> tokens = new ArrayList<>();
          try (TokenStream stream =
              resource(s, Analyzer.class).get().tokenStream(text(a[0]), text(a[1]))) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            OffsetAttribute offset = stream.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute increment =
                stream.addAttribute(PositionIncrementAttribute.class);
            stream.reset();
            int position = -1;
            while (stream.incrementToken()) {
              position += increment.getPositionIncrement();
              tokens.add(
                  new Token(term.toString(), position, offset.startOffset(), offset.endOffset()));
            }
            stream.end();
          }
          return obj("Tokens", new Tokens(List.copyOf(tokens)));
        });
    method(
        "Tokens",
        "len",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((Tokens) s.asObject()).items().size()));
    method(
        "Tokens",
        "at",
        "Token",
        List.of("int"),
        (s, a) -> obj("Token", item(((Tokens) s.asObject()).items(), a[0])));
    method(
        "Token",
        "term",
        "string",
        List.of(),
        (s, a) -> Value.ofString(((Token) s.asObject()).term()));
    method(
        "Token",
        "position",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((Token) s.asObject()).position()));
    method(
        "Token", "start", "int", List.of(), (s, a) -> Value.ofInt(((Token) s.asObject()).start()));
    method("Token", "end", "int", List.of(), (s, a) -> Value.ofInt(((Token) s.asObject()).end()));
    method(
        "Tokens",
        "emit",
        "void",
        List.of("string"),
        (s, a) -> {
          List<Object> rows = new ArrayList<>();
          for (Token t : ((Tokens) s.asObject()).items())
            rows.add(
                Map.of(
                    "term",
                    t.term(),
                    "position",
                    t.position(),
                    "start",
                    t.start(),
                    "end",
                    t.end()));
          details.add(Map.of("label", text(a[0]), "tokens", rows));
          return Value.none();
        });
    method(
        "Document",
        "vector_text",
        "Document",
        List.of("string", "string", "bool"),
        (s, a) -> {
          FieldType type = new FieldType(TextField.TYPE_NOT_STORED);
          type.setStored(a[2].asBool());
          type.setStoreTermVectors(true);
          type.setStoreTermVectorPositions(true);
          type.setStoreTermVectorOffsets(true);
          type.freeze();
          doc(s).add(new Field(text(a[0]), text(a[1]), type));
          return s;
        });
    method(
        "DirectoryReader",
        "term_vectors",
        "TermVectors",
        List.of("int", "string"),
        (s, a) -> {
          Terms terms =
              resource(s, DirectoryReader.class)
                  .get()
                  .termVectors()
                  .get(Math.toIntExact(a[0].asInt()), text(a[1]));
          List<VectorTerm> result = new ArrayList<>();
          if (terms != null) {
            TermsEnum iterator = terms.iterator();
            BytesRef bytes;
            while ((bytes = iterator.next()) != null) {
              String term = bytes.utf8ToString();
              PostingsEnum postings = iterator.postings(null, PostingsEnum.ALL);
              postings.nextDoc();
              List<Token> positions = new ArrayList<>();
              for (int i = 0; i < postings.freq(); i++)
                positions.add(
                    new Token(
                        term,
                        postings.nextPosition(),
                        postings.startOffset(),
                        postings.endOffset()));
              result.add(new VectorTerm(term, List.copyOf(positions)));
            }
          }
          return obj("TermVectors", new VectorTerms(List.copyOf(result)));
        });
    method(
        "TermVectors",
        "len",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((VectorTerms) s.asObject()).items().size()));
    method(
        "TermVectors",
        "at",
        "VectorTerm",
        List.of("int"),
        (s, a) -> obj("VectorTerm", item(((VectorTerms) s.asObject()).items(), a[0])));
    method(
        "VectorTerm",
        "term",
        "string",
        List.of(),
        (s, a) -> Value.ofString(((VectorTerm) s.asObject()).term()));
    method(
        "VectorTerm",
        "len",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((VectorTerm) s.asObject()).positions().size()));
    method(
        "VectorTerm",
        "at",
        "Token",
        List.of("int"),
        (s, a) -> obj("Token", item(((VectorTerm) s.asObject()).positions(), a[0])));
    method(
        "VectorTerm",
        "tokens",
        "Tokens",
        List.of(),
        (s, a) -> obj("Tokens", new Tokens(((VectorTerm) s.asObject()).positions())));
    method(
        "Document",
        "sort_number",
        "Document",
        List.of("string", "int"),
        (s, a) -> {
          doc(s).add(new NumericDocValuesField(text(a[0]), a[1].asInt()));
          return s;
        });
    method(
        "Document",
        "sort_keyword",
        "Document",
        List.of("string", "string"),
        (s, a) -> {
          doc(s).add(new SortedDocValuesField(text(a[0]), new BytesRef(text(a[1]))));
          return s;
        });
    method(
        "IndexSearcher",
        "sorted_search",
        "TopDocs",
        List.of("Query", "string", "SortType", "bool", "int"),
        (s, a) -> {
          Search search = search(s);
          Query q = query(a[0]);
          Sort sort =
              new Sort(
                  new SortField(text(a[1]), SortField.Type.valueOf(text(a[2])), a[3].asBool()));
          TopDocs hits =
              search
                  .get()
                  .search(
                      q,
                      new TopFieldCollectorManager(
                          sort, positive(a[4], "hit limit"), Integer.MAX_VALUE));
          TopFieldCollector.populateScores(hits.scoreDocs, search.get(), q);
          return checkedHits(hits, search);
        });
    method(
        "IndexSearcher",
        "search_after",
        "TopDocs",
        List.of("Query", "Hit", "int"),
        (s, a) -> {
          Search search = search(s);
          Hit after = (Hit) a[1].asObject();
          if (after.search() != search)
            throw VmException.host("search_after cursor belongs to another searcher");
          if (after.value() instanceof FieldDoc)
            throw VmException.host("search_after requires a score-ordered cursor");
          return checkedHits(
              search
                  .get()
                  .search(
                      query(a[0]),
                      new TopScoreDocCollectorManager(
                          positive(a[2], "hit limit"), after.value(), Integer.MAX_VALUE)),
              search);
        });
    fn("facet_config", "FacetConfig", List.of(), (s, a) -> obj("FacetConfig", new FacetsConfig()));
    method(
        "FacetConfig",
        "multi_valued",
        "FacetConfig",
        List.of("string"),
        (s, a) -> {
          FacetsConfig config = (FacetsConfig) s.asObject();
          config.setMultiValued(text(a[0]), true);
          config.setRequireDimCount(text(a[0]), true);
          return s;
        });
    method(
        "Document",
        "facet",
        "Document",
        List.of("string", "string"),
        (s, a) -> {
          doc(s).add(new SortedSetDocValuesFacetField(text(a[0]), text(a[1])));
          return s;
        });
    method(
        "FacetConfig",
        "build",
        "Document",
        List.of("Document"),
        (s, a) -> obj("Document", ((FacetsConfig) s.asObject()).build(doc(a[0]))));
    fn(
        "drill_down",
        "Query",
        List.of("FacetConfig", "Query", "string", "string"),
        (s, a) -> {
          DrillDownQuery q = new DrillDownQuery((FacetsConfig) a[0].asObject(), query(a[1]));
          q.add(text(a[2]), text(a[3]));
          return obj("Query", q);
        });
    method(
        "IndexSearcher",
        "facets",
        "FacetCounts",
        List.of("Query", "FacetConfig", "string", "int"),
        (s, a) -> {
          Search search = search(s);
          search.get();
          SortedSetDocValuesReaderState state =
              new DefaultSortedSetDocValuesReaderState(
                  search.reader().get(), (FacetsConfig) a[1].asObject());
          FacetsCollector collected =
              search.get().search(query(a[0]), new FacetsCollectorManager());
          FacetResult counts =
              new SortedSetDocValuesFacetCounts(state, collected)
                  .getTopChildren(positive(a[3], "facet limit"), text(a[2]));
          return obj(
              "FacetCounts",
              new Counts(
                  text(a[2]),
                  counts == null ? 0 : counts.value.longValue(),
                  counts == null ? List.of() : List.of(counts.labelValues)));
        });
    method(
        "FacetCounts",
        "total",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((Counts) s.asObject()).total()));
    method(
        "FacetCounts",
        "len",
        "int",
        List.of(),
        (s, a) -> Value.ofInt(((Counts) s.asObject()).labels().size()));
    method(
        "FacetCounts",
        "label",
        "string",
        List.of("int"),
        (s, a) -> Value.ofString(item(((Counts) s.asObject()).labels(), a[0]).label));
    method(
        "FacetCounts",
        "value",
        "int",
        List.of("int"),
        (s, a) -> Value.ofInt(item(((Counts) s.asObject()).labels(), a[0]).value.longValue()));
    method(
        "FacetCounts",
        "count",
        "int",
        List.of("string"),
        (s, a) -> {
          for (LabelAndValue label : ((Counts) s.asObject()).labels())
            if (label.label.equals(text(a[0]))) return Value.ofInt(label.value.longValue());
          return Value.ofInt(0);
        });
    method(
        "FacetCounts",
        "emit",
        "void",
        List.of("string"),
        (s, a) -> {
          Counts counts = (Counts) s.asObject();
          List<Object> rows = new ArrayList<>();
          for (LabelAndValue label : counts.labels())
            rows.add(Map.of("label", label.label, "count", label.value));
          details.add(
              Map.of(
                  "label",
                  text(a[0]),
                  "dimension",
                  counts.dimension(),
                  "total",
                  counts.total(),
                  "counts",
                  rows));
          return Value.none();
        });
    method(
        "Document",
        "vector",
        "Document",
        List.of("string", "FloatList"),
        (s, a) -> {
          doc(s)
              .add(
                  new KnnFloatVectorField(
                      text(a[0]), vector(a[1]), VectorSimilarityFunction.EUCLIDEAN));
          return s;
        });
    fn(
        "knn_query",
        "Query",
        List.of("string", "FloatList", "int"),
        (s, a) ->
            obj("Query", new KnnFloatVectorQuery(text(a[0]), vector(a[1]), positive(a[2], "k"))));
    fn(
        "knn_query",
        "Query",
        List.of("string", "FloatList", "int", "Query"),
        (s, a) ->
            obj(
                "Query",
                new KnnFloatVectorQuery(
                    text(a[0]), vector(a[1]), positive(a[2], "k"), query(a[3]))));
  }

  private static <T> T item(List<T> list, Value index) {
    long i = index.asInt();
    if (i < 0 || i >= list.size()) throw VmException.host("index out of range");
    return list.get((int) i);
  }

  private static float[] vector(Value value) {
    List<Value> list = Stdlib.collectionValues(value);
    if (list.isEmpty()) throw VmException.host("vector must not be empty");
    float[] result = new float[list.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = (float) list.get(i).asFloat();
      if (!Float.isFinite(result[i]))
        throw VmException.host("vector components must be finite float32 values");
    }
    return result;
  }

  private static Value checkedHits(TopDocs hits, Search search) {
    for (ScoreDoc hit : hits.scoreDocs)
      if (!Float.isFinite(hit.score) || hit.score < 0)
        throw VmException.host("query produced an invalid score");
    return obj("TopDocs", new Hits(hits, search));
  }

  private void fn(String name, String returns, List<String> params, Body body) {
    bind(null, name, returns, params, body);
  }

  private void method(String type, String name, String returns, List<String> params, Body body) {
    bind(type, name, returns, params, body);
  }

  @SuppressWarnings("unchecked")
  private void bind(String type, String name, String returns, List<String> params, Body body) {
    Map<String, Object> methods =
        type == null
            ? functions
            : (Map<String, Object>) types.computeIfAbsent(type, k -> new TreeMap<String, Object>());
    List<Object> overloads = (List<Object>) methods.computeIfAbsent(name, k -> new ArrayList<>());
    overloads.add(Map.of("params", params, "returns", returns));
    // Also declare opaque types that have no methods (e.g. Query).
    for (String t : params) declareType(t);
    declareType(returns);
    Registry.Callback call =
        (c, s, a) -> {
          if (callbackOnly
              && !"CollectionStats".equals(type)
              && !(type == null && (name.equals("print") || name.equals("assert_true"))))
            throw VmException.host(
                "Lucene operations are unavailable inside a rule callback: " + name);
          try {
            return body.call(s, a);
          } catch (VmException e) {
            throw e;
          } catch (Exception e) {
            throw VmException.host(
                name + ": " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
          }
        };
    if (type == null) registry.function(name, params, returns, call);
    else registry.method(type, name, params, returns, call);
  }

  private void declareType(String t) {
    if (!Set.of(
            "void", "int", "float", "bool", "string", "OpenMode", "Occur", "SortType", "FloatList")
        .contains(t)) types.computeIfAbsent(t, k -> new TreeMap<String, Object>());
  }

  public byte[] schema() {
    Map<String, Object> declared = new TreeMap<>();
    types.forEach((k, v) -> declared.put(k, Map.of("methods", v)));
    Map<String, Object> interfaces =
        Map.of(
            "FieldWeight",
                Map.of(
                    "params",
                    List.of(Map.of("name", "stats", "type", "CollectionStats")),
                    "returns",
                    "float"),
            "TokenTransform",
                Map.of(
                    "params",
                    List.of(Map.of("name", "token", "type", "string")),
                    "returns",
                    "string"));
    return (json(
                Map.of(
                    "dsl.schema",
                    2,
                    "name",
                    "lmlucene",
                    "types",
                    declared,
                    "functions",
                    functions,
                    "context",
                    Map.of(),
                    "enums",
                    Map.of(
                        "OpenMode",
                        List.of("CREATE", "APPEND", "CREATE_OR_APPEND"),
                        "Occur",
                        List.of("MUST", "SHOULD", "MUST_NOT", "FILTER"),
                        "SortType",
                        List.of("LONG", "STRING")),
                    "interfaces",
                    interfaces))
            + "\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  public void attach(byte[] bytes) {
    callbacks = new Host(true);
    rules = Program.load(bytes, schema(), callbacks.registry);
  }

  private int entry(String name, String signature) {
    if (rules == null) throw VmException.host("no rule program loaded");
    for (int i = 0; i < rules.entries().size(); i++) {
      Program.Entry e = rules.entries().get(i);
      if (e.name().equals(name)) {
        if (!e.iface().equals(signature))
          throw VmException.host("rule " + name + " must implement " + signature);
        return i;
      }
    }
    throw VmException.host("unknown rule entry: " + name);
  }

  private Value invoke(int entry, Value argument) {
    callbackCalls++;
    // Independent registry, stack, budget and stdlib arena; never re-enter the body registry.
    return rules.call(entry, new Context(), callbacks.registry, List.of(argument));
  }

  public String result() {
    return json(Map.of("searches", results, "details", details, "rule_calls", callbackCalls));
  }

  private <T extends AutoCloseable> Value own(String type, T value) {
    Resource<T> r = new Resource<>(value);
    resources.add(r);
    return obj(type, r);
  }

  @SuppressWarnings("unchecked")
  private static <T extends AutoCloseable> Resource<T> resource(Value value, Class<T> type) {
    Resource<?> r = (Resource<?>) value.asObject();
    type.cast(r.get());
    return (Resource<T>) r;
  }

  private static Document doc(Value v) {
    return (Document) v.asObject();
  }

  private static IndexWriterConfig config(Value v) {
    return (IndexWriterConfig) v.asObject();
  }

  private static IndexWriter writer(Value v) {
    return resource(v, IndexWriter.class).get();
  }

  private static Query query(Value v) {
    return (Query) v.asObject();
  }

  private static Search search(Value v) {
    return (Search) v.asObject();
  }

  private static Value obj(String t, Object v) {
    return Value.object(t, v);
  }

  private static Field.Store store(Value v) {
    return v.asBool() ? Field.Store.YES : Field.Store.NO;
  }

  private static int positive(Value v, String label) {
    long n = v.asInt();
    if (n < 1 || n > 1_000_000) throw VmException.host(label + " must be in 1..1000000");
    return (int) n;
  }

  static String text(Value v) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(v.asString().bytes()))
          .toString();
    } catch (Exception e) {
      throw VmException.host("Lucene text must be valid UTF-8");
    }
  }

  static String json(Object value) {
    if (value == null) return "null";
    if (value instanceof String s) return Reports.escape(s);
    if (value instanceof Number || value instanceof Boolean) return value.toString();
    if (value instanceof Map<?, ?> map) {
      List<String> parts = new ArrayList<>();
      map.entrySet().stream()
          .sorted(Comparator.comparing(e -> e.getKey().toString()))
          .forEach(
              e -> parts.add(Reports.escape(e.getKey().toString()) + ":" + json(e.getValue())));
      return "{" + String.join(",", parts) + "}";
    }
    if (value instanceof Collection<?> c)
      return "[" + String.join(",", c.stream().map(Host::json).toList()) + "]";
    throw new IllegalArgumentException("unsupported JSON value");
  }

  @Override
  public void close() throws Exception {
    Exception failure = null;
    for (int i = resources.size() - 1; i >= 0; i--)
      try {
        resources.get(i).cleanup();
      } catch (Exception e) {
        if (failure == null) failure = e;
        else failure.addSuppressed(e);
      }
    if (failure != null) throw failure;
  }
}
