package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.*
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.exceptions.UnexpectedValueException
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.GenericType
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.UnionType
import hlaaftana.kismet.vm.*

import java.math.RoundingMode

import static hlaaftana.kismet.call.ExprBuilder.*
import static hlaaftana.kismet.lib.Functions.func
import static hlaaftana.kismet.lib.Functions.funcc
import static hlaaftana.kismet.lib.Functions.TEMPLATE_TYPE

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class Numbers extends LibraryModule {
    private static Map<String, RoundingMode> roundingModes = [
            '^': RoundingMode.CEILING, 'v': RoundingMode.FLOOR,
            '^0': RoundingMode.UP, 'v0': RoundingMode.DOWN,
            '/^': RoundingMode.HALF_UP, '/v': RoundingMode.HALF_DOWN,
            '/2': RoundingMode.HALF_EVEN, '!': RoundingMode.UNNECESSARY
    ].asImmutable()
    TypedContext typed = new TypedContext("numbers")
    Context defaultContext = new Context()

    Numbers() {
        for (final n : NumberType.values())
            define n.name(), new GenericType(Types.META_TYPE, n), n
        define 'percent',  funcc(true) { ... a -> a[0].invokeMethod 'div', 100 }
        define 'to_percent',  funcc(true) { ... a -> a[0].invokeMethod 'multiply', 100 }
        define 'strip_trailing_zeros', func(NumberType.Float, NumberType.Float), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                new KFloat(((KFloat) args[0]).inner.stripTrailingZeros())
            }
        }
        define 'e', NumberType.Float, new KFloat(BigDecimal.valueOf(Math.E))
        define 'pi', NumberType.Float, new KFloat(BigDecimal.valueOf(Math.PI))
        define 'bit_not', func(NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KInt(((KInt) args[0]).inner.not())
            }
        }
        define 'bit_not', func(NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KInt64(((KInt64) args[0]).inner.bitwiseNegate().longValue())
            }
        }
        define 'bit_not', func(NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KInt32(((KInt32) args[0]).inner.bitwiseNegate().intValue())
            }
        }
        define 'bit_not', func(NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KInt16(((KInt16) args[0]).inner.bitwiseNegate().shortValue())
            }
        }
        define 'bit_not', func(NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KInt8(((KInt8) args[0]).inner.bitwiseNegate().byteValue())
            }
        }
        define 'bit_xor', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt) args[0]).xor((KInt) args[1])
            }
        }
        define 'bit_xor', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).xor((KInt64) args[1])
            }
        }
        define 'bit_xor', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).xor((KInt32) args[1])
            }
        }
        define 'bit_xor', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).xor((KInt16) args[1])
            }
        }
        define 'bit_xor', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).xor((KInt8) args[1])
            }
        }
        define 'bit_and', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt) args[0]).and((KInt) args[1])
            }
        }
        define 'bit_and', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).and((KInt64) args[1])
            }
        }
        define 'bit_and', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).and((KInt32) args[1])
            }
        }
        define 'bit_and', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).and((KInt16) args[1])
            }
        }
        define 'bit_and', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).and((KInt8) args[1])
            }
        }
        define 'bit_or', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt) args[0]).or((KInt) args[1])
            }
        }
        define 'bit_or', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).or((KInt64) args[1])
            }
        }
        define 'bit_or', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).or((KInt32) args[1])
            }
        }
        define 'bit_or', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).or((KInt16) args[1])
            }
        }
        define 'bit_or', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).or((KInt8) args[1])
            }
        }
        define 'left_shift', func(NumberType.Int, NumberType.Int, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KInt(((KInt) args[0]).inner.shiftLeft(((KInt32) args[1]).inner))
            }
        }
        define 'left_shift', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).leftShift((KInt64) args[1])
            }
        }
        define 'left_shift', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).leftShift((KInt32) args[1])
            }
        }
        define 'left_shift', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).leftShift((KInt16) args[1])
            }
        }
        define 'left_shift', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).leftShift((KInt8) args[1])
            }
        }
        define 'right_shift', func(NumberType.Int, NumberType.Int, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KInt(((KInt) args[0]).inner.shiftRight(((KInt32) args[1]).inner))
            }
        }
        define 'right_shift', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).rightShift((KInt64) args[1])
            }
        }
        define 'right_shift', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).rightShift((KInt32) args[1])
            }
        }
        define 'right_shift', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).rightShift((KInt16) args[1])
            }
        }
        define 'right_shift', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).rightShift((KInt8) args[1])
            }
        }
        define 'unsigned_right_shift', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).rightShiftUnsigned((KInt64) args[1])
            }
        }
        define 'unsigned_right_shift', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).rightShiftUnsigned((KInt32) args[1])
            }
        }
        define 'unsigned_right_shift', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).rightShiftUnsigned((KInt16) args[1])
            }
        }
        define 'unsigned_right_shift', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).rightShiftUnsigned((KInt8) args[1])
            }
        }
        define '<', func(Logic.BOOLEAN_TYPE, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).compareTo((KismetNumber) args[1]) < 0)
            }
        }
        define '>', func(Logic.BOOLEAN_TYPE, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).compareTo((KismetNumber) args[1]) > 0)
            }
        }
        define '<=', func(Logic.BOOLEAN_TYPE, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).compareTo((KismetNumber) args[1]) <= 0)
            }
        }
        define '>=', func(Logic.BOOLEAN_TYPE, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).compareTo((KismetNumber) args[1]) >= 0)
            }
        }
        define 'cmp', func(NumberType.Int32, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... a) {
                new KInt32(((KismetNumber) a[0]).compareTo((KismetNumber) a[1]))
            }
        }
        define 'positive', func(NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KismetNumber) args[0]).unaryPlus()
            }
        }
        define 'positive', func(NumberType.Float, NumberType.Float), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat) args[0]).unaryPlus()
            }
        }
        define 'positive', func(NumberType.Float64, NumberType.Float64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat64) args[0]).unaryPlus()
            }
        }
        define 'positive', func(NumberType.Float32, NumberType.Float32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat32) args[0]).unaryPlus()
            }
        }
        define 'positive', func(NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt) args[0]).unaryPlus()
            }
        }
        define 'positive', func(NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).unaryPlus()
            }
        }
        define 'positive', func(NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).unaryPlus()
            }
        }
        define 'positive', func(NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).unaryPlus()
            }
        }
        define 'positive', func(NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).unaryPlus()
            }
        }
        define 'negative', func(NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KismetNumber) args[0]).unaryMinus()
            }
        }
        define 'negative', func(NumberType.Float, NumberType.Float), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat) args[0]).unaryMinus()
            }
        }
        define 'negative', func(NumberType.Float64, NumberType.Float64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat64) args[0]).unaryMinus()
            }
        }
        define 'negative', func(NumberType.Float32, NumberType.Float32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat32) args[0]).unaryMinus()
            }
        }
        define 'negative', func(NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt) args[0]).unaryMinus()
            }
        }
        define 'negative', func(NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).unaryMinus()
            }
        }
        define 'negative', func(NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).unaryMinus()
            }
        }
        define 'negative', func(NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).unaryMinus()
            }
        }
        define 'negative', func(NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).unaryMinus()
            }
        }
        define 'positive?', func(Logic.BOOLEAN_TYPE, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).compareTo(KInt32.ZERO) > 0)
            }
        }
        define 'negative?', func(Logic.BOOLEAN_TYPE, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).compareTo(KInt32.ZERO) < 0)
            }
        }
        define 'zero?', func(Logic.BOOLEAN_TYPE, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).compareTo(KInt32.ZERO) == 0)
            }
        }
        define 'one?', func(Logic.BOOLEAN_TYPE, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).compareTo(KInt32.ONE) == 0)
            }
        }
        define 'even?', func(Logic.BOOLEAN_TYPE, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).divisibleBy(KInt32.TWO))
            }
        }
        negated 'even?', 'odd?'
        define 'divisible_by?', func(Logic.BOOLEAN_TYPE, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).divisibleBy((KismetNumber) args[1]))
            }
        }
        define 'integer?', func(Logic.BOOLEAN_TYPE, new UnionType(NumberType.Number, NumberType.Float32, NumberType.Float64, NumberType.Float)), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetNumber) args[0]).divisibleBy(KInt32.ONE))
            }
        }
        define 'integer?', func(Logic.BOOLEAN_TYPE, new UnionType(NumberType.Int8, NumberType.Int16, NumberType.Int32, NumberType.Int64, NumberType.Int)), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.TRUE
            }
        }
        negated 'integer?', 'decimal?'
        define 'natural?', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                def onc = new OnceExpression(args[0])
                call(name('and'),
                        call(name('integer?'), onc),
                        call(name('positive?'), onc))
            }
        }
        define 'absolute',  funcc(true) { ... a -> a[0].invokeMethod('abs', null) }
        alias 'absolute', 'abs'
        define 'squared',  funcc(true) { ... a -> a[0].invokeMethod('multiply', [a[0]] as Object[]) }
        define 'square_root',  funcc(true) { ... args -> ((Object) Math).invokeMethod('sqrt', [args[0]] as Object[]) }
        alias 'square_root', 'sqrt'
        define 'cube_root',  funcc(true) { ... args -> ((Object) Math).invokeMethod('cbrt', [args[0]] as Object[]) }
        alias 'cube_root', 'cbrt'
        define 'sin',  funcc(true) { ... args -> ((Object) Math).invokeMethod('sin', [args[0]] as Object[]) }
        define 'cos',  funcc(true) { ... args -> ((Object) Math).invokeMethod('cos', [args[0]] as Object[]) }
        define 'tan',  funcc(true) { ... args -> ((Object) Math).invokeMethod('tan', [args[0]] as Object[]) }
        define 'sinh',  funcc(true) { ... args -> ((Object) Math).invokeMethod('sinh', [args[0]] as Object[]) }
        define 'cosh',  funcc(true) { ... args -> ((Object) Math).invokeMethod('cosh', [args[0]] as Object[]) }
        define 'tanh',  funcc(true) { ... args -> ((Object) Math).invokeMethod('tanh', [args[0]] as Object[]) }
        define 'arcsin',  funcc(true) { ... args -> Math.asin(args[0] as double) }
        define 'arccos',  funcc(true) { ... args -> Math.acos(args[0] as double) }
        define 'arctan',  funcc(true) { ... args -> Math.atan(args[0] as double) }
        define 'arctan2',  funcc(true) { ... args -> Math.atan2(args[0] as double, args[1] as double) }
        define 'do_round',  funcc(true) { ...args ->
            def value = args[0]
            String mode = args[1]?.toString()
            if (null != mode) value = value as BigDecimal
            if (value instanceof BigDecimal) {
                RoundingMode realMode
                if (null != mode) {
                    final m = roundingModes[mode]
                    if (null == m) throw new UnexpectedValueException('Unknown rounding mode ' + mode)
                    realMode = m
                } else realMode = RoundingMode.HALF_UP
                value.setScale(null == args[2] ? 0 : args[2] as int, realMode).stripTrailingZeros()
            } else if (value instanceof BigInteger
                    || value instanceof Integer
                    || value instanceof Long) value
            else if (value instanceof Float) Math.round(value.floatValue())
            else Math.round(((Number) value).doubleValue())
        }
        define 'round',  new Template() {
            @Override
            Expression transform(Parser parser, Expression... args) {
                def x = new ArrayList<Expression>(4)
                x.add(name('do_round'))
                x.add(args[0])
                if (args.length > 1) x.add(new StaticExpression(args[1], Syntax.toAtom(args[1])))
                if (args.length > 2) x.add(args[2])
                call(x)
            }
        }
        define 'floor',  funcc(true) { ... args ->
            def value = args[0]
            if (args.length > 1) value = value as BigDecimal
            if (value instanceof BigDecimal)
                ((BigDecimal) value).setScale(args.length > 1 ? args[1] as int : 0,
                        RoundingMode.FLOOR).stripTrailingZeros()
            else if (value instanceof BigInteger ||
                    value instanceof Integer ||
                    value instanceof Long) value
            else Math.floor(value as double)
        }
        define 'ceil',  funcc(true) { ... args ->
            def value = args[0]
            if (args.length > 1) value = value as BigDecimal
            if (value instanceof BigDecimal)
                ((BigDecimal) value).setScale(args.length > 1 ? args[1] as int : 0,
                        RoundingMode.CEILING).stripTrailingZeros()
            else if (value instanceof BigInteger ||
                    value instanceof Integer ||
                    value instanceof Long) value
            else Math.ceil(value as double)
        }
        define 'logarithm', func(NumberType.Float64, NumberType.Float64), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KFloat64(Math.log(((KFloat64) args[0]).inner))
            }
        }
        define 'plus', func(NumberType.Number, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KismetNumber) args[0]).plus((KismetNumber) args[1])
            }
        }
        define 'plus', func(NumberType.Float, NumberType.Float, NumberType.Float), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat) args[0]).plus((KFloat) args[1])
            }
        }
        define 'plus', func(NumberType.Float64, NumberType.Float64, NumberType.Float64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat64) args[0]).plus((KFloat64) args[1])
            }
        }
        define 'plus', func(NumberType.Float32, NumberType.Float32, NumberType.Float32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat32) args[0]).plus((KFloat32) args[1])
            }
        }
        define 'plus', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt) args[0]).plus((KInt) args[1])
            }
        }
        define 'plus', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).plus((KInt64) args[1])
            }
        }
        define 'plus', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).plus((KInt32) args[1])
            }
        }
        define 'plus', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).plus((KInt16) args[1])
            }
        }
        define 'plus', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).plus((KInt8) args[1])
            }
        }
        define 'minus', func(NumberType.Number, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KismetNumber) args[0]).minus((KismetNumber) args[1])
            }
        }
        define 'minus', func(NumberType.Float, NumberType.Float, NumberType.Float), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat) args[0]).minus((KFloat) args[1])
            }
        }
        define 'minus', func(NumberType.Float64, NumberType.Float64, NumberType.Float64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat64) args[0]).minus((KFloat64) args[1])
            }
        }
        define 'minus', func(NumberType.Float32, NumberType.Float32, NumberType.Float32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat32) args[0]).minus((KFloat32) args[1])
            }
        }
        define 'minus', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt) args[0]).minus((KInt) args[1])
            }
        }
        define 'minus', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).minus((KInt64) args[1])
            }
        }
        define 'minus', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).minus((KInt32) args[1])
            }
        }
        define 'minus', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).minus((KInt16) args[1])
            }
        }
        define 'minus', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).minus((KInt8) args[1])
            }
        }
        define 'multiply', func(NumberType.Number, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KismetNumber) args[0]).multiply((KismetNumber) args[1])
            }
        }
        define 'multiply', func(NumberType.Float, NumberType.Float, NumberType.Float), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat) args[0]).multiply((KFloat) args[1])
            }
        }
        define 'multiply', func(NumberType.Float64, NumberType.Float64, NumberType.Float64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat64) args[0]).multiply((KFloat64) args[1])
            }
        }
        define 'multiply', func(NumberType.Float32, NumberType.Float32, NumberType.Float32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat32) args[0]).multiply((KFloat32) args[1])
            }
        }
        define 'multiply', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt) args[0]).multiply((KInt) args[1])
            }
        }
        define 'multiply', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).multiply((KInt64) args[1])
            }
        }
        define 'multiply', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).multiply((KInt32) args[1])
            }
        }
        define 'multiply', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).multiply((KInt16) args[1])
            }
        }
        define 'multiply', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).multiply((KInt8) args[1])
            }
        }
        define 'divide', func(NumberType.Number, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KismetNumber) args[0]).div((KismetNumber) args[1])
            }
        }
        define 'divide', func(NumberType.Float, NumberType.Float, NumberType.Float), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat) args[0]).div((KFloat) args[1])
            }
        }
        define 'divide', func(NumberType.Float64, NumberType.Float64, NumberType.Float64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat64) args[0]).div((KFloat64) args[1])
            }
        }
        define 'divide', func(NumberType.Float32, NumberType.Float32, NumberType.Float32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat32) args[0]).div((KFloat32) args[1])
            }
        }
        define 'divide', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt) args[0]).div((KInt) args[1])
            }
        }
        define 'divide', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).div((KInt64) args[1])
            }
        }
        define 'divide', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).div((KInt32) args[1])
            }
        }
        define 'divide', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).div((KInt16) args[1])
            }
        }
        define 'divide', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).div((KInt8) args[1])
            }
        }
        define 'div', func(NumberType.Number, NumberType.Number, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KismetNumber) args[0]).intdiv((KismetNumber) args[1])
            }
        }
        define 'div', func(NumberType.Float, NumberType.Float, NumberType.Float), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat) args[0]).intdiv((KFloat) args[1])
            }
        }
        define 'div', func(NumberType.Float64, NumberType.Float64, NumberType.Float64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat64) args[0]).intdiv((KFloat64) args[1])
            }
        }
        define 'div', func(NumberType.Float32, NumberType.Float32, NumberType.Float32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KFloat32) args[0]).intdiv((KFloat32) args[1])
            }
        }
        define 'div', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt) args[0]).intdiv((KInt) args[1])
            }
        }
        define 'div', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt64) args[0]).intdiv((KInt64) args[1])
            }
        }
        define 'div', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt32) args[0]).intdiv((KInt32) args[1])
            }
        }
        define 'div', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt16) args[0]).intdiv((KInt16) args[1])
            }
        }
        define 'div', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
            IKismetObject call(IKismetObject... args) {
                ((KInt8) args[0]).intdiv((KInt8) args[1])
            }
        }
        alias 'plus', '+'
        alias 'minus', '-'
        alias 'multiply', '*'
        alias 'divide', '/'
        define 'rem',  funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('mod', [b] as Object[]) } }
        define 'mod',  funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('abs', null).invokeMethod('mod', [b] as Object[]) } }
        define 'pow',  funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('power', [b] as Object[]) } }
        define 'reciprocal',  funcc(true) { ... args -> 1.div(args[0] as Number) }
        define 'integer_from_int8_list',  funcc(true) { ... args -> new BigInteger(args[0] as byte[]) }
        define 'integer_to_int8_list',  funcc(true) { ... args -> (args[0] as BigInteger).toByteArray() as List<Byte> }
        define 'number?',  funcc { ... args -> args[0] instanceof Number }
        define 'gcd',  funcc { ... args -> gcd(args[0] as Number, args[1] as Number) }
        define 'lcm',  funcc { ... args -> lcm(args[0] as Number, args[1] as Number) }
        define 'reduce_ratio',  funcc { ... args ->
            Pair pair = args[0] as Pair
            def a = pair.first as Number, b = pair.second as Number
            Number gcd = gcd(a, b)
            (a, b) = [a.intdiv(gcd), b.intdiv(gcd)]
            new Pair(a, b)
        }
        define 'int', func(NumberType.Int, Type.ANY), func(true) { IKismetObject... a -> a[0] as BigInteger }
        define 'int8',func(NumberType.Int8, Type.ANY), func(true) { IKismetObject... a -> a[0] as byte }
        define 'int16', func(NumberType.Int16, Type.ANY), func(true) { IKismetObject... a -> a[0] as short }
        define 'int32', func(NumberType.Int32, Type.ANY), func(true) { IKismetObject... a -> a[0] as int }
        define 'int64', func(NumberType.Int64, Type.ANY), func(true) { IKismetObject... a -> a[0] as long }
        define 'char', func(NumberType.Char, Type.ANY), func(true) { IKismetObject... a -> a[0] as Character }
        define 'float', func(NumberType.Float, Type.ANY), func(true) { IKismetObject... a -> a[0] as BigDecimal }
        define 'float32', func(NumberType.Float32, Type.ANY), func(true) { IKismetObject... a -> a[0] as float }
        define 'float64', func(NumberType.Float64, Type.ANY), func(true) { IKismetObject... a -> a[0] as double }
        define 'to_base', funcc { ... a -> (a[0] as BigInteger).toString(a[1] as int) }
        define 'from_base',  funcc { ... a -> new BigInteger(a[0].toString(), a[1] as int) }
        define 'hex', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                if (args[0] instanceof NumberExpression || args[0] instanceof NameExpression) {
                    String t = args[0] instanceof NumberExpression ?
                            ((NumberExpression) args[0]).value.inner().toString() :
                            ((NameExpression) args[0]).text
                    number(new BigInteger(t, 16))
                } else throw new UnexpectedSyntaxException('Expression after hex was not a hexadecimal number literal.'
                        + ' To convert hex strings to integers do [from_base str 16], '
                        + ' and to convert integers to hex strings do [to_base i 16].')
            }
        }
        define 'binary', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                if (args[0] instanceof NumberExpression) {
                    String t = ((NumberExpression) args[0]).value.inner().toString()
                    number(new BigInteger(t, 2))
                } else throw new UnexpectedSyntaxException('Expression after binary was not a binary number literal.'
                        + ' To convert binary strings to integers do [from_base str 2], '
                        + ' and to convert integers to binary strings do [to_base i 2].')
            }
        }
        define 'octal', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                if (args[0] instanceof NumberExpression) {
                    String t = ((NumberExpression) args[0]).value.inner().toString()
                    number(new BigInteger(t, 8))
                } else throw new UnexpectedSyntaxException('Expression after octal was not a octal number literal.'
                        + ' To convert octal strings to integers do [from_base str 8], '
                        + ' and to convert integers to octal strings do [to_base i 8].')
            }
        }
        alias 'divisible_by?', 'divides?', 'divs?'
        define 'next', func(NumberType.Number, NumberType.Number), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetNumber) args[0]).plus(new KInt32(1))
            }
        }
        define 'prev', func(NumberType.Number, NumberType.Number), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((KismetNumber) args[0]).minus(new KInt32(1))
            }
        }
    }

    static Number gcd(Number a, Number b) {
        a = a.abs()
        if (b == 0) return a
        b = b.abs()
        while (a % b) (b, a) = [a % b, b]
        b
    }

    static Number lcm(Number a, Number b) {
        a.multiply(b).abs().intdiv(gcd(a, b))
    }
}
