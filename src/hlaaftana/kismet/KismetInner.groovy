package hlaaftana.kismet

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.parser.*

import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.regex.Pattern

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class KismetInner {
	private static Map<String, RoundingMode> roundingModes = [
			'^': RoundingMode.CEILING, 'v': RoundingMode.FLOOR,
			'^0': RoundingMode.UP, 'v0': RoundingMode.DOWN,
			'/^': RoundingMode.HALF_UP, '/v': RoundingMode.HALF_DOWN,
			'/2': RoundingMode.HALF_EVEN, '!': RoundingMode.UNNECESSARY
	].asImmutable()
	static Map<String, KismetObject> defaultContext = [
			Class: KismetClass.META.object,
			Null: new KismetClass(null, 'Null').object,
			Integer: new KismetClass(BigInteger, 'Integer').object,
			Float: new KismetClass(BigDecimal, 'Float').object,
			String: new KismetClass(String, 'String').object,
			Boolean: new KismetClass(Boolean, 'Boolean').object,
			Int8: new KismetClass(Byte, 'Int8').object,
			Int16: new KismetClass(Short, 'Int16').object,
			Int32: new KismetClass(Integer, 'Int32').object,
			Int64: new KismetClass(Long, 'Int64').object,
			Float32: new KismetClass(Float, 'Float32').object,
			Float64: new KismetClass(Double, 'Float64').object,
			Character: new KismetClass(Character, 'Character').object,
			Path: new KismetClass(Path, 'Path').object,
			Set: new KismetClass(Set, 'Set').object,
			List: new KismetClass(List, 'List').object,
			Tuple: new KismetClass(Tuple, 'Tuple').object,
			Map: new KismetClass(Map, 'Map').object,
			Expression: new KismetClass(Expression, 'Expression').object,
			PathExpression: new KismetClass(PathExpression, 'PathExpression').object,
			CallExpression: new KismetClass(CallExpression, 'CallExpression').object,
			BlockExpression: new KismetClass(BlockExpression, 'BlockExpression').object,
			StringExpression: new KismetClass(StringExpression, 'StringExpression').object,
			NumberExpression: new KismetClass(NumberExpression, 'NumberExpression').object,
			FakeExpression: new KismetClass(StaticExpression, 'FakeExpression').object,
			PathStep: new KismetClass(Path.PathStep, 'PathStep').object,
			PropertyPathStep: new KismetClass(Path.PropertyPathStep, 'PropertyPathStep').object,
			SubscriptPathStep: new KismetClass(Path.SubscriptPathStep, 'SubscriptPathStep').object,
			Block: new KismetClass(Block, 'Block').object,
			Function: new KismetClass(Function, 'Function').object,
			Macro: new KismetClass(Macro, 'Macro').object,
			Native: new KismetClass(Object, 'Native').object,
			Regex: new KismetClass(Pattern, 'Regex').object,
			Range: new KismetClass(Range, 'Range').object,
			Pair: new KismetClass(Pair, 'Pair').object,
			Iterator: new KismetClass(Iterator, 'Iterator').object,
			Throwable: new KismetClass(Throwable, 'Throwable').object,
			RNG: new KismetClass(Random, 'RNG').object,
			Date: new KismetClass(Date, 'Date').object,
			JSONParser: new KismetClass(JsonSlurper, 'JSONParser').object]

	static {
		Map<String, Object> toConvert = [
				euler_constant: Math.E.toBigDecimal(), pi: Math.PI.toBigDecimal(),
				now_nanos: funcc { ...args -> System.nanoTime() },
				now_millis: funcc { ...args -> System.currentTimeMillis() },
				now_seconds: funcc { ...args -> System.currentTimeSeconds() },
				now_date: funcc { ...args -> new Date() },
				new_date: funcc { ...args -> Date.invokeMethod('newInstance', args) },
				parse_date_from_format: funcc { ...args -> new SimpleDateFormat(args[1].toString()).parse(args[0].toString()) },
				format_date: funcc { ...args -> new SimpleDateFormat(args[1].toString()).format(args[0] as File) },
				true: true, false: false, null: null,
				yes: true, no: false, on: true, off: false,
				class: func { KismetObject... a -> a[0].kclass },
				class_from_name: func { KismetObject... a -> KismetClass.fromName(a[0].toString()) },
				'instance?': func { KismetObject... a ->
					final a0 = a[0]
					final b = a0.inner()
					final c = b instanceof KismetClass ? (KismetClass) b : a0.kclass.inner()
					for (int i = 1; i < a.length; ++i) {
						if (c.isInstance(a[i])) return true
					}
					false
				},
				'of?': func { KismetObject... a ->
					final a0 = a[0]
					for (int i = 1; i < a.length; ++i) {
						final ai = a[i]
						final x = ai.inner()
						final y = x instanceof KismetClass ? (KismetClass) x : ai.kclass.inner()
						if (y.isInstance(a0)) return true
						else throw new UnexpectedValueException('Argument in of? not class')
					}
					false
				},
				'not_of?': func { KismetObject... a ->
					final a0 = a[0]
					for (int i = 1; i < a.length; ++i) {
						final ai = a[i]
						final x = ai.inner()
						final y = x instanceof KismetClass ? (KismetClass) x : ai.kclass.inner()
						if (y.isInstance(a0)) return false
						else throw new UnexpectedValueException('Argument in not_of? not class')
					}
					true
				},
				variable: macr { Context c, Expression... exprs ->
					String name = exprs[0].evaluate(c).toString()
					exprs.length > 1 ? c.set(name, exprs[1].evaluate(c))
							: c.getProperty(name)
				},
				variables: macr { Context c, Expression... exprs -> new HashMap<>(c.variables) },
				current_block: macr { Context c, Expression... exprs -> c },
				java_class_name: funcc { ...args -> args[0].class.name },
				'<=>': funcc { ... a -> a[0].invokeMethod('compareTo', a[1]) as int },
				try: macr { Context c, Expression... exprs ->
					try {
						exprs[0].evaluate(c)
					} catch (ex) {
						c = c.child()
						c.set(resolveName(exprs[1], c, 'try'), Kismet.model(ex))
						exprs[2].evaluate(c)
					}
				},
				raise: func { ...args ->
					if (args[0] instanceof Throwable) throw (Throwable) args[0]
					else throw new UnexpectedValueException('raise called with non-throwable ' + args[0])
				},
				do: Function.NOP,
				'don\'t': macr { Context c, Expression... args -> },
				assert: macr { Context c, Expression... exprs ->
					KismetObject val
					for (e in exprs) if (!(val = e.evaluate(c)))
						throw new KismetAssertionError('Assertion failed for expression ' +
								e.repr() + '. Value should have been true-like but was ' + val)
				},
				assert_not: macr { Context c, Expression... exprs ->
					KismetObject val
					for (e in exprs) if ((val = e.evaluate(c)))
						throw new KismetAssertionError('Assertion failed for expression ' +
								e.repr() + '. Value should have been false-like but was ' + val)
				},
				assert_is: macr { Context c, Expression... exprs ->
					KismetObject val = exprs[0].evaluate(c)
					KismetObject latest
					for (e in exprs.tail()) if (val != (latest = e.evaluate(c)))
						throw new KismetAssertionError('Assertion failed for expression ' +
								e.repr() + '. Value was expected to be ' + val +
								' but was ' + latest)
				},
				'assert_isn\'t': macr { Context c, Expression... exprs ->
					List<KismetObject> values = [exprs[0].evaluate(c)]
					KismetObject retard
					KismetObject latest
					for (e in exprs.tail()) if ((retard = values.find((latest = e.evaluate(c)).&equals)))
						throw new KismetAssertionError('Assertion failed for expression ' +
								e.repr() + '. Value was expected NOT to be ' + retard +
								' but was ' + latest)
				},
				assert_of: macr { Context c, Expression... exprs ->
					KismetObject val = exprs[0].evaluate(c)
					List a = []
					for (e in exprs.tail()) {
						def cl = e.evaluate(c).inner()
						if (!(cl instanceof KismetClass))
							throw new UnexpectedValueException('Argument in assert_of wasn\'t a class')
						a.add(cl)
						if (((KismetClass) cl).isInstance(val)) return
					}
					throw new KismetAssertionError('Assertion failed for expression ' +
							exprs[0].repr() + '. Value was expected to be an instance ' +
							'of one of classes ' + a.join(', ') + ' but turned out to be value ' +
							val + ' and of class ' + val.kclass)
				},
				assert_not_of: macr { Context c, Expression... exprs ->
					KismetObject val = exprs[0].evaluate(c)
					List a = []
					for (e in exprs.tail()) {
						def cl = e.evaluate(c).inner()
						if (!(cl instanceof KismetClass))
							throw new UnexpectedValueException('Argument in assert_not_of wasn\'t a class')
						a.add(cl)
						if (((KismetClass) cl).isInstance(val))
							throw new KismetAssertionError('Assertion failed for expression ' +
									exprs[0].repr() + '. Value was expected to be NOT an instance ' +
									'of class ' + a.join(', ') + ' but turned out to be value ' +
									val + ' and of class ' + val.kclass)
					}
				},
				'!%': funcc { ... a -> a[0].hashCode() },
				percent: funcc { ... a -> a[0].invokeMethod 'div', 100 },
				to_percent: funcc { ... a -> a[0].invokeMethod 'multiply', 100 },
				strip_trailing_zeros: funcc { ...a -> ((BigDecimal) a[0]).stripTrailingZeros() },
				as: func { KismetObject... a -> a[0].invokeMethod('as', a[1].inner()) },
				'is?': funcc { ...args -> args.inject { a, b -> a == b } },
				'isn\'t?': funcc { ...args -> args.inject { a, b -> a != b } },
				'same?': funcc { ... a -> a[0].is(a[1]) },
				'not_same?': funcc { ... a -> !a[0].is(a[1]) },
				'empty?': funcc { ... a -> a[0].invokeMethod('isEmpty', null) },
				'in?': funcc { ... a -> a[0] in a[1] },
				'not_in?': funcc { ... a -> !(a[0] in a[1]) },
				not: funcc { ... a -> !(a[0]) },
				and: macr { Context c, Expression... exprs ->
					KismetObject last = Kismet.model(true)
					for (it in exprs) if (!(last = it.evaluate(c))) return last; last
				},
				or: macr { Context c, Expression... exprs ->
					KismetObject last = Kismet.model(false)
					for (it in exprs) if ((last = it.evaluate(c))) return last; last
				},
				'??': macr { Context c, Expression... exprs ->
					KismetObject x = Kismet.NULL
					for (it in exprs) if ((x = it.evaluate(c))) return x; x
				},
				xor: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'xor', b } },
				bool: funcc { ... a -> a[0] as boolean },
				bit_not: funcc { ... a -> a[0].invokeMethod 'bitwiseNegate', null },
				bit_and: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'and', b } },
				bit_or: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'or', b } },
				bit_xor: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'xor', b } },
				left_shift: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'leftShift', b } },
				right_shift: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'rightShift', b } },
				unsigned_right_shift: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'rightShiftUnsigned', b } },
				'<': funcc { ...args ->
					for (int i = 1; i < args.length; ++i) {
						if (((int) args[i-1].invokeMethod('compareTo', args[i])) >= 0)
							return false
					}
					true
				},
				'>': funcc { ...args ->
					for (int i = 1; i < args.length; ++i) {
						if (((int) args[i-1].invokeMethod('compareTo', args[i])) <= 0)
							return false
					}
					true
				},
				'<=': funcc { ...args ->
					for (int i = 1; i < args.length; ++i) {
						if (((int) args[i-1].invokeMethod('compareTo', args[i])) > 0)
							return false
					}
					true
				},
				'>=': funcc { ...args ->
					for (int i = 1; i < args.length; ++i) {
						if (((int) args[i-1].invokeMethod('compareTo', args[i])) < 0)
							return false
					}
					true
				},
				positive: funcc { ... a -> a[0].invokeMethod 'unaryPlus', null },
				negative: funcc { ... a -> a[0].invokeMethod 'unaryMinus', null },
				'positive?': funcc { ...args -> ((int) args[0].invokeMethod('compareTo', 0)) > 0 },
				'negative?': funcc { ...args -> ((int) args[0].invokeMethod('compareTo', 0)) < 0 },
				'null?': funcc { ...args -> null == args[0] },
				'not_null?': funcc { ...args -> null != args[0] },
				'false?': funcc { ...args -> !args[0].asBoolean() },
				'bool?': funcc { ...args -> args[0] instanceof boolean },
				'zero?': funcc { ...args -> args[0] == 0 },
				'one?': funcc { ...args -> args[0] == 1 },
				'even?': funcc { ...args -> args[0].invokeMethod('mod', 2) == 0 },
				'odd?': funcc { ...args -> args[0].invokeMethod('mod', 2) != 0 },
				'divisible_by?': funcc { ...args -> args[0].invokeMethod('mod', args[1]) == 0 },
				'integer?': funcc { ...args -> args[0].invokeMethod('mod', 1) == 0 },
				'decimal?': funcc { ...args -> args[0].invokeMethod('mod', 1) != 0 },
				'natural?': funcc { ...args -> args[0].invokeMethod('mod', 1) == 0 && ((int) args[0].invokeMethod('compareTo', 0)) >= 0 },
				absolute: funcc { ... a -> a[0].invokeMethod('abs', null) },
				squared: funcc { ... a -> a[0].invokeMethod 'multiply', a[0] },
				square_root: funcc { ...args -> ((Object) Math).invokeMethod('sqrt', args[0]) },
				cube_root: funcc { ...args -> ((Object) Math).invokeMethod('cbrt', args[0]) },
				sine: funcc { ...args -> ((Object) Math).invokeMethod('sin', args[0]) },
				cosine: funcc { ...args -> ((Object) Math).invokeMethod('cos', args[0]) },
				tangent: funcc { ...args -> ((Object) Math).invokeMethod('tan', args[0]) },
				hyperbolic_sine: funcc { ...args -> ((Object) Math).invokeMethod('sinh', args[0]) },
				hyperbolic_cosine: funcc { ...args -> ((Object) Math).invokeMethod('cosh', args[0]) },
				hyperbolic_tangent: funcc { ...args -> ((Object) Math).invokeMethod('tanh', args[0]) },
				arcsine: funcc { ...args -> Math.asin(args[0] as double) },
				arccosine: funcc { ...args -> Math.acos(args[0] as double) },
				arctangent: funcc { ...args -> Math.atan(args[0] as double) },
				arctan2: funcc { ...args -> Math.atan2(args[0] as double, args[1] as double) },
				round: macr { Context c, Expression... args ->
					def value = args[0].evaluate(c).inner()
					if (args.length > 1 || !(value instanceof Number)) value = value as BigDecimal
					if (value instanceof BigDecimal) {
						if (args.length > 1) {
							Expression x = args[1]
							String a = x instanceof PathExpression ?
									((PathExpression) x).text : x.evaluate(c).toString()
							RoundingMode mode = roundingModes[a]
							if (null == mode) throw new UnexpectedValueException('Unknown rounding mode ' + a)
							value.setScale(args.length > 2 ? args[2].evaluate(c) as int : 0, mode).stripTrailingZeros()
						} else value.setScale(0, RoundingMode.HALF_UP).stripTrailingZeros()
					} else if (value instanceof BigInteger
							|| value instanceof int
							|| value instanceof long) value
					else if (value instanceof float) Math.round(value.floatValue())
					else Math.round(((Number) value).doubleValue())
				},
				floor: funcc { ...args ->
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
				ceil: funcc { ...args ->
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
				logarithm: funcc { ...args -> Math.log(args[0] as double) },
				'+': funcc { ...args -> args.sum() },
				'-': funcc { ...args -> args.inject { a, b -> a.invokeMethod 'minus', b } },
				'*': funcc { ...args -> args.inject { a, b -> a.invokeMethod 'multiply', b } },
				'/': funcc { ...args -> args.inject { a, b -> a.invokeMethod 'div', b } },
				div: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'intdiv', b } },
				rem: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'mod', b } },
				mod: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'abs', null invokeMethod 'mod', b } },
				pow: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'power', b } },
				sum: funcc { ...args -> args[0].invokeMethod('sum', null) },
				product: funcc { ...args -> args[0].inject { a, b -> a.invokeMethod 'multiply', b } },
				reciprocal: funcc { ...args -> 1.div(args[0] as Number) },
				'defined?': macr { Context c, Expression... exprs ->
					try {
						c.getProperty(resolveName(exprs[0], c, "defined?"))
						true
					} catch (UndefinedVariableException ignored) {
						false
					}
				},
				integer_from_int8_list: funcc { ...args -> new BigInteger(args[0] as byte[]) },
				integer_to_int8_list: funcc { ...args -> (args[0] as BigInteger).toByteArray() as List<Byte> },
				new_rng: funcc { ...args -> args.length > 0 ? new Random(args[0] as long) : new Random() },
				random_int8_list_from_reference: funcc { ...args ->
					byte[] bytes = args[1] as byte[]
					(args[0] as Random).nextBytes(bytes)
					bytes as List<Byte>
				},
				random_int32: funcc { ...args ->
					if (args.length == 0) return (args[0] as Random).nextInt()
					int max = (args.length > 2 ? args[2] as int : args[1] as int) + 1
					int min = args.length > 2 ? args[1] as int : 0
					(args[0] as Random).nextInt(max) + min
				},
				random_int64_of_all: funcc { ...args -> (args[0] as Random).nextLong() },
				random_float32_between_0_and_1: funcc { ...args -> (args[0] as Random).nextFloat() },
				random_float64_between_0_and_1: funcc { ...args -> (args[0] as Random).nextDouble() },
				random_bool: funcc { ...args -> (args[0] as Random).nextBoolean() },
				next_gaussian: funcc { ...args -> (args[0] as Random).nextGaussian() },
				random_int: funcc { ...args ->
					BigInteger lower = args.length > 2 ? args[1] as BigInteger : 0g
					BigInteger higher = args.length > 2 ? args[2] as BigInteger : args[1] as BigInteger
					double x = (args[0] as Random).nextDouble()
					lower + (((higher - lower) * (x as BigDecimal)) as BigInteger)
				},
				random_float: funcc { ...args ->
					BigDecimal lower = args.length > 2 ? args[1] as BigDecimal : 0g
					BigDecimal higher = args.length > 2 ? args[2] as BigDecimal : args[1] as BigDecimal
					double x = (args[0] as Random).nextDouble()
					lower + (higher - lower) * (x as BigDecimal)
				},
				'shuffle!': funcc { ...args ->
					def l = toList(args[0])
					args[1] instanceof Random ? Collections.shuffle(l, (Random) args[1])
							: Collections.shuffle(l)
					l
				},
				shuffle: funcc { ...args ->
					def l = new ArrayList(toList(args[0]))
					args[1] instanceof Random ? Collections.shuffle(l, (Random) args[1])
							: Collections.shuffle(l)
					l
				},
				sample: funcc { ...args ->
					List x = toList(args[0])
					Random r = args.length > 1 && args[1] instanceof Random ? (Random) args[1] : new Random()
					x[r.nextInt(x.size())]
				},
				high: funcc { ...args ->
					if (args[0] instanceof Number) ((Object) args[0].class).invokeMethod('getProperty', 'MAX_VALUE')
					else if (args[0] instanceof KismetClass && Number.isAssignableFrom(((KismetClass) args[0]).orig))
						((KismetClass) args[0]).orig.invokeMethod('getProperty', 'MAX_VALUE')
					else if (args[0] instanceof Range) ((Range) args[0]).to
					else if (args[0] instanceof Collection) ((Collection) args[0]).size() - 1
					else throw new UnexpectedValueException('Don\'t know how to get high of ' + args[0] + ' with class ' + args[0].class)
				},
				low: funcc { ...args ->
					if (args[0] instanceof Number) ((Object) args[0].class).invokeMethod('getProperty', 'MIN_VALUE')
					else if (args[0] instanceof KismetClass && Number.isAssignableFrom(((KismetClass) args[0]).orig))
						((KismetClass) args[0]).orig.invokeMethod('getProperty', 'MIN_VALUE')
					else if (args[0] instanceof Range) ((Range) args[0]).from
					else if (args[0] instanceof Collection) 0
					else throw new UnexpectedValueException('Don\'t know how to get low of ' + args[0] + ' with class ' + args[0].class)
				},
				collect_range_with_step: funcc { ...args -> (args[0] as Range).step(args[1] as int) },
				each_range_with_step: func { KismetObject... args ->
					(args[0].inner() as Range)
							.step(args[1].inner() as int, args[2].&call)
				},
				replace: funcc { ...args ->
					args[0].toString().replace(args[1].toString(),
							args.length > 2 ? args[2].toString() : '')
				},
				replace_all_regex: func { KismetObject... args ->
					def replacement = args.length > 2 ?
							(args[2].inner() instanceof String ? args[2].inner() : args[2].&call) : ''
					def str = args[0].inner().toString()
					def pattern = args[1].inner() instanceof Pattern ? (Pattern) args[1].inner() : args[1].inner().toString()
					str.invokeMethod('replaceAll', [pattern, replacement])
				},
				replace_first_regex: func { KismetObject... args ->
					def replacement = args.length > 2 ?
							(args[2].inner() instanceof String ? args[2].inner() : args[2].&call) : ''
					def str = args[0].inner().toString()
					def pattern = args[1].inner() instanceof Pattern ? (Pattern) args[1].inner() : args[1].inner().toString()
					str.invokeMethod('replaceFirst', [pattern, replacement])
				},
				'blank?': func { KismetObject... args -> args[0].toString().isAllWhitespace() },
				quote_regex: func { KismetObject... args -> Pattern.quote(args[0].toString()) },
				'codepoints~': func { KismetObject... args -> args[0].toString().codePoints().iterator() },
				'chars~': func { KismetObject... args -> args[0].toString().chars().iterator() },
				chars: func { KismetObject... args -> args[0].toString().chars.toList() },
				codepoint_to_chars: funcc { ...args -> Character.toChars((int) args[0]).toList() },
				upper: funcc { ...args ->
					args[0] instanceof Character ? Character.toUpperCase((char) args[0]) :
							args[0] instanceof Integer ? Character.toUpperCase((int) args[0]) :
									args[0].toString().toUpperCase()
				},
				lower: funcc { ...args ->
					args[0] instanceof Character ? Character.toLowerCase((char) args[0]) :
							args[0] instanceof Integer ? Character.toLowerCase((int) args[0]) :
									args[0].toString().toLowerCase()
				},
				'upper?': funcc { ...args ->
					args[0] instanceof Character ? Character.isUpperCase((char) args[0]) :
							args[0] instanceof Integer ? Character.isUpperCase((int) args[0]) :
									args[0].toString().toCharArray().every { Character it -> !Character.isLowerCase(it) }
				},
				'lower?': funcc { ...args ->
					args[0] instanceof Character ? Character.isLowerCase((char) args[0]) :
							args[0] instanceof Integer ? Character.isLowerCase((int) args[0]) :
									args[0].toString().toCharArray().every { char it -> !Character.isUpperCase(it) }
				},
				parse_number: funcc { ...args ->
					Class c = args.length > 1 ? ((KismetClass) args[1]).orig : BigDecimal
					c.newInstance(args[0].toString())
				},
				strip: funcc { ...args -> args[0].toString().trim() },
				strip_start: funcc { ...args ->
					String x = args[0].toString()
					char[] chars = x.chars
					for (int i = 0; i < chars.length; ++i) {
						if (!Character.isWhitespace(chars[i]))
							return x.substring(i)
					}
					''
				},
				strip_end: funcc { ...args ->
					String x = args[0].toString()
					char[] chars = x.chars
					for (int i = chars.length - 1; i >= 0; --i) {
						if (!Character.isWhitespace(chars[i]))
							return x.substring(0, i + 1)
					}
					''
				},
				regex: macr { Context c, Expression... a ->
					a[0] instanceof StringExpression ? ~((StringExpression) a[0]).raw
							: ~a[0].evaluate(c).toString()},
				set_at: funcc { ... a -> a[0].invokeMethod('putAt', [a[1], a[2]]) },
				at: funcc { ... a -> a[0].invokeMethod('getAt', a[1]) },
				string: func { KismetObject... a ->
					if (a.length == 1) return a[0].toString()
					StringBuilder x = new StringBuilder()
					for (s in a) x.append(s)
					x.toString()
				},
				int: func { KismetObject... a -> a[0].as BigInteger },
				int8: func { KismetObject... a -> a[0].as byte },
				int16: func { KismetObject... a -> a[0].as short },
				int32: func { KismetObject... a -> a[0].as int },
				int64: func { KismetObject... a -> a[0].as long },
				Character: func { KismetObject... a -> a[0].as Character },
				float: func { KismetObject... a -> a[0].as BigDecimal },
				float32: func { KismetObject... a -> a[0].as float },
				float64: func { KismetObject... a -> a[0].as double },
				'~': funcc { ...args -> args[0].iterator() },
				list_iterator: funcc { ...args -> args[0].invokeMethod('listIterator', null) },
				'has_next?': funcc { ...args -> args[0].invokeMethod('hasNext', null) },
				next: funcc { ...args -> args[0].invokeMethod('next', null) },
				'has_prev?': funcc { ...args -> args[0].invokeMethod('hasPrevious', null) },
				prev: funcc { ...args -> args[0].invokeMethod('previous', null) },
				new_list: funcc { ...args -> new ArrayList(args[0] as int) },
				list: funcc { ...args -> args.toList() },
				new_set: funcc { ...args -> args.length > 1 ? new HashSet(args[0] as int, args[1] as float) : new HashSet(args[0] as int) },
				set: funcc { ...args ->
					Set x = new HashSet()
					for (a in args) x.add(a)
					x
				},
				pair: funcc { ...args -> new Pair(args[0], args[1]) },
				tuple: funcc { ...args -> new Tuple(args) },
				// assert_is x [+ [bottom_half x] [top_half x]]
				bottom_half: funcc { ...args ->
					args[0] instanceof Number ? ((Number) args[0]).intdiv(2) :
					args[0] instanceof Pair ? ((Pair) args[0]).first :
					args[0] instanceof Collection ? ((Collection) args[0]).take(((Collection) args[0]).size().intdiv(2) as int) :
					args[0] instanceof Map ? ((Map) args[0]).values() :
					args[0] },
				top_half: funcc { ...args ->
					args[0] instanceof Number ? ((Number) args[0]).minus(((Number) args[0]).intdiv(2)) :
					args[0] instanceof Pair ? ((Pair) args[0]).second :
					args[0] instanceof Collection ? ((Collection) args[0]).takeRight(
							((Collection) args[0]).size().minus(((Collection) args[0]).size().intdiv(2)) as int) :
					args[0] instanceof Map ? ((Map) args[0]).keySet() :
					args[0] },
				to_list: funcc { ...args -> toList(args[0]) },
				to_set: funcc { ...args -> toSet(args[0]) },
				to_pair: funcc { ...args -> new Pair(args[0].invokeMethod('getAt', 0), args[0].invokeMethod('getAt', 1)) },
				to_tuple: funcc { ...args -> new Tuple(args[0] as Object[]) },
				entry_pairs: funcc { ...args ->
					def r = []
					for (x in (args[0] as Map)) r.add(new Pair(x.key, x.value))
					r
				},
				map_from_pairs: funcc { ...args ->
					Map m = [:]
					for (x in args[0]) {
						def p = x as Pair
						m.put(p.first, p.second)
					}
					m
				},
				map: funcc { ...args ->
					Map map = [:]
					Iterator iter = args.iterator()
					while (iter.hasNext()) {
						def a = iter.next()
						if (iter.hasNext()) map.put(a, iter.next())
					}
					map
				},
				uncons: funcc { ...args -> new Pair(args[0].invokeMethod('head', null), args[0].invokeMethod('tail', null)) },
				cons: funcc { ...args ->
					def y = args[1]
					List a = new ArrayList((y.invokeMethod('size', null) as int) + 1)
					a.add(args[0])
					a.addAll(y)
					a
				},
				intersperse: funcc { ...args ->
					List r = []
					boolean x = false
					for (a in args[0]) {
						if (x) r.add(args[1])
						else x = true
						r.add(a)
					}
					r
				},
				intersperse_all: funcc { ...args ->
					List r = []
					boolean x = false
					for (a in args[0]) {
						if (x) r.addAll(args[1])
						else x = true
						r.add(a)
					}
					r
				},
				memoize: func { KismetObject... args ->
					def x = args[1]
					Map<KismetObject[], KismetObject> results = new HashMap<>()
					func { KismetObject... a ->
						def p = results.get(a)
						null == p ? x.call(a) : p
					}
				},
				path: func { KismetObject... args -> new PathExpression(args[0].toString()) },
				'path?': func { KismetObject... args -> args[0].inner() instanceof PathExpression },
				static_expr: funcc { ...args -> StaticExpression.class.newInstance(args) },
				'static_expr?': func { KismetObject... args -> args[0].inner() instanceof StaticExpression },
				number_expr: func { KismetObject... args -> new NumberExpression(args[0].toString()) },
				'number_expr?': func { KismetObject... args -> args[0].inner() instanceof NumberExpression },
				string_expr: func { KismetObject... args -> new StringExpression(args[0].toString()) },
				'string_expr?': func { KismetObject... args -> args[0].inner() instanceof StringExpression },
				call_expr: funcc { ...args -> new CallExpression(args.toList() as List<Expression>) },
				'call_expr?': func { KismetObject... args -> args[0].inner() instanceof CallExpression },
				block_expr: funcc { ...args -> new BlockExpression(args.toList() as List<Expression>) },
				'block_expr?': func { KismetObject... args -> args[0].inner() instanceof BlockExpression },
				escape: funcc { ...args -> StringEscaper.escapeSoda(args[0].toString()) },
				unescape: funcc { ...args -> StringEscaper.escapeSoda(args[0].toString()) },
				copy_map: funcc { ...args -> new HashMap(args[0] as Map) },
				new_map: funcc { ...args -> args.length > 1 ? new HashMap(args[0] as int, args[1] as float) : new HashMap(args[0] as int) },
				zip: funcc { ...args -> args.toList().transpose() },
				knit: func { KismetObject... args ->
					toList(args[0].inner()).transpose()
							.collect { args[1].invokeMethod('call', it as Object[]) }
				},
				transpose: funcc { ...args -> toList(args[0]).transpose() },
				'unique?': funcc { ...args ->
					args[0].invokeMethod('size', null) ==
							args[0].invokeMethod('unique', false).invokeMethod('size', null)
				},
				'unique!': funcc { ...args -> args[0].invokeMethod('unique', null) },
				unique: funcc { ...args -> args[0].invokeMethod('unique', false) },
				'unique_via?': func { KismetObject... args ->
					args[0].inner().invokeMethod('size', null) ==
							args[0].inner().invokeMethod('unique', [false, args[1].&call])
				},
				'unique_via!': func { KismetObject... args -> args[0].inner().invokeMethod('unique', args[1].&call) },
				unique_via: func { KismetObject... args -> args[0].inner().invokeMethod('unique', [false, args[1].&call]) },
				spread: funcc { ...args -> args[0].invokeMethod('toSpreadMap', null) },
				invert_map: funcc { ...args -> StringEscaper.flip(args[0] as Map) },
				new_json_parser: funcc { ...args -> new JsonSlurper() },
				parse_json: funcc { ...args ->
					String text = args.length > 1 ? args[1].toString() : args[0].toString()
					JsonSlurper sl = args.length > 1 ? args[0] as JsonSlurper : new JsonSlurper()
					sl.parseText(text)
				},
				to_json: funcc { ...args -> ((Object) JsonOutput).invokeMethod('toJson', args[0]) },
				pretty_print_json: funcc { ...args -> JsonOutput.prettyPrint(args[0].toString()) },
				size: funcc { ... a -> a[0].invokeMethod('size', null) },
				keys: funcc { ... a -> a[0].invokeMethod('keySet', null).invokeMethod('toList', null) },
				values: funcc { ... a -> a[0].invokeMethod('values', null) },
				reverse: funcc { ... a -> a[0].invokeMethod('reverse', a[0] instanceof CharSequence ? null : false) },
				'reverse!': funcc { ... a -> a[0].invokeMethod('reverse', null) },
				'reverse?': funcc { ... a -> a[0].invokeMethod('reverse', false) == a[1] },
				sprintf: funcc { ...args -> String.invokeMethod('format', args) },
				expr_type: funcc { ...args ->
					args[0] instanceof Expression ?
							(args[0].class.simpleName - 'Expression').uncapitalize() : null
				},
				capitalize: func { KismetObject... args -> args[0].toString().capitalize() },
				uncapitalize: func { KismetObject... args -> args[0].toString().uncapitalize() },
				center: funcc { ...args ->
					args.length > 2 ? args[0].toString().center(args[1] as Number, args[2].toString()) :
							args[0].toString().center(args[1] as Number)
				},
				pad_start: funcc { ...args ->
					args.length > 2 ? args[0].toString().padLeft(args[1] as Number, args[2].toString()) :
							args[0].toString().padLeft(args[1] as Number)
				},
				pad_end: funcc { ...args ->
					args.length > 2 ? args[0].toString().padRight(args[1] as Number, args[2].toString()) :
							args[0].toString().padRight(args[1] as Number)
				},
				'prefix?': funcc { ...args ->
					if (args[1] instanceof String) ((String) args[1]).startsWith(args[0].toString())
					else Collections.indexOfSubList(toList(args[1]), toList(args[0])) == 0
				},
				'suffix?': funcc { ...args ->
					if (args[1] instanceof String) ((String) args[1]).endsWith(args[0].toString())
					else {
						def a = toList(args[0])
						def b = toList(args[1])
						Collections.lastIndexOfSubList(b, a) == b.size() - a.size()
					}
				},
				'infix?': funcc { ...args ->
					if (args[1] instanceof String) ((String) args[1]).contains(args[0].toString())
					else Collections.invokeMethod('indexOfSubList', [toList(args[1]), toList(args[0])]) != -1
				},
				'subset?': funcc { ...args -> args[1].invokeMethod('containsAll', args[0]) },
				'rotate!': funcc { ...args ->
					List x = (List) args[0]
					Collections.rotate(x, args[1] as int)
					x
				},
				rotate: funcc { ...args ->
					List x = new ArrayList(toList(args[0]))
					Collections.rotate(x, args[1] as int)
					x
				},
				lines: funcc { ...args -> args[0].invokeMethod('readLines', null) },
				denormalize: funcc { ...args -> args[0].toString().denormalize() },
				normalize: funcc { ...args -> args[0].toString().normalize() },
				hex: macr { Context c, Expression... x ->
					if (x[0] instanceof NumberExpression || x[0] instanceof PathExpression) {
						String t = x[0] instanceof NumberExpression ? ((NumberExpression) x[0]).value.inner().toString()
								: ((PathExpression) x[0]).text
						new BigInteger(t, 16)
					} else throw new UnexpectedSyntaxException('Expression after hex was not a hexadecimal number literal.'
							+ ' To convert hex strings to integers do [from_base str 16], '
							+ ' and to convert integers to hex strings do [to_base i 16].')
				},
				binary: macr { Context c, Expression... x ->
					if (x[0] instanceof NumberExpression) {
						String t = ((NumberExpression) x[0]).value.inner().toString()
						new BigInteger(t, 2)
					} else throw new UnexpectedSyntaxException('Expression after binary was not a binary number literal.'
							+ ' To convert binary strings to integers do [from_base str 2], '
							+ ' and to convert integers to binary strings do [to_base i 2].')
				},
				octal: macr { Context c, Expression... x ->
					if (x[0] instanceof NumberExpression) {
						String t = ((NumberExpression) x[0]).value.inner().toString()
						new BigInteger(t, 8)
					} else throw new UnexpectedSyntaxException('Expression after octal was not a octal number literal.'
							+ ' To convert octal strings to integers do [from_base str 8], '
							+ ' and to convert integers to octal strings do [to_base i 8].')
				},
				to_base: funcc { ... a -> (a[0] as BigInteger).toString(a[1] as int) },
				from_base: funcc { ... a -> new BigInteger(a[0].toString(), a[1] as int) },
				':::=': macr { Context c, Expression... x ->
					if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for :::=')
					KismetObject value = x.length == 1 ? Kismet.NULL : x.last().evaluate(c)
					int i = 1
					for (a in x.init()) {
						c.change(resolveName(a, c, ":::= argument $i"), value)
						++i
					}
					value
				},
				'::=': macr { Context c, Expression... x ->
					if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for ::=')
					KismetObject value = x.length == 1 ? Kismet.NULL : x.last().evaluate(c)
					int i = 1
					for (a in x.init()) {
						c.set(resolveName(a, c, "::= argument $i"), value)
						++i
					}
					value
				},
				':=': macr { Context c, Expression... x ->
					if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for :=')
					KismetObject value = x.length == 1 ? Kismet.NULL : x.last().evaluate(c)
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
				def: macr { Context c, Expression... x ->
					if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for :=')
					if (x[0] instanceof PathExpression) {
						KismetObject value = x.length == 1 ? Kismet.NULL : x.last().evaluate(c)
						int i = 1
						for (a in x.init()) {
							c.define(resolveName(a, c, ":= argument $i"), value)
							++i
						}
						value
					} else {
						final f = new KismetFunction(c, true, x)
						c.define(f.name, Kismet.model(f))
					}
				},
				fn: macr { Context c, Expression... exprs ->
					new KismetFunction(c, false, exprs)
				},
				mcr: macr { Context c, Expression... exprs ->
					new KismetMacro(Kismet.model(c.child(exprs)))
				},
				defn: macr { Context c, Expression... exprs ->
					final x = new KismetFunction(c, true, exprs)
					c.define(x.name, Kismet.model(x))
				},
				defmcr: macr { Context c, Expression... exprs ->
					List<Expression> kill = new ArrayList<>()
					kill.add(new PathExpression('mcr'))
					for (int i = 1; i < exprs.length; ++i) kill.add(exprs[i])
					def ex = new CallExpression([new PathExpression(':='), exprs[0],
							new CallExpression(kill)])
					ex.evaluate(c)
				},
				'fn*': macr { Context c, Expression... exprs ->
					List<Expression> kill = new ArrayList<>()
					kill.add(new PathExpression('fn'))
					kill.addAll(exprs)
					StaticExpression fake = new StaticExpression(new CallExpression(kill), c)
					def ex = new CallExpression([new PathExpression('fn'), new CallExpression([
							new PathExpression('call'), fake, new PathExpression('$0')])])
					ex.evaluate(c)
				},
				undef: macr { Context c, Expression... exprs -> c.getVariables().containsKey(exprs[0].evaluate(c)) },
				block: macr { Context c, Expression... exprs ->
					c.child(exprs)
				},
				incr: macr { Context c, Expression... exprs ->
					assign(c, exprs.take(1), new CallExpression([new PathExpression('next'), exprs[0]]).evaluate(c), 'increment')
				},
				decr: macr { Context c, Expression... exprs ->
					assign(c, exprs.take(1), new CallExpression([new PathExpression('prev'), exprs[0]]).evaluate(c), 'decrement')
				},
				'|>=': macr { Context c, Expression... exprs ->
					assign(c, exprs.take(1), pipeForward(c, exprs[0].evaluate(c), exprs.drop(1).toList()), '|>=')
				},
				'<|=': macr { Context c, Expression... exprs ->
					assign(c, exprs.take(1), pipeBackward(c, exprs[0].evaluate(c), exprs.drop(1).toList()), '|>=')
				},
				let: macr { Context c, Expression... exprs ->
					Expression cnt = exprs[0]
					Block b = c.child(exprs.tail())
					if (cnt instanceof CallExpression) {
						CallExpression ex = (CallExpression) cnt
						Iterator<Expression> defs = ex.expressions.iterator()
						while (defs.hasNext()) {
							Expression n = defs.next()
							if (!defs.hasNext()) break
							String name
							if (n instanceof PathExpression) name = ((PathExpression) n).text
							else if (n instanceof NumberExpression) throw new UnexpectedSyntaxException('Cant define a number (let)')
							else {
								KismetObject val = n.evaluate(c)
								if (val.inner() instanceof String) name = val.inner()
								else throw new UnexpectedValueException('Evaluated first expression of define wasnt a string (let)')
							}
							b.context.set(name, defs.next().evaluate(c))
						}
					} else throw new UnexpectedSyntaxException('Expression after let is not a call-type expression')
					b
				},
				eval: macr { Context c, Expression... a ->
					def x = a[0].evaluate(c)
					if (x.inner() instanceof Block) ((Block) x.inner()).evaluate()
					else if (x.inner() instanceof Expression)
						((Expression) x.inner()).evaluate(a.length > 1 ? a[1].evaluate(c).inner() as Context : c)
					else if (x.inner() instanceof Path)
						((Path) x.inner()).apply(a.length > 1 ? a[1].evaluate(c).inner() as Context : c)
					else if (x.inner() instanceof String)
						if (a.length > 1) Parser.parse(a.length > 2 ? a[2].evaluate(c).inner as Context : c, (String) x.inner())
								.evaluate(a[1].evaluate(c).inner() as Context)
						else c.childEval(Parser.parse(c, (String) x.inner()))
					else throw new UnexpectedValueException('Expected first value of eval to be an expression, block, path or string')
				},
				quote: macr { Context c, Expression... exprs ->
					exprs.length == 1 ? exprs[0] :
							new BlockExpression(exprs.toList())
				},
				if_then: macr { Context c, Expression... exprs ->
					exprs[0].evaluate(c) ? c.childEval(exprs.tail()) : Kismet.NULL
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
							if (x instanceof PathExpression) {
								String text = ((PathExpression) x).text
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
							if (x instanceof PathExpression) {
								String text = ((PathExpression) x).text
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
				'if_then!': macr { Context c, Expression... exprs ->
					exprs[0].evaluate(c) ? c.eval(exprs.tail()) : Kismet.NULL
				},
				'if!': macr { Context c, Expression... exprs ->
					if (exprs.length == 2)
						exprs[0].evaluate(c) ? c.eval(exprs[1]) : Kismet.NULL
					else {
						def cond = exprs[0].evaluate(c)
						int b = 1
						int i = 1
						for (int s = exprs.length; i < s; ++i) {
							def x = exprs[i]
							if (x instanceof PathExpression) {
								String text = ((PathExpression) x).text
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
				'unless_then!': macr { Context c, Expression... exprs ->
					!exprs[0].evaluate(c) ? c.eval(exprs.tail()) : Kismet.NULL
				},
				'unless!': macr { Context c, Expression... exprs ->
					if (exprs.length == 2)
						!exprs[0].evaluate(c) ? c.eval(exprs[1]) : Kismet.NULL
					else {
						def cond = exprs[0].evaluate(c)
						int b = 1
						int i = 1
						for (int s = exprs.length; i < s; ++i) {
							def x = exprs[i]
							if (x instanceof PathExpression) {
								String text = ((PathExpression) x).text
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
					KismetObject x = Kismet.NULL
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
					KismetObject x = Kismet.NULL
					while (a.hasNext()) {
						x = c.childEval(a.next())
						if (a.hasNext())
							if (!x) return c.childEval(a.next())
							else a.next()
						else return x
					}
					x
				},
				'if_chain!': macr { Context c, Expression... ab ->
					Iterator<Expression> a = ab.iterator()
					KismetObject x = Kismet.NULL
					while (a.hasNext()) {
						x = a.next().evaluate(c)
						if (a.hasNext())
							if (x) return a.next().evaluate(c)
							else a.next()
						else return x
					}
					x
				},
				'unless_chain!': macr { Context c, Expression... ab ->
					Iterator<Expression> a = ab.iterator()
					KismetObject x = Kismet.NULL
					while (a.hasNext()) {
						x = a.next().evaluate(c)
						if (a.hasNext())
							if (!x) return a.next().evaluate(c)
							else a.next()
						else return x
					}
					x
				},
				if_else: macr { Context c, Expression... x -> x[0].evaluate(c) ? c.childEval(x[1]) : c.childEval(x[2]) },
				unless_else: macr { Context c, Expression... x -> !x[0].evaluate(c) ? c.childEval(x[1]) :
						c.childEval(x[2]) },
				'if_else!': macr { Context c, Expression... x -> x[0].evaluate(c) ? x[1].evaluate(c) :
						x[2].evaluate(c) },
				'unless_else!': macr { Context c, Expression... x -> !x[0].evaluate(c) ? x[1].evaluate(c) :
						x[2].evaluate(c) },
				check: macr { Context c, Expression... ab ->
					Iterator<Expression> a = ab.iterator()
					KismetObject val = a.next().evaluate(c)
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
					List<Expression> l = exprs.toList()
					KismetObject j = Kismet.NULL
					while (exprs[0].evaluate(c)) j = c.childEval(l)
					j
				},
				until: macr { Context c, Expression... exprs ->
					List<Expression> l = exprs.toList()
					KismetObject j = Kismet.NULL
					while (!exprs[0].evaluate(c)) j = c.childEval(l)
					j
				},
				'while!': macr { Context c, Expression... exprs ->
					BlockExpression b = new BlockExpression(exprs.tail().toList())
					KismetObject j = Kismet.NULL
					while (exprs[0].evaluate(c)) j = b.evaluate(c)
					j
				},
				'until!': macr { Context c, Expression... exprs ->
					BlockExpression b = new BlockExpression(exprs.tail().toList())
					KismetObject j = Kismet.NULL
					while (!exprs[0].evaluate(c)) j = b.evaluate(c)
					j
				},
				for_each: macr { Context c, Expression... exprs ->
					String n = resolveName(exprs[0], c, 'foreach')
					Block b = c.child(exprs.drop(2))
					KismetObject a = Kismet.NULL
					for (x in exprs[1].evaluate(c).inner()) {
						Block y = b.child()
						y.context.set(n, Kismet.model(x))
						a = y()
					}
					a
				},
				for_i32: macr { Context c, Expression... exprs ->
					String n = resolveName(exprs[0], c, 'for_i32')
					Block b = c.child(exprs.drop(2))
					KismetObject a = Kismet.NULL
					if (!(exprs[1] instanceof CallExpression))
						throw new UnexpectedSyntaxException('second argument in for_i32 was a ' + exprs[1].class)
					def range = ((CallExpression) exprs[1]).expressions
					int bottom, top
					if (range.size() == 1) {
						bottom = 0
						top = range[0].evaluate(b.context).inner() as int
					} else {
						bottom = range[0].evaluate(b.context).inner() as int
						top = range[1].evaluate(b.context).inner() as int
					}
					int step = null == range[2] ? 1 : range[2].evaluate(b.context).inner() as int
					for (; bottom < top; bottom += step) {
						Block y = b.child()
						y.context.set(n, Kismet.model(bottom))
						a = y()
					}
					a
				},
				for: macr { Context c, Expression... exprs ->
					Block b = c.child(exprs.drop(3))
					KismetObject j = Kismet.NULL
					exprs[0].evaluate(c)
					while (exprs[1].evaluate(c)) {
						j = b.child().evaluate()
						exprs[2].evaluate(c)
					}
					j
				},
				'for_each!': macr { Context c, Expression... exprs ->
					String n = resolveName(exprs[0], c, 'foreach!')
					final b = new BlockExpression(exprs.drop(2).toList())
					KismetObject a = Kismet.NULL
					for (x in exprs[1].evaluate(c).inner()) {
						c.set(n, Kismet.model(x))
						a = b.evaluate(c)
					}
					a
				},
				'for_i32!': macr { Context c, Expression... exprs ->
					String n = resolveName(exprs[0], c, 'for_i32!')
					Block b = c.child(exprs.drop(2))
					KismetObject a = Kismet.NULL
					if (!(exprs[1] instanceof CallExpression))
						throw new UnexpectedSyntaxException('second argument in for_i32! was a ' + exprs[1].class)
					def range = ((CallExpression) exprs[1]).expressions
					int bottom, top
					if (range.size() == 1) {
						bottom = 0
						top = range[0].evaluate(b.context).inner() as int
					} else {
						bottom = range[0].evaluate(b.context).inner() as int
						top = range[1].evaluate(b.context).inner() as int
					}
					int step = null == range[2] ? 1 : range[2].evaluate(b.context).inner() as int
					for (; bottom < top; bottom += step) {
						b.context.set(n, Kismet.model(bottom))
						a = b()
					}
					a
				},
				'for!': macr { Context c, Expression... exprs ->
					final b = new BlockExpression(exprs.drop(3).toList())
					KismetObject j = Kismet.NULL
					exprs[0].evaluate(c)
					while (exprs[1].evaluate(c)) {
						j = b.evaluate(c)
						exprs[2].evaluate(c)
					}
					j
				},
				each: func { KismetObject... args -> args[0].inner().each(args[1].&call) },
				each_with_index: func { KismetObject... args -> args[0].inner().invokeMethod('eachWithIndex', args[1].&call) },
				collect: func { KismetObject... args -> args[0].inner().collect(args[1].&call) },
				collect_nested: func { KismetObject... args -> args[0].inner().invokeMethod('collectNested', args[1].&call) },
				collect_many: func { KismetObject... args -> args[0].inner().invokeMethod('collectMany', args[1].&call) },
				collect_map: func { KismetObject... args ->
					args[0].inner()
							.invokeMethod('collectEntries') { ... a -> args[1].call(a).inner() }
				},
				subsequences: funcc { ...args -> args[0].invokeMethod('subsequences', null) },
				combinations: funcc { ...args -> args[0].invokeMethod('combinations', null) },
				permutations: funcc { ...args -> args[0].invokeMethod('permutations', null) },
				'permutations~': funcc { ...args ->
					new PermutationGenerator(args[0] instanceof Collection ? (Collection) args[0]
							: args[0] instanceof Iterable ? (Iterable) args[0]
							: args[0] instanceof Iterator ? new IteratorIterable((Iterator) args[0])
							: args[0] as Collection)
				},
				'any?': func { KismetObject... args ->
						args.length > 1 ? args[0].inner().any(args[1].&call) : args[0].inner().any() },
				'every?': func { KismetObject... args ->
						args.length > 1 ? args[0].inner().every(args[1].&call) : args[0].inner().every() },
				'none?': func { KismetObject... args -> !(
						args.length > 1 ? args[0].inner().any(args[1].&call) : args[0].inner().any()) },
				find: func { KismetObject... args -> args[0].inner().invokeMethod('find', args[1].&call) },
				find_result: func { KismetObject... args -> args[0].inner().invokeMethod('findResult', args[1].&call) },
				count: func { KismetObject... args -> args[0].inner().invokeMethod('count', args[1].&call) },
				count_element: func { KismetObject... args ->
					BigInteger i = 0
					def a = args[1].inner()
					def iter = args[0].iterator()
					while (iter.hasNext()) {
						def x = iter.next()
						if (x instanceof KismetObject) x = x.inner()
						if (a == x) ++i
					}
					i
				},
				count_elements: func { KismetObject... args ->
					BigInteger i = 0
					def c = args.tail()
					def b = new Object[c.length]
					for (int m = 0; m < c.length; ++i) b[m] = c[m].inner()
					boolean j = args.length == 1
					def iter = args[0].iterator()
					outer: while (iter.hasNext()) {
						def x = iter.next()
						if (x instanceof KismetObject) x = x.inner()
						if (j) ++i
						else for (a in b) if (a == x) {
							++i
							continue outer
						}
					}
					i
				},
				count_by: func { KismetObject... args -> args[0].inner().invokeMethod('countBy', args[1].&call) },
				group_by: func { KismetObject... args -> args[0].inner().invokeMethod('groupBy', args[1].&call) },
				indexed: func { KismetObject... args -> args[0].inner().invokeMethod('indexed', args.length > 1 ? args[1] as int : null) },
				find_all: func { KismetObject... args -> args[0].inner().findAll(args[1].&call) },
				join: funcc { ...args ->
					args[0].invokeMethod('join', args.length > 1 ? args[1].toString() : '')
				},
				inject: func { KismetObject... args -> args[0].inner().inject { a, b -> args[1].call(a, b) } },
				collate: funcc { ...args -> args[0].invokeMethod('collate', args.tail()) },
				pop: funcc { ...args -> args[0].invokeMethod('pop', null) },
				add: funcc { ...args -> args[0].invokeMethod('add', args[1]) },
				add_at: funcc { ...args -> args[0].invokeMethod('add', [args[1] as int, args[2]]) },
				add_all: funcc { ...args -> args[0].invokeMethod('addAll', args[1]) },
				add_all_at: funcc { ...args -> args[0].invokeMethod('addAll', [args[1] as int, args[2]]) },
				remove: funcc { ...args -> args[0].invokeMethod('remove', args[1]) },
				remove_elements: funcc { ...args -> args[0].invokeMethod('removeAll', args[1]) },
				remove_any: func { KismetObject... args -> args[0].inner().invokeMethod('removeAll', args[1].&call) },
				remove_element: funcc { ...args -> args[0].invokeMethod('removeElement', args[1]) },
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
						if (x instanceof PathExpression)
							r = r.invokeMethod('getAt', ((PathExpression) x).text)
						else r = r.invokeMethod('getAt', x.evaluate(c))
					}
					r
				},
				empty: funcc { ...args -> args[0].invokeMethod('clear', null) },
				put: funcc { ...args -> args[0].invokeMethod('put', [args[1], args[2]]) },
				put_all: funcc { ...args -> args[0].invokeMethod('putAll', args[1]) },
				'keep_all!': funcc { ...args -> args[0].invokeMethod('retainAll', args[1]) },
				'keep_any!': func { KismetObject... args -> args[0].inner().invokeMethod('retainAll', args[1].&call) },
				'has?': funcc { ...args -> args[0].invokeMethod('contains', args[1]) },
				'has_all?': funcc { ...args -> args[0].invokeMethod('containsAll', args[1]) },
				'has_key?': funcc { ...args -> args[0].invokeMethod('containsKey', args[1]) },
				'has_key_traverse?': funcc { ...args ->
					def x = args[0]
					for (a in args.tail()) {
						if (!((boolean) x.invokeMethod('containsKey', args[1]))) return false
						else x = args[0].invokeMethod('getAt', a)
					}
					true
				},
				'has_value?': funcc { ...args -> args[0].invokeMethod('containsValue', args[1]) },
				'is_code_kismet?': funcc { ...args -> args[0] instanceof KismetFunction || args[0] instanceof KismetMacro },
				'disjoint?': funcc { ...args -> args[0].invokeMethod('disjoint', args[1]) },
				'intersect?': funcc { ...args -> !args[0].invokeMethod('disjoint', args[1]) },
				call: func { KismetObject... args -> args[0].call(args[1].inner() as Object[]) },
				range: funcc { ...args -> args[0]..args[1] },
				parse_independent_kismet: func { KismetObject... args -> Kismet.parse(args[0].toString()) },
				parse_kismet: macr { Context c, Expression... args ->
					Parser.parse(args.length > 1 ? (Context) args[1].evaluate(c).inner() : c.child(),
							args[0].evaluate(c).toString())
				},
				context_child: funcc { ...args ->
					def b = args[0] as Context
					args.length > 1 ? b.invokeMethod('child', args.tail()) : b.child()
				},
				context_child_eval: funcc { ...args -> (args[0] as Context).invokeMethod('childEval', args.tail()) },
				parse_path: funcc { ...args -> Path.parse(args[0].toString()) },
				apply_path: funcc { ...args -> ((Path) args[0]).apply(args[1]) },
				'sort!': funcc { ...args -> args[0].invokeMethod('sort', null) },
				sort: funcc { ...args -> args[0].invokeMethod('sort', false) },
				'sort_via!': func { KismetObject... args -> args[0].inner().invokeMethod('sort', args[1].&call) },
				sort_via: func { KismetObject... args -> args[0].inner().invokeMethod('sort', [false, args[1].&call]) },
				head: funcc { ...args -> args[0].invokeMethod('head', null) },
				tail: funcc { ...args -> args[0].invokeMethod('tail', null) },
				init: funcc { ...args -> args[0].invokeMethod('init', null) },
				last: funcc { ...args -> args[0].invokeMethod('last', null) },
				first: funcc { ...args -> args[0].invokeMethod('first', null) },
				immutable: funcc { ...args -> args[0].invokeMethod('asImmutable', null) },
				identity: Function.IDENTITY,
				flatten: funcc { ...args -> args[0].invokeMethod('flatten', null) },
				concat_list: funcc { ...args ->
					def c = new ArrayList()
					for (int i = 0; i < args.length; ++i) {
						final x = args[i]
						x instanceof Collection ? c.addAll(x) : c.add(x)
					}
					c
				},
				concat_set: funcc { ...args ->
					def c = new HashSet()
					for (int i = 0; i < args.length; ++i) {
						final x = args[i]
						x instanceof Collection ? c.addAll(x) : c.add(x)
					}
					c
				},
				concat_tuple: funcc { ...args ->
					def c = new ArrayList()
					for (int i = 0; i < args.length; ++i) {
						final x = args[i]
						x instanceof Collection ? c.addAll(x) : c.add(x)
					}
					new Tuple(c.toArray())
				},
				indices: funcc { ...args -> args[0].invokeMethod('getIndices', null) },
				find_index: func { KismetObject... args -> args[0].inner().invokeMethod('findIndexOf', args[1].&call) },
				find_index_after: func { KismetObject... args ->
					args[0].inner()
							.invokeMethod('findIndexOf', [args[1] as int, args[2].&call])
				},
				find_last_index: func { KismetObject... args -> args[0].inner().invokeMethod('findLastIndexOf', args[1].&call) },
				find_last_index_after: func { KismetObject... args ->
					args[0].inner()
							.invokeMethod('findLastIndexOf', [args[1] as int, args[2].&call])
				},
				find_indices: func { KismetObject... args -> args[0].inner().invokeMethod('findIndexValues', args[1].&call) },
				find_indices_after: func { KismetObject... args ->
					args[0].inner()
							.invokeMethod('findIndexValues', [args[1] as int, args[2].&call])
				},
				intersect: funcc { ...args -> args[0].invokeMethod('intersect', args[1]) },
				split: funcc { ...args -> args[0].invokeMethod('split', args.tail()) as List },
				tokenize: funcc { ...args -> args[0].invokeMethod('tokenize', args.tail()) },
				partition: func { KismetObject... args -> args[0].inner().invokeMethod('split', args[1].&call) },
				each_consecutive: func { KismetObject... args ->
					def x = args[0].inner()
					int siz = x.invokeMethod('size', null) as int
					int con = args[1].inner() as int
					Closure fun = args[2].&call
					List b = []
					for (int i = 0; i < siz - con; ++i) {
						List a = new ArrayList(con)
						for (int j = 0; j < siz; ++j) a.add(x.invokeMethod('getAt', i + j))
						fun(a as Object[])
						b.add(a)
					}
					b
				},
				consecutives: funcc { ...args ->
					def x = args[0]
					int siz = x.invokeMethod('size', null) as int
					int con = args[1] as int
					List b = []
					for (int i = 0; i < siz - con; ++i) {
						List a = new ArrayList(con)
						for (int j = 0; j < siz; ++j) a.add(x.invokeMethod('getAt', i + j))
						b.add(a)
					}
					b
				},
				'consecutives~': funcc { ...args ->
					def x = args[0]
					int siz = x.invokeMethod('size', null) as int
					int con = args[1] as int
					new IteratorIterable<>(new Iterator<List>() {
						int i = 0

						boolean hasNext() { this.i < siz - con }

						@Override
						List next() {
							List a = new ArrayList(con)
							for (int j = 0; j < siz; ++j) a.add(x.invokeMethod('getAt', this.i + j))
							a
						}
					})
				},
				drop: funcc { ...args -> args[0].invokeMethod('drop', args[1] as int) },
				drop_right: funcc { ...args -> args[0].invokeMethod('dropRight', args[1] as int) },
				drop_while: func { KismetObject... args -> args[0].inner().invokeMethod('dropWhile', args[1].&call) },
				take: funcc { ...args -> args[0].invokeMethod('take', args[1] as int) },
				take_right: funcc { ...args -> args[0].invokeMethod('takeRight', args[1] as int) },
				take_while: func { KismetObject... args -> args[0].inner().invokeMethod('takeWhile', args[1].&call) },
				each_combination: func { KismetObject... args -> args[0].inner().invokeMethod('eachCombination', args[1].&call) },
				each_permutation: func { KismetObject... args -> args[0].inner().invokeMethod('eachPermutation', args[1].&call) },
				each_key_value: func { KismetObject... args ->
					def m = (args[0].inner() as Map)
					for (e in m) {
						args[1].call(e.key, e.value)
					}
					m
				},
				'within_range?': funcc { ...args -> (args[1] as Range).containsWithinBounds(args[0]) },
				'is_range_reverse?': funcc { ...args -> (args[1] as Range).reverse },
				max: funcc { ...args -> args.max() },
				min: funcc { ...args -> args.min() },
				max_in: funcc { ...args -> args[0].invokeMethod('max', null) },
				min_in: funcc { ...args -> args[0].invokeMethod('min', null) },
				max_via: func { KismetObject... args -> args[0].inner().invokeMethod('max', args[1].&call) },
				min_via: func { KismetObject... args -> args[0].inner().invokeMethod('min', args[1].&call) },
				consume: func { KismetObject... args -> args[1].call(args[0]) },
				tap: func { KismetObject... args -> args[1].call(args[0]); args[0] },
				sleep: funcc { ...args -> sleep args[0] as long },
				times_do: func { KismetObject... args ->
					def n = (Number) args[0].inner()
					def l = new ArrayList(n.intValue())
					for (def i = n - n; i < n; i += 1) l.add args[1].call(Kismet.model(i))
					l
				},
				compose: func { KismetObject... args ->
					funcc { ...a ->
						def l = a
						for (int i = args.length - 1; i >= 0; --i) {
							l = args[i].call(l)
						}
						l
					}
				},
				with_result: macr { Context c, Expression... exprs ->
					if (exprs.length == 0) return null
					String name = 'result'
					KismetObject value = Kismet.NULL
					Expression code
					if (exprs.length == 1) code = exprs[0]
					else if (exprs.length == 2) {
						name = resolveName(exprs[0], c, 'setting a result name')
						code = exprs[1]
					} else if (exprs.length == 3) {
						name = resolveName(exprs[0], c, 'setting a result name')
						value = exprs[1].evaluate(c)
						code = exprs[2]
					} else throw new UnexpectedSyntaxException('with_result argument length not 0..3')
					def d = c.child()
					d.set(name, value)
					code.evaluate(d)
					d.getProperty(name)
				},
				'number?': funcc { ...args -> args[0] instanceof Number },
				'|>': macr { Context c, Expression... args ->
					pipeForward(c, args[0].evaluate(c), args.tail().toList())
				},
				'<|': macr { Context c, Expression... args ->
					pipeBackward(c, args[0].evaluate(c), args.tail().toList())
				},
				gcd: funcc { ...args -> gcd(args[0] as Number, args[1] as Number) },
				lcm: funcc { ...args -> lcm(args[0] as Number, args[1] as Number) },
				reduce_ratio: funcc { ...args ->
					Pair pair = args[0] as Pair
					def (Number a, Number b) = [pair.first as Number, pair.second as Number]
					Number gcd = gcd(a, b)
					(a, b) = [a.intdiv(gcd), b.intdiv(gcd)]
					new Pair(a, b)
				},
				repr_expr: funcc { ...args -> ((Expression) args[0]).repr() },
				sum_range: funcc { ...args ->
					Range r = args[0] as Range
					def (Number to, Number from) = [r.to as Number, r.from as Number]
					Number x = to.minus(from).next()
					x.multiply(from.plus(x)).intdiv(2)
				},
				sum_range_with_step: funcc { ...args ->
					Range r = args[0] as Range
					Number step = args[1] as Number
					def (Number to, Number from) = [(r.to as Number).next(), r.from as Number]
					to.minus(from).intdiv(step).multiply(from.plus(to.minus(step))).intdiv(2)
				},
				'subsequence?': funcc { ...args ->
					Iterator a = args[1].iterator()
					Iterator b = args[0].iterator()
					if (!b.hasNext()) return true
					def last = ++b
					while (a.hasNext() && b.hasNext()) {
						if (last == ++a) last = ++b
					}
					b.hasNext()
				},
				'supersequence?': funcc { ...args ->
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
					List<Long> times = new ArrayList<>(iterations)
					for (int i = 0; i < iterations; ++i) {
						long a = System.nanoTime()
						args[1].evaluate(c)
						long b = System.nanoTime()
						times.add(b - a)
					}
					(times.sum() as long) / times.size()
				},
				average_time_millis: macr { Context c, Expression... args ->
					int iterations = args[0].evaluate(c).inner() as int
					List<Long> times = new ArrayList<>(iterations)
					for (int i = 0; i < iterations; ++i) {
						long a = System.currentTimeMillis()
						args[1].evaluate(c)
						long b = System.currentTimeMillis()
						times.add(b - a)
					}
					(times.sum() as long) / times.size()
				},
				average_time_seconds: macr { Context c, Expression... args ->
					int iterations = args[0].evaluate(c).inner() as int
					List<Long> times = new ArrayList<>(iterations)
					for (int i = 0; i < iterations; ++i) {
						long a = System.currentTimeSeconds()
						args[1].evaluate(c)
						long b = System.currentTimeSeconds()
						times.add(b - a)
					}
					(times.sum() as long) / times.size()
				},
				list_time_nanos: macr { Context c, Expression... args ->
					int iterations = args[0].evaluate(c).inner() as int
					List<Long> times = new ArrayList<>(iterations)
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
					List<Long> times = new ArrayList<>(iterations)
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
					List<Long> times = new ArrayList<>(iterations)
					for (int i = 0; i < iterations; ++i) {
						long a = System.currentTimeSeconds()
						args[1].evaluate(c)
						long b = System.currentTimeSeconds()
						times.add(b - a)
					}
					times
				},
				'|>|': macr { Context c, Expression... args ->
					infixCallsLTR(c, args.toList())
				},
				probability: funcc { ...a ->
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
		if (n instanceof PathExpression) name = ((PathExpression) n).text
		else if (n instanceof NumberExpression) throw new UnexpectedSyntaxException("Name in $op was a number, not allowed")
		else {
			KismetObject val = n.evaluate(c)
			if (val.inner() instanceof String) name = val.inner()
			else throw new UnexpectedValueException("Name in $op wasnt a string")
		}
		name
	}

	static KismetObject assign(Context c, Expression[] x, KismetObject value, String op = '=') {
		int i = 1
		for (a in x) {
			if (a instanceof StringExpression)
				c.assign(((StringExpression) a).value.inner(), value)
			else if (a instanceof PathExpression)
				if (((PathExpression) a).path.expressions.size() == 1)
					c.assign(((PathExpression) a).text, value)
				else {
					def b = ((PathExpression) a).path.dropLastAndLast()
					def (Path.PathStep last, Path p) = [b.first, b.second]
					KismetObject val = Kismet.model p.apply(c)
					if (last instanceof Path.PropertyPathStep)
						val.invokeMethod('putAt', [last.raw, value])
					else val.invokeMethod('putAt', [((Path.SubscriptPathStep) last).val, value])
				}
			else throw new UnexpectedSyntaxException(
						"Did not expect expression type for argument $i of $op to be ${a.class.simpleName - 'Expression'}")
			++i
		}
		value
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

	static KismetObject pipeForward(Context c, KismetObject val, List<Expression> args) {
		for (exp in args) {
			if (exp instanceof CallExpression) {
				List<Expression> exprs = new ArrayList<>()
				exprs.add(((CallExpression) exp).value)
				c.set('$_', val)
				exprs.add(new PathExpression('$_'))
				exprs.addAll(((CallExpression) exp).arguments)
				def ex = new CallExpression(exprs)
				val = ex.evaluate(c)
			} else if (exp instanceof BlockExpression) {
				val = pipeForward(c, val, ((BlockExpression) exp).content)
			} else if (exp instanceof PathExpression) {
				c.set('$_', val)
				val = new CallExpression([exp, new PathExpression('$_')]).evaluate(c)
			} else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in |>')
		}
		val
	}

	static KismetObject pipeBackward(Context c, KismetObject val, List<Expression> args) {
		for (exp in args) {
			if (exp instanceof CallExpression) {
				List<Expression> exprs = new ArrayList<>()
				exprs.add(((CallExpression) exp).value)
				exprs.addAll(((CallExpression) exp).arguments)
				c.set('$_', val)
				exprs.add(new PathExpression('$_'))
				CallExpression x = new CallExpression(exprs)
				val = x.evaluate(c)
			} else if (exp instanceof BlockExpression) {
				val = pipeBackward(c, val, ((BlockExpression) exp).content)
			} else if (exp instanceof PathExpression) {
				c.set('$_', val)
				val = new CallExpression([exp, new PathExpression('$_')]).evaluate(c)
			} else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in <|')
		}
		val
	}

	static Expression prepareInfixLTR(Context c, Expression expr) {
		if (expr instanceof BlockExpression) {
			new StaticExpression(expr, evalInfixLTR(c, expr))
		} else if (expr instanceof CallExpression) {
			new StaticExpression(expr, evalInfixLTR(c, expr))
		} else expr
	}

	static KismetObject evalInfixLTR(Context c, Expression expr) {
		if (expr instanceof CallExpression) infixCallsLTR(c, ((CallExpression) expr).expressions)
		else if (expr instanceof BlockExpression) {
			def result = Kismet.NULL
			for (x in ((BlockExpression) expr).content) result = evalInfixLTR(c, x)
			result
		} else expr.evaluate(c)
	}

	private static final PathExpression INFIX_CALLS_LTR_PATH = new PathExpression('|>|')
	static KismetObject infixCallsLTR(Context c, List<Expression> args) {
		if (args.empty) return Kismet.NULL
		else if (args.size() == 1) return evalInfixLTR(c, args[0])
		else if (args.size() == 2) {
			return INFIX_CALLS_LTR_PATH == args[0] ?
					args[1].evaluate(c) :
					evalInfixLTR(c, args[0]).call(evalInfixLTR(c, args[1]))
		} else if (args.size() % 2 == 0)
			throw new UnexpectedSyntaxException('Even number of arguments for LTR infix function calls')
		List<List<Expression>> calls = [[
				prepareInfixLTR(c, args[1]),
				prepareInfixLTR(c, args[0]),
				prepareInfixLTR(c, args[2])]]
		for (int i = 3; i < args.size(); ++i) {
			Expression ex = prepareInfixLTR c, args[i]
			def last = calls.last()
			if (i % 2 == 0) last.add(ex)
			else if (ex != last[0]) calls.add([ex])
		}
		new CallExpression((List<Expression>) calls.inject { a, b ->
			[b[0], new CallExpression(a), *b.drop(1)]
		}).evaluate(c)
	}

	static boolean check(Context c, KismetObject val, Expression exp) {
		if (exp instanceof CallExpression) {
			List<Expression> exprs = new ArrayList<>()
			def valu = ((CallExpression) exp).value
			exprs.add(valu instanceof PathExpression ? new PathExpression(((PathExpression) valu).text + '?') : valu)
			c.set('$_', val)
			exprs.add(new PathExpression('$_'))
			exprs.addAll(((CallExpression) exp).arguments)
			CallExpression x = new CallExpression(exprs)
			x.evaluate(c)
		} else if (exp instanceof BlockExpression) {
			boolean result = true
			for (x in ((BlockExpression) exp).content) result = check(c, val, x)
			result
		} else if (exp instanceof PathExpression) {
			c.set('$_', val)
			new CallExpression([new PathExpression(((PathExpression) exp).text + '?'),
					new PathExpression('$_')] as List<Expression>).evaluate(c)
		} else if (exp instanceof StringExpression) {
			val.inner() == ((StringExpression) exp).value.inner()
		} else if (exp instanceof NumberExpression) {
			val.inner() == ((NumberExpression) exp).value.inner()
		}
		else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in check')
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

	static GroovyMacro macr(Closure c) {
		new GroovyMacro(c)
	}

	static GroovyFunction func(Closure c) {
		new GroovyFunction(false, c)
	}

	static GroovyFunction funcc(Closure c) {
		new GroovyFunction(true, c)
	}
}


@CompileStatic
class Block {
	Expression expression
	Context context

	Block(Expression expr, Context context = new Context()) {
		expression = expr
		this.context = context
	}

	KismetObject evaluate() { expression.evaluate(context) }

	KismetObject call() { evaluate() }

	Block child() {
		new Block(expression, new Context(context))
	}
}

@CompileStatic
class Context {
	Context parent
	Map<String, KismetObject> variables
	Map<String, KismetObject<KismetCallable>> callables

	Context(Context parent = null, Map<String, KismetObject> variables = [:]) {
		this.parent = parent
		setVariables variables
	}

	KismetObject getProperty(String name) {
		def ours = getVariables().get(name)
		if (null != ours) ours
		else if (null != parent) parent.getProperty(name)
		else throw new UndefinedVariableException(name)
	}

	KismetObject set(String name, KismetObject value) {
		getVariables().put(name, value)
	}

	KismetObject define(String name, KismetObject value) {
		def d = getVariables()
		if (d.containsKey(name))
			throw new VariableExistsException("Variable $name already exists")
		d.put(name, value)
	}

	KismetObject assign(Context original = this, String name, KismetObject value) {
		def d = getVariables()
		if (d.containsKey(name)) d.put(name, value)
		else if (null != parent)
			parent.assign(original, name, value)
		else original.set(name, value)
	}

	KismetObject change(String name, KismetObject value) {
		def d = getVariables()
		if (d.containsKey(name)) d.put(name, value)
		else if (null != parent)
			parent.change(name, value)
		else throw new UndefinedVariableException(name)
	}

	Block child(Expression expr) {
		new Block(expr, this)
	}

	Block child(Expression[] expr) {
		new Block(new BlockExpression(expr.toList()), this)
	}

	Block child(List<Expression> expr) {
		new Block(new BlockExpression(expr), this)
	}

	Context child() {
		new Context(this)
	}

	KismetObject childEval(Expression expr) {
		child(expr).evaluate()
	}

	KismetObject childEval(Expression[] expr) {
		child(expr).evaluate()
	}

	KismetObject childEval(List<Expression> expr) {
		child(expr).evaluate()
	}

	KismetObject eval(Expression expr) {
		expr.evaluate this
	}

	KismetObject eval(Expression[] expr) {
		def last = Kismet.NULL
		for (e in expr) last = eval(e)
		last
	}

	KismetObject eval(List<Expression> expr) {
		def last = Kismet.NULL
		for (e in expr) last = eval(e)
		last
	}

	def clone() {
		new Context(parent, new HashMap<>(getVariables()))
	}
}

@InheritConstructors
class KismetException extends Exception {}

@InheritConstructors
class KismetAssertionError extends Error {}

@InheritConstructors
class UndefinedVariableException extends KismetException {}

@InheritConstructors
class VariableExistsException extends KismetException {}

@InheritConstructors
class UnexpectedSyntaxException extends KismetException {}

@InheritConstructors
class UnexpectedValueException extends KismetException {}

@CompileStatic
class LineColumnException extends KismetException {
	int ln
	int cl

	LineColumnException(Throwable cause, int ln, int cl) {
		super("At line $ln column $cl", cause)
		this.ln = ln
		this.cl = cl
	}
}