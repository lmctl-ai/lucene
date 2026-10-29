package io.lmctl.lmcode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
/** C %.15g semantics: round the exact binary64 value, not Double.toString's shortest decimal. */
public final class Formatting {
 private static final MathContext G15=new MathContext(15,RoundingMode.HALF_EVEN);
 private Formatting(){}
 public static String formatG15(double value){
  if(Double.isNaN(value))return "nan";
  if(Double.isInfinite(value))return value<0?"-inf":"inf";
  if(value==0)return Double.doubleToRawLongBits(value)<0?"-0":"0";
  BigDecimal rounded=new BigDecimal(value).round(G15).stripTrailingZeros();
  int exponent=rounded.precision()-rounded.scale()-1;
  if(exponent>=-4&&exponent<15)return rounded.toPlainString();
  String digits=Integer.toString(Math.abs(exponent));if(digits.length()<2)digits="0"+digits;
  return rounded.movePointLeft(exponent).toPlainString()+"e"+(exponent<0?"-":"+")+digits;
 }
}
