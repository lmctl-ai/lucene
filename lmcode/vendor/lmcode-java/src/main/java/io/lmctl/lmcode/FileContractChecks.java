package io.lmctl.lmcode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Direct file-catalog contract checks, callable by the launcher without JUnit. */
public final class FileContractChecks {
  private FileContractChecks() {}
  public static void main(String[] args) { int n=run(); System.out.println("PASS file contract checks ("+n+")"); }
  public static int run() {
    Registry r=new Registry(false); Stdlib.register(r); Context c=new Context();
    Registry.Binding read=r.resolve("fn file_read(string) -> string");
    Registry.Binding exists=r.resolve("fn file_exists(string) -> bool");
    Path dir;
    try { dir=Files.createTempDirectory("lmcode-file-contract-"); } catch(Exception e){throw new AssertionError(e);}
    try {
      Path file=dir.resolve("raw.bin"); Files.write(file,new byte[]{'a',0,(byte)0xff});
      Value got=read.callback.call(c,Value.none(),new Value[]{Value.ofString(file.toString())});
      byte[] bytes=got.asString().bytes(); require(bytes.length==3&&bytes[0]=='a'&&bytes[1]==0&&(bytes[2]&255)==255,"raw file bytes");
      require(exists.callback.call(c,Value.none(),new Value[]{Value.ofString(file.toString())}).asBool(),"regular file exists");
      require(!exists.callback.call(c,Value.none(),new Value[]{Value.ofString(dir.toString())}).asBool(),"directory is not a file");
      if(!StdFile.isWindows()) {
        byte[] prefix=dir.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] rawPath=Arrays.copyOf(prefix,prefix.length+6);
        byte[] suffix={'/',(byte)0xff,'.','b','i','n'};System.arraycopy(suffix,0,rawPath,prefix.length,suffix.length);
        Path raw=Path.of(java.net.URI.create(StdFile.fileUri(rawPath)));
        require(raw.toUri().getRawPath().contains("%FF"),"raw filename URI remains percent encoded");
        Value pathValue=Value.ofString(new VmString(rawPath));
        if(System.getProperty("os.name").startsWith("Mac")) {
          boolean rejected=false;
          try { r.resolve("fn file_write(string, string) -> void").callback.call(c,Value.none(),new Value[]{pathValue,Value.ofString("data")}); }
          catch(VmException e){rejected=e.code.equals("RUN_HOST")&&e.getMessage().startsWith("file_write: bytes:")&&e.getMessage().contains(": ");}
          require(rejected,"macOS invalid filename reports the OS error instead of replacing bytes");
        } else {
          Files.write(raw,new byte[]{9,0,8});
          Value rawValue=read.callback.call(c,Value.none(),new Value[]{pathValue});
          require(Arrays.equals(rawValue.asString().bytes(),new byte[]{9,0,8}),"invalid UTF-8 filename round trip");
          Files.deleteIfExists(raw);
        }
      } else {
        boolean invalid=false;try{read.callback.call(c,Value.none(),new Value[]{Value.ofString(new VmString(new byte[]{(byte)0xff}))});}
        catch(VmException e){invalid=e.code.equals("RUN_HOST")&&e.getMessage().startsWith("file_read: bytes:ff: ");}
        require(invalid,"Windows rejects malformed UTF-8 paths without replacement decoding");
        require(!exists.callback.call(c,Value.none(),new Value[]{Value.ofString("NUL.txt")}).asBool(),"DOS device aliases are not regular files");
        boolean device=false;try{r.resolve("fn file_write(string, string) -> void").callback.call(c,Value.none(),new Value[]{Value.ofString("NUL.txt"),Value.ofString("data")});}
        catch(VmException e){device=e.code.equals("RUN_HOST")&&e.getMessage().startsWith("file_write: NUL.txt: ");}
        require(device,"DOS device writes are rejected as host file errors");
      }
      boolean nul=false;try{read.callback.call(c,Value.none(),new Value[]{Value.ofString(new VmString(new byte[]{'a',0,(byte)0xff}))});}catch(VmException e){nul=e.code.equals("RUN_HOST")&&e.getMessage().startsWith("file_read: ")&&e.getMessage().indexOf(':',11)>0;}require(nul,"NUL path has stable function:path:reason error");
      return 6;
    } catch(Exception e) { throw new AssertionError(e); }
    finally { try(var paths=Files.walk(dir)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}catch(Exception ignored){} }
  }
  private static void require(boolean ok,String m){if(!ok)throw new AssertionError(m);}
}
