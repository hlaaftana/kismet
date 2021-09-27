package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.parser.StringEscaper
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.SingleType
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.*

import java.util.regex.Pattern

import static hlaaftana.kismet.call.ExprBuilder.call
import static hlaaftana.kismet.call.ExprBuilder.name
import static hlaaftana.kismet.lib.Functions.*

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class Strings extends NativeModule {
    static final SingleType STRING_TYPE = new SingleType('String') {
        boolean check(IKismetObject obj) { obj instanceof KismetString }
        boolean checkGenerics(IKismetObject obj, Type... args) { true }
    },
            REGEX_TYPE = new SingleType('Regex')  {
                boolean check(IKismetObject obj) { obj.inner() instanceof Pattern }
                boolean checkGenerics(IKismetObject obj, Type... args) { true }
            }

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
        super("strings")
        define STRING_TYPE
        define '.[]', func(NumberType.Char, STRING_TYPE, NumberType.Int32), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                new KChar(((KismetString) args[0]).charAt(((KismetNumber) args[1]).intValue()))
            }
        }
        define '.[]=', func(NumberType.Char, STRING_TYPE, NumberType.Int32, NumberType.Char), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).setCharAt(((KismetNumber) args[1]).intValue(), ((KChar) args[2]).inner)
                args[2]
            }
        }
        define 'codepoint_at', func(NumberType.Rune, STRING_TYPE, NumberType.Int32), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).codePointAt(((KismetNumber) args[1]).intValue())
            }
        }
        alias 'codepoint_at', 'rune_at'
        define 'add', func(STRING_TYPE, STRING_TYPE, Type.ANY), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).add(args[1].toString())
            }
        }
        define 'insert', func(STRING_TYPE, STRING_TYPE, NumberType.Int32, NumberType.Char), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).insert(((KismetNumber) args[1]).intValue(), ((KChar) args[2]).inner)
            }
        }
        define 'insert', func(STRING_TYPE, STRING_TYPE, NumberType.Int32, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).insert(((KismetNumber) args[1]).intValue(), args[2].toString())
            }
        }
        define 'delete_chars', func(STRING_TYPE, STRING_TYPE, NumberType.Int32, NumberType.Int32), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).delete(((KismetNumber) args[1]).intValue(), ((KismetNumber) args[2]).intValue())
            }
        }
        define 'string', FUNCTION_TYPE.generic(TupleType.BASE, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def result = new KismetString()
                for (final a : args) {
                    result.add(a.toString())
                }
                result
            }
        }
        define 'call', func(STRING_TYPE, STRING_TYPE, new TupleType().withVarargs(Type.ANY)), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def result = new KismetString((KismetString) args[0])
                def tup = (KismetTuple) args[1]
                for (x in tup) {
                    result.add(x.toString())
                }
                result
            }
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
        define 'set_chars', func(STRING_TYPE, STRING_TYPE, NumberType.Int32, NumberType.Int32, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def res = (KismetString) args[0]
                res.inner.replace(((KismetNumber) args[1]).intValue(), ((KismetNumber) args[2]).intValue(), args[3].toString())
                res
            }
        }
        define 'replace', func(STRING_TYPE, STRING_TYPE, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                new KismetString(args[0].toString().replace(args[1].toString(), args[2].toString()))
            }
        }
        define 'replace_first', func(STRING_TYPE, STRING_TYPE, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def res = new KismetString(args[0].toString())
                def pat = args[1].toString()
                def ind = res.indexOf(pat)
                res.inner.replace(ind, ind + pat.length(), args[2].toString())
                res
            }
        }
        define 'replace!', func(STRING_TYPE, STRING_TYPE, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).inner = new StringBuilder(args[0].toString().replace(args[1].toString(), args[2].toString()))
                args[0]
            }
        }
        define 'replace_first!', func(STRING_TYPE, STRING_TYPE, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def res = (KismetString) args[0]
                def pat = args[1].toString()
                def ind = res.indexOf(pat)
                res.inner.replace(ind, ind + pat.length(), args[2].toString())
                res
            }
        }
        define 'replace', func(STRING_TYPE, NumberType.Char, NumberType.Char,), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                new KismetString(args[0].toString().replace(((KChar) args[1]).inner, ((KChar) args[2]).inner))
            }
        }
        define 'replace_first', func(STRING_TYPE, NumberType.Char, NumberType.Char), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def res = new KismetString(args[0].toString())
                def ind = res.indexOf(args[1].toString())
                res.inner.replace(ind, ind + 1, args[2].toString())
                res
            }
        }
        define 'replace!', func(STRING_TYPE, NumberType.Char, NumberType.Char), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).inner = new StringBuilder(args[0].toString().replace(
                    ((KChar) args[1]).inner, ((KChar) args[2]).inner))
                args[0]
            }
        }
        define 'replace_first!', func(STRING_TYPE, NumberType.Char, NumberType.Char), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def res = (KismetString) args[0]
                def pat = args[1].toString()
                def ind = res.indexOf(pat)
                res.inner.replace(ind, ind + 1, args[2].toString())
                res
            }
        }
        define 'replace', func(STRING_TYPE, CollectionsIterators.MAP_TYPE.generic(STRING_TYPE, STRING_TYPE)), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                new KismetString(args[0].toString().replace((Map<CharSequence, CharSequence>) args[1].inner()))
            }
        }
        define 'replace!', func(STRING_TYPE, CollectionsIterators.MAP_TYPE.generic(STRING_TYPE, STRING_TYPE)), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).inner = new StringBuilder(args[0].toString().replace((Map<CharSequence, CharSequence>) args[1].inner()))
                args[0]
            }
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
        define 'replace', func(STRING_TYPE, REGEX_TYPE, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                new KismetString(args[0].toString().replaceAll((Pattern) args[1].inner(), args[2].toString()))
            }
        }
        define 'replace_first', func(STRING_TYPE, REGEX_TYPE, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                new KismetString(args[0].toString().replaceFirst((Pattern) args[1].inner(), args[2].toString()))
            }
        }
        define 'replace!', func(STRING_TYPE, REGEX_TYPE, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).inner = new StringBuilder(
                    args[0].toString().replaceAll((Pattern) args[1].inner(), args[2].toString()))
                args[0]
            }
        }
        define 'replace_first!', func(STRING_TYPE, REGEX_TYPE, STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetString) args[0]).inner = new StringBuilder(
                    args[0].toString().replaceFirst((Pattern) args[1].inner(), args[2].toString()))
                args[0]
            }
        }
        define 'replace', func(STRING_TYPE, REGEX_TYPE, func(STRING_TYPE, CollectionsIterators.LIST_TYPE.generic(STRING_TYPE))), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def f = (Function) args[2]
                new KismetString(args[0].toString().replaceAll((Pattern) args[1].inner()) { List<String> it -> f.call(Kismet.model(it)) })
            }
        }
        define 'replace_first', func(STRING_TYPE, REGEX_TYPE, func(STRING_TYPE, CollectionsIterators.LIST_TYPE.generic(STRING_TYPE))), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def f = (Function) args[2]
                new KismetString(args[0].toString().replaceFirst((Pattern) args[1].inner()) { List<String> it -> f.call(Kismet.model(it)) })
            }
        }
        define 'replace!', func(STRING_TYPE, REGEX_TYPE, func(STRING_TYPE, CollectionsIterators.LIST_TYPE.generic(STRING_TYPE))), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def f = (Function) args[2]
                ((KismetString) args[0]).inner = new StringBuilder(
                    args[0].toString().replaceAll((Pattern) args[1].inner()) { List<String> it -> f.call(Kismet.model(it)) })
                args[0]
            }
        }
        define 'replace_first!', func(STRING_TYPE, REGEX_TYPE, func(STRING_TYPE, CollectionsIterators.LIST_TYPE.generic(STRING_TYPE))), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def f = (Function) args[2]
                ((KismetString) args[0]).inner = new StringBuilder(
                    args[0].toString().replaceFirst((Pattern) args[1].inner()) { List<String> it -> f.call(Kismet.model(it)) })
                args[0]
            }
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
        define 'codepoints~', func(Type.ANY, STRING_TYPE), func { IKismetObject... args -> ((CharSequence) args[0].inner()).codePoints().iterator() }
        define 'chars~',  func { IKismetObject... args -> ((CharSequence) args[0].inner()).chars().iterator() }
        define 'chars',  func { IKismetObject... args -> ((CharSequence) args[0].inner()).chars.toList() }
        define 'codepoint_to_chars', func(CollectionsIterators.LIST_TYPE.generic(NumberType.Char), NumberType.Rune), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                Kismet.model(Character.toChars(((KismetNumber) args[0]).intValue()).toList())
            }
        }
        alias 'codepoint_to_chars', 'rune_to_chars'
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
