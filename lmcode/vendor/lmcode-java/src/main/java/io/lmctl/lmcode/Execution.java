package io.lmctl.lmcode;

import java.util.*;

/** Allocation-free dispatch layer for the bytecode VM (apart from its value stack). */
public final class Execution {
  private Execution() {}

  public static void run(Program p, Context ctx, Registry registry) { execute(p,ctx,registry,List.of(),false); }

  static Value execute(Program p, Context ctx, Registry registry,List<Value> arguments,boolean captureResult) {
    Registry owner = p.boundRegistry != null ? p.boundRegistry : registry;
    owner.enterRun(ctx);
    ctx.resetBudget();
    try {
      ArrayList<Value> stack = new ArrayList<>(Math.min(p.maxStack,256));
      ctx.charge((long)p.locals.size()*16+Math.min(p.maxStack,256)*8L);
      Value[] locals = new Value[p.locals.size()];
      for(int i=0;i<arguments.size();i++)locals[i]=arguments.get(i);
      Reader r = new Reader(p.code);
      long instructions = 0;
      while (!r.done()) {
        int fault = r.pos;
        try {
          if (instructions >= ctx.instructionLimit) throw error("instruction budget exceeded");
          instructions++;
          // Account for the VM value cell allocated by each dispatched instruction.
          ctx.charge(16);
          Op op = Op.of(r.u8());
          if (op == null) throw error("unknown opcode " + (p.code[fault] & 255));
          switch (op) {
            case PUSH_INT -> stack.add(Value.ofInt(r.i64()));
            case PUSH_FLOAT -> stack.add(Value.ofFloat(r.f64()));
            case PUSH_STR -> { VmString s=p.consts.get((int)r.u32()); ctx.charge(s.length()); stack.add(Value.ofString(s)); }
            case PUSH_BOOL -> stack.add(Value.ofBool(r.u8() != 0));
            case LOAD_LOCAL -> { int s=r.u16(); Value v=locals[s]; if(v==null||v.kind==Value.Kind.NONE) throw error("local slot "+s+" read before assignment"); stack.add(v); }
            case STORE_LOCAL -> locals[r.u16()]=pop(stack);
            case LOAD_CTX -> { String n=p.contexts.get(r.u16()); Value v=ctx.get(n); if(v==null||v.kind==Value.Kind.NONE) throw error("context variable '"+n+"' was not provided by the host for this run"); stack.add(v); }
            case CALL_FN -> { Registry.Binding b=p.imports.get(r.u16()); Value[] a=args(stack,r.u8()); Value v=b.callback.call(ctx,Value.none(),a); if(v!=null&&v.kind!=Value.Kind.NONE){if(v.kind==Value.Kind.STR)ctx.charge(v.asString().length());stack.add(v);} }
            case CALL_METHOD -> { Registry.Binding b=p.imports.get(r.u16()); Value[] a=args(stack,r.u8()); Value self=pop(stack); if(self.kind!=Value.Kind.OBJ || !self.objectType().equals(b.type)) throw error("method "+b.type+"."+b.name+" called on a "+(self.kind==Value.Kind.OBJ?self.objectType():self.kindName())); Value v=b.callback.call(ctx,self,a); if(v!=null&&v.kind!=Value.Kind.NONE){if(v.kind==Value.Kind.STR)ctx.charge(v.asString().length());stack.add(v);} }
            case EQ, NE -> { Value b=pop(stack), a=pop(stack); boolean eq=equal(a,b); stack.add(Value.ofBool(op==Op.EQ?eq:!eq)); }
            case NOT -> stack.add(Value.ofBool(!unary(stack,Value.Kind.BOOL,"! applied to a").asBool()));
            case JUMP -> jump(r,r.i32(),p.code.length);
            case JUMP_IF_FALSE -> { int d=r.i32(); if(!condition(stack)) jump(r,d,p.code.length); }
            case JUMP_IF_TRUE -> { int d=r.i32(); if(condition(stack)) jump(r,d,p.code.length); }
            case DUP -> { if(stack.isEmpty()) throw error("stack underflow"); stack.add(stack.get(stack.size()-1)); }
            case POP -> pop(stack);
            case HALT -> { return captureResult?pop(stack):Value.none(); }
            case ADD_I -> binI(stack,(a,b)->a+b); case SUB_I -> binI(stack,(a,b)->a-b); case MUL_I -> binI(stack,(a,b)->a*b);
            case DIV_I -> binI(stack,Execution::div); case MOD_I -> binI(stack,Execution::mod);
            case ADD_F -> binF(stack,(a,b)->a+b); case SUB_F -> binF(stack,(a,b)->a-b); case MUL_F -> binF(stack,(a,b)->a*b); case DIV_F -> binF(stack,(a,b)->a/b);
            case NEG_I -> stack.add(Value.ofInt(-unary(stack,Value.Kind.INT,"int negation applied to").asInt()));
            case NEG_F -> stack.add(Value.ofFloat(-unary(stack,Value.Kind.FLOAT,"float negation applied to").asFloat()));
            case I2F -> stack.add(Value.ofFloat(unary(stack,Value.Kind.INT,"float() applied to").asInt()));
            case LT_I -> cmpI(stack,(a,b)->a<b); case LE_I -> cmpI(stack,(a,b)->a<=b); case GT_I -> cmpI(stack,(a,b)->a>b); case GE_I -> cmpI(stack,(a,b)->a>=b);
            case LT_F -> cmpF(stack,(a,b)->a<b); case LE_F -> cmpF(stack,(a,b)->a<=b); case GT_F -> cmpF(stack,(a,b)->a>b); case GE_F -> cmpF(stack,(a,b)->a>=b);
            case CONCAT -> { Value b=pop(stack),a=pop(stack); require(a,Value.Kind.STR,"string + applied to "+a.kindName()+" and "+b.kindName()); require(b,Value.Kind.STR,"string + applied to "+a.kindName()+" and "+b.kindName()); ctx.charge((long)a.asString().length()+b.asString().length());VmString s=VmString.concat(a.asString(),b.asString());stack.add(Value.ofString(s)); }
            case I2S -> { VmString s=VmString.fromString(Long.toString(unary(stack,Value.Kind.INT,"str(int) applied to").asInt()));ctx.charge(s.length());stack.add(Value.ofString(s)); }
            case F2S -> { VmString s=VmString.fromString(Formatting.formatG15(unary(stack,Value.Kind.FLOAT,"str(float) applied to").asFloat()));ctx.charge(s.length());stack.add(Value.ofString(s)); }
            case B2S -> { VmString s=VmString.fromString(unary(stack,Value.Kind.BOOL,"str(bool) applied to").asBool()?"true":"false");ctx.charge(s.length());stack.add(Value.ofString(s)); }
            case MATH -> math(stack,r.u8());
            case FOR_RANGE -> { int c=r.u16(),e=r.u16(),d=r.i32(); long end=pop(stack).asInt(),start=pop(stack).asInt(); locals[c]=Value.ofInt(start); locals[e]=Value.ofInt(end); if(start>=end) jump(r,d,p.code.length); }
            case FOR_NEXT -> { r.u16(); int d=r.i32(); Program.Loop l=p.loops.get(fault); if(l==null) throw error("missing loop metadata"); long i=locals[l.counter()].asInt()+1; locals[l.counter()]=Value.ofInt(i); if(i<locals[l.end()].asInt()) jump(r,d,p.code.length); }
            case WHILE_BEGIN -> {}
            case WHILE_TEST -> { int d=r.i32(); if(!pop(stack).asBool()) jump(r,d,p.code.length); }
            case LOOP_BACK -> jump(r,r.i32(),p.code.length);
          }
        } catch (VmException e) { Program.Line line=p.lineOf(fault); if(line!=null)e.at(line); throw e; }
      }
      return Value.none();
    } finally { owner.exitRun(ctx); }
  }

