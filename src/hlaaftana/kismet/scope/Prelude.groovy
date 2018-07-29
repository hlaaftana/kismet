package hlaaftana.kismet.scope

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.exceptions.*
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.parser.StringEscaper
import hlaaftana.kismet.vm.*

import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.regex.Pattern

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class Prelude {
	private static Map<String, RoundingMode> roundingModes = [
			'^': RoundingMode.CEILING, 'v': RoundingMode.FLOOR,
			'^0': RoundingMode.UP, 'v0': RoundingMode.DOWN,
			'/^': RoundingMode.HALF_UP, '/v': RoundingMode.HALF_DOWN,
			'/2': RoundingMode.HALF_EVEN, '!': RoundingMode.UNNECESSARY
	].asImmutable()
	static Map<String, IKismetObject> defaultContext = [
			Class: MetaKismetClass.OBJECT,
			Object: new WrapperKismetClass(IKismetObject, 'Object').object,
			Null: NullClass.INSTANCE,
			UnknownNumber: NonPrimitiveNumClass.INSTANCE,
			Integer: IntClass.INSTANCE,
			Float: FloatClass.INSTANCE,
			String: StringClass.INSTANCE,
			Boolean: new WrapperKismetClass(Boolean, 'Boolean').object,
			Int8: Int8Class.INSTANCE,
			Int16: Int16Class.INSTANCE,
			Int32: Int32Class.INSTANCE,
			Int64: Int64Class.INSTANCE,
			Float32: Float32Class.INSTANCE,
			Float64: Float64Class.INSTANCE,
			Character: CharClass.INSTANCE,
			Set: new WrapperKismetClass(Set, 'Set').object,
			List: new WrapperKismetClass(List, 'List').object,
			Tuple: new WrapperKismetClass(Tuple, 'Tuple').object,
			Map: new WrapperKismetClass(Map, 'Map').object,
			Expression: new WrapperKismetClass(Expression, 'Expression').object,
			PathExpression: new WrapperKismetClass(PathExpression, 'PathExpression').object,
			CallExpression: new WrapperKismetClass(CallExpression, 'CallExpression').object,
			BlockExpression: new WrapperKismetClass(BlockExpression, 'BlockExpression').object,
			StringExpression: new WrapperKismetClass(StringExpression, 'StringExpression').object,
			NumberExpression: new WrapperKismetClass(NumberExpression, 'NumberExpression').object,
			FakeExpression: new WrapperKismetClass(StaticExpression, 'FakeExpression').object,
			PathStep: new WrapperKismetClass(PathExpression.Step, 'PathStep').object,
			PropertyPathStep: new WrapperKismetClass(PathExpression.PropertyStep, 'PropertyPathStep').object,
			SubscriptPathStep: new WrapperKismetClass(PathExpression.SubscriptStep, 'SubscriptPathStep').object,
			Block: new WrapperKismetClass(Block, 'Block').object,
			Callable: new WrapperKismetClass(KismetCallable, 'Callable').object,
			Function: new WrapperKismetClass(Function, 'Function').object,
			FunctionArguments: new WrapperKismetClass(KismetFunction.Arguments, 'FunctionArguments').object,
			Template: new WrapperKismetClass(Template, 'Template').object,
			Macro: new WrapperKismetClass(Macro, 'Macro').object,
			Native: new WrapperKismetClass(Object, 'Native').object,
			Regex: new WrapperKismetClass(Pattern, 'Regex').object,
			Range: new WrapperKismetClass(Range, 'Range').object,
			Pair: new WrapperKismetClass(Pair, 'Pair').object,
			Iterator: new WrapperKismetClass(Iterator, 'Iterator').object,
			Throwable: new WrapperKismetClass(Throwable, 'Throwable').object,
			RNG: new WrapperKismetClass(Random, 'RNG').object,
			Date: new WrapperKismetClass(Date, 'Date').object,
			JSONParser: new WrapperKismetClass(JsonSlurper, 'JSONParser').object,
			KismetParser: new WrapperKismetClass(Parser, 'KismetParser').object,
			Variable: new WrapperKismetClass(Variable, 'Variable').object]

	static {
		Map<String, Object> toConvert = [
				euler_constant: Math.E.toBigDecimal(), pi: Math.PI.toBigDecimal(),
				now_nanos: func { IKismetObject... args -> System.nanoTime() },
				now_millis: func { IKismetObject... args -> System.currentTimeMillis() },
				now_seconds: func { IKismetObject... args -> System.currentTimeSeconds() },
				now_date: func { IKismetObject... args -> new Date() },
				new_date: funcc { ... args -> Date.invokeMethod('newInstance', args) },
				parse_date_from_format: funcc(true) { ... args -> new SimpleDateFormat(args[1].toString()).parse(args[0].toString()) },
				format_date: funcc(true) { ... args -> new SimpleDateFormat(args[1].toString()).format(args[0].toString()) },
				true: true, false: false, null: null,
				yes: true, no: false, on: true, off: false,
				class: func(true) { IKismetObject... a -> a[0].kismetClass() },
				class_from_name: func { IKismetObject... a -> WrapperKismetClass.fromName(a[0].toString()) },
				'instance?': new Function() {
					{ setPure(true) }

					IKismetObject call(IKismetObject... a) {
						final b = a[0].inner()
						final c = b instanceof IKismetClass ? (IKismetClass) b : a[0].kismetClass()
						for (int i = 1; i < a.length; ++i) if (c.isInstance(a[i])) return Kismet.model(true)
						Kismet.model(false)
					}
				},
				'of?': func(true) { IKismetObject... a ->
					final a0 = a[0]
					for (int i = 1; i < a.length; ++i) {
						final ai = a[i]
						final x = ai.inner()
						final y = x instanceof IKismetClass ? (IKismetClass) x : ai.kismetClass()
						if (y.isInstance(a0)) return true
						else throw new UnexpectedValueException('Argument in of? not class')
					}
					false
				},
				'not_of?': func(true) { IKismetObject... a ->
					final a0 = a[0]
					for (int i = 1; i < a.length; ++i) {
						final ai = a[i]
						final x = ai.inner()
						final y = x instanceof IKismetClass ? (IKismetClass) x : ai.kismetClass()
						if (y.isInstance(a0)) return false
						else throw new UnexpectedValueException('Argument in not_of? not class')
					}
					true
				},
				variable: new Macro() {
					@CompileStatic
					IKismetObject call(Context c, Expression... args) {
						if (args.length == 0) throw new UnexpectedSyntaxException('No arguments for variable')
						final first = args[0].evaluate(c)
						if (first.inner() instanceof String) {
							final name = (String) first.inner()
							args.length == 2 ? c.set(name, args[1].evaluate(c)) : c.get(name)
						} else if (first.inner() instanceof Variable && args.length == 2) {
							final val = args[1].evaluate(c)
							((Variable) first.inner()).value = val
							val
						} else throw new UnexpectedSyntaxException("weird argument for variable: " + first)
					}
				},
				variables: new KismetCallable() {
					IKismetObject call(Context c, Expression... args) {
						Kismet.model(c.variables)
					}
				},
				current_context: new KismetCallable() {
					IKismetObject call(Context c, Expression... args) {
						Kismet.model(c)
					}
				},
				java_class_name: funcc(true) { ... args -> args[0].class.name },
				'<=>': funcc(true) { ... a -> a[0].invokeMethod('compareTo', [a[1]] as Object[]) as int },
				try: macr { Context c, Expression... exprs ->
					try {
						exprs[0].evaluate(c)
					} catch (ex) {
						c = c.child()
						c.set(resolveName(exprs[1], c, 'try'), Kismet.model(ex))
						exprs[2].evaluate(c)
					}
				},
				raise: func { ... args ->
					if (args.length == 0) throw new KismetException()
					else if (args[0] instanceof String) throw new KismetException((String) args[0])
					else if (args[0] instanceof Throwable) throw (Throwable) args[0]
					else throw new UnexpectedValueException('raise called with non-throwable ' + args[0])
				},
				do: Function.NOP,
				'don\'t': new AbstractTemplate() {
					@CompileStatic
					Expression transform(Parser parser, Expression... args) {
						NoExpression.INSTANCE
					}
				},
				assert: macr { Context c, Expression... exprs ->
					IKismetObject val
					for (e in exprs) if (!(val = e.evaluate(c)))
						throw new KismetAssertionError('Assertion failed for expression ' +
								e.repr() + '. Value was ' + val)
				},
				assert_not: macr { Context c, Expression... exprs ->
					IKismetObject val
					for (e in exprs) if ((val = e.evaluate(c)))
						throw new KismetAssertionError('Assertion failed for expression ' +
								e.repr() + '. Value was ' + val)
				},
				assert_is: macr { Context c, Expression... exprs ->
					IKismetObject val = exprs[0].evaluate(c)
					IKismetObject latest
					for (e in exprs.tail()) if (val != (latest = e.evaluate(c)))
						throw new KismetAssertionError('Assertion failed for expression ' +
								e.repr() + '. Value was expected to be ' + val +
								' but was ' + latest)
				},
				'assert_isn\'t': macr { Context c, Expression... exprs ->
					def values = [exprs[0].evaluate(c)]
					IKismetObject retard
					IKismetObject latest
					for (e in exprs.tail()) if ((retard = values.find((latest = e.evaluate(c)).&equals)))
						throw new KismetAssertionError('Assertion failed for expression ' +
								e.repr() + '. Value was expected NOT to be ' + retard +
								' but was ' + latest)
				},
				assert_of: macr { Context c, Expression... exprs ->
					IKismetObject val = exprs[0].evaluate(c)
					def a = []
					for (e in exprs.tail()) {
						def cl = e.evaluate(c).inner()
						if (!(cl instanceof WrapperKismetClass))
							throw new UnexpectedValueException('Argument in assert_of wasn\'t a class')
						a.add(cl)
						if (((WrapperKismetClass) cl).isInstance(val)) return
					}
					throw new KismetAssertionError('Assertion failed for expression ' +
							exprs[0].repr() + '. Value was expected to be an instance ' +
							'of one of classes ' + a.join(', ') + ' but turned out to be callValue ' +
							val + ' and of class ' + val.kismetClass())
				},
				assert_not_of: macr { Context c, Expression... exprs ->
					IKismetObject val = exprs[0].evaluate(c)
					def a = []
					for (e in exprs.tail()) {
						def cl = e.evaluate(c).inner()
						if (!(cl instanceof WrapperKismetClass))
							throw new UnexpectedValueException('Argument in assert_not_of wasn\'t a class')
						a.add(cl)
						if (((WrapperKismetClass) cl).isInstance(val))
							throw new KismetAssertionError('Assertion failed for expression ' +
									exprs[0].repr() + '. Value was expected to be NOT an instance ' +
									'of class ' + a.join(', ') + ' but turned out to be callValue ' +
									val + ' and of class ' + val.kismetClass())
					}
				},
				hash: funcc { ... a -> a[0].hashCode() },
				percent: funcc(true) { ... a -> a[0].invokeMethod 'div', 100 },
				to_percent: funcc(true) { ... a -> a[0].invokeMethod 'multiply', 100 },
				strip_trailing_zeros: funcc(true) { ... a -> ((BigDecimal) a[0]).stripTrailingZeros() },
				as: func { IKismetObject... a -> a[0].invokeMethod('as', [a[1].inner()] as Object[]) },
				'is?': funcc { ... args -> args.inject { a, b -> a == b } },
				'isn\'t?': funcc { ... args -> args.inject { a, b -> a != b } },
				'same?': funcc { ... a -> a[0].is(a[1]) },
				'not_same?': funcc { ... a -> !a[0].is(a[1]) },
				'empty?': funcc { ... a -> a[0].invokeMethod('isEmpty', null) },
				'in?': funcc { ... a -> a[0] in a[1] },
				'not_in?': funcc { ... a -> !(a[0] in a[1]) },
				not: funcc(true) { ... a -> !(a[0]) },
				and: macr(true) { Context c, Expression... exprs ->
					IKismetObject last = Kismet.model(true)
					for (it in exprs) if (!(last = it.evaluate(c))) return last; last
				},
				or: macr(true) { Context c, Expression... exprs ->
					IKismetObject last = Kismet.model(false)
					for (it in exprs) if ((last = it.evaluate(c))) return last; last
				},
				pick: macr(true) { Context c, Expression... exprs ->
					IKismetObject x = Kismet.NULL
					for (it in exprs) if ((x = it.evaluate(c))) return x; x
				},
				'??': macr { Context c, Expression... exprs ->
					def p = (PathExpression) exprs[0]
					if (null == p.root) {
						new CallExpression([new NameExpression('fn'), new CallExpression([
								new NameExpression('??'), new PathExpression(new NameExpression('$0'),
								p.steps)])]).evaluate(c)
					} else {
						IKismetObject result = p.root.evaluate(c)
						def iter = p.steps.iterator()
						while (result.inner() != null && iter.hasNext()) {
							final b = iter.next()
							result = b.apply(c, result)
						}
						result
					}
				},
				xor: funcc { ... args -> args.inject { a, b -> a.invokeMethod('xor', [b] as Object[]) } },
				bool: funcc(true) { ... a -> a[0] as boolean },
				bit_not: funcc(true) { ... a -> a[0].invokeMethod 'bitwiseNegate', null },
				bit_and: funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('and', [b] as Object[]) } },
				bit_or: funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('or', [b] as Object[]) } },
				bit_xor: funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('xor', [b] as Object[]) } },
				left_shift: funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('leftShift', [b] as Object[]) } },
				right_shift: funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('rightShift', [b] as Object[]) } },
				unsigned_right_shift: funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('rightShiftUnsigned', [b] as Object[]) } },
				'<': funcc(true) { ... args ->
					for (int i = 1; i < args.length; ++i) {
						if (((int) args[i - 1].invokeMethod('compareTo', [args[i]] as Object[])) >= 0)
							return false
					}
					true
				},
				'>': funcc(true) { ... args ->
					for (int i = 1; i < args.length; ++i) {
						if (((int) args[i - 1].invokeMethod('compareTo', [args[i]] as Object[])) <= 0)
							return false
					}
					true
				},
				'<=': funcc(true) { ... args ->
					for (int i = 1; i < args.length; ++i) {
						if (((int) args[i - 1].invokeMethod('compareTo', [args[i]] as Object[])) > 0)
							return false
					}
					true
				},
				'>=': funcc(true) { ... args ->
					for (int i = 1; i < args.length; ++i) {
						if (((int) args[i - 1].invokeMethod('compareTo', [args[i]] as Object[])) < 0)
							return false
					}
					true
				},
				positive: funcc(true) { ... a -> a[0].invokeMethod 'unaryPlus', null },
				negative: funcc(true) { ... a -> a[0].invokeMethod 'unaryMinus', null },
				'positive?': funcc(true) { ... args -> ((int) args[0].invokeMethod('compareTo', 0)) > 0 },
				'negative?': funcc(true) { ... args -> ((int) args[0].invokeMethod('compareTo', 0)) < 0 },
				'null?': funcc(true) { ... args -> null == args[0] },
				'not_null?': funcc(true) { ... args -> null != args[0] },
				'false?': funcc(true) { ... args -> !args[0].asBoolean() },
				'bool?': funcc(true) { ... args -> args[0] instanceof boolean },
				'zero?': funcc(true) { ... args -> args[0] == 0 },
				'one?': funcc(true) { ... args -> args[0] == 1 },
				'even?': funcc(true) { ... args -> args[0].invokeMethod('mod', 2) == 0 },
				'odd?': funcc(true) { ... args -> args[0].invokeMethod('mod', 2) != 0 },
				'divisible_by?': funcc(true) { ... args -> args[0].invokeMethod('mod', [args[1]] as Object[]) == 0 },
				'integer?': funcc(true) { ... args -> args[0].invokeMethod('mod', 1) == 0 },
				'decimal?': funcc(true) { ... args -> args[0].invokeMethod('mod', 1) != 0 },
				'natural?': funcc(true) { ... args -> args[0].invokeMethod('mod', 1) == 0 && ((int) args[0].invokeMethod('compareTo', 0)) >= 0 },
				absolute: funcc(true) { ... a -> a[0].invokeMethod('abs', null) },
				squared: funcc(true) { ... a -> a[0].invokeMethod('multiply', [a[0]] as Object[]) },
				square_root: funcc(true) { ... args -> ((Object) Math).invokeMethod('sqrt', [args[0]] as Object[]) },
				cube_root: funcc(true) { ... args -> ((Object) Math).invokeMethod('cbrt', [args[0]] as Object[]) },
				sine: funcc(true) { ... args -> ((Object) Math).invokeMethod('sin', [args[0]] as Object[]) },
				cosine: funcc(true) { ... args -> ((Object) Math).invokeMethod('cos', [args[0]] as Object[]) },
				tangent: funcc(true) { ... args -> ((Object) Math).invokeMethod('tan', [args[0]] as Object[]) },
				hyperbolic_sine: funcc(true) { ... args -> ((Object) Math).invokeMethod('sinh', [args[0]] as Object[]) },
				hyperbolic_cosine: funcc(true) { ... args -> ((Object) Math).invokeMethod('cosh', [args[0]] as Object[]) },
				hyperbolic_tangent: funcc(true) { ... args -> ((Object) Math).invokeMethod('tanh', [args[0]] as Object[]) },
				arcsine: funcc(true) { ... args -> Math.asin(args[0] as double) },
				arccosine: funcc(true) { ... args -> Math.acos(args[0] as double) },
				arctangent: funcc(true) { ... args -> Math.atan(args[0] as double) },
				arctan2: funcc(true) { ... args -> Math.atan2(args[0] as double, args[1] as double) },
				do_round: funcc(true) { ...args ->
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
							|| value instanceof int
							|| value instanceof long) value
					else if (value instanceof float) Math.round(value.floatValue())
					else Math.round(((Number) value).doubleValue())
				},
				round: new AbstractTemplate() {
					@Override
					Expression transform(Parser parser, Expression... args) {
						def x = new ArrayList<Expression>(4)
						x.add(new NameExpression('do_round'))
						x.add(args[0])
						if (args.length > 1) x.add(new StaticExpression(args[1], toAtom(args[1])))
						if (args.length > 2) x.add(args[2])
						new CallExpression(x)
					}
				},
				floor: funcc(true) { ... args ->
					def value = args[0]
					if (args.length > 1) value = value as BigDecimal
					if (value instanceof BigDecimal)
						((BigDecimal) value).setScale(args.length > 1 ? args[1] as int : 0,
								RoundingMode.FLOOR).stripTrailingZeros()
					else if (value instanceof BigInteger ||
							value instanceof int ||
							value instanceof long) value
					else Math.floor(value as double)
				},
				ceil: funcc(true) { ... args ->
					def value = args[0]
					if (args.length > 1) value = value as BigDecimal
					if (value instanceof BigDecimal)
						((BigDecimal) value).setScale(args.length > 1 ? args[1] as int : 0,
								RoundingMode.CEILING).stripTrailingZeros()
					else if (value instanceof BigInteger ||
							value instanceof int ||
							value instanceof long) value
					else Math.ceil(value as double)
				},
				logarithm: funcc(true) { ... args -> Math.log(args[0] as double) },
				'+': funcc(true) { ... args -> args.sum() },
				'-': funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('minus', [b] as Object[]) } },
				'*': funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('multiply', [b] as Object[]) } },
				'/': funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('div', [b] as Object[]) } },
				div: funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('intdiv', [b] as Object[]) } },
				rem: funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('mod', [b] as Object[]) } },
				mod: funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('abs', null).invokeMethod('mod', [b] as Object[]) } },
				pow: funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('power', [b] as Object[]) } },
				sum: funcc(true) { ... args -> args[0].invokeMethod('sum', null) },
				product: funcc(true) { ... args -> args[0].inject { a, b -> a.invokeMethod('multiply', [b] as Object[]) } },
				reciprocal: funcc(true) { ... args -> 1.div(args[0] as Number) },
				'defined?': macr { Context c, Expression... exprs ->
					try {
						c.get(resolveName(exprs[0], c, "defined?"))
						true
					} catch (UndefinedVariableException ignored) {
						false
					}
				},
				integer_from_int8_list: funcc(true) { ... args -> new BigInteger(args[0] as byte[]) },
				integer_to_int8_list: funcc(true) { ... args -> (args[0] as BigInteger).toByteArray() as List<Byte> },
				new_rng: funcc { ... args -> args.length > 0 ? new Random(args[0] as long) : new Random() },
				random_int8_list_from_reference: funcc { ... args ->
					byte[] bytes = args[1] as byte[]
					(args[0] as Random).nextBytes(bytes)
					bytes as List<Byte>
				},
				random_int32: funcc { ... args ->
					if (args.length == 0) return (args[0] as Random).nextInt()
					int max = (args.length > 2 ? args[2] as int : args[1] as int) + 1
					int min = args.length > 2 ? args[1] as int : 0
					(args[0] as Random).nextInt(max) + min
				},
				random_int64_of_all: funcc { ... args -> (args[0] as Random).nextLong() },
				random_float32_between_0_and_1: funcc { ... args -> (args[0] as Random).nextFloat() },
				random_float64_between_0_and_1: funcc { ... args -> (args[0] as Random).nextDouble() },
				random_bool: funcc { ... args -> (args[0] as Random).nextBoolean() },
				next_gaussian: funcc { ... args -> (args[0] as Random).nextGaussian() },
				random_int: funcc { ... args ->
					BigInteger lower = args.length > 2 ? args[1] as BigInteger : 0g
					BigInteger higher = args.length > 2 ? args[2] as BigInteger : args[1] as BigInteger
					double x = (args[0] as Random).nextDouble()
					lower + (((higher - lower) * (x as BigDecimal)) as BigInteger)
				},
				random_float: funcc { ... args ->
					BigDecimal lower = args.length > 2 ? args[1] as BigDecimal : 0g
					BigDecimal higher = args.length > 2 ? args[2] as BigDecimal : args[1] as BigDecimal
					double x = (args[0] as Random).nextDouble()
					lower + (higher - lower) * (x as BigDecimal)
				},
				'shuffle!': funcc { ... args ->
					def l = toList(args[0])
					args[1] instanceof Random ? Collections.shuffle(l, (Random) args[1])
							: Collections.shuffle(l)
					l
				},
				shuffle: funcc { ... args ->
					def l = new ArrayList(toList(args[0]))
					args[1] instanceof Random ? Collections.shuffle(l, (Random) args[1])
							: Collections.shuffle(l)
					l
				},
				sample: funcc { ... args ->
					def x = toList(args[0])
					Random r = args.length > 1 && args[1] instanceof Random ? (Random) args[1] : new Random()
					x[r.nextInt(x.size())]
				},
				high: funcc { ... args ->
					if (args[0] instanceof Number) ((Object) args[0].class).invokeMethod('getProperty', 'MAX_VALUE')
					else if (args[0] instanceof WrapperKismetClass && Number.isAssignableFrom(((WrapperKismetClass) args[0]).orig))
						((WrapperKismetClass) args[0]).orig.invokeMethod('getProperty', 'MAX_VALUE')
					else if (args[0] instanceof Range) ((Range) args[0]).to
					else if (args[0] instanceof Collection) ((Collection) args[0]).size() - 1
					else throw new UnexpectedValueException('Don\'t know how to get high of ' + args[0] + ' with class ' + args[0].class)
				},
				low: funcc { ... args ->
					if (args[0] instanceof Number) ((Object) args[0].class).invokeMethod('getProperty', 'MIN_VALUE')
					else if (args[0] instanceof WrapperKismetClass && Number.isAssignableFrom(((WrapperKismetClass) args[0]).orig))
						((WrapperKismetClass) args[0]).orig.invokeMethod('getProperty', 'MIN_VALUE')
					else if (args[0] instanceof Range) ((Range) args[0]).from
					else if (args[0] instanceof Collection) 0
					else throw new UnexpectedValueException('Don\'t know how to get low of ' + args[0] + ' with class ' + args[0].class)
				},
				collect_range_with_step: funcc { ... args -> (args[0] as Range).step(args[1] as int) },
				each_range_with_step: func { IKismetObject... args ->
					(args[0].inner() as Range)
							.step(args[1].inner() as int, args[2].kismetClass().&call.curry(args[2]))
				},
				replace: funcc { ... args ->
					args[0].toString().replace(args[1].toString(),
							args.length > 2 ? args[2].toString() : '')
				},
				replace_all_regex: func { IKismetObject... args ->
					def replacement = args.length > 2 ?
							(args[2].inner() instanceof String ? args[2].inner() : args[2].kismetClass().&call.curry(args[2])) : ''
					def str = args[0].inner().toString()
					def pattern = args[1].inner() instanceof Pattern ? (Pattern) args[1].inner() : args[1].inner().toString()
					str.invokeMethod('replaceAll', [pattern, replacement] as Object[])
				},
				replace_first_regex: func { IKismetObject... args ->
					def replacement = args.length > 2 ?
							(args[2].inner() instanceof String ? args[2].inner() : args[2].kismetClass().&call.curry(args[2])) : ''
					def str = args[0].inner().toString()
					def pattern = args[1].inner() instanceof Pattern ? (Pattern) args[1].inner() : args[1].inner().toString()
					str.invokeMethod('replaceFirst', [pattern, replacement] as Object[])
				},
				'blank?': func(true) { IKismetObject... args -> ((String) args[0].inner() ?: "").isAllWhitespace() },
				'whitespace?': func(true) { IKismetObject... args -> Character.isWhitespace((int) args[0].inner()) },
				quote_regex: func(true) { IKismetObject... args -> Pattern.quote((String) args[0].inner()) },
				'codepoints~': func { IKismetObject... args -> ((CharSequence) args[0].inner()).codePoints().iterator() },
				'chars~': func { IKismetObject... args -> ((CharSequence) args[0].inner()).chars().iterator() },
				chars: func { IKismetObject... args -> ((CharSequence) args[0].inner()).chars.toList() },
				codepoint_to_chars: funcc { ... args -> Character.toChars((int) args[0]).toList() },
				upper: funcc(true) { ... args ->
					args[0] instanceof Character ? Character.toUpperCase((char) args[0]) :
							args[0] instanceof Integer ? Character.toUpperCase((int) args[0]) :
									((String) args[0]).toString().toUpperCase()
				},
				lower: funcc(true) { ... args ->
					args[0] instanceof Character ? Character.toLowerCase((char) args[0]) :
							args[0] instanceof Integer ? Character.toLowerCase((int) args[0]) :
									((String) args[0]).toString().toLowerCase()
				},
				'upper?': funcc(true) { ... args ->
					args[0] instanceof Character ? Character.isUpperCase((char) args[0]) :
							args[0] instanceof Integer ? Character.isUpperCase((int) args[0]) :
									((String) args[0]).chars.every { Character it -> !Character.isLowerCase(it) }
				},
				'lower?': funcc(true) { ... args ->
					args[0] instanceof Character ? Character.isLowerCase((char) args[0]) :
							args[0] instanceof Integer ? Character.isLowerCase((int) args[0]) :
									((String) args[0]).chars.every { char it -> !Character.isUpperCase(it) }
				},
				parse_number: funcc(true) { ... args ->
					Class c = args.length > 1 ? ((WrapperKismetClass) args[1]).orig : BigDecimal
					c.newInstance((String) args[0])
				},
				strip: funcc(true) { ... args -> ((String) args[0]).trim() },
				strip_start: funcc(true) { ... args ->
					def x = (String) args[0]
					char[] chars = x.chars
					for (int i = 0; i < chars.length; ++i) {
						if (!Character.isWhitespace(chars[i]))
							return x.substring(i)
					}
					''
					/*
					defn [strip_start x] {
					  for< x.length {
					    if_then [not [whitespace? x[i]]
					  }
					}
					*/
				},
				strip_end: funcc(true) { ... args ->
					def x = (String) args[0]
					char[] chars = x.chars
					for (int i = chars.length - 1; i >= 0; --i) {
						if (!Character.isWhitespace(chars[i]))
							return x.substring(0, i + 1)
					}
					''
				},
				regex: macr(true) { Context c, Expression... a ->
					a[0] instanceof StringExpression ? ~((StringExpression) a[0]).raw
							: ~((String) a[0].evaluate(c).inner())
				},
				set_at: func { IKismetObject... a -> a[0].kismetClass().subscriptSet(a[0], a[1], a[2]) },
				at: func { IKismetObject... a -> a[0].kismetClass().subscriptGet(a[0], a[1]) },
				string: func(true) { IKismetObject... a ->
					if (a.length == 1) return a[0].toString()
					StringBuilder x = new StringBuilder()
					for (s in a) x.append(s)
					x.toString()
				},
				int: func(true) { IKismetObject... a -> a[0] as BigInteger },
				int8: func(true) { IKismetObject... a -> a[0] as byte },
				int16: func(true) { IKismetObject... a -> a[0] as short },
				int32: func(true) { IKismetObject... a -> a[0] as int },
				int64: func(true) { IKismetObject... a -> a[0] as long },
				char: func(true) { IKismetObject... a -> a[0] as Character },
				float: func(true) { IKismetObject... a -> a[0] as BigDecimal },
				float32: func(true) { IKismetObject... a -> a[0] as float },
				float64: func(true) { IKismetObject... a -> a[0] as double },
				to_iterator: funcc { ... args -> toIterator(args[0]) },
				list_iterator: funcc { ... args -> args[0].invokeMethod('listIterator', null) },
				'has_next?': funcc { ... args -> args[0].invokeMethod('hasNext', null) },
				next: funcc { ... args -> args[0].invokeMethod('next', null) },
				'has_prev?': funcc { ... args -> args[0].invokeMethod('hasPrevious', null) },
				prev: funcc { ... args -> args[0].invokeMethod('previous', null) },
				new_list: funcc { ... args -> new ArrayList(args[0] as int) },
				list: funcc { ... args -> args.toList() },
				new_set: funcc { ... args -> args.length > 1 ? new HashSet(args[0] as int, args[1] as float) : new HashSet(args[0] as int) },
				set: funcc { ... args ->
					Set x = new HashSet()
					for (a in args) x.add(a)
					x
				},
				pair: funcc { ... args -> new Pair(args[0], args[1]) },
				tuple: funcc(true) { ... args -> new Tuple(args) },
				// assert_is x [+ [bottom_half x] [top_half x]]
				bottom_half: funcc { ... args ->
					args[0] instanceof Number ? ((Number) args[0]).intdiv(2) :
							args[0] instanceof Pair ? ((Pair) args[0]).first :
									args[0] instanceof Collection ? ((Collection) args[0]).take(((Collection) args[0]).size().intdiv(2) as int) :
											args[0] instanceof Map ? ((Map) args[0]).values() :
													args[0]
				},
				top_half: funcc { ... args ->
					args[0] instanceof Number ? ((Number) args[0]).minus(((Number) args[0]).intdiv(2)) :
							args[0] instanceof Pair ? ((Pair) args[0]).second :
									args[0] instanceof Collection ? ((Collection) args[0]).takeRight(
											((Collection) args[0]).size().minus(((Collection) args[0]).size().intdiv(2)) as int) :
											args[0] instanceof Map ? ((Map) args[0]).keySet() :
													args[0]
				},
				to_list: funcc { ... args -> toList(args[0]) },
				to_set: funcc { ... args -> toSet(args[0]) },
				to_pair: funcc { ... args -> new Pair(args[0].invokeMethod('getAt', 0), args[0].invokeMethod('getAt', 1)) },
				to_tuple: funcc(true) { ... args -> new Tuple(args[0] as Object[]) },
				entry_pairs: funcc { ... args ->
					def r = []
					for (x in (args[0] as Map)) r.add(new Pair(x.key, x.value))
					r
				},
				map_from_pairs: funcc { ... args ->
					def m = new HashMap()
					for (x in args[0]) {
						def p = x as Pair
						m.put(p.first, p.second)
					}
					m
				},
				'##': macr { Context c, Expression... args ->
					final map = new HashMap()
					for (e in args) {
						expressiveMap(map, c, e)
					}
					map
				},
				uncons: funcc { ... args -> new Pair(args[0].invokeMethod('head', null), args[0].invokeMethod('tail', null)) },
				cons: funcc { ... args ->
					def y = args[1]
					def a = new ArrayList((y.invokeMethod('size', null) as int) + 1)
					a.add(args[0])
					a.addAll(y)
					a
				},
				intersperse: funcc { ... args ->
					def r = []
					boolean x = false
					for (a in args[0]) {
						if (x) r.add(args[1])
						else x = true
						r.add(a)
					}
					r
				},
				intersperse_all: funcc { ... args ->
					def r = []
					boolean x = false
					for (a in args[0]) {
						if (x) r.addAll(args[1])
						else x = true
						r.add(a)
					}
					r
				},
				memoize: func { IKismetObject... args ->
					def x = args[1]
					Map<IKismetObject[], IKismetObject> results = new HashMap<>()
					func { IKismetObject... a ->
						def p = results.get(a)
						null == p ? x.kismetClass().call(x, a) : p
					}
				},
				name_expr: func { IKismetObject... args -> new NameExpression(args[0].toString()) },
				'name_expr?': func { IKismetObject... args -> args[0].inner() instanceof NameExpression },
				expr_to_name: func { IKismetObject... args ->
					def i = args[0].inner()
					i instanceof Expression ? toAtom((Expression) i) : null
				},
				static_expr: funcc { ... args -> StaticExpression.class.newInstance(args) },
				'static_expr?': func { IKismetObject... args -> args[0].inner() instanceof StaticExpression },
				number_expr: func { IKismetObject... args -> new NumberExpression(args[0].toString()) },
				'number_expr?': func { IKismetObject... args -> args[0].inner() instanceof NumberExpression },
				string_expr: func { IKismetObject... args -> new StringExpression(args[0].toString()) },
				'string_expr?': func { IKismetObject... args -> args[0].inner() instanceof StringExpression },
				call_expr: funcc { ... args -> new CallExpression(args.toList() as List<Expression>) },
				'call_expr?': func { IKismetObject... args -> args[0].inner() instanceof CallExpression },
				block_expr: funcc { ... args -> new BlockExpression(args.toList() as List<Expression>) },
				'block_expr?': func { IKismetObject... args -> args[0].inner() instanceof BlockExpression },
				escape: funcc { ... args -> StringEscaper.escape(args[0].toString()) },
				unescape: funcc { ... args -> StringEscaper.escape(args[0].toString()) },
				copy_map: funcc { ... args -> new HashMap(args[0] as Map) },
				new_map: funcc { ... args -> args.length > 1 ? new HashMap(args[0] as int, args[1] as float) :
						args.length == 1 ? new HashMap(args[0] as int) : new HashMap() },
				zip: funcc { ... args -> args.toList().transpose() },
				knit: func { IKismetObject... args ->
					toList(args[0].inner()).transpose()
							.collect { args[1].invokeMethod('call', it as Object[]) }
				},
				transpose: funcc { ... args -> toList(args[0]).transpose() },
				'unique?': funcc { ... args ->
					args[0].invokeMethod('size', null) ==
							args[0].invokeMethod('unique', false).invokeMethod('size', null)
				},
				'unique!': funcc { ... args -> args[0].invokeMethod('unique', null) },
				unique: funcc { ... args -> args[0].invokeMethod('unique', false) },
				'unique_via?': func { IKismetObject... args ->
					args[0].inner().invokeMethod('size', null) ==
							args[0].inner().invokeMethod('unique', [false, args[1].kismetClass().&call.curry(args[1])] as Object[])
				},
				'unique_via!': func { IKismetObject... args -> args[0].inner().invokeMethod('unique', args[1].kismetClass().&call.curry(args[1])) },
				unique_via: func { IKismetObject... args -> args[0].inner().invokeMethod('unique', [false, args[1].kismetClass().&call.curry(args[1])]) },
				spread: funcc { ... args -> args[0].invokeMethod('toSpreadMap', null) },
				invert_map: funcc { ... args ->
					final m0 = args[0] as Map
					def m = new HashMap(m0.size())
					for (final e : m0.entrySet()) {
						m.put(e.value, e.key)
					}
					m
				},
				new_json_parser: funcc { ... args -> new JsonSlurper() },
				parse_json: funcc { ... args ->
					String text = args.length > 1 ? args[1].toString() : args[0].toString()
					JsonSlurper sl = args.length > 1 ? args[0] as JsonSlurper : new JsonSlurper()
					sl.parseText(text)
				},
				to_json: funcc { ... args -> ((Object) JsonOutput).invokeMethod('toJson', [args[0]] as Object[]) },
				pretty_print_json: funcc { ... args -> JsonOutput.prettyPrint(args[0].toString()) },
				size: funcc { ... a -> a[0].invokeMethod('size', null) },
				keys: funcc { ... a -> a[0].invokeMethod('keySet', null).invokeMethod('toList', null) },
				values: funcc { ... a -> a[0].invokeMethod('values', null) },
				reverse: funcc { ... a -> a[0].invokeMethod('reverse', a[0] instanceof CharSequence ? null : false) },
				'reverse!': funcc { ... a -> a[0].invokeMethod('reverse', null) },
				'reverse?': funcc { ... a -> a[0].invokeMethod('reverse', false) == a[1] },
				sprintf: funcc { ... args -> String.invokeMethod('format', args) },
				expr_type: funcc { ... args ->
					args[0] instanceof Expression ?
							(args[0].class.simpleName - 'Expression').uncapitalize() : null
				},
				capitalize: func { IKismetObject... args -> args[0].toString().capitalize() },
				uncapitalize: func { IKismetObject... args -> args[0].toString().uncapitalize() },
				center: funcc { ... args ->
					args.length > 2 ? args[0].toString().center(args[1] as Number, args[2].toString()) :
							args[0].toString().center(args[1] as Number)
				},
				pad_start: funcc { ... args ->
					args.length > 2 ? args[0].toString().padLeft(args[1] as Number, args[2].toString()) :
							args[0].toString().padLeft(args[1] as Number)
				},
				pad_end: funcc { ... args ->
					args.length > 2 ? args[0].toString().padRight(args[1] as Number, args[2].toString()) :
							args[0].toString().padRight(args[1] as Number)
				},
				'prefix?': funcc { ... args ->
					if (args[1] instanceof String) ((String) args[1]).startsWith(args[0].toString())
					else Collections.indexOfSubList(toList(args[1]), toList(args[0])) == 0
				},
				'suffix?': funcc { ... args ->
					if (args[1] instanceof String) ((String) args[1]).endsWith(args[0].toString())
					else {
						def a = toList(args[0])
						def b = toList(args[1])
						Collections.lastIndexOfSubList(b, a) == b.size() - a.size()
					}
				},
				'infix?': funcc { ... args ->
					if (args[1] instanceof String) ((String) args[1]).contains(args[0].toString())
					else Collections.invokeMethod('indexOfSubList', [toList(args[1]), toList(args[0])] as Object[]) != -1
				},
				'subset?': funcc { ... args -> args[1].invokeMethod('containsAll', [args[0]] as Object[]) },
				'rotate!': funcc { ... args ->
					List x = (List) args[0]
					Collections.rotate(x, args[1] as int)
					x
				},
				rotate: funcc { ... args ->
					def x = new ArrayList(toList(args[0]))
					Collections.rotate(x, args[1] as int)
					x
				},
				lines: funcc { ... args -> args[0].invokeMethod('readLines', null) },
				denormalize: funcc { ... args -> args[0].toString().denormalize() },
				normalize: funcc { ... args -> args[0].toString().normalize() },
				hex: new AbstractTemplate() {
					@Override
					Expression transform(Parser parser, Expression... args) {
						if (args[0] instanceof NumberExpression || args[0] instanceof NameExpression) {
							String t = args[0] instanceof NumberExpression ?
									((NumberExpression) args[0]).value.inner().toString() :
									((NameExpression) args[0]).text
							new NumberExpression(new BigInteger(t, 16))
						} else throw new UnexpectedSyntaxException('Expression after hex was not a hexadecimal number literal.'
								+ ' To convert hex strings to integers do [from_base str 16], '
								+ ' and to convert integers to hex strings do [to_base i 16].')
					}
				},
				binary: new AbstractTemplate() {
					@Override
					Expression transform(Parser parser, Expression... args) {
						if (args[0] instanceof NumberExpression) {
							String t = ((NumberExpression) args[0]).value.inner().toString()
							new NumberExpression(new BigInteger(t, 2))
						} else throw new UnexpectedSyntaxException('Expression after binary was not a binary number literal.'
								+ ' To convert binary strings to integers do [from_base str 2], '
								+ ' and to convert integers to binary strings do [to_base i 2].')
					}
				},
				octal: new AbstractTemplate() {
					@Override
					Expression transform(Parser parser, Expression... args) {
						if (args[0] instanceof NumberExpression) {
							String t = ((NumberExpression) args[0]).value.inner().toString()
							new NumberExpression(new BigInteger(t, 8))
						} else throw new UnexpectedSyntaxException('Expression after octal was not a octal number literal.'
								+ ' To convert octal strings to integers do [from_base str 8], '
								+ ' and to convert integers to octal strings do [to_base i 8].')
					}
				},
				to_base: funcc { ... a -> (a[0] as BigInteger).toString(a[1] as int) },
				from_base: funcc { ... a -> new BigInteger(a[0].toString(), a[1] as int) },
				':::=': macr { Context c, Expression... x ->
					if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for :::=')
					IKismetObject value = x.length == 1 ? Kismet.NULL : x.last().evaluate(c)
					int i = 1
					for (a in x.init()) {
						c.change(resolveName(a, c, ":::= argument $i"), value)
						++i
					}
					value
				},
				'::=': macr { Context c, Expression... x ->
					if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for ::=')
					IKismetObject value = x.length == 1 ? Kismet.NULL : x.last().evaluate(c)
					int i = 1
					for (a in x.init()) {
						c.set(resolveName(a, c, "::= argument $i"), value)
						++i
					}
					value
				},
				':=': macr { Context c, Expression... x ->
					if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for :=')
					IKismetObject value = x.length == 1 ? Kismet.NULL : x.last().evaluate(c)
					int i = 1
					for (a in x.init()) {
						c.define(resolveName(a, c, ":= argument $i"), value)
						++i
					}
					value
				},
				'=': macr { Context c, Expression... x ->
					if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for =')
					assign(c, x.init(), x.length == 1 ? Kismet.NULL : x.last().evaluate(c))
				},
				'+=': (Template) { Expression... args ->
					new CallExpression([new NameExpression('='), args[0],
							new CallExpression([new NameExpression('+'), args[0], args[1]])])
				},
				def: macr { Context c, Expression... x ->
					if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for :=')
					if (x[0] instanceof NameExpression) {
						IKismetObject value = x.length == 1 ? Kismet.NULL : x.last().evaluate(c)
						int i = 1
						for (a in x.init()) {
							c.define(resolveName(a, c, "def argument $i"), value)
							++i
						}
						value
					} else {
						final f = new KismetFunction(c, true, x)
						c.define(f.name, Kismet.model(f))
					}
				},
				fn: new AbstractTemplate() {
					@Override
					Expression transform(Parser parser, Expression... args) {
						KismetFunction.Arguments arguments
						final a = args[0]
						if (args.length == 1) {
							arguments = KismetFunction.Arguments.EMPTY
						} else {
							arguments = new KismetFunction.Arguments(a instanceof CallExpression ?
									((CallExpression) a).members : a instanceof BlockExpression ?
									((BlockExpression) a).members : [a])
						}
						new CallExpression(new StaticExpression(new ContextFunction() {
							IKismetObject call(Context c, IKismetObject... _) {
								Kismet.model new KismetFunction(arguments, args.length == 1 ? c.child(a) :
										c.childBlock(args.tail()))
							}
						}))
					}
				},
				mcr: macr { Context c, Expression... exprs ->
					new KismetMacro(c.childBlock(exprs))
				},
				defn: macr { Context c, Expression... exprs ->
					final x = new KismetFunction(c, true, exprs)
					c.define(x.name, Kismet.model(x))
				},
				tmpl: macr { Context c, Expression... exprs ->
					new AbstractTemplate() {
						@CompileStatic
						Expression transform(Parser parser, Expression... args) {
							final co = c.childBlock(exprs)
							for (int i = 0; i < args.length; ++i) {
								co.context.set("\$$i", Kismet.model(args[i]))
							}
							co().inner() as Expression
						}
					}
				},
				defmcr: new AbstractTemplate() {
					@CompileStatic
					Expression transform(Parser parser, Expression... args) {
						def a = new ArrayList<Expression>()
						a.add(new NameExpression('mcr'))
						for (int i = 1; i < args.length; ++i)
							a.add(args[i])
						new CallExpression([new NameExpression(':='), args[0], new CallExpression(a)])
					}
				},
				'fn*': new AbstractTemplate() {
					@Override
					Expression transform(Parser parser, Expression... args) {
						def kill = new ArrayList<Expression>()
						kill.add(new NameExpression('fn'))
						kill.addAll(args)
						new CallExpression(new NameExpression('fn'), new CallExpression(
								new NameExpression('call'), new CallExpression(kill),
								new NameExpression('$0')))
					}
				},
				undef: macr { Context c, Expression... exprs ->
					c.getVariables().remove(c.getVariable(exprs[0].evaluate(c).toString()))
				},
				block: new Macro() {
					@CompileStatic
					IKismetObject call(Context c, Expression... args) {
						Kismet.model(c.childBlock(args))
					}
				},
				incr: new AbstractTemplate() {
					Expression transform(Parser parser, Expression... args) {
						new CallExpression(new NameExpression('='), args[0],
								args.length > 1 ? new CallExpression(new NameExpression('+'), args[0], args[1]) :
										new CallExpression(new NameExpression('next'), args[0]))
					}
				},
				decr: new AbstractTemplate() {
					Expression transform(Parser parser, Expression... args) {
						new CallExpression(new NameExpression('='), args[0],
								args.length > 1 ? new CallExpression(new NameExpression('-'), args[0], args[1]) :
										new CallExpression(new NameExpression('prev'), args[0]))
					}
				},
				'|>=': new AbstractTemplate() {
					@CompileStatic
					Expression transform(Parser parser, Expression... args) {
						new CallExpression([new NameExpression('='), args[0], pipeForwardExpr(args[0], args.tail().toList())])
					}
				},
				'<|=': new AbstractTemplate() {
					@CompileStatic
					Expression transform(Parser parser, Expression... args) {
						new CallExpression([new NameExpression('='), args[0], pipeBackwardExpr(args[0], args.tail().toList())])
					}
				},
				dive: new AbstractTemplate() {
					@CompileStatic
					Expression transform(Parser parser, Expression... args) {
						new DiveExpression(args.length == 1 ? args[0] : new BlockExpression(args.toList()))
					}
				},
				static: new AbstractTemplate() {
					@CompileStatic
					Expression transform(Parser parser, Expression... args) {
						new StaticExpression(args.length == 1 ? args[0] : new BlockExpression(args.toList()), parser.context)
					}
				},
				let: new AbstractTemplate() {
					@CompileStatic
					Expression transform(Parser parser, Expression... args) {
						if (args.length == 0) throw new UnexpectedSyntaxException('Empty let expression not allowed')
						if (!(args[0] instanceof CallExpression))
							throw new UnexpectedSyntaxException("Did not expect " + args[0].class + " in let expression")
						final cnt = (CallExpression) args[0]
						Expression resultVar = null
						final len = cnt.arguments.size() + 1
						def vars = new ArrayList<Expression>((int) (len / 2))
						final defs = cnt.members.iterator()
						while (defs.hasNext()) {
							final key = defs.next()
							if (!defs.hasNext()) {
								println "warning; ignoring odd expression in let definitions: $key"
								break
							}
							CallExpression temp
							if (key instanceof CallExpression &&
								toAtom((temp = ((CallExpression) key)).callValue) == 'result' &&
								temp.arguments.size() == 1) {
								vars.add(resultVar = temp[1])
							} else {
								vars.add(key)
							}
							vars.add(defs.next())
						}
						def resultExpr = new ArrayList<Expression>((int) (vars.size() / 2) + args.length - (null == resultVar ? 1 : 0))
						final variter = vars.iterator()
						while (variter.hasNext()) {
							resultExpr.add(new CallExpression(new NameExpression('::='), variter.next(), variter.next()))
						}
						resultExpr.addAll(args.tail())
						if (null != resultVar) resultExpr.add(resultVar)
						final r = new BlockExpression(resultExpr)
						args.length == 1 ? r : new DiveExpression(r)
					}
				},
				eval: macr { Context c, Expression... a ->
					def x = a[0].evaluate(c)
					if (x.inner() instanceof Block) ((Block) x.inner()).evaluate()
					else if (x.inner() instanceof Expression)
						((Expression) x.inner()).evaluate(a.length > 1 ? a[1].evaluate(c).inner() as Context : c)
					else if (x.inner() instanceof String)
						if (a.length > 1)
							new Parser(context: a.length > 2 ? a[2].evaluate(c).inner() as Context : c)
									.parse((String) x.inner())
									.evaluate(a[1].evaluate(c).inner() as Context)
						else c.childEval(new Parser(context: c).parse((String) x.inner()))
					else throw new UnexpectedValueException('Expected first callValue of eval to be an expression, block, path or string')
				},
				quote: new AbstractTemplate() {
					@CompileStatic
					Expression transform(Parser parser, Expression... args) {
						def slowdown = new ArrayList<Expression>(args.length + 1)
						slowdown.add(new NameExpression('quote'))
						slowdown.addAll(args)
						new StaticExpression(new CallExpression(slowdown),
							args.length == 1 ? args[0] :
									new BlockExpression(args.toList()))
					}
				},
				if_then: macr { Context c, Expression... exprs ->
					exprs[0].evaluate(c) ? c.childEval(exprs.tail()) : Kismet.NULL
				},
				get_or_set: macr { Context c, Expression... args ->
					final a0 = args[0].evaluate(c)
					final a1 = args[1].evaluate(c)
					final v = a0.kismetClass().subscriptGet(a0, a1)
					null == v.inner() ? a0.kismetClass().subscriptSet(a0, a1, args[2].evaluate(c)) : v
				},
				if: macr { Context c, Expression... exprs ->
					c = c.child()
					if (exprs.length == 2)
						c.eval(exprs[0]) ? c.eval(exprs[1]) : Kismet.NULL
					else {
						def cond = c.eval(exprs[0])
						int b = 1
						int i = 1
						for (int s = exprs.length; i < s; ++i) {
							def x = exprs[i]
							if (x instanceof NameExpression) {
								String text = ((NameExpression) x).text
								if (text == 'else')
									return cond ?
											c.eval(Arrays.copyOfRange(exprs, b, i)) :
											c.eval(Arrays.copyOfRange(exprs, i + 1, exprs.length))
								else if (text == 'else?') {
									if (cond) return c.eval(Arrays.copyOfRange(exprs, b, i))
									cond = exprs[++i].evaluate(c)
									b = i + 1
								}
							}
						}
						c.eval(Arrays.copyOfRange(exprs, b, i))
					}
				},
				unless_then: macr { Context c, Expression... exprs ->
					!exprs[0].evaluate(c) ? c.childEval(exprs.tail()) : Kismet.NULL
				},
				unless: macr { Context c, Expression... exprs ->
					c = c.child()
					if (exprs.length == 2)
						!c.eval(exprs[0]) ? c.eval(exprs[1]) : Kismet.NULL
					else {
						def cond = c.eval(exprs[0])
						int b = 1
						int i = 1
						for (int s = exprs.length; i < s; ++i) {
							def x = exprs[i]
							if (x instanceof NameExpression) {
								String text = ((NameExpression) x).text
								if (text == 'else')
									return !cond ?
											c.eval(Arrays.copyOfRange(exprs, b, i)) :
											c.eval(Arrays.copyOfRange(exprs, i + 1, exprs.length))
								else if (text == 'else?') {
									if (!cond) return c.eval(Arrays.copyOfRange(exprs, b, i))
									cond = exprs[++i].evaluate(c)
									b = i + 1
								}
							}
						}
						c.eval(Arrays.copyOfRange(exprs, b, i))
					}
				},
				if_chain: macr { Context c, Expression... ab ->
					Iterator<Expression> a = ab.iterator()
					IKismetObject x = Kismet.NULL
					while (a.hasNext()) {
						x = c.childEval(a.next())
						if (a.hasNext())
							if (x) return c.childEval(a.next())
							else a.next()
						else return x
					}
					x
				},
				unless_chain: macr { Context c, Expression... ab ->
					Iterator<Expression> a = ab.iterator()
					IKismetObject x = Kismet.NULL
					while (a.hasNext()) {
						x = c.childEval(a.next())
						if (a.hasNext())
							if (!x) return c.childEval(a.next())
							else a.next()
						else return x
					}
					x
				},
				'or?': macr { Context c, Expression... x -> x[0].evaluate(c) ? c.childEval(x[1]) : c.childEval(x[2]) },
				'not_or?': macr { Context c, Expression... x ->
					!x[0].evaluate(c) ? c.childEval(x[1]) :
							c.childEval(x[2])
				},
				check: macr { Context c, Expression... ab ->
					Iterator<Expression> a = ab.iterator()
					IKismetObject val = a.next().evaluate(c)
					while (a.hasNext()) {
						def b = a.next()
						if (a.hasNext())
							if (check(c, val, b)) return a.next().evaluate(c)
							else a.next()
						else return b.evaluate(c)
					}
					val
				},
				while: macr { Context c, Expression... exprs ->
					final l = exprs.toList()
					IKismetObject j = Kismet.NULL
					while (exprs[0].evaluate(c)) j = c.childEval(l)
					j
				},
				until: macr { Context c, Expression... exprs ->
					final l = exprs.toList()
					IKismetObject j = Kismet.NULL
					while (!exprs[0].evaluate(c)) j = c.childEval(l)
					j
				},
				'for:': macr { Context c, Expression... exprs ->
					String n = 'it'
					Block b = c.childBlock(exprs.drop(2))
					def range = exprs[0] instanceof CallExpression ? ((CallExpression) exprs[0]).members : [exprs[0]]
					Iterator iter
					int i = 0
					String ni
					c = b.context
					if (range.size() == 1) {
						iter = toIterator(range[0].evaluate(c).inner())
					} else if (range.size() == 2) {
						n = resolveName(range[0], c, 'for:')
						iter = toIterator(range[1].evaluate(c).inner())
					} else {
						def ic = range[0]
						if (ic instanceof CallExpression) {
							ni = resolveName(((CallExpression) ic).callValue, c, 'for:')
							i = ((CallExpression) ic).arguments[0].evaluate(c).inner() as int
						} else ni = resolveName(ic, c, 'for:')
						n = resolveName(range[1], c, 'for:')
						iter = toIterator(range[2].evaluate(c).inner())
					}
					for (; iter.hasNext(); ++i) {
						def it = iter.next()
						Block y = b.child()
						y.context.set(n, Kismet.model(it))
						if (null != ni) y.context.set(ni, Kismet.model(i))
						y()
					}
					Kismet.NULL
				},
				'&for:': macr { Context c, Expression... exprs ->
					String n = 'it'
					Block b = c.childBlock(exprs.drop(2))
					def range = exprs[0] instanceof CallExpression ? ((CallExpression) exprs[0]).members : [exprs[0]]
					Iterator iter
					int i = 0
					String ni
					c = b.context
					if (range.size() == 1) {
						iter = toIterator(range[0].evaluate(c).inner())
					} else if (range.size() == 2) {
						n = resolveName(range[0], c, '&for:')
						iter = toIterator(range[1].evaluate(c).inner())
					} else {
						def ic = range[0]
						if (ic instanceof CallExpression) {
							ni = resolveName(((CallExpression) ic).callValue, c, '&for:')
							i = ((CallExpression) ic).arguments[0].evaluate(c).inner() as int
						} else ni = resolveName(ic, c, '&for:')
						n = resolveName(range[1], c, '&for:')
						iter = toIterator(range[2].evaluate(c).inner())
					}
					def result = new ArrayList()
					for (; iter.hasNext(); ++i) {
						def it = iter.next()
						Block y = b.child()
						y.context.set(n, Kismet.model(it))
						if (null != ni) y.context.set(ni, Kismet.model(i))
						result.add(y())
					}
					result
				},
				for: macr { Context c, Expression... exprs ->
					Block b = c.childBlock(exprs.tail())
					def range = exprs[0] instanceof CallExpression ? ((CallExpression) exprs[0]).members : [exprs[0]]
					String n
					int bottom, top
					if (range.size() == 1) {
						n = 'it'
						bottom = 1
						top = range[0].evaluate(b.context).inner() as int
					} else if (range.size() == 2) {
						n = 'it'
						bottom = range[0].evaluate(b.context).inner() as int
						top = range[1].evaluate(b.context).inner() as int
					} else {
						n = resolveName(range[0], c, 'for')
						bottom = range[1].evaluate(b.context).inner() as int
						top = range[2].evaluate(b.context).inner() as int
					}
					int step = null == range[3] ? 1 : range[3].evaluate(b.context).inner() as int
					for (; bottom <= top; bottom += step) {
						Block y = b.child()
						y.context.set(n, Kismet.model(bottom))
						y()
					}
					Kismet.NULL
				},
				'&for': macr { Context c, Expression... exprs ->
					Block b = c.childBlock(exprs.tail())
					def range = exprs[0] instanceof CallExpression ? ((CallExpression) exprs[0]).members : [exprs[0]]
					String n
					int bottom, top
					if (range.size() == 1) {
						n = 'it'
						bottom = 1
						top = range[0].evaluate(b.context).inner() as int
					} else if (range.size() == 2) {
						n = 'it'
						bottom = range[0].evaluate(b.context).inner() as int
						top = range[1].evaluate(b.context).inner() as int
					} else {
						n = resolveName(range[0], c, '&for')
						bottom = range[1].evaluate(b.context).inner() as int
						top = range[2].evaluate(b.context).inner() as int
					}
					int step = null == range[3] ? 1 : range[3].evaluate(b.context).inner() as int
					def a = new ArrayList<IKismetObject>()
					for (; bottom <= top; bottom += step) {
						Block y = b.child()
						y.context.set(n, Kismet.model(bottom))
						a.add(y())
					}
					a
				},
				'for<': macr { Context c, Expression... exprs ->
					Block b = c.childBlock(exprs.tail())
					def range = exprs[0] instanceof CallExpression ? ((CallExpression) exprs[0]).members : [exprs[0]]
					String n
					int bottom, top
					if (range.size() == 1) {
						n = 'it'
						bottom = 0
						top = range[0].evaluate(b.context).inner() as int
					} else if (range.size() == 2) {
						n = 'it'
						bottom = range[0].evaluate(b.context).inner() as int
						top = range[1].evaluate(b.context).inner() as int
					} else {
						n = resolveName(range[0], c, 'for')
						bottom = range[1].evaluate(b.context).inner() as int
						top = range[2].evaluate(b.context).inner() as int
					}
					int step = null == range[3] ? 1 : range[3].evaluate(b.context).inner() as int
					for (; bottom < top; bottom += step) {
						Block y = b.child()
						y.context.set(n, Kismet.model(bottom))
						y()
					}
					Kismet.NULL
				},
				'&for<': macr { Context c, Expression... exprs ->
					Block b = c.childBlock(exprs.tail())
					def range = exprs[0] instanceof CallExpression ? ((CallExpression) exprs[0]).members : [exprs[0]]
					String n
					int bottom, top
					if (range.size() == 1) {
						n = 'it'
						bottom = 0
						top = range[0].evaluate(b.context).inner() as int
					} else if (range.size() == 2) {
						n = 'it'
						bottom = range[0].evaluate(b.context).inner() as int
						top = range[1].evaluate(b.context).inner() as int
					} else {
						n = resolveName(range[0], c, '&for')
						bottom = range[1].evaluate(b.context).inner() as int
						top = range[2].evaluate(b.context).inner() as int
					}
					int step = null == range[3] ? 1 : range[3].evaluate(b.context).inner() as int
					def a = new ArrayList<IKismetObject>()
					for (; bottom < top; bottom += step) {
						Block y = b.child()
						y.context.set(n, Kismet.model(bottom))
						a.add(y())
					}
					a
				},
				each: func { IKismetObject... args -> args[0].inner().each(args[1].kismetClass().&call.curry(args[1])) },
				each_with_index: func { IKismetObject... args -> args[0].inner().invokeMethod('eachWithIndex', args[1].kismetClass().&call.curry(args[1])) },
				collect: func { IKismetObject... args ->
					final fn = args[1]
					final cl = args[1].kismetClass()
					args[0].inner().collect { ...a ->
						def arr = new IKismetObject[a.length]
						for (int i = 0; i < a.length; ++i) {
							arr[i] = Kismet.model(a[i])
						}
						cl.call(fn, arr)
					}
				},
				collect_nested: func { IKismetObject... args -> args[0].inner().invokeMethod('collectNested', args[1].kismetClass().&call.curry(args[1])) },
				collect_many: func { IKismetObject... args -> args[0].inner().invokeMethod('collectMany', args[1].kismetClass().&call.curry(args[1])) },
				collect_map: func { IKismetObject... args ->
					args[0].inner()
							.invokeMethod('collectEntries') { ... a -> args[1].kismetClass().call(args[1], Kismet.model(a)).inner() }
				},
				subsequences: funcc { ... args -> args[0].invokeMethod('subsequences', null) },
				combinations: funcc { ... args -> args[0].invokeMethod('combinations', null) },
				permutations: funcc { ... args -> args[0].invokeMethod('permutations', null) },
				'permutations~': funcc { ... args ->
					new PermutationGenerator(args[0] instanceof Collection ? (Collection) args[0]
							: args[0] instanceof Iterable ? (Iterable) args[0]
							: args[0] instanceof Iterator ? new IteratorIterable((Iterator) args[0])
							: args[0] as Collection)
				},
				'any?': func { IKismetObject... args ->
					args.length > 1 ? args[0].inner().any(args[1].kismetClass().&call.curry(args[1])) : args[0].inner().any()
				},
				'every?': func { IKismetObject... args ->
					args.length > 1 ? args[0].inner().every(args[1].kismetClass().&call.curry(args[1])) : args[0].inner().every()
				},
				'none?': func { IKismetObject... args ->
					!(args.length > 1 ? args[0].inner().any(args[1].kismetClass().&call.curry(args[1])) : args[0].inner().any())
				},
				find: func { IKismetObject... args -> args[0].inner().invokeMethod('find', args[1].kismetClass().&call.curry(args[1])) },
				find_result: func { IKismetObject... args -> args[0].inner().invokeMethod('findResult', args[1].kismetClass().&call.curry(args[1])) },
				count: func { IKismetObject... args -> args[0].inner().invokeMethod('count', args[1].kismetClass().&call.curry(args[1])) },
				count_element: func { IKismetObject... args ->
					BigInteger i = 0
					def a = args[1].inner()
					def iter = args[0].iterator()
					while (iter.hasNext()) {
						def x = iter.next()
						if (x instanceof IKismetObject) x = x.inner()
						if (a == x) ++i
					}
					i
				},
				count_elements: func { IKismetObject... args ->
					BigInteger i = 0
					def c = args.tail()
					def b = new Object[c.length]
					for (int m = 0; m < c.length; ++i) b[m] = c[m].inner()
					boolean j = args.length == 1
					def iter = args[0].iterator()
					outer:
					while (iter.hasNext()) {
						def x = iter.next()
						if (x instanceof IKismetObject) x = x.inner()
						if (j) ++i
						else for (a in b) if (a == x) {
							++i
							continue outer
						}
					}
					i
				},
				count_by: func { IKismetObject... args -> args[0].inner().invokeMethod('countBy', args[1].kismetClass().&call.curry(args[1])) },
				group_by: func { IKismetObject... args -> args[0].inner().invokeMethod('groupBy', args[1].kismetClass().&call.curry(args[1])) },
				indexed: func { IKismetObject... args -> args[0].inner().invokeMethod('indexed', args.length > 1 ? args[1] as int : null) },
				find_all: func { IKismetObject... args -> args[0].inner().findAll(args[1].kismetClass().&call.curry(args[1])) },
				join: funcc { ... args ->
					args[0].invokeMethod('join', args.length > 1 ? args[1].toString() : '')
				},
				inject: func { IKismetObject... args -> args[0].inner().inject { a, b -> args[1].kismetClass().call(args[1], Kismet.model(a), Kismet.model(b)) } },
				collate: funcc { ... args -> args[0].invokeMethod('collate', args.tail()) },
				pop: funcc { ... args -> args[0].invokeMethod('pop', null) },
				add: func { IKismetObject... args -> args[0].invokeMethod('add', args[1]) },
				add_at: funcc { ... args -> args[0].invokeMethod('add', [args[1] as int, args[2]]) },
				add_all: funcc { ... args -> args[0].invokeMethod('addAll', args[1]) },
				add_all_at: funcc { ... args -> args[0].invokeMethod('addAll', [args[1] as int, args[2]]) },
				remove: funcc { ... args -> args[0].invokeMethod('remove', args[1]) },
				remove_elements: funcc { ... args -> args[0].invokeMethod('removeAll', args[1]) },
				remove_any: func { IKismetObject... args -> args[0].inner().invokeMethod('removeAll', args[1].kismetClass().&call.curry(args[1])) },
				remove_element: funcc { ... args -> args[0].invokeMethod('removeElement', args[1]) },
				get: funcc { ... a ->
					def r = a[0]
					for (int i = 1; i < a.length; ++i)
						r = r.invokeMethod('get', a[i])
					r
				},
				walk: macr { Context c, Expression... args ->
					def r = args[0].evaluate(c)
					for (int i = 1; i < args.length; ++i) {
						Expression x = args[i]
						if (x instanceof NameExpression)
							r = r.invokeMethod('getAt', ((NameExpression) x).text)
						else r = r.invokeMethod('getAt', x.evaluate(c))
					}
					r
				},
				empty: funcc { ... args -> args[0].invokeMethod('clear', null) },
				put: funcc { ... args -> args[0].invokeMethod('put', [args[1], args[2]]) },
				put_all: funcc { ... args -> args[0].invokeMethod('putAll', args[1]) },
				'keep_all!': funcc { ... args -> args[0].invokeMethod('retainAll', args[1]) },
				'keep_any!': func { IKismetObject... args -> args[0].inner().invokeMethod('retainAll', args[1].kismetClass().&call.curry(args[1])) },
				'has?': funcc { ... args -> args[0].invokeMethod('contains', args[1]) },
				'has_all?': funcc { ... args -> args[0].invokeMethod('containsAll', args[1]) },
				'has_key?': funcc { ... args -> args[0].invokeMethod('containsKey', args[1]) },
				'has_key_traverse?': funcc { ... args ->
					def x = args[0]
					for (a in args.tail()) {
						if (!((boolean) x.invokeMethod('containsKey', args[1]))) return false
						else x = args[0].invokeMethod('getAt', a)
					}
					true
				},
				'has_value?': funcc { ... args -> args[0].invokeMethod('containsValue', args[1]) },
				'is_code_kismet?': funcc { ... args -> args[0] instanceof KismetFunction || args[0] instanceof KismetMacro },
				'disjoint?': funcc { ... args -> args[0].invokeMethod('disjoint', args[1]) },
				'intersect?': funcc { ... args -> !args[0].invokeMethod('disjoint', args[1]) },
				call: func { IKismetObject... args ->
					def x = args[1].inner() as Object[]
					def ar = new IKismetObject[x.length]
					for (int i = 0; i < ar.length; ++i) {
						ar[i] = Kismet.model(x[i])
					}
					args[0].kismetClass().call(args[0], ar)
				},
				range: funcc { ... args -> args[0]..args[1] },
				parse_independent_kismet: func { IKismetObject... args -> Kismet.parse(args[0].toString()) },
				parse_kismet: new ContextFunction() {
					@CompileStatic
					IKismetObject call(Context c, IKismetObject... args) {
						Kismet.model(new Parser(context:
							args.length > 1 ? (Context) args[1].inner() :
								c.child()).parse(args[0].toString()))
					}
				},
				context_child: funcc { ... args ->
					def b = args[0] as Context
					args.length > 1 ? b.invokeMethod('child', args.tail()) : b.child()
				},
				context_child_eval: funcc { ... args -> (args[0] as Context).invokeMethod('childEval', args.tail()) },
				'sort!': funcc { ... args -> args[0].invokeMethod('sort', null) },
				sort: funcc { ... args -> args[0].invokeMethod('sort', false) },
				'sort_via!': func { IKismetObject... args -> args[0].inner().invokeMethod('sort', args[1].kismetClass().&call.curry(args[1])) },
				sort_via: func { IKismetObject... args -> args[0].inner().invokeMethod('sort', [false, args[1].kismetClass().&call.curry(args[1])]) },
				head: funcc { ... args -> args[0].invokeMethod('head', null) },
				tail: funcc { ... args -> args[0].invokeMethod('tail', null) },
				init: funcc { ... args -> args[0].invokeMethod('init', null) },
				last: funcc { ... args -> args[0].invokeMethod('last', null) },
				first: funcc { ... args -> args[0].invokeMethod('first', null) },
				immutable: funcc { ... args -> args[0].invokeMethod('asImmutable', null) },
				identity: Function.IDENTITY,
				flatten: funcc { ... args -> args[0].invokeMethod('flatten', null) },
				concat_list: funcc { ... args ->
					def c = new ArrayList()
					for (int i = 0; i < args.length; ++i) {
						final x = args[i]
						x instanceof Collection ? c.addAll(x) : c.add(x)
					}
					c
				},
				concat_set: funcc { ... args ->
					def c = new HashSet()
					for (int i = 0; i < args.length; ++i) {
						final x = args[i]
						x instanceof Collection ? c.addAll(x) : c.add(x)
					}
					c
				},
				concat_tuple: funcc { ... args ->
					def c = new ArrayList()
					for (int i = 0; i < args.length; ++i) {
						final x = args[i]
						x instanceof Collection ? c.addAll(x) : c.add(x)
					}
					new Tuple(c.toArray())
				},
				indices: funcc { ... args -> args[0].invokeMethod('getIndices', null) },
				find_index: func { IKismetObject... args -> args[0].inner().invokeMethod('findIndexOf', args[1].kismetClass().&call.curry(args[1])) },
				find_index_after: func { IKismetObject... args ->
					args[0].inner()
							.invokeMethod('findIndexOf', [args[1] as int, args[2].kismetClass().&call.curry(args[2])])
				},
				find_last_index: func { IKismetObject... args -> args[0].inner().invokeMethod('findLastIndexOf', args[1].kismetClass().&call.curry(args[1])) },
				find_last_index_after: func { IKismetObject... args ->
					args[0].inner()
							.invokeMethod('findLastIndexOf', [args[1] as int, args[2].kismetClass().&call.curry(args[2])])
				},
				find_indices: func { IKismetObject... args -> args[0].inner().invokeMethod('findIndexValues', args[1].kismetClass().&call.curry(args[1])) },
				find_indices_after: func { IKismetObject... args ->
					args[0].inner()
							.invokeMethod('findIndexValues', [args[1] as int, args[2].kismetClass().&call.curry(args[2])])
				},
				intersect: funcc { ... args -> args[0].invokeMethod('intersect', args[1]) },
				split: funcc { ... args -> args[0].invokeMethod('split', args.tail()) as List },
				tokenize: funcc { ... args -> args[0].invokeMethod('tokenize', args.tail()) },
				partition: func { IKismetObject... args -> args[0].inner().invokeMethod('split', args[1].kismetClass().&call.curry(args[1])) },
				each_consecutive: func { IKismetObject... args ->
					def x = args[0].inner()
					int siz = x.invokeMethod('size', null) as int
					int con = args[1].inner() as int
					Closure fun = args[2].kismetClass().&call.curry(args[2])
					def b = []
					for (int i = 0; i <= siz - con; ++i) {
						def a = new Object[con]
						for (int j = 0; j < con; ++j) a[j] = x.invokeMethod('getAt', i + j)
						fun.invokeMethod('call', a)
						b.add(a)
					}
					b
				},
				consecutives: funcc { ... args ->
					def x = args[0]
					int siz = x.invokeMethod('size', null) as int
					int con = args[1] as int
					def b = []
					for (int i = 0; i <= siz - con; ++i) {
						def a = new ArrayList(con)
						for (int j = 0; j < con; ++j) a.add(x.invokeMethod('getAt', i + j))
						b.add(a)
					}
					b
				},
				'consecutives~': funcc { ... args ->
					def x = args[0]
					int siz = x.invokeMethod('size', null) as int
					int con = args[1] as int
					new IteratorIterable<>(new Iterator<List>() {
						int i = 0

						boolean hasNext() { this.i <= siz - con }

						@Override
						List next() {
							def a = new ArrayList(con)
							for (int j = 0; j < con; ++j) a.add(x.invokeMethod('getAt', this.i + j))
							a
						}
					})
				},
				drop: funcc { ... args -> args[0].invokeMethod('drop', args[1] as int) },
				drop_right: funcc { ... args -> args[0].invokeMethod('dropRight', args[1] as int) },
				drop_while: func { IKismetObject... args -> args[0].inner().invokeMethod('dropWhile', args[1].kismetClass().&call.curry(args[1])) },
				take: funcc { ... args -> args[0].invokeMethod('take', args[1] as int) },
				take_right: funcc { ... args -> args[0].invokeMethod('takeRight', args[1] as int) },
				take_while: func { IKismetObject... args -> args[0].inner().invokeMethod('takeWhile', args[1].kismetClass().&call.curry(args[1])) },
				each_combination: func { IKismetObject... args -> args[0].inner().invokeMethod('eachCombination', args[1].kismetClass().&call.curry(args[1])) },
				each_permutation: func { IKismetObject... args -> args[0].inner().invokeMethod('eachPermutation', args[1].kismetClass().&call.curry(args[1])) },
				each_key_value: func { IKismetObject... args ->
					def m = (args[0].inner() as Map)
					for (e in m) {
						args[1].kismetClass().call(args[1], Kismet.model(e.key), Kismet.model(e.value))
					}
					m
				},
				'within_range?': funcc { ... args -> (args[1] as Range).containsWithinBounds(args[0]) },
				'is_range_reverse?': funcc { ... args -> (args[1] as Range).reverse },
				max: funcc { ... args -> args.max() },
				min: funcc { ... args -> args.min() },
				max_in: funcc { ... args -> args[0].invokeMethod('max', null) },
				min_in: funcc { ... args -> args[0].invokeMethod('min', null) },
				max_via: func { IKismetObject... args -> args[0].inner().invokeMethod('max', args[1].kismetClass().&call.curry(args[1])) },
				min_via: func { IKismetObject... args -> args[0].inner().invokeMethod('min', args[1].kismetClass().&call.curry(args[1])) },
				consume: func { IKismetObject... args -> args[1].kismetClass().call(args[1], args[0]) },
				tap: func { IKismetObject... args -> args[1].kismetClass().call(args[1], args[0]); args[0] },
				sleep: funcc { ... args -> sleep args[0] as long },
				times_do: func { IKismetObject... args ->
					def n = (Number) args[0].inner()
					def l = new ArrayList(n.intValue())
					for (def i = n - n; i < n; i += 1) l.add args[1].kismetClass().call(args[1], Kismet.model(i))
					l
				},
				compose: func { IKismetObject... args ->
					funcc { ... a ->
						IKismetObject r = args[0]
						for (int i = args.length - 1; i >= 0; --i) {
							r = args[i].kismetClass().call(args[i], r)
						}
						r
					}
				},
				'number?': funcc { ... args -> args[0] instanceof Number },
				'|>': new AbstractTemplate() {
					@CompileStatic
					Expression transform(Parser parser, Expression... args) {
						pipeForwardExpr(args[0], args.tail().toList())
					}
				},
				'<|': new AbstractTemplate() {
					@CompileStatic
					Expression transform(Parser parser, Expression... args) {
						pipeBackwardExpr(args[0], args.tail().toList())
					}
				},
				gcd: funcc { ... args -> gcd(args[0] as Number, args[1] as Number) },
				lcm: funcc { ... args -> lcm(args[0] as Number, args[1] as Number) },
				reduce_ratio: funcc { ... args ->
					Pair pair = args[0] as Pair
					def a = pair.first as Number, b = pair.second as Number
					Number gcd = gcd(a, b)
					(a, b) = [a.intdiv(gcd), b.intdiv(gcd)]
					new Pair(a, b)
				},
				repr_expr: funcc { ... args -> ((Expression) args[0]).repr() },
				sum_range: funcc { ... args ->
					Range r = args[0] as Range
					def to = r.to as Number, from = r.from as Number
					Number x = to.minus(from).next()
					x.multiply(from.plus(x)).intdiv(2)
				},
				sum_range_with_step: funcc { ... args ->
					Range r = args[0] as Range
					Number step = args[1] as Number
					def to = (r.to as Number).next(), from = r.from as Number
					to.minus(from).intdiv(step).multiply(from.plus(to.minus(step))).intdiv(2)
				},
				'subsequence?': funcc { ... args ->
					Iterator a = args[1].iterator()
					Iterator b = args[0].iterator()
					if (!b.hasNext()) return true
					def last = ++b
					while (a.hasNext() && b.hasNext()) {
						if (last == ++a) last = ++b
					}
					b.hasNext()
				},
				'supersequence?': funcc { ... args ->
					Iterator a = args[0].iterator()
					Iterator b = args[1].iterator()
					if (!b.hasNext()) return true
					def last = ++b
					while (a.hasNext() && b.hasNext()) {
						if (last == ++a) last = ++b
					}
					b.hasNext()
				},
				average_time_nanos: macr { Context c, Expression... args ->
					int iterations = args[0].evaluate(c).inner() as int
					long sum = 0, size = 0
					for (int i = 0; i < iterations; ++i) {
						long a = System.nanoTime()
						args[1].evaluate(c)
						long b = System.nanoTime()
						sum += b - a
						--size
					}
					sum / size
				},
				average_time_millis: macr { Context c, Expression... args ->
					int iterations = args[0].evaluate(c).inner() as int
					long sum = 0, size = 0
					for (int i = 0; i < iterations; ++i) {
						long a = System.currentTimeMillis()
						args[1].evaluate(c)
						long b = System.currentTimeMillis()
						sum += b - a
						--size
					}
					sum / size
				},
				average_time_seconds: macr { Context c, Expression... args ->
					int iterations = args[0].evaluate(c).inner() as int
					long sum = 0, size = 0
					for (int i = 0; i < iterations; ++i) {
						long a = System.currentTimeSeconds()
						args[1].evaluate(c)
						long b = System.currentTimeSeconds()
						sum += b - a
						--size
					}
					sum / size
				},
				list_time_nanos: macr { Context c, Expression... args ->
					int iterations = args[0].evaluate(c).inner() as int
					def times = new ArrayList<Long>(iterations)
					for (int i = 0; i < iterations; ++i) {
						long a = System.nanoTime()
						args[1].evaluate(c)
						long b = System.nanoTime()
						times.add(b - a)
					}
					times
				},
				list_time_millis: macr { Context c, Expression... args ->
					int iterations = args[0].evaluate(c).inner() as int
					def times = new ArrayList<Long>(iterations)
					for (int i = 0; i < iterations; ++i) {
						long a = System.currentTimeMillis()
						args[1].evaluate(c)
						long b = System.currentTimeMillis()
						times.add(b - a)
					}
					times
				},
				list_time_seconds: macr { Context c, Expression... args ->
					int iterations = args[0].evaluate(c).inner() as int
					def times = new ArrayList<Long>(iterations)
					for (int i = 0; i < iterations; ++i) {
						long a = System.currentTimeSeconds()
						args[1].evaluate(c)
						long b = System.currentTimeSeconds()
						times.add(b - a)
					}
					times
				},
				'|>|': new AbstractTemplate() {
					Expression transform(Parser parser, Expression... args) {
						infixLTR(args.toList())
					}
				},
				probability: funcc { ... a ->
					Random rand = a.length > 1 ? a[1] as Random : new Random()
					Number x = a[0] as Number
					rand.nextDouble() < x
				}]
		toConvert.'true?' = toConvert.'yes?' = toConvert.'on?' =
				toConvert.'?' = toConvert.bool
		toConvert.'no?' = toConvert.'off?' = toConvert.'false?'
		toConvert.'superset?' = toConvert.'has_all?'
		toConvert.fold = toConvert.reduce = toConvert.inject
		toConvert.half = toConvert.bottom_half
		toConvert.length = toConvert.size
		toConvert.filter = toConvert.select = toConvert.find_all
		toConvert.succ = toConvert.next
		toConvert.'all?' = toConvert.'every?'
		toConvert.'some?' = toConvert.'find?' = toConvert.'any?'
		toConvert.'less?' = toConvert.'<'
		toConvert.'greater?' = toConvert.'>'
		toConvert.'less_equal?' = toConvert.'<='
		toConvert.'greater_equal?' = toConvert.'>='
		toConvert.'+/' = toConvert.sum
		toConvert.'*/' = toConvert.product
		toConvert.'&' = toConvert.list
		toConvert.'#' = toConvert.set
		toConvert.'$' = toConvert.tuple
		toConvert.'&/' = toConvert.concat_list
		toConvert.'#/' = toConvert.concat_set
		toConvert.'$/' = toConvert.concat_tuple
		toConvert.assign = toConvert.'='
		toConvert.define = toConvert.':='
		toConvert.set_to = toConvert.'::='
		toConvert.change = toConvert.':::='
		toConvert.'variable?' = toConvert.'defined?'
		toConvert.'with_index' = toConvert.'indexed'
		toConvert.'divs?' = toConvert.'divides?' = toConvert.'divisible_by?'
		for (e in toConvert) defaultContext.put(e.key, Kismet.model(e.value))
		defaultContext = defaultContext.asImmutable()
	}

	static String resolveName(Expression n, Context c, String op) {
		String name
		if (n instanceof NameExpression) name = ((NameExpression) n).text
		else if (n instanceof NumberExpression) throw new UnexpectedSyntaxException("Name in $op was a number, not allowed")
		else {
			IKismetObject val = n.evaluate(c)
			if (val.inner() instanceof String) name = val.inner()
			else throw new UnexpectedValueException("Name in $op wasnt a string")
		}
		name
	}

	static IKismetObject assign(Context c, Expression[] x, IKismetObject value, String op = '=') {
		int i = 1
		for (a in x) {
			if (a instanceof StringExpression)
				c.assign(((StringExpression) a).value.inner(), value)
			else if (a instanceof NameExpression)
				c.assign(((NameExpression) a).text, value)
			else if (a instanceof PathExpression) {
				def steps = ((PathExpression) a).steps
				def toApply = steps.init()
				def toSet = steps.last()
				IKismetObject val = PathExpression.applySteps(c, ((PathExpression) a).root.evaluate(c), toApply)
				if (toSet instanceof PathExpression.PropertyStep)
					val.kismetClass().propertySet(val, ((PathExpression.PropertyStep) toSet).name, value)
				else if (toSet instanceof PathExpression.SubscriptStep)
					val.kismetClass().subscriptSet(val, ((PathExpression.SubscriptStep) toSet).expression.evaluate(c), value)
			} else throw new UnexpectedSyntaxException(
					"Did not expect expression type for argument $i of $op to be ${a.class.simpleName - 'Expression'}")
			++i
		}
		value
	}

	static Iterator toIterator(x) {
		if (x instanceof Iterable) ((Iterable) x).iterator()
		else if (x instanceof Iterator) (Iterator) x
		else x.iterator()
	}

	static List toList(x) {
		List r
		try {
			r = x as List
		} catch (ex) {
			try {
				r = (List) x.invokeMethod('toList', null)
			} catch (ignore) {
				throw ex
			}
		}
		r
	}

	static Set toSet(x) {
		Set r
		try {
			r = x as Set
		} catch (ex) {
			try {
				r = (Set) x.invokeMethod('toSet', null)
			} catch (ignore) {
				throw ex
			}
		}
		r
	}

	static CallExpression pipeForwardExpr(Expression base, Collection<Expression> args) {
		if (args.empty) throw new UnexpectedSyntaxException('no |> for epic!')
		for (exp in args) {
			if (exp instanceof CallExpression) {
				Collection<Expression> exprs = new ArrayList<>()
				exprs.add(((CallExpression) exp).callValue)
				exprs.add(base)
				exprs.addAll(((CallExpression) exp).arguments)
				def ex = new CallExpression(exprs)
				base = ex
			} else if (exp instanceof BlockExpression) {
				base = pipeForwardExpr(base, ((BlockExpression) exp).members)
			} else if (exp instanceof NameExpression) {
				base = new CallExpression([exp, base])
			} else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in |>')
		}
		(CallExpression) base
	}

	static CallExpression pipeBackwardExpr(Expression base, Collection<Expression> args) {
		if (args.empty) throw new UnexpectedSyntaxException('no <| for epic!')
		for (exp in args) {
			if (exp instanceof CallExpression) {
				Collection<Expression> exprs = new ArrayList<>()
				exprs.add(((CallExpression) exp).callValue)
				exprs.addAll(((CallExpression) exp).arguments)
				exprs.add(base)
				def ex = new CallExpression(exprs)
				base = ex
			} else if (exp instanceof BlockExpression) {
				base = pipeBackwardExpr(base, ((BlockExpression) exp).members)
			} else if (exp instanceof NameExpression) {
				base = new CallExpression([exp, base])
			} else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in |>')
		}
		(CallExpression) base
	}

	static Expression infixLTR(Expression expr) {
		if (expr instanceof CallExpression) infixLTR(((CallExpression) expr).members)
		else if (expr instanceof BlockExpression) {
			final mems = ((BlockExpression) expr).members
			def result = new ArrayList<Expression>(mems.size())
			for (x in ((BlockExpression) expr).members) result.add(infixLTR(x))
			new BlockExpression(result)
		} else expr
	}

	private static final NameExpression INFIX_CALLS_LTR_PATH = new NameExpression('|>|')

	static Expression infixLTR(Collection<Expression> args) {
		if (args.empty) return NoExpression.INSTANCE
		else if (args.size() == 1) return infixLTR(args[0])
		else if (args.size() == 2) {
			if (INFIX_CALLS_LTR_PATH == args[0]) return args[1]
			final val = infixLTR(args[0])
			def result = new CallExpression((Expression[]) null)
			result.callValue = val
			result.arguments = Arrays.asList(val, infixLTR(args[1]))
			result
		} else if (args.size() % 2 == 0)
			throw new UnexpectedSyntaxException('Even number of arguments for LTR infix function calls')
		def calls = new ArrayList<List<Expression>>()
		for (int i = 3; i < args.size(); ++i) {
			Expression ex = infixLTR args[i]
			def last = calls.last()
			if (i % 2 == 0) last.add(ex)
			else if (ex != last[0]) calls.add([ex])
		}
		CallExpression result = new CallExpression(
				infixLTR(args[1]),
				infixLTR(args[0]),
				infixLTR(args[2]))
		for (b in calls) {
			def exprs = new ArrayList<>(b.size() + 1)
			int i = 0
			exprs.add(b.get(i++))
			exprs.add(result)
			while (i < b.size()) exprs.add(b.get(i++))
			result = new CallExpression(exprs)
		}
		result
	}

	static void putPathExpression(Context c, Map map, PathExpression path, value) {
		final exprs = path.steps
		final key = path.root instanceof NameExpression ? ((NameExpression) path.root).text : path.root.evaluate(c)
		for (ps in exprs.reverse()) {
			if (ps instanceof PathExpression.SubscriptStep) {
				def k = ((PathExpression.SubscriptStep) ps).expression.evaluate(c).inner()
				if (k instanceof Number) {
					final list = new ArrayList()
					list.set(k.intValue(), value)
					value = list
				} else {
					final hash = new HashMap()
					hash.put(k, value)
					value = hash
				}
			} else if (ps instanceof PathExpression.PropertyStep) {
				final hash = new HashMap()
				hash.put(((PathExpression.PropertyStep) ps).name, value)
				value = hash
			} else throw new UnexpectedSyntaxException("Tried to use path step $ps as key")
		}
		map.put(key, value)
	}

	static void expressiveMap(Map map, Context c, Expression expr) {
		if (expr instanceof NameExpression) map.put(((NameExpression) expr).text, expr.evaluate(c))
		else if (expr instanceof PathExpression)
			putPathExpression(c, map, (PathExpression) expr, c.eval(expr))
		else if (expr instanceof CallExpression) {
			final exprs = ((CallExpression) expr).members
			final value = exprs.last().evaluate(c)
			for (x in exprs.init())
				if (x instanceof NameExpression)
					map.put(((NameExpression) x).text, value)
				else if (x instanceof PathExpression)
					putPathExpression(c, map, (PathExpression) x, value)
				else map.put(x.evaluate(c), value)
		} else if (expr instanceof BlockExpression) {
			final exprs = ((BlockExpression) expr).members
			for (x in exprs) expressiveMap(map, c, x)
		} else {
			final value = expr.evaluate(c)
			map.put(value, value)
		}
	}

	static boolean isAlpha(String string) {
		for (char ch : string.toCharArray()) {
			if (!((ch >= ((char) 'a') && ch <= ((char) 'z')) ||
					(ch >= ((char) 'A') && ch <= ((char) 'Z')) ||
					(ch >= ((char) '0') && ch <= ((char) '9')))) return false
		}
		true
	}

	static boolean check(Context c, IKismetObject val, Expression exp) {
		if (exp instanceof CallExpression) {
			def exprs = new ArrayList<>()
			def valu = ((CallExpression) exp).callValue
			if (valu instanceof NameExpression) {
				def t = ((NameExpression) valu).text
				exprs.add(new NameExpression(isAlpha(t) ? t + '?' : t))
			} else exprs.add(valu)
			c.set('it', val)
			exprs.add(new NameExpression('it'))
			exprs.addAll(((CallExpression) exp).arguments)
			CallExpression x = new CallExpression(exprs)
			x.evaluate(c)
		} else if (exp instanceof BlockExpression) {
			boolean result = true
			for (x in ((BlockExpression) exp).members) result = check(c, val, x)
			result
		} else if (exp instanceof NameExpression) {
			c.set('it', val)
			def t = ((NameExpression) exp).text
			new CallExpression(new NameExpression(isAlpha(t) ? t + '?' : t),
					new NameExpression('it')).evaluate(c)
		} else if (exp instanceof StringExpression) {
			val.inner() == ((StringExpression) exp).value.inner()
		} else if (exp instanceof NumberExpression) {
			val.inner() == ((NumberExpression) exp).value.inner()
		} else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in check')
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

	static GroovyMacro macr(boolean pure = false, Closure c) {
		def result = new GroovyMacro(c)
		result.pure = pure
		result
	}

	static GroovyFunction func(boolean pure = false, Closure c) {
		def result = new GroovyFunction(false, c)
		result.pure = pure
		result
	}

	static GroovyFunction funcc(boolean pure = false, Closure c) {
		def result = new GroovyFunction(true, c)
		result.pure = pure
		result
	}

	static String toAtom(Expression expression) {
		if (expression instanceof StringExpression) {
			return ((StringExpression) expression).value
		} else if (expression instanceof NameExpression) {
			return ((NameExpression) expression).text
		}
		null
	}
}








