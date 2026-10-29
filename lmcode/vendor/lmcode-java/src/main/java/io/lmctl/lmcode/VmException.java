package io.lmctl.lmcode;
/** Stable load/run code and optional bytecode source location. */
public final class VmException extends RuntimeException {
 private static final long serialVersionUID=1L;
 public final String code; public Long line; public Integer column; private String detail;
 public VmException(String code,String message){super(message);this.code=code;detail=message;}
 public static VmException badBytecode(String message){return new VmException("LOAD_BAD_BYTECODE",message);} public static VmException run(String message){return new VmException("RUN_ERROR",message);} public static VmException host(String message){return new VmException("RUN_HOST",message);}
 public VmException at(Program.Line location){if(location!=null&&line==null&&location.number()!=0){line=location.number();column=location.column()==0?null:location.column();detail="dsl line "+line+(column==null?"":":"+column)+": "+detail;}return this;}
 @Override public String getMessage(){return detail;}
}