  private static VmException error(String s){return new VmException("RUN_ERROR",s);}
  private static Value pop(List<Value>s){if(s.isEmpty())throw error("stack underflow");return s.remove(s.size()-1);}
  private static void require(Value v,Value.Kind k,String m){if(v.kind!=k)throw error(m);}
  private static Value unary(List<Value>s,Value.Kind k,String m){Value v=pop(s);if(v.kind!=k)throw error(m+" "+v.kindName());return v;}
  private static boolean condition(List<Value>s){Value v=pop(s);if(v.kind!=Value.Kind.BOOL)throw error("condition is a "+v.kindName()+", not a bool");return v.asBool();}
  private static Value[] args(List<Value>s,int n){Value[]a=new Value[n];for(int i=n-1;i>=0;i--)a[i]=pop(s);return a;}
  private static boolean equal(Value a,Value b){if(a.kind!=b.kind)throw error("cannot compare "+a.kindName()+" with "+b.kindName());return switch(a.kind){case NONE->true;case INT->a.asInt()==b.asInt();case FLOAT->a.asFloat()==b.asFloat();case STR->a.asString().equals(b.asString());case BOOL->a.asBool()==b.asBool();case OBJ->a.asObject()==b.asObject();};}
  private static long div(long a,long b){if(b==0)throw error("int division by zero");return a==Long.MIN_VALUE&&b==-1?Long.MIN_VALUE:a/b;}
  private static long mod(long a,long b){if(b==0)throw error("int modulo by zero");return a==Long.MIN_VALUE&&b==-1?0:a%b;}
  private interface IL{long f(long a,long b);} private interface FL{double f(double a,double b);} private interface BL{boolean f(long a,long b);} private interface BF{boolean f(double a,double b);}
  private static Value[] pair(List<Value>s,Value.Kind k,String label){Value b=pop(s),a=pop(s);if(a.kind!=k||b.kind!=k)throw error(label+" operator applied to "+a.kindName()+" and "+b.kindName());return new Value[]{a,b};}
  private static void binI(List<Value>s,IL f){Value[]v=pair(s,Value.Kind.INT,"int");s.add(Value.ofInt(f.f(v[0].asInt(),v[1].asInt())));}
  private static void binF(List<Value>s,FL f){Value[]v=pair(s,Value.Kind.FLOAT,"float");s.add(Value.ofFloat(f.f(v[0].asFloat(),v[1].asFloat())));}
  private static void cmpI(List<Value>s,BL f){Value[]v=pair(s,Value.Kind.INT,"int");s.add(Value.ofBool(f.f(v[0].asInt(),v[1].asInt())));}
  private static void cmpF(List<Value>s,BF f){Value[]v=pair(s,Value.Kind.FLOAT,"float");s.add(Value.ofBool(f.f(v[0].asFloat(),v[1].asFloat())));}
  private static void jump(Reader r,int d,int n){long t=(long)r.pos+d;if(t<0||t>n)throw error("jump target is outside code");r.pos=(int)t;}
  private static double min(double a,double b){return Double.isNaN(a)?a:Double.isNaN(b)?b:a<b?a:b<a?b:a;} private static double max(double a,double b){return Double.isNaN(a)?a:Double.isNaN(b)?b:a>b?a:b>a?b:a;}
  private static void math(List<Value>s,int o){if(o>=14){long r=unary(s,Value.Kind.INT,"math applied to").asInt();if(o==14){s.add(Value.ofInt(r<0?-r:r));return;}long l=unary(s,Value.Kind.INT,"math applied to").asInt();if(o==15){s.add(Value.ofInt(Math.min(l,r)));return;}if(o==16){s.add(Value.ofInt(Math.max(l,r)));return;}long v=unary(s,Value.Kind.INT,"math applied to").asInt();s.add(Value.ofInt(Math.min(Math.max(v,l),r)));return;}double x=unary(s,Value.Kind.FLOAT,"math applied to").asFloat();switch(o){case 0->s.add(Value.ofFloat(Math.sin(x)));case 1->s.add(Value.ofFloat(Math.cos(x)));case 2->s.add(Value.ofFloat(Math.sqrt(x)));case 3->s.add(Value.ofFloat(Math.abs(x)));case 4->s.add(Value.ofFloat(Math.floor(x)));case 5->s.add(Value.ofFloat(Math.pow(unary(s,Value.Kind.FLOAT,"math applied to").asFloat(),x)));case 6->s.add(Value.ofFloat(min(unary(s,Value.Kind.FLOAT,"math applied to").asFloat(),x)));case 7->s.add(Value.ofFloat(max(unary(s,Value.Kind.FLOAT,"math applied to").asFloat(),x)));case 8->{double lo=unary(s,Value.Kind.FLOAT,"math applied to").asFloat(),v=unary(s,Value.Kind.FLOAT,"math applied to").asFloat();s.add(Value.ofFloat(min(max(v,lo),x)));}case 9->{double b=unary(s,Value.Kind.FLOAT,"math applied to").asFloat(),a=unary(s,Value.Kind.FLOAT,"math applied to").asFloat();s.add(Value.ofFloat(a+(b-a)*x));}case 10->{x=min(max(x,0),1);s.add(Value.ofFloat(x*x*(3-2*x)));}case 11->{x=min(max(x,0),1);s.add(Value.ofFloat(x*x));}case 12->{x=min(max(x,0),1);double d=1-x;s.add(Value.ofFloat(1-d*d));}case 13->{double z=0x1p63;s.add(Value.ofInt(Double.isNaN(x)?0:x>=z?Long.MAX_VALUE:x<=-z?Long.MIN_VALUE:(long)x));}default->throw error("unsupported math function");}}
}
