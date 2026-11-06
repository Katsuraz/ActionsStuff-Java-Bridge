package dev.actionsstuff.bridge.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Strict, bounded, read-only subset of Bedrock MoLang's numeric expressions.
 *
 * Supports source animation-time math, trigonometry (degrees), comparisons,
 * boolean operators, ternary choices and several Math.* helpers. It is not a
 * complete MoLang VM: assignment, query engine, arrays and script state are
 * intentionally unsupported. Unknown variables are reported, not invented.
 */
public final class BedrockMolang {
    private BedrockMolang() {}

    public static double evaluate(String source, Map<String, Double> variables) {
        if (source == null || source.length() > 1024) throw new ParseFailure("Expression too long");
        Parser parser = new Parser(source, variables);
        double result = parser.expression();
        parser.whitespace();
        if (parser.cursor != source.length()) throw new ParseFailure("Unexpected token at " + parser.cursor);
        if (!Double.isFinite(result)) throw new ParseFailure("Non-finite expression result");
        return result;
    }

    /** Numeric and MoLang-aware original Bedrock vector sampling. */
    public static float[] sample(JsonElement channel, float time, Map<String,Double> context) {
        if (channel == null) return null;
        if (channel.isJsonArray()) return vector(channel, context);
        if (!channel.isJsonObject()) return null;

        List<Key> keys = new ArrayList<>();
        for (var entry : channel.getAsJsonObject().entrySet()) {
            float at;
            try { at = Float.parseFloat(entry.getKey()); }
            catch (NumberFormatException ex) { continue; }
            if (!Float.isFinite(at) || at < 0 || at > 180) continue;
            JsonElement e = entry.getValue();
            JsonElement pre = e, post = e;
            if (e.isJsonObject()) {
                JsonObject obj = e.getAsJsonObject();
                pre = obj.has("pre") ? obj.get("pre") : obj.get("post");
                post = obj.has("post") ? obj.get("post") : pre;
            }
            if (pre == null || post == null) continue;
            boolean catmull = e.isJsonObject() && e.getAsJsonObject().has("lerp_mode")
                && "catmullrom".equalsIgnoreCase(e.getAsJsonObject().get("lerp_mode").getAsString());
            keys.add(new Key(at,pre,post,catmull));
        }
        if (keys.isEmpty()) return null;
        keys.sort(Comparator.comparingDouble(Key::time));
        if (time <= keys.get(0).time) return vector(keys.get(0).pre,context);
        for (int i = 1; i < keys.size(); i++) {
            Key left = keys.get(i-1), right = keys.get(i);
            if (time <= right.time) {
                float[] a = vector(left.post, context), b = vector(right.pre, context);
                if (a == null || b == null) return null;
                float w = Math.max(0,Math.min(1,(time-left.time)/Math.max(0.000001f,right.time-left.time)));
                if (left.catmull || right.catmull) {
                    float[] p0 = i >= 2 ? vector(keys.get(i-2).post,context) : a;
                    float[] p3 = i+1 < keys.size() ? vector(keys.get(i+1).pre,context) : b;
                    if (p0 != null && p3 != null) {
                        float[] result = new float[3];
                        float w2=w*w,w3=w2*w;
                        for(int axis=0;axis<3;axis++) {
                            float curve = 0.5f*(2f*a[axis]+(-p0[axis]+b[axis])*w
                                +(2f*p0[axis]-5f*a[axis]+4f*b[axis]-p3[axis])*w2
                                +(-p0[axis]+3f*a[axis]-3f*b[axis]+p3[axis])*w3);
                            if (!Float.isFinite(curve)) return null;
                            result[axis]=Math.max(-10000f,Math.min(10000f,curve));
                        }
                        return result;
                    }
                }
                return new float[] {a[0]+(b[0]-a[0])*w, a[1]+(b[1]-a[1])*w, a[2]+(b[2]-a[2])*w};
            }
        }
        return vector(keys.get(keys.size()-1).post,context);
    }

