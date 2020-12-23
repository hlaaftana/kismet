package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.*
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.parser.StringEscaper
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.SingleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.*

import java.util.regex.Pattern

import static hlaaftana.kismet.call.ExprBuilder.call
import static hlaaftana.kismet.call.ExprBuilder.name
import static hlaaftana.kismet.lib.Functions.func
import static hlaaftana.kismet.lib.Functions.funcc

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class Strings extends LibraryModule {
    static final SingleType STRING_TYPE = new SingleType('String'),
            REGEX_TYPE = new SingleType('Regex')
    TypedContext typed = new TypedContext("strings")
    Context defaultContext = new Context()

    static boolean isAlphaNum(char ch) {
        (ch >= ((char) 'a') && ch <= ((char) 'z')) ||
                (ch >= ((char) 'A') && ch <= ((char) 'Z')) ||
                (ch >= ((char) '0') && ch <= ((char) '9'))
    }

    static boolean isAlphaNum(String string) {
        for (char ch : string.toCharArray()) {
            if (!isAlphaNum(ch)) return false
        }
        true
    }

    Strings() {
        define STRING_TYPE
        define 'string', new GenericType(Functions.FUNCTION_TYPE, TupleType.BASE, STRING_TYPE), func(true) { IKismetObject... a ->
            if (a.length == 1) return a[0].toString()
            StringBuilder x = new StringBuilder()
            for (s in a) x.append(s)
            x.toString()
        }
        define 'call', new GenericType(Functions.FUNCTION_TYPE, new TupleType(STRING_TYPE).withVarargs(Type.ANY), STRING_TYPE), func(true) { IKismetObject... a ->
            if (a.length == 1) return a[0].toString()
            StringBuilder x = new StringBuilder()
            for (s in a) x.append(s)
            x.toString()
        }
        define 'size', func(NumberType.Int32, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetNumber.from(((KismetString) args[0]).size())
            }
        }
        define 'cmp', func(NumberType.Int32, STRING_TYPE, STRING_TYPE), new Function() {
            IKismetObject call(IKismetObject... a) {
                new KInt32(((KismetString) a[0]).compareTo((KismetString) a[1]))
            }
        }
        define 'replace',  funcc { ... args ->
            args[0].toString().replace(args[1].toString(),
                    args.length > 2 ? args[2].toString() : '')
        }
        define 'do_regex', func(REGEX_TYPE, Type.ANY), func(true) { IKismetObject... args -> ~(args[0].toString()) }
        define 'regex', new Template() {
            Expression transform(Parser parser, Expression... args) {
                call(name('do_regex'), args[0] instanceof StringExpression ?
                        new StaticExpression(((StringExpression) args[0]).raw) : args[0])
            }
        }
        define REGEX_TYPE
        define 'replace_all_regex',  func { IKismetObject... args ->
            def replacement = args.length > 2 ?
                    (args[2].inner() instanceof String ? args[2].inner() : ((Function) args[2]).toClosure()) : ''
            def str = args[0].inner().toString()
            def pattern = args[1].inner() instanceof Pattern ? (Pattern) args[1].inner() : args[1].inner().toString()
            str.invokeMethod('replaceAll', [pattern, replacement] as Object[])
        }
        define 'replace_first_regex',  func { IKismetObject... args ->
            def replacement = args.length > 2 ?
                    (args[2].inner() instanceof String ? args[2].inner() : ((Function) args[2]).toClosure()) : ''
            def str = args[0].inner().toString()
            def pattern = args[1].inner() instanceof Pattern ? (Pattern) args[1].inner() : args[1].inner().toString()
            str.invokeMethod('replaceFirst', [pattern, replacement] as Object[])
        }
        define 'blank?',  func(true) { IKismetObject... args -> ((String) args[0].inner() ?: "").isAllWhitespace() }
        define 'whitespace?',  func(true) { IKismetObject... args -> Character.isWhitespace((int) args[0].inner()) }
        define 'alphanumeric?', func(Logic.BOOLEAN_TYPE, NumberType.Char), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(isAlphaNum(((KChar) args[0]).inner))
            }
        }
        define 'alphanumeric?', func(Logic.BOOLEAN_TYPE, STRING_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(isAlphaNum(((KismetString) args[0]).inner()))
            }
        }
        define 'quote_regex',  func(true) { IKismetObject... args -> Pattern.quote((String) args[0].inner()) }
        define 'codepoints~',  func { IKismetObject... args -> ((CharSequence) args[0].inner()).codePoints().iterator() }
        define 'chars~',  func { IKismetObject... args -> ((CharSequence) args[0].inner()).chars().iterator() }
        define 'chars',  func { IKismetObject... args -> ((CharSequence) args[0].inner()).chars.toList() }
        define 'codepoint_to_chars',  funcc { ... args -> Character.toChars((int) args[0]).toList() }
        define 'upper',  funcc(true) { ... args ->
            args[0] instanceof Character ? Character.toUpperCase((char) args[0]) :
                    args[0] instanceof Integer ? Character.toUpperCase((int) args[0]) :
                            ((String) args[0]).toString().toUpperCase()
        }
        define 'lower',  funcc(true) { ... args ->
            args[0] instanceof Character ? Character.toLowerCase((char) args[0]) :
                    args[0] instanceof Integer ? Character.toLowerCase((int) args[0]) :
                            ((String) args[0]).toString().toLowerCase()
        }
        define 'upper?',  funcc(true) { ... args ->
            args[0] instanceof Character ? Character.isUpperCase((char) args[0]) :
                    args[0] instanceof Integer ? Character.isUpperCase((int) args[0]) :
                            ((String) args[0]).chars.every { Character it -> !Character.isLowerCase(it) }
        }
        define 'lower?',  funcc(true) { ... args ->
            args[0] instanceof Character ? Character.isLowerCase((char) args[0]) :
                    args[0] instanceof Integer ? Character.isLowerCase((int) args[0]) :
                            ((String) args[0]).chars.every { char it -> !Character.isUpperCase(it) }
        }
        define 'parse_number',  funcc(true) { ... args ->
            new NumberExpression(args[0].toString()).value
        }
        define 'strip',  funcc(true) { ... args -> ((String) args[0]).trim() }
        define 'strip_start',  funcc(true) { ... args ->
            def x = (String) args[0]
            char[] chars = x.chars
            for (int i = 0; i < chars.length; ++i) {
                if (!Character.isWhitespace(chars[i]))
                    return x.substring(i)
            }
            ''
            /*
            defn [strip_start x] {
              i: 0
              while [and [< i [size x]] [whitespace? x[i]]] [incr i]
            }
            */
        }
        define 'strip_end',  funcc(true) { ... args ->
            def x = (String) args[0]
            char[] chars = x.chars
            for (int i = chars.length - 1; i >= 0; --i) {
                if (!Character.isWhitespace(chars[i]))
                    return x.substring(0, i + 1)
            }
            ''
        }
        define 'sprintf',  funcc { ... args -> String.invokeMethod('format', args) }
        define 'capitalize',  func { IKismetObject... args -> args[0].toString().capitalize() }
        define 'uncapitalize',  func { IKismetObject... args -> args[0].toString().uncapitalize() }
        define 'center',  funcc { ... args ->
            args.length > 2 ? args[0].toString().center(args[1] as Number, args[2].toString()) :
                    args[0].toString().center(args[1] as Number)
        }
        define 'pad_start',  funcc { ... args ->
            args.length > 2 ? args[0].toString().padLeft(args[1] as Number, args[2].toString()) :
                    args[0].toString().padLeft(args[1] as Number)
        }
        define 'pad_end',  funcc { ... args ->
            args.length > 2 ? args[0].toString().padRight(args[1] as Number, args[2].toString()) :
                    args[0].toString().padRight(args[1] as Number)
        }
        define 'escape',  funcc { ... args -> StringEscaper.escape(args[0].toString()) }
        define 'unescape',  funcc { ... args -> StringEscaper.escape(args[0].toString()) }
        define 'lines',  funcc { ... args -> args[0].invokeMethod('readLines', null) }
        define 'denormalize',  funcc { ... args -> args[0].toString().denormalize() }
        define 'normalize',  funcc { ... args -> args[0].toString().normalize() }
    }
}
