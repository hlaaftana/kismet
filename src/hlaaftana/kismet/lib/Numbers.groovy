package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.*
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.exceptions.UnexpectedValueException
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.*
import hlaaftana.kismet.vm.*

import java.math.RoundingMode

import static hlaaftana.kismet.call.ExprBuilder.*
import static hlaaftana.kismet.lib.Functions.*

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class Numbers extends NativeModule {
    private static Map<String, RoundingMode> roundingModes = [
        'ceiling': RoundingMode.CEILING, 'floor': RoundingMode.FLOOR,
        'up': RoundingMode.UP, 'down': RoundingMode.DOWN,
        'half_up': RoundingMode.HALF_UP, 'half_down': RoundingMode.HALF_DOWN,
        'half_even': RoundingMode.HALF_EVEN, 'none': RoundingMode.UNNECESSARY,
        '^': RoundingMode.CEILING, 'v': RoundingMode.FLOOR,
        '^0': RoundingMode.UP, 'v0': RoundingMode.DOWN,
        '/^': RoundingMode.HALF_UP, '/v': RoundingMode.HALF_DOWN,
        '/2': RoundingMode.HALF_EVEN, '!': RoundingMode.UNNECESSARY
    ].asImmutable()

    Numbers() {
        super("numbers")
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
        define 'natural?', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                def onc = new OnceExpression(args[0])
                call(name('and'),
                        call(name('integer?'), onc),
                        call(name('positive?'), onc))
            }
        }
        define 'square_root',  funcc(true) { ... args -> ((Object) Math).invokeMethod('sqrt', [args[0]] as Object[]) }
        define 'cube_root',  funcc(true) { ... args -> ((Object) Math).invokeMethod('cbrt', [args[0]] as Object[]) }
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
        for (nt in NumberType.values()) {
            /*define 'plus', typedTmpl(nt, nt, nt), new TypedTemplate() {
                @Override
                TypedExpression transform(TypedContext tc, TypedExpression... args) {
                    def leftInstr = args[0].instruction
                    def rightInstr = args[1].instruction
                    new BasicTypedExpression(nt, new Instruction() {
                        @Override
                        IKismetObject evaluate(Memory mem) {
                            ((KismetNumber) leftInstr.evaluate(mem))
                                .plus((KismetNumber) rightInstr.evaluate(mem))
                        }
                    })
                }
            }*/
            define 'plus', func(nt, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).plus((KismetNumber) args[1])
                }
            }
            define 'minus', func(nt, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).minus((KismetNumber) args[1])
                }
            }
            define 'multiply', func(nt, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).multiply((KismetNumber) args[1])
                }
            }
            define 'divide', func(nt, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).div((KismetNumber) args[1])
                }
            }
            define 'div', func(nt, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).intdiv((KismetNumber) args[1])
                }
            }
            define 'rem', func(nt, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).abs().mod((KismetNumber) args[1])
                }
            }
            define 'mod', func(nt, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).mod((KismetNumber) args[1])
                }
            }
            define 'pow', func(nt, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).power((KismetNumber) args[1])
                }
            }
            define 'gcd', func(nt, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    KismetNumber.from gcd(((KismetNumber) args[0]).inner(), ((KismetNumber) args[1]).inner())
                }
            }
            define 'lcm', func(nt, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    KismetNumber.from lcm(((KismetNumber) args[0]).inner(), ((KismetNumber) args[1]).inner())
                }
            }
            define 'next', func(nt, nt), new Function() {
                @Override
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).plus(new KInt32(1))
                }
            }
            define 'prev', func(nt, nt), new Function() {
                @Override
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).minus(new KInt32(1))
                }
            }
            define 'first_half', func(nt, nt), new Function() {
                @Override
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).intdiv(new KInt32(2))
                }
            }
            define 'second_half', func(nt, nt), new Function() {
                @Override
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).minus(((KismetNumber) args[0]).intdiv(new KInt32(2)))
                }
            }
            define 'call', func(nt, nt, new TupleType().withVarargs(nt)), new Function() {
                @Override
                IKismetObject call(IKismetObject... args) {
                    def result = (KismetNumber) args[0]
                    def tup = (KismetTuple) args[1]
                    for (x in tup) {
                        result *= (KismetNumber) x
                    }
                    KismetNumber.from(result)
                }
            }
            if (((NumberType) nt).isInteger()) {
                define 'bit_not', func(nt, nt), new Function() {
                    IKismetObject call(IKismetObject... args) {
                        KismetNumber.from(((KismetNumber) args[0]).inner().bitwiseNegate())
                    }
                }
                define 'bit_xor', func(nt, nt, nt), new Function() {
                    IKismetObject call(IKismetObject... args) {
                        ((KismetNumber) args[0]).xor((KismetNumber) args[1])
                    }
                }
                define 'bit_or', func(nt, nt, nt), new Function() {
                    IKismetObject call(IKismetObject... args) {
                        ((KismetNumber) args[0]).or((KismetNumber) args[1])
                    }
                }
                define 'bit_and', func(nt, nt, nt), new Function() {
                    IKismetObject call(IKismetObject... args) {
                        ((KismetNumber) args[0]).and((KismetNumber) args[1])
                    }
                }
                define 'left_shift', func(nt, nt, nt), new Function() {
                    IKismetObject call(IKismetObject... args) {
                        ((KismetNumber) args[0]).leftShift((KismetNumber) args[1])
                    }
                }
                define 'right_shift', func(nt, nt, nt), new Function() {
                    IKismetObject call(IKismetObject... args) {
                        ((KismetNumber) args[0]).rightShift((KismetNumber) args[1])
                    }
                }
                define 'unsigned_right_shift', func(nt, nt, nt), new Function() {
                    IKismetObject call(IKismetObject... args) {
                        ((KismetNumber) args[0]).rightShiftUnsigned((KismetNumber) args[1])
                    }
                }
            }
            define 'positive', func(nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).unaryPlus()
                }
            }
            define 'negative', func(nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).unaryMinus()
                }
            }
            define 'positive?', func(Logic.BOOLEAN_TYPE, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    KismetBoolean.from(((KismetNumber) args[0]).compareTo(KInt32.ZERO) > 0)
                }
            }
            define 'negative?', func(Logic.BOOLEAN_TYPE, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    KismetBoolean.from(((KismetNumber) args[0]).compareTo(KInt32.ZERO) < 0)
                }
            }
            define 'zero?', func(Logic.BOOLEAN_TYPE, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    KismetBoolean.from(((KismetNumber) args[0]).compareTo(KInt32.ZERO) == 0)
                }
            }
            define 'one?', func(Logic.BOOLEAN_TYPE, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    KismetBoolean.from(((KismetNumber) args[0]).compareTo(KInt32.ONE) == 0)
                }
            }
            define 'even?', func(Logic.BOOLEAN_TYPE, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    KismetBoolean.from(((KismetNumber) args[0]).divisibleBy(KInt32.TWO))
                }
            }
            define 'divisible_by?', func(Logic.BOOLEAN_TYPE, nt, nt), new Function() {
                IKismetObject call(IKismetObject... args) {
                    KismetBoolean.from(((KismetNumber) args[0]).divisibleBy((KismetNumber) args[1]))
                }
            }
            define 'absolute', func(nt, nt), new Function() {
                @Override
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).abs()
                }
            }
            define 'squared', func(nt, nt), new Function() {
                @Override
                IKismetObject call(IKismetObject... args) {
                    ((KismetNumber) args[0]).multiply((KismetNumber) args[0])
                }
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
        alias 'plus', '+'
        alias 'minus', '-'
        alias 'multiply', '*'
        alias 'divide', '/'
        alias 'first_half', 'half'
        alias 'divisible_by?', 'divides?', 'divs?'
        alias 'absolute', 'abs'
        alias 'square_root', 'sqrt'
        alias 'cube_root', 'cbrt'
        negated 'integer?', 'decimal?'
        negated 'even?', 'odd?'
        define 'reciprocal',  funcc(true) { ... args -> 1.div(args[0] as Number) }
        define 'integer_from_int8_list',  funcc(true) { ... args -> new BigInteger(args[0] as byte[]) }
        define 'integer_to_int8_list',  funcc(true) { ... args -> (args[0] as BigInteger).toByteArray() as List<Byte> }
        define 'number?',  funcc { ... args -> args[0] instanceof Number }
        define 'reduce_ratio', func(new TupleType(NumberType.Number, NumberType.Number), new TupleType(NumberType.Number, NumberType.Number)), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                def tup = (KismetTuple) args[0]
                def a = (KismetNumber) tup[0], b = (KismetNumber) tup[1]
                Number gcd = gcd(a.inner(), b.inner())
                a = a.intdiv(gcd)
                b = b.intdiv(gcd)
                new KismetTuple(KismetNumber.from(a), KismetNumber.from(b))
            }
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
