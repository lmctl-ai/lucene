package io.lmctl.lmcode;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class Program {
  public static final int VERSION=6;
  List<VmString> consts=new ArrayList<>();
  List<Registry.Binding> imports=new ArrayList<>();
  List<String> contexts=new ArrayList<>();
  final List<Local> locals=new ArrayList<>();
  final List<Line> lines=new ArrayList<>();
  final Map<Integer,Loop> loops=new HashMap<>();
  byte[] code; int maxStack; Registry boundRegistry;
  private final List<Rule> rules=new ArrayList<>();
  public record Entry(String name,String annotation,String iface,List<String> params,String returns) {
    public Entry { params=List.copyOf(params); }
  }
  private record Rule(Entry entry,Program region) {}
  private Program() {}
  public record Local(String typeName, boolean hidden) {}
  public record Line(int pc,long number,int column) {}
  public record Loop(int counter,int end) {}
  public static Program load(byte[] bytecode, byte[] schema, Registry registry) {
    Objects.requireNonNull(bytecode); Objects.requireNonNull(schema); Objects.requireNonNull(registry);
    Reader r=new Reader(bytecode); if (bytecode.length<4 || !Arrays.equals(r.bytes(4),new byte[]{'D','S','L','B'})) throw VmException.badBytecode("not a DSL bytecode file: bad magic (expected 'DSLB')");
    int version=r.u16(); if(version!=VERSION) throw VmException.badBytecode("unsupported bytecode version "+version+" (this runtime reads version "+VERSION+")");
    byte[] actual=r.bytes(32), expected=sha256(schema); if(!Arrays.equals(actual,expected)) throw new VmException("LOAD_SCHEMA_MISMATCH","schema mismatch: bytecode was compiled against schema sha256 "+hex(actual)+", the given schema has sha256 "+hex(expected));
    Program p=new Program(); long n=r.u32(); bounded(n,bytecode.length,"constant"); for(long i=0;i<n;i++){int tag=r.u8(); if(tag!=1) throw VmException.badBytecode("constant "+i+" has unknown tag "+tag+" (1 = string)"); long len=r.u32(); if(len>Integer.MAX_VALUE) throw VmException.badBytecode("constant "+i+" is too large"); p.consts.add(new VmString(r.bytes((int)len)));}
    n=r.u16(); bounded(n,bytecode.length,"import"); for(int i=0;i<n;i++){String s=p.ident((int)r.u32(),"import"); Registry.Binding b=registry.resolve(s); if(b==null) throw new VmException("LOAD_UNBOUND","import '"+s+"' is not provided by the host"+(registry.declaredFor(s).isEmpty()?"":" (the host declares "+registry.declaredFor(s)+")")); p.imports.add(b);}
    n=r.u16(); bounded(n,bytecode.length,"context"); for(int i=0;i<n;i++){String name=p.ident((int)r.u32(),"context name"), type=p.ident((int)r.u32(),"context type"); String have=registry.contextType(name); if(have==null) throw new VmException("LOAD_UNBOUND","context variable '"+name+"' is not declared by the host"); if(!have.equals(type)) throw new VmException("LOAD_UNBOUND","context variable '"+name+"' is declared as "+type+" by the bytecode, the host provides "+have); p.contexts.add(name);}
    readRegion(r,p,bytecode.length);
    p.boundRegistry=registry; Verifier.verify(p);
    int count=r.u16();
    for(int i=0;i<count;i++) {
      String name="<"+i+">";
      try {
        name=p.ident((int)r.u32(),"entry name");
        for(Rule prev:p.rules) if(prev.entry().name().equals(name)) throw VmException.badBytecode("duplicate entry name '"+name+"'");
        String annotation=p.ident((int)r.u32(),"entry annotation"),iface=p.ident((int)r.u32(),"entry interface");
        int nparams=r.u16();
        // Regions own execution metadata; their immutable pools and registry are shared.
        Program region=new Program();region.consts=p.consts;region.imports=p.imports;region.contexts=p.contexts;region.boundRegistry=registry;
        readRegion(r,region,bytecode.length);
        if(nparams>region.locals.size())throw VmException.badBytecode("nparams "+nparams+" exceeds nlocals "+region.locals.size());
        var match=java.util.regex.Pattern.compile("interface ([A-Za-z_][A-Za-z0-9_]*)\\(([^()]*)\\) -> ([A-Za-z_][A-Za-z0-9_]*)").matcher(iface);
        if(!match.matches())throw VmException.badBytecode("interface '"+iface+"' is not an interface signature");
        List<String> params=match.group(2).isEmpty()?List.of():Arrays.asList(match.group(2).split(", ",-1));
        for(String type:params)if(!type.matches("[A-Za-z_][A-Za-z0-9_]*"))throw VmException.badBytecode("interface '"+iface+"' is not an interface signature");
        if(nparams!=params.size())throw VmException.badBytecode("entry '"+name+"' declares "+nparams+" parameter(s); "+iface+" has "+params.size());
        for(int j=0;j<nparams;j++) {
          Local local=region.locals.get(j);
          if(local.hidden())throw VmException.badBytecode("entry '"+name+"' parameter "+(j+1)+" is hidden");
          if(!local.typeName().equals(params.get(j)))throw VmException.badBytecode("entry '"+name+"' parameter "+(j+1)+" has type "+local.typeName()+"; the interface expects "+params.get(j));
        }
        String returns=match.group(3);Verifier.verify(region,returns.equals("void")?0:1,returns);
        p.rules.add(new Rule(new Entry(name,annotation,iface,params,returns),region));
      } catch(VmException e) {
        String prefix="entry '"+name+"'";
        String detail=e.getMessage();if(detail.startsWith("control flow target"))detail="jump "+detail;
        throw VmException.badBytecode(detail.startsWith(prefix)?detail:prefix+": "+detail);
      }
    }
    if(!r.done())throw VmException.badBytecode("bytecode has "+(bytecode.length-r.pos)+" trailing byte(s) after the entries");
    return p;
  }
  private static void readRegion(Reader r,Program p,int bytes) {
    int n=r.u16();bounded(n,bytes,"local");
    for(int i=0;i<n;i++){String type=p.ident((int)r.u32(),"local type");int hidden=r.u8();if(hidden>1)throw VmException.badBytecode("local "+i+" hidden flag must be 0 or 1");p.locals.add(new Local(type,hidden==1));}
    p.maxStack=r.u16();long length=r.u32();if(length>Integer.MAX_VALUE)throw VmException.badBytecode("code section is too large");p.code=r.bytes((int)length);
    long lines=r.u32();bounded(lines,bytes,"line table");
    for(long i=0;i<lines;i++){long pc=r.u32(),line=r.u32();int col=r.u16();if(pc>Integer.MAX_VALUE)throw VmException.badBytecode("line table entry points at pc "+pc+", beyond the code");if(!p.lines.isEmpty()&&pc<p.lines.getLast().pc())throw VmException.badBytecode("line table is not sorted by pc at entry "+i);p.lines.add(new Line((int)pc,line,col));}
  }
  public List<Entry> entries(){return rules.stream().map(Rule::entry).toList();}
  public Value call(int entry,Context context,Registry registry,List<Value> args){
    if(entry<0||entry>=rules.size())throw VmException.host("call entry index "+entry+" out of range ("+rules.size()+" entries)");
    Rule rule=rules.get(entry);int expected=rule.entry().params().size();
    if(args.size()!=expected)throw VmException.host("call entry '"+rule.entry().name()+"' expects "+expected+" argument(s); found "+args.size());
    return Execution.execute(rule.region(),context,registry,args,!rule.entry().returns().equals("void"));
  }

  private String ident(int i,String what){if(i<0||i>=consts.size()) throw VmException.badBytecode(what+": constant index "+i+" out of range ("+consts.size()+" constants)"); try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(consts.get(i).bytes())).toString();}catch(CharacterCodingException e){throw new VmException("LOAD_UNBOUND",what+" is not a valid UTF-8 schema identifier");}}
  private static void bounded(long n,int bytes,String what){if(n>bytes)throw VmException.badBytecode(what+" table has too many entries: "+n);}
  private static byte[] sha256(byte[] x){try{return MessageDigest.getInstance("SHA-256").digest(x);}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
  private static String hex(byte[] b){StringBuilder s=new StringBuilder(); for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x)); return s.toString();}
  public int constantCount(){return consts.size();} public int importCount(){return imports.size();} public int localCount(){return locals.size();} public int codeSize(){return code.length;} public int maxStack(){return maxStack;}
  public List<Local> locals(){return List.copyOf(locals);} public List<Line> lines(){return List.copyOf(lines);}
  public Line lineOf(int pc){Line best=null; for(Line l:lines){if(l.pc()>pc)break;best=l;}return best;}
  public void run(Context context, Registry registry){Execution.run(this,context,boundRegistry!=null?boundRegistry:registry);}
}
