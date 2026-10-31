package it.ampy.ampyrocket;

import java.math.BigDecimal;
import java.math.MathContext;

/** Tiny calculator for the search bar: + - * / ^ % ( ) and decimals. */
public final class MathEval {
    private final String s;private int i=0;
    private MathEval(String s){this.s=s;}

    public static String tryEval(String in){
        if(in==null)return null;String t=in.replace(" ","").replace(',','.').replace('x','*').replace('×','*').replace('÷','/');
        if(t.length()<3||!t.matches("[0-9.+\\-*/^%()]+")||!t.matches(".*\\d.*[+\\-*/^%].*\\d.*"))return null;
        try{MathEval m=new MathEval(t);double v=m.expr();if(m.i!=t.length()||Double.isNaN(v)||Double.isInfinite(v))return null;
            return new BigDecimal(v).round(new MathContext(12)).stripTrailingZeros().toPlainString();}
        catch(Exception e){return null;}
    }
    private double expr(){double v=term();while(i<s.length()){char c=s.charAt(i);if(c=='+'){i++;v+=term();}else if(c=='-'){i++;v-=term();}else break;}return v;}
    private double term(){double v=power();while(i<s.length()){char c=s.charAt(i);
        if(c=='*'){i++;v*=power();}else if(c=='/'){i++;v/=power();}else if(c=='%'){i++;v=v/100.0*(i<s.length()&&Character.isDigit(s.charAt(i))?power():1);}else break;}return v;}
    private double power(){double b=unary();if(i<s.length()&&s.charAt(i)=='^'){i++;return Math.pow(b,power());}return b;}
    private double unary(){if(i<s.length()&&s.charAt(i)=='-'){i++;return -unary();}if(i<s.length()&&s.charAt(i)=='+'){i++;return unary();}return atom();}
    private double atom(){
        if(i<s.length()&&s.charAt(i)=='('){i++;double v=expr();if(i>=s.length()||s.charAt(i)!=')')throw new IllegalStateException();i++;return v;}
        int st=i;while(i<s.length()&&(Character.isDigit(s.charAt(i))||s.charAt(i)=='.'))i++;
        if(st==i)throw new IllegalStateException();return Double.parseDouble(s.substring(st,i));
    }
}
