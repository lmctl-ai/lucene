#!/usr/bin/env python3
"""Real compiler + real Lucene integration and failure controls; no remote services."""
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
HOST = str(ROOT / 'lmcode/lmlucene')
COMPILER = os.environ.get('LMSCRIPT_BIN', 'lmscript')
ENV = dict(os.environ, PATH=str(ROOT / 'lmcode') + os.pathsep + os.environ['PATH'])
checks = 0

def run(argv, expected=0):
    result = subprocess.run([str(x) for x in argv], cwd=ROOT, env=ENV, capture_output=True, text=True, timeout=90)
    assert result.returncode == expected, (argv, result.returncode, result.stdout, result.stderr)
    return result

def passed(label):
    global checks
    checks += 1
    print('PASS ' + label, flush=True)

def envelope(argv, expected=0):
    output = run(argv, expected).stdout
    assert len(output.splitlines()) == 1, output
    return json.loads(output)

with tempfile.TemporaryDirectory(prefix='lmlucene-test-') as tmp:
    tmp = Path(tmp)
    serial = 0

    def compile_body(body, expected=0):
        global serial
        serial += 1
        source, bytecode = tmp / f'{serial}.lms', tmp / f'{serial}.dslb'
        schema = ROOT / 'lmcode/lucene.dsl.json'
        source.write_text(f'p = _lmcode_ schema={json.dumps(str(schema))};\n{body}\nend _lmcode_\nsavedsl(p, {json.dumps(str(bytecode))});\n')
        result = run([COMPILER, '--no-preflight', source], expected)
        return bytecode, result

    def execute(body, expected=0):
        bytecode, _ = compile_body(body)
        return envelope([HOST, '--dsl', bytecode], expected)

    assert run([HOST, '--dsl-schema', '--json']).stdout == (ROOT / 'lmcode/lucene.dsl.json').read_text()
    assert run([HOST, '--schema']).stdout == (ROOT / 'lmcode/cli.schema.dev.json').read_text()
    have = run([HOST, '--dsl-signatures']).stdout.splitlines()
    want = run([COMPILER, '--dsl-signatures', ROOT / 'lmcode/lucene.dsl.json']).stdout.splitlines()
    assert sorted(have) == sorted(want), (set(have)-set(want),set(want)-set(have))
    passed('published schemas and all registry signatures match')

    for example in ['basic', 'rules', 'schema-cli']:
        result = run([COMPILER, ROOT / f'lmcode/examples/{example}.lms'])
        payload = json.loads(result.stdout)
        assert payload['ok'], payload
        if example == 'basic':
            assert [x['total'] for x in payload['result']['searches']] == [2, 1]
        if example == 'rules':
            baseline, custom = payload['result']['searches']
            before = {h['fields']['id'][0]: h['score'] for h in baseline['hits']}
            after = {h['fields']['id'][0]: h['score'] for h in custom['hits']}
            assert abs(after['title-hit'] / before['title-hit'] - 10) < 0.00001
            assert after['body-hit'] == before['body-hit']
            assert payload['result']['rule_calls'] > 0
        passed(example + ' example, including normal LMScript preflight')

    index = tmp / 'persistent index with spaces'
    for _ in range(2):
        run([COMPILER, ROOT / 'lmcode/examples/index.lms', index])
    payload = json.loads(run([COMPILER, ROOT / 'lmcode/examples/query.lms', index, 'lucene AND rules']).stdout)
    assert payload['result']['searches'][0]['total'] == 1
    assert payload['result']['searches'][0]['hits'][0]['fields']['id'] == ['2']
    count = execute(f'let d=fs_directory({json.dumps(str(index))}); let r=directory_reader(d); assert_true(r.num_docs()==2,"idempotent update");')
    assert count['ok']
    passed('persistent index, separate query process, idempotent updates and paths with spaces')

    body = '''let d=memory_directory(); let a=standard_analyzer(); let w=index_writer(d,writer_config(a));
      w.add(document().keyword("id","old",true).text("body","hello world",true));
      let before=w.reader();
      w.update("id","old",document().keyword("id","new",true).text("body","hello moon",true));
      let after=w.reader();
      assert_true(index_searcher(before).count(term_query("id","old"))==1,"snapshot retained");
      assert_true(index_searcher(after).count(term_query("id","new"))==1,"updated snapshot");
      w.delete("id","new"); let deleted=w.reader();
      assert_true(deleted.num_docs()==0,"delete visible to fresh reader");'''
    assert execute(body)['ok']
    passed('near-real-time reader snapshots, update and delete')

    body = '''@analysis
      fn normalize(t:string)->string implements TokenTransform {
        let local=str_list();local.add(t);return local.at(0);
      }
      let saved=str_list();saved.add("alive");
      let d=memory_directory();let a=rule_analyzer("normalize");let w=index_writer(d,writer_config(a));
      w.add(document().text("body","one two three",true));
      assert_true(saved.at(0)=="alive","callback arena must not invalidate caller collections");'''
    assert execute(body)['ok']
    passed('nested callbacks use independent stdlib arenas')

    rollback = tmp / 'rollback'
    prefix = f'let d=fs_directory({json.dumps(str(rollback))});let a=standard_analyzer();let w=index_writer(d,writer_config(a));'
    assert execute(prefix + 'w.commit();')['ok']
    payload = execute(prefix + 'w.add(document().text("body","uncommitted",true));assert_true(false,"intentional failure");', 3)
    assert payload['code']=='RUN_HOST' and payload['line'] >= 2
    assert execute(f'let d=fs_directory({json.dumps(str(rollback))});assert_true(directory_reader(d).num_docs()==0,"rollback");')['ok']
    assert execute(prefix + 'w.add(document().text("body","close commits",true));w.close();')['ok']
    assert execute(f'let d=fs_directory({json.dumps(str(rollback))});assert_true(directory_reader(d).num_docs()==1,"close commits");')['ok']
    passed('exception cleanup releases lock and rolls back; explicit close commits')

    for body, message in [
        ('let d=memory_directory();d.close();directory_reader(d);','resource is closed'),
        ('rule_analyzer("missing");','unknown rule entry'),
        ('@x\nfn wrong(t:string)->string implements TokenTransform{return t;}\nlet d=memory_directory();let a=standard_analyzer();let w=index_writer(d,writer_config(a));let r=w.reader();index_searcher(r).similarity_rule("wrong");','must implement'),
        ('@x\nfn bad(t:string)->string implements TokenTransform{assert_true(false,"callback broke");return t;}\nlet d=memory_directory();let a=rule_analyzer("bad");let w=index_writer(d,writer_config(a));w.add(document().text("body","text",true));','callback broke'),
        ('@x\nfn bad(t:string)->string implements TokenTransform{memory_directory();return t;}\nlet d=memory_directory();let a=rule_analyzer("bad");let w=index_writer(d,writer_config(a));w.add(document().text("body","text",true));','unavailable inside a rule callback'),
        ('@x\nfn bad(s:CollectionStats)->float implements FieldWeight{return -1.0;}\nlet d=memory_directory();let a=standard_analyzer();let w=index_writer(d,writer_config(a));w.add(document().text("body","text",true));let r=w.reader();index_searcher(r).similarity_rule("bad").search(term_query("body","text"),1);','finite nonnegative'),
        ('let d=memory_directory();let a=standard_analyzer();let w=index_writer(d,writer_config(a));let r=w.reader();index_searcher(r).search(all_query(),0);','hit limit'),
        ('let d=memory_directory();let a=standard_analyzer();let w=index_writer(d,writer_config(a));let r=w.reader();index_searcher(r).search(all_query(),1).at(0);','hit index'),
        ('parse_query("body","(",standard_analyzer());','parse_query'),
        ('document().stored("bad",str_hex_decode("ff"));','valid UTF-8'),
        ('@x\nfn empty(t:string)->string implements TokenTransform{return "";}\nlet d=memory_directory();let a=rule_analyzer("empty");let w=index_writer(d,writer_config(a));w.add(document().text("body","text",true));','nonempty token'),
    ]:
        payload=execute(body,3)
        assert message in payload['message'], payload
        assert payload['line'] >= 2, payload
    passed('located failures: closed handles, missing/wrong rules, callback errors and reentry, scores, limits, bounds, query syntax')

    setup = 'let d=memory_directory();let a=standard_analyzer();let w=index_writer(d,writer_config(a));'
    for body, message in [
        ('standard_analyzer().tokens("body","hello").at(1);', 'index out of range'),
        ('document().vector("v",float_list());', 'must not be empty'),
        ('document().vector("v",float_list().add(0.0 / 0.0));', 'finite float32'),
        (setup+'w.add(document().vector("v",float_list().add(1.0).add(0.0)));let r=w.reader();index_searcher(r).search(knn_query("v",float_list().add(1.0),1),1);', 'dimension'),
        (setup+'w.add(document().text("body","x",true));let r=w.reader();let s=index_searcher(r);let hit=s.search(all_query(),1).at(0);index_searcher(r).search_after(all_query(),hit,1);', 'another searcher'),
        (setup+'w.add(document().sort_number("n",1));let r=w.reader();let s=index_searcher(r);let hit=s.sorted_search(all_query(),"n",SortType.LONG,false,1).at(0);s.search_after(all_query(),hit,1);', 'score-ordered cursor'),
        (setup+'let f=facet_config();w.add(f.build(document().facet("Tag","x")));let r=w.reader();index_searcher(r).facets(all_query(),f,"Tag",0);', 'facet limit'),
    ]:
        payload=execute(body,3)
        assert message.lower() in payload['message'].lower(), payload
        assert payload['line'] >= 2, payload
    empty_facets = execute(setup+'let f=facet_config();w.add(f.build(document().facet("Tag","x")));let r=w.reader();let counts=index_searcher(r).facets(term_query("missing","none"),f,"Tag",10);assert_true(counts.total()==0 && counts.len()==0,"empty facet matches");')
    assert empty_facets['ok']
    passed('gallery API failure controls: token bounds, invalid vectors, cursor ownership/order, facet limit and empty matches')

    _, result = compile_body('index_writer("wrong", "types");', 2)
    assert '_lmcode_' in result.stderr
    _, result = compile_body('@x\nfn wrong(t:string)->int implements TokenTransform{return 1;}', 2)
    assert 'TokenTransform' in result.stderr
    passed('compiler rejects ill-typed API calls and SAM definitions')

    good,_=compile_body('assert_true(true,"ok");')
    for offset, value, code in [(0,0,'LOAD_BAD_BYTECODE'), (5,5,'LOAD_BAD_BYTECODE'),(6,0,'LOAD_SCHEMA_MISMATCH')]:
        raw=bytearray(good.read_bytes());raw[offset]=value if raw[offset]!=value else 1
        bad=tmp/f'bad-{offset}.dslb';bad.write_bytes(raw)
        payload=envelope([HOST,'--dsl',bad],3)
        assert payload['phase']=='load' and payload['code']==code,payload
    truncated=tmp/'truncated.dslb';truncated.write_bytes(good.read_bytes()[:-1])
    assert envelope([HOST,'--dsl',truncated],3)['phase']=='load'
    passed('bad magic, old bytecode version, schema mismatch and truncation rejected before effects')

    no_effects=tmp/'must not exist'
    program,_=compile_body(f'fs_directory({json.dumps(str(no_effects))});')
    assert envelope([HOST,'run','--program',program,'--dryrun'])['result']['validated']
    assert not no_effects.exists()
    assert envelope([HOST,'run','--program',program,'--readonly'],3)['code']=='READONLY'
    assert not no_effects.exists()
    passed('dry-run and read-only policy create no directory')

    document=json.dumps({'op':'run','params':{'program':str(good)}})
    assert envelope([HOST,'run','--input-json',document])['ok']
    input_file=tmp/'input.json';input_file.write_text(document)
    assert envelope([HOST,'run','--input-file',input_file])['ok']
    for args in [[],['--unknown'],['run','--input-json','{}'],['run','--program',str(good),'--typo'],['run','--input-json',json.dumps({'op':'run','params':{'program':42}})]]:
        assert envelope([HOST,*args],2)['code']=='CLI_USAGE'
    passed('CLI flags, JSON input, file input and strict usage validation')

print(f'PASS {checks} integration groups')
