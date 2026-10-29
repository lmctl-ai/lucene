package io.lmctl.lmcode;
import java.nio.file.*;import java.util.*;import java.util.regex.*;
public final class GalleryVerifier {
 private GalleryVerifier(){}
 public static void main(String[] args)throws Exception {
  String readme=Files.readString(Path.of("README.md"));Set<String> images=new HashSet<>();Matcher m=Pattern.compile("docs/images/[a-z_]+\\.png").matcher(readme);
  while(m.find())images.add(m.group());if(images.size()<6)throw new AssertionError("six gallery images required");
  for(String name:images){Path image=Path.of(name);byte[]b=Files.readAllBytes(image);if(b.length>300000||b.length<24||!Arrays.equals(Arrays.copyOf(b,8),HexFormat.of().parseHex("89504e470d0a1a0a")))throw new AssertionError("invalid PNG "+name);String stem=image.getFileName().toString().replace(".png","");Path example=Path.of("examples",stem+".lms");String code=Files.readString(example);if(!code.contains("assert(r.code == 0)")||!readme.contains("lmctl autorun https://raw.githubusercontent.com/lmctl-ai/lmcode-java/main/"+example.toString().replace('\\','/')))throw new AssertionError("gallery caption or execution check missing: "+example);}
  System.out.println("PASS "+images.size()+" gallery images and self-executing captions");
 }
}
