package io.lmctl.lmcode;
import java.util.HashMap;
import java.util.Map;
/** Host objects survive a run; standard collection arenas do not. */
public final class Context {
 private final Map<String,Value> values=new HashMap<>(); private final Map<String,Object> state=new HashMap<>(); private long used;
 public long instructionLimit=10_000_000L; public long memoryLimitBytes=4096L*1024*1024;
 public Context set(String name,Value value){values.put(name,value);return this;} public Context setObject(String name,String type,Object value){return set(name,Value.object(type,value));}
 public Value get(String name){return values.get(name);} public Map<String,Object> state(){return state;}
 public void resetBudget(){used=0;} public void charge(long bytes){if(bytes<0||used>memoryLimitBytes-bytes)throw VmException.run("memory budget exceeded");used+=bytes;}
}