    private static float[] vector(JsonElement json, Map<String,Double> context) {
        if (!json.isJsonArray()) return null;
        JsonArray arr = json.getAsJsonArray();
        if (arr.size() != 3) return null;
        float[] result = new float[3];
        for (int i=0;i<3;i++) {
            JsonElement v = arr.get(i);
            try {
                double num = v.getAsJsonPrimitive().isNumber() ? v.getAsDouble() : evaluate(v.getAsString(),context);
                if (!Double.isFinite(num) || Math.abs(num)>10000) return null;
                result[i]=(float)num;
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return result;
    }

    private record Key(float time, JsonElement pre, JsonElement post, boolean catmull) { }
    public static final class ParseFailure extends RuntimeException {
        public ParseFailure(String message) { super(message); }
    }
    private static final class Parser {
        final String s;
        final Map<String,Double> vars;
        int cursor, operations;

        Parser(String src,Map<String,Double> variables) {
            this.s=src;
            this.vars=variables;
        }
        void whitespace() {while(cursor<s.length()&&Character.isWhitespace(s.charAt(cursor)))cursor++;}
        boolean eat(String token) {
            whitespace();
            if(s.startsWith(token,cursor)){cursor+=token.length();return true;}
            return false;
        }
        char peek() {whitespace();return cursor<s.length()?s.charAt(cursor):'\0';}
        void step() {if(++operations>700)throw new ParseFailure("Expression complexity limit");}

        double expression() {
            double value = or();
            if(eat("?")) {
                double yes=expression();
                if(!eat(":")) throw new ParseFailure("Expected ':'");
                double no=expression();
                return value!=0?yes:no;
            }
            return value;
        }
        double or() {
            double a=and();
            while(eat("||")){step();double b=and();a=(a!=0||b!=0)?1:0;}
            return a;
        }
        double and() {
            double a=comparison();
            while(eat("&&")){step();double b=comparison();a=(a!=0&&b!=0)?1:0;}
            return a;
        }
        double comparison() {
            double a=add();
            while(true) {
                step();
                if(eat("==")){double b=add();a=a==b?1:0;}
                else if(eat("!=")){double b=add();a=a!=b?1:0;}
                else if(eat(">=")){double b=add();a=a>=b?1:0;}
                else if(eat("<=")){double b=add();a=a<=b?1:0;}
                else if(eat(">")){double b=add();a=a>b?1:0;}
                else if(eat("<")){double b=add();a=a<b?1:0;}
                else return a;
            }
        }
        double add() {
            double a=mul();
            while(true) {
                step();
                if(eat("+"))a+=mul();
                else if(eat("-"))a-=mul();
                else return a;
            }
        }
        double mul() {
            double a=unary();
            while(true) {
                step();
                if(eat("*"))a*=unary();
                else if(eat("/")){double b=unary();if(b==0)throw new ParseFailure("Division by zero");a/=b;}
                else if(eat("%")){double b=unary();if(b==0)throw new ParseFailure("Modulo by zero");a%=b;}
                else return a;
            }
        }
        double unary() {
            step();
            if(eat("+"))return unary();
            if(eat("-"))return -unary();
            if(eat("!"))return unary()==0?1:0;
            return atom();
        }
        double atom() {
            step();
            if(eat("(")){
                double x=expression();
                if(!eat(")"))throw new ParseFailure("Expected ')'");
                return x;
            }
            char c=peek();
            if(Character.isDigit(c)||c=='.') {
                int from=cursor;
                while(cursor<s.length()&&
                    (Character.isDigit(s.charAt(cursor))||s.charAt(cursor)=='.'))cursor++;
                if(cursor<s.length()&&(s.charAt(cursor)=='e'||s.charAt(cursor)=='E')) {
                    cursor++;
                    if(cursor<s.length()&&(s.charAt(cursor)=='+'||s.charAt(cursor)=='-'))cursor++;
                    while(cursor<s.length()&&Character.isDigit(s.charAt(cursor)))cursor++;
                }
                try{return Double.parseDouble(s.substring(from,cursor));}
                catch(NumberFormatException ex){throw new ParseFailure("Bad numeric literal");}
            }
            if(!Character.isLetter(c)&&c!='_')throw new ParseFailure("Expected number or name at "+cursor);
            int from=cursor;
            while(cursor<s.length()) {
                char ch=s.charAt(cursor);
                if(Character.isLetterOrDigit(ch)||ch=='_'||ch=='.')cursor++;
                else break;
            }
            String name=s.substring(from,cursor).toLowerCase(Locale.ROOT);
            if(eat("(")){
                List<Double> args=new ArrayList<>();
                if(!eat(")")) {
                    do { if(args.size()>12)throw new ParseFailure("Too many function arguments");args.add(expression());}
                    while(eat(","));
                    if(!eat(")"))throw new ParseFailure("Expected ')' after function");
                }
                return call(name,args);
            }
            if(name.equals("math.pi")||name.equals("pi"))return Math.PI;
            if(name.equals("true"))return 1;
            if(name.equals("false"))return 0;
            Double value=vars.get(name);
            if(value==null) {
                // Aliases used by the original Bedrock animation format.
                if(name.equals("query.anim_time")||name.equals("q.anim_time"))
                    value=vars.get("q.anim_time");
                else if(name.equals("query.life_time")||name.equals("q.life_time"))
                    value=vars.get("q.life_time");
            }
            if(value==null)throw new ParseFailure("Unsupported variable: "+name);
            return value;
        }

        static void arity(String name,List<Double> a,int n) {
            if(a.size()!=n)throw new ParseFailure(name+" expects "+n+" argument(s)");
        }
        double call(String name,List<Double> a) {
            step();
            String n=name.startsWith("math.")?name.substring(5):name;
            switch(n){
                case "sin": arity(n,a,1);return Math.sin(Math.toRadians(a.get(0)));
                case "cos": arity(n,a,1);return Math.cos(Math.toRadians(a.get(0)));
                case "tan": arity(n,a,1);return Math.tan(Math.toRadians(a.get(0)));
                case "asin":arity(n,a,1);return Math.toDegrees(Math.asin(a.get(0)));
                case "acos":arity(n,a,1);return Math.toDegrees(Math.acos(a.get(0)));
                case "atan":arity(n,a,1);return Math.toDegrees(Math.atan(a.get(0)));
                case "abs": arity(n,a,1);return Math.abs(a.get(0));
                case "sqrt":arity(n,a,1);return Math.sqrt(a.get(0));
                case "floor":arity(n,a,1);return Math.floor(a.get(0));
                case "ceil":arity(n,a,1);return Math.ceil(a.get(0));
                case "round":arity(n,a,1);return Math.round(a.get(0));
                case "trunc":arity(n,a,1);return a.get(0)<0?Math.ceil(a.get(0)):Math.floor(a.get(0));
                case "sign":arity(n,a,1);return Math.signum(a.get(0));
                case "min":arity(n,a,2);return Math.min(a.get(0),a.get(1));
                case "max":arity(n,a,2);return Math.max(a.get(0),a.get(1));
                case "pow":arity(n,a,2);return Math.pow(a.get(0),a.get(1));
                case "lerp":arity(n,a,3);return a.get(0)+(a.get(1)-a.get(0))*a.get(2);
                case "clamp":arity(n,a,3);return Math.max(a.get(1),Math.min(a.get(2),a.get(0)));
                case "mod":arity(n,a,2);if(a.get(1)==0)throw new ParseFailure("Modulo by zero");return a.get(0)%a.get(1);
                default:throw new ParseFailure("Unsupported MoLang function: "+name);
            }
        }
    }
}
