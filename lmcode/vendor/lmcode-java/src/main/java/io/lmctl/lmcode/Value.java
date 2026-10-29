package io.lmctl.lmcode;
import java.util.Objects;
/** A tagged VM value. Objects are host-owned Java references. */
public final class Value {
 public enum Kind { NONE,INT,FLOAT,STR,BOOL,OBJ }
 public final Kind kind; private final long integer;private final double number;private final VmString text;private final Object object;private final String type;
 private static final Value NONE=new Value(Kind.NONE,0,0,null,null,"");
 private Value(Kind k,long i,double d,VmString s,Object o,String t) {kind=k;integer=i;number=d;text=s;object=o;type=t;}
 public static Value none(){return NONE;} public static Value ofInt(long n){return new Value(Kind.INT,n,0,null,null,"");}
 public static Value ofFloat(double n){return new Value(Kind.FLOAT,0,n,null,null,"");} public static Value ofBool(boolean b){return new Value(Kind.BOOL,b?1:0,0,null,null,"");}
 public static Value ofString(VmString s){return new Value(Kind.STR,0,0,Objects.requireNonNull(s),null,"");} public static Value ofString(String s){return ofString(VmString.fromString(s));}
 public static Value object(String type,Object object){return new Value(Kind.OBJ,0,0,null,object,Objects.requireNonNull(type));}
 public Kind kind(){return kind;} public String objectType(){return type;} public String kindName(){return switch(kind){case INT->"int";case FLOAT->"float";case STR->"string";case BOOL->"bool";case OBJ->"object";default->"void";};}
 private VmException expected(String t){return VmException.run("expected "+t+", got "+kindName());}
 public long asInt(){if(kind!=Kind.INT)throw expected("int");return integer;} public double asFloat(){if(kind!=Kind.FLOAT)throw expected("float");return number;}
 public boolean asBool(){if(kind!=Kind.BOOL)throw expected("bool");return integer!=0;} public VmString asString(){if(kind!=Kind.STR)throw expected("string");return text;}
 public Object asObject(){if(kind!=Kind.OBJ)throw expected("object");return object;}
}
