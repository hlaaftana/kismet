package hlaaftana.kismet

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.parser.*

import java.text.SimpleDateFormat
import java.util.regex.Pattern

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class KismetInner {
	static Map<String, KismetObject> defaultContext = [
	Class: KismetClass.meta.object,
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
	Map: new KismetClass(Map, 'Map').object,
	Expression: new KismetClass(Expression, 'Expression').object,
	AtomExpression: new KismetClass(AtomExpression, 'AtomExpression').object,
	CallExpression: new KismetClass(CallExpression, 'CallExpression').object,
	BlockExpression: new KismetClass(BlockExpression, 'BlockExpression').object,
	StringExpression: new KismetClass(StringExpression, 'StringExpression').object,
	NumberExpression: new KismetClass(NumberExpression, 'NumberExpression').object,
	PathExpression: new KismetClass(Path.PathExpression, 'PathExpression').object,
	PropertyPathExpression: new KismetClass(Path.PropertyPathExpression, 'PropertyPathExpression').object,
	SubscriptPathExpression: new KismetClass(Path.SubscriptPathExpression, 'SubscriptPathExpression').object,
	Block: new KismetClass(Block, 'Block').object,
	Function: new KismetClass(Function, 'Function').object,
	Macro: new KismetClass(Macro, 'Macro').object,
	Native: new KismetClass(Object, 'Native').object,
	Regex: new KismetClass(Pattern, 'Regex').object,
	Range: new KismetClass(Range, 'Range').object,
	Pair: new KismetClass(Tuple2, 'Pair').object,
	Iterator: new KismetClass(Iterator, 'Iterator').object,
	Throwable: new KismetClass(Throwable, 'Throwable').object,
	RNG: new KismetClass(Random, 'RNG').object,
	Date: new KismetClass(Date, 'Date').object,
	JSONParser: new KismetClass(JsonSlurper, 'JSONParser').object]

	static {
		Map<String, Object> toConvert = [
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
		'instance?': func { KismetObject... a -> a.drop(1).any { KismetObject<KismetClass> b -> b.inner().isInstance a[0] } },
		'of?': func { KismetObject... a -> a.drop(1).any { KismetObject b ->
			def x = b.inner()
			if (x instanceof KismetClass) x.isInstance(a[0])
			else throw new UnexpectedValueException('Argument in of? not class')
		} },
		'not_of?': func { KismetObject... a -> a.drop(1).every { KismetObject b ->
			def x = b.inner()
			if (x instanceof KismetClass) !x.isInstance(a[0])
			else throw new UnexpectedValueException('Argument in not_of? not class')
		} },
		variable: macro { Block c, Expression... exprs ->
			String name = exprs[0].evaluate(c).toString()
			exprs.length > 1 ? c.context.directSet(name, exprs[1].evaluate(c))
							 : c.context.getProperty(name)
		},
		variables: macro { Block c, Expression... exprs -> new HashMap<>(c.context.data) },
		current_block: macro { Block c, Expression... exprs -> c },
		java_class_name: funcc { ...args -> args[0].class.name },
		try: macro { Block c, Expression... exprs ->
			try {
				exprs[0].evaluate(c)
			} catch (ex) {
				Block x = c.anonymousClone()
				x.context.directSet(resolveName(exprs[1], x, 'catching an exception'), Kismet.model(ex))
				exprs[2].evaluate(c)
			}
		},
		raise: func { ...args ->
			if (args[0] instanceof Throwable) throw (Throwable) args[0]
			else throw new UnexpectedValueException('raise called with non-throwable ' + args[0])
		},
		nop: func { KismetObject... args -> },
		assert: macro { Block c, Expression... exprs ->
			KismetObject val
			for (e in exprs) if (!(val = e.evaluate(c)))
				throw new KismetAssertionError('Assertion failed for expression ' +
						Expression.repr(e) + '. Value should have been true-like but was ' + val)
		},
		assert_not: macro { Block c, Expression... exprs ->
			KismetObject val
			for (e in exprs) if ((val = e.evaluate(c)))
				throw new KismetAssertionError('Assertion failed for expression ' +
						Expression.repr(e) + '. Value should have been false-like but was ' + val)
		},
		assert_is: macro { Block c, Expression... exprs ->
			KismetObject val = exprs[0].evaluate(c)
			KismetObject latest
			for (e in exprs.drop(1)) if (val != (latest = e.evaluate(c)))
				throw new KismetAssertionError('Assertion failed for expression ' +
						Expression.repr(e) + '. Value was expected to be ' + val +
						' but was ' + latest)
		},
		'assert_isn\'t': macro { Block c, Expression... exprs ->
			List<KismetObject> values = [exprs[0].evaluate(c)]
			KismetObject retard
			KismetObject latest
			for (e in exprs.drop(1)) if ((retard = values.find((latest = e.evaluate(c)).&equals)))
				throw new KismetAssertionError('Assertion failed for expression ' +
						Expression.repr(e) + '. Value was expected NOT to be ' + retard +
						' but was ' + latest)
		},
		assert_of: macro { Block c, Expression... exprs ->
			KismetObject val = exprs[0].evaluate(c)
			List a = []
			for (e in exprs.drop(1)) {
				def cl = e.evaluate(c).inner()
				if (!(cl instanceof KismetClass))
					throw new UnexpectedValueException('Argument in assert_of wasn\'t a class')
				a.add(cl)
				if (((KismetClass) cl).isInstance(val)) return
			}
			throw new KismetAssertionError('Assertion failed for expression ' +
				Expression.repr(exprs[0]) + '. Value was expected to be an instance ' +
					'of one of classes ' + a.join(', ') + ' but turned out to be value ' +
					val + ' and of class ' + val.kclass)
		},
		assert_not_of: macro { Block c, Expression... exprs ->
			KismetObject val = exprs[0].evaluate(c)
			List a = []
			for (e in exprs.drop(1)) {
				def cl = e.evaluate(c).inner()
				if (!(cl instanceof KismetClass))
					throw new UnexpectedValueException('Argument in assert_not_of wasn\'t a class')
				a.add(cl)
				if (((KismetClass) cl).isInstance(val))
					throw new KismetAssertionError('Assertion failed for expression ' +
						Expression.repr(exprs[0]) + '. Value was expected to be NOT an instance ' +
						'of class ' + a.join(', ') + ' but turned out to be value ' +
						val + ' and of class ' + val.kclass)
			}
		},
		'!%': funcc { ...a -> a[0].hashCode() },
		percent: funcc { ...a -> a[0].invokeMethod 'div', 100 },
		to_percent: funcc { ...a -> a[0].invokeMethod 'multiply', 100 },
		as: func { KismetObject... a -> a[0].as((Class) a[1].inner()) },
		'is?': funcc { ...args -> args.inject { a, b -> a == b } },
		'isn\'t?': funcc { ...args -> args.inject { a, b -> a != b } },
		'same?': funcc { ...a -> a[0].is(a[1]) },
		'not_same?': funcc { ...a -> !a[0].is(a[1]) },
		'empty?': funcc { ...a -> a[0].invokeMethod('isEmpty', null) },
		'in?': funcc { ...a -> a[0] in a[1] },
		'not_in?': funcc { ...a -> !(a[0] in a[1]) },
		not: funcc { ...a -> !(a[0]) },
		and: macro { Block c, Expression... exprs ->
			for (it in exprs) if (!it.evaluate(c)) return false; true
		},
		or: macro { Block c, Expression... exprs ->
			for (it in exprs) if (it.evaluate(c)) return true; false
		},
		'??': macro { Block c, Expression... exprs ->
			KismetObject x = Kismet.model(null)
			for (it in exprs) if ((x = it.evaluate(c))) return x; x
		},
		xor: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'xor', b } },
		bool: funcc { ...a -> a[0] as boolean },
		bnot: funcc { ...a -> a[0].invokeMethod 'bitwiseNegate', null },
		band: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'and', b } },
		bor: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'or', b } },
		bxor: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'xor', b } },
		lsh: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'leftShift', b } },
		rsh: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'rightShift', b } },
		ursh: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'rightShiftUnsigned', b } },
		'<': funcc { ...args -> args.inject { a, b -> ((int) a.invokeMethod('compareTo', b)) < 0 } },
		'>': funcc { ...args -> args.inject { a, b -> ((int) a.invokeMethod('compareTo', b)) > 0 } },
		'<=': funcc { ...args -> args.inject { a, b -> ((int) a.invokeMethod('compareTo', b)) <= 0 } },
		'>=': funcc { ...args -> args.inject { a, b -> ((int) a.invokeMethod('compareTo', b)) >= 0 } },
		positive: funcc { ...a -> a[0].invokeMethod 'unaryPlus', null },
		negative: funcc { ...a -> a[0].invokeMethod 'unaryMinus', null },
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
		absolute: funcc { ...a -> a[0].invokeMethod('abs', null) },
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
		'defined?': macro { Block c, Expression... exprs ->
			try {
				c.context.getProperty(resolveName(exprs[0], c, "checking if variable is defined"))
				true
			} catch (UndefinedVariableException ignored) {
				false
			}
		},
		new_rng: funcc { ...args -> args.length > 0 ? new Random(args[0] as long) : new Random() },
		random_int8_list_from_reference: funcc { ...args ->
			byte[] bytes = args[1] as byte[]
			(args[0] as Random).nextBytes(bytes)
			bytes as List<Byte>
		},
		random_int32_of_all: funcc { ...args -> (args[0] as Random).nextInt() },
		random_int32_with_max: funcc { ...args -> (args[0] as Random).nextInt(args[1] as int) },
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
		shuffle: funcc { ...args ->
			args[1] instanceof Random ? Collections.shuffle(toList(args[0]), args[1] as Random)
					: Collections.shuffle(toList(args[0]))
		},
		sample: funcc { ...args ->
			List x = toList(args[0])
			Random r = args[1] instanceof Random ? args[1] as Random : new Random()
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
		each_range_with_step: func { KismetObject... args -> (args[0].inner() as Range)
				.step(args[1].inner() as int, args[2].&call) },
		replace: funcc { ...args -> args[0].toString().replace(args[1].toString(),
				args.length > 2 ? args[2].toString() : '') },
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
		upper: funcc { ...args -> args[0] instanceof char ? Character.toUpperCase((char) args[0]) :
								  args[0] instanceof int ? Character.toUpperCase((int) args[0]) :
								  args[1].toString().toUpperCase() },
		lower: funcc { ...args -> args[0] instanceof char ? Character.toLowerCase((char) args[0]) :
								  args[0] instanceof int ? Character.toLowerCase((int) args[0]) :
								  args[0].toString().toLowerCase() },
		'upper?': funcc { ...args -> args[0] instanceof char ? Character.isUpperCase((char) args[0]) :
									 args[0] instanceof int ? Character.isUpperCase((int) args[0]) :
									 args[0].toString().toCharArray().every(Character.&isUpperCase) },
		'lower?': funcc { ...args -> args[0] instanceof char ? Character.isLowerCase((char) args[0]) :
									 args[0] instanceof int ? Character.isLowerCase((int) args[0]) :
									 args[0].toString().toCharArray().every(Character.&isLowerCase) },
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
					return x.substring(0, chars.length - i + 1)
			}
			''
		},
		regex: funcc { ...a -> a[0].invokeMethod 'bitwiseNegate', null },
		access_set: funcc { ...a -> a[0].invokeMethod('putAt', [a[1], a[2]]) },
		access_get: funcc { ...a -> a[0].invokeMethod('getAt', a[1]) },
		string: func { KismetObject... a ->
			if (a.length == 1) return a[0].toString()
			StringBuilder x = new StringBuilder()
			for (s in a) x.append(s.toString())
			x.toString()
		},
		int: func { KismetObject... a -> a[0].as BigInteger },
		int8: func { KismetObject... a -> a[0].as byte },
		int16: func { KismetObject... a -> a[0].as short },
		int32: func { KismetObject... a -> a[0].as int },
		int64: func { KismetObject... a -> a[0].as long },
		char: func { KismetObject... a -> a[0].as char },
		float: func { KismetObject... a -> a[0].as BigDecimal },
		float32: func { KismetObject... a -> a[0].as float },
		float64: func { KismetObject... a -> a[0].as double },
		'~': funcc { ...args -> args[0].iterator() },
		list_iterator: funcc { ...args -> args[0].invokeMethod('listIterator', null) },
		has_next: funcc { ...args -> args[0].invokeMethod('hasNext', null) },
		next: funcc { ...args -> args[0].invokeMethod('next', null) },
		has_prev: funcc { ...args -> args[0].invokeMethod('hasPrevious', null) },
		prev: funcc { ...args -> args[0].invokeMethod('previous', null) },
		new_list: funcc { ...args -> new ArrayList(args[0] as int) },
		list: funcc { ...args -> args.toList() },
		new_set: funcc { ...args -> args.length > 1 ? new HashSet(args[0] as int, args[1] as float) : new HashSet(args[0] as int) },
		set: funcc { ...args ->
			Set x = new HashSet()
			for (a in args) x.add(a)
			x
		},
		pair: funcc { ...args -> new Tuple2(args[0], args[1]) },
		to_list: funcc { ...args -> toList(args[0]) },
		to_set: funcc { ...args -> toSet(args[0]) },
		to_pair: funcc { ...args -> new Tuple2(args[0].invokeMethod('getAt', 0), args[0].invokeMethod('getAt', 1)) },
		entry_pairs: funcc { ...args ->
			def r = []
			for (x in (args[0] as Map)) r.add(new Tuple2(x.key, x.value))
			r
		},
		map_from_pairs: funcc { ...args ->
			Map m = [:]
			for (x in args[0]) {
				def p = x as Tuple2
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
		uncons: funcc { ...args -> new Tuple2(args[0].invokeMethod('head', null), args[0].invokeMethod('tail', null)) },
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
		escape: funcc { ...args -> StringEscaper.escapeSoda(args[0].toString()) },
		unescape: funcc { ...args -> StringEscaper.escapeSoda(args[0].toString()) },
		copy_map: funcc { ...args -> new HashMap(args[0] as Map) },
		new_map: funcc { ...args -> args.length > 1 ? new HashMap(args[0] as int, args[1] as float) : new HashMap(args[0] as int) },
		zip: funcc { ...args -> args.toList().transpose() },
		knit: func { KismetObject... args -> toList(args[0].inner()).transpose()
				.collect { args[1].invokeMethod('call', it as Object[]) } },
		transpose: funcc { ...args -> toList(args[0]).transpose() },
		'unique?': funcc { ...args -> args[0].invokeMethod('size', null) ==
					args[0].invokeMethod('unique', false).invokeMethod('size', null) },
		'unique!': funcc { ...args -> args[0].invokeMethod('unique', null) },
		unique: funcc { ...args -> args[0].invokeMethod('unique', false) },
		'unique_via?': func { KismetObject... args -> args[0].inner().invokeMethod('size', null) ==
			args[0].inner().invokeMethod('unique', [false, args[1].&call]) },
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
		size: funcc { ...a -> a[0].invokeMethod('size', null) },
		keys: funcc { ...a -> a[0].invokeMethod('keySet', null).invokeMethod('toList', null) },
		values: funcc { ...a -> a[0].invokeMethod('values', null) },
		reverse: funcc { ...a -> a[0].invokeMethod('reverse', a[0] instanceof CharSequence ? null : false) },
		'reverse!': funcc { ...a -> a[0].invokeMethod('reverse', null) },
		'reverse?': funcc { ...a -> a[0].invokeMethod('reverse', false) == a[1] },
		sprintf: funcc { ...args -> String.invokeMethod('format', args) },
		expr_type: funcc { ...args -> args[0] instanceof Expression ?
			(args[0].class.simpleName - 'Expression').uncapitalize() : null },
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
			else Collections.invokeMethod('indexOfSubList', [toList(args[1]), toList(args[0])]) == 0
		},
		'suffix?': funcc { ...args ->
			if (args[1] instanceof String) ((String) args[1]).endsWith(args[0].toString())
			else {
				def a = toList(args[0])
				def b = toList(args[1])
				Collections.invokeMethod('lastIndexOfSubList', [b, a]) == b.size() - a.size()
			}
		},
		'infix?': funcc { ...args ->
			if (args[1] instanceof String) ((String) args[1]).contains(args[0].toString())
			else Collections.invokeMethod('indexOfSubList', [toList(args[1]), toList(args[0])]) != -1
		},
		'superset?': funcc { ...args -> args[0].invokeMethod('containsAll', args[1]) },
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
		hex: macro { Block c, Expression... x ->
			if (x[0] instanceof NumberExpression || x[0] instanceof AtomExpression) {
				String t = x[0] instanceof NumberExpression ? ((NumberExpression) x[0]).value.toString()
						 : ((AtomExpression) x[0]).text
				new BigInteger(t, 16)
			} else throw new UnexpectedSyntaxException('Expression after hex was not a hexadecimal number literal.'
													+ ' To convert hex strings to integers do [from_base str 16], '
													+ ' and to convert integers to hex strings do [to_base i 16].')
		},
		binary: macro { Block c, Expression... x ->
			if (x[0] instanceof NumberExpression) {
				String t = ((NumberExpression) x[0]).value.toString()
				new BigInteger(t, 2)
			} else throw new UnexpectedSyntaxException('Expression after binary was not a binary number literal.'
													+ ' To convert binary strings to integers do [from_base str 2], '
													+ ' and to convert integers to binary strings do [to_base i 2].')
		},
		octal: macro { Block c, Expression... x ->
			if (x[0] instanceof NumberExpression) {
				String t = ((NumberExpression) x[0]).value.toString()
				new BigInteger(t, 8)
			} else throw new UnexpectedSyntaxException('Expression after octal was not a octal number literal.'
													+ ' To convert octal strings to integers do [from_base str 8], '
													+ ' and to convert integers to octal strings do [to_base i 8].')
		},
		to_base: funcc { ...a -> (a[0] as BigInteger).toString(a[1] as int) },
		from_base: funcc { ...a -> new BigInteger(a[0].toString(), a[1] as int) },
		'::=': macro { Block c, Expression... x ->
			if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for ::=')
			KismetObject value = x.length == 1 ? Kismet.model(null) : x.last().evaluate(c)
			int i = 1
			for (a in x.init()) {
				c.context.directSet(resolveName(a, c, "direct setting argument $i"), value)
				++i
			}
			value
		},
		':=': macro { Block c, Expression... x ->
			if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for :=')
			KismetObject value = x.length == 1 ? Kismet.model(null) : x.last().evaluate(c)
			int i = 1
			for (a in x.init()) {
				c.context.define(resolveName(a, c, "defining argument $i"), value)
				++i
			}
			value
		},
		'=': macro { Block c, Expression... x ->
			if (x.length == 0) throw new UnexpectedSyntaxException('0 arguments for =')
			KismetObject value = x.length == 1 ? Kismet.model(null) : x.last().evaluate(c)
			int i = 1
			for (a in x.init()) {
				if (a instanceof StringExpression)
					c.context.change(((StringExpression) a).value, value)
				else if (a instanceof AtomExpression)
					if (((AtomExpression) a).path.expressions.size() == 1)
						c.context.change(((AtomExpression) a).text, value)
					else {
						def b = ((AtomExpression) a).path.dropLastAndLast()
						def (Path.PathExpression last, Path p) = [b.first, b.second]
						KismetObject val = Kismet.model p.apply(c.context)
						if (last instanceof Path.PropertyPathExpression)
							val.invokeMethod('putAt', [last.raw, value])
						else val.invokeMethod('putAt', [((Path.SubscriptPathExpression) last).val, value])
					}
				else throw new UnexpectedSyntaxException(
					"Did not expect expression type for argument $i of = to be ${a.class.simpleName - 'Expression'}")
				++i
			}
			value
		},
		fn: macro { Block c, Expression... exprs ->
			new KismetFunction(exprs.init(), Kismet.model(newCode(c, exprs.last())))
		},
		mcr: macro { Block c, Expression... exprs ->
			new KismetMacro(Kismet.model(newCode(c, exprs)))
		},
		defn: macro { Block c, Expression... exprs ->
			List<Expression> kill = new ArrayList<>()
			kill.add(new AtomExpression('fn'))
			for (int i = 1; i < exprs.length; ++i) kill.add(exprs[i])
			def ex = new CallExpression([new AtomExpression(':='), exprs[0],
			                             new CallExpression(kill)])
			ex.evaluate(c)
		},
		defmcr: macro { Block c, Expression... exprs ->
			List<Expression> kill = new ArrayList<>()
			kill.add(new AtomExpression('mcr'))
			for (int i = 1; i < exprs.length; ++i) kill.add(exprs[i])
			def ex = new CallExpression([new AtomExpression(':='), exprs[0],
			                             new CallExpression(kill)])
			ex.evaluate(c)
		},
		undef: macro { Block c, Expression... exprs -> c.context.getData().containsKey(exprs[0].evaluate(c)) },
		block: macro { Block c, Expression... exprs ->
			newCode(c, exprs)
		},
		increment: macro { Block c, Expression... exprs ->
			new CallExpression([new AtomExpression('='), exprs[0],
			                    new CallExpression([new AtomExpression('next'), exprs[0]])])
								.evaluate(c)
		},
		decrement: macro { Block c, Expression... exprs ->
			new CallExpression([new AtomExpression('='), exprs[0],
			                    new CallExpression([new AtomExpression('prev'), exprs[0]])])
					.evaluate(c)
		},
		let: macro { Block c, Expression... exprs ->
			Expression cnt = exprs[0]
			Block b = newCode(c, exprs.drop(1))
			if (cnt instanceof CallExpression) {
				CallExpression ex = (CallExpression) cnt
				Iterator<Expression> defs = ex.expressions.iterator()
				while (defs.hasNext()) {
					Expression n = defs.next()
					if (!defs.hasNext()) break
					String name
					if (n instanceof AtomExpression) name = ((AtomExpression) n).text
					else if (n instanceof NumberExpression) throw new UnexpectedSyntaxException('Cant define a number (let)')
					else {
						KismetObject val = n.evaluate(c)
						if (val.inner() instanceof String) name = val.inner()
						else throw new UnexpectedValueException('Evaluated first expression of define wasnt a string (let)')
					}
					b.context.directSet(name, defs.next().evaluate(c))
				}
			} else throw new UnexpectedSyntaxException('Expression after let is not a call-type expression')
			b
		},
		eval: macro { Block c, Expression... a ->
			def x = a[0].evaluate(c)
			if (x.inner() instanceof Block) ((Block) x.inner()).evaluate()
			else if (x.inner() instanceof Expression)
				((Expression) x.inner()).evaluate(a.length > 1 ? a[1].evaluate(c).inner() as Block : c)
			else if (x.inner() instanceof Path)
				((Path) x.inner()).apply((a.length > 1 ? a[1].evaluate(c).inner() as Block : c).context)
			else if (x.inner() instanceof String)
				if (a.length > 1) parse((String) x.inner()).evaluate(a[1].evaluate(c).inner() as Block)
				else newCode(c, parse((String) x.inner())).evaluate()
			else throw new UnexpectedValueException('Expected first value of eval to be an expression, block, path or string')
		},
		quote: macro { Block c, Expression... exprs -> exprs.length == 1 ? exprs[0] :
				new BlockExpression(exprs.toList()) },
		if: macro { Block c, Expression... exprs ->
			Block b = newCode(c, exprs.drop(1))
			KismetObject j = Kismet.model(null)
			if (exprs[0].evaluate(c)) j = b.evaluate()
			j
		},
		unless: macro { Block c, Expression... exprs ->
			Block b = newCode(c, exprs.drop(1))
			KismetObject j = Kismet.model(null)
			if (!exprs[0].evaluate(c)) j = b.evaluate()
			j
		},
		if_chain: macro { Block c, Expression... ab ->
			Iterator<Expression> a = ab.iterator()
			KismetObject x = Kismet.NULL
			while (a.hasNext()) {
				x = a.next().evaluate(c)
				if (a.hasNext() && x) return a.next().evaluate(c)
			}
			x
		},
		unless_chain: macro { Block c, Expression... ab ->
			Iterator<Expression> a = ab.iterator()
			KismetObject x = Kismet.NULL
			while (a.hasNext()) {
				x = a.next().evaluate(c)
				if (a.hasNext() && !x) return a.next().evaluate(c)
			}
			x
		},
		if_else: macro { Block c, Expression... x -> x[0].evaluate(c) ? x[1].evaluate(c) : x[2].evaluate(c) },
		unless_else: macro { Block c, Expression... x -> !x[0].evaluate(c) ? x[1].evaluate(c) : x[2].evaluate(c) },
		while: macro { Block c, Expression... exprs ->
			Block b = newCode(c, exprs.drop(1))
			KismetObject j = Kismet.model(null)
			while (exprs[0].evaluate(c)) j = b.anonymousClone().evaluate()
			j
		},
		until: macro { Block c, Expression... exprs ->
			Block b = newCode(c, exprs.drop(1))
			KismetObject j = Kismet.model(null)
			while (!exprs[0].evaluate(c)) j = b.anonymousClone().evaluate()
			j
		},
		for_each: macro { Block c, Expression... exprs ->
			String n = resolveName(exprs[0], c, 'foreach')
			Block b = newCode(c, exprs.drop(2))
			KismetObject a = Kismet.model(null)
			for (x in exprs[1].evaluate(c).inner()){
				Block y = b.anonymousClone()
				y.context.directSet(n, Kismet.model(x))
				a = y()
			}
			a
		},
		for: macro { Block c, Expression... exprs ->
			Block b = newCode(c, exprs.drop(3))
			KismetObject result = Kismet.model(null)
			for (exprs[0].evaluate(b); exprs[1].evaluate(b); exprs[2].evaluate(b)){
				result = b.anonymousClone()()
			}
			result
		},
		each: func { KismetObject... args -> args[0].inner().each(args[1].&call) },
		each_with_index: func { KismetObject... args -> args[0].inner().invokeMethod('eachWithIndex', args[1].&call) },
		collect: func { KismetObject... args -> args[0].inner().collect(args[1].&call) },
		collect_nested: func { KismetObject... args -> args[0].inner().invokeMethod('collectNested', args[1].&call) },
		collect_many: func { KismetObject... args -> args[0].inner().invokeMethod('collectMany', args[1].&call) },
		collect_map: func { KismetObject... args -> args[0].inner()
				.invokeMethod('collectEntries') { ...a -> args[1].call(a).inner() } },
		subsequences: funcc { ...args -> args[0].invokeMethod('subsequences', null) },
		combinations: funcc { ...args -> args[0].invokeMethod('combinations', null) },
		permutations: funcc { ...args -> args[0].invokeMethod('permutations', null) },
		'permutations~': funcc { ...args -> new PermutationGenerator(args[0] instanceof Collection ? (Collection) args[0]
																   : args[0] instanceof Iterable ? (Iterable) args[0]
																   : args[0] instanceof Iterator ? new IteratorIterable((Iterator) args[0])
																   : args[0] as Collection) },
		'any?': func { KismetObject... args -> args[0].inner().any(args[1].&call) },
		'every?': func { KismetObject... args -> args[0].inner().every(args[1].&call) },
		'none?': func { KismetObject... args -> !args[0].inner().any(args[1].&call) },
		find: func { KismetObject... args -> args[0].inner().invokeMethod('find', args[1].&call) },
		find_result: func { KismetObject... args -> args[0].inner().invokeMethod('findResult', args[1].&call) },
		count: func { KismetObject... args -> args[0].inner().invokeMethod('count', args[1].&call) },
		count_by: func { KismetObject... args -> args[0].inner().invokeMethod('countBy', args[1].&call) },
		group_by: func { KismetObject... args -> args[0].inner().invokeMethod('groupBy', args[1].&call) },
		indexed: func { KismetObject... args -> args[0].inner().invokeMethod('indexed', args.length > 1 ? args[1] as int : null) },
		find_all: func { KismetObject... args -> args[0].inner().findAll(args[1].&call) },
		join: funcc { ...args -> args[0].invokeMethod('join', args[1].toString()) },
		inject: func { KismetObject... args -> args[0].inner().inject(args[1].&call) },
		collate: funcc { ...args -> args[0].invokeMethod('collate', args.drop(1)) },
		pop: funcc { ...args -> args[0].invokeMethod('pop', null) },
		add: funcc { ...args -> args[0].invokeMethod('add', args[1]) },
		add_at: funcc { ...args -> args[0].invokeMethod('add', [args[1] as int, args[2]]) },
		add_all: funcc { ...args -> args[0].invokeMethod('addAll', args[1]) },
		add_all_at: funcc { ...args -> args[0].invokeMethod('addAll', [args[1] as int, args[2]]) },
		remove: funcc { ...args -> args[0].invokeMethod('remove', args[1]) },
		remove_elems: funcc { ...args -> args[0].invokeMethod('removeAll', args[1]) },
		remove_any: func { KismetObject... args -> args[0].inner().invokeMethod('removeAll', args[1].&call) },
		remove_elem: funcc { ...args -> args[0].invokeMethod('removeElement', args[1]) },
		get: funcc { ...a ->
			def r = a[0]
			for (int i = 1; i < a.length; ++i)
				r = r.invokeMethod('get', a[i])
			r
		},
		walk: macro { Block c, Expression... args ->
			def r = args[0].evaluate(c)
			for (int i = 1; i < args.length; ++i) {
				Expression x = args[i]
				if (x instanceof AtomExpression)
					r = r.invokeMethod('getAt', ((AtomExpression) x).text)
				else r = r.invokeMethod('getAt', x.evaluate(c))
			}
			r
		},
		clear: funcc { ...args -> args[0].invokeMethod('clear', null) },
		put: funcc { ...args -> args[0].invokeMethod('put', [args[1], args[2]]) },
		put_all: funcc { ...args -> args[0].invokeMethod('putAll', args[1]) },
		'keep_all!': funcc { ...args -> args[0].invokeMethod('retainAll', args[1]) },
		'keep_any!': func { KismetObject... args -> args[0].inner().invokeMethod('retainAll', args[1].&call) },
		'has?': funcc { ...args -> args[0].invokeMethod('contains', args[1]) },
		'has_all?': funcc { ...args -> args[0].invokeMethod('containsAll', args[1]) },
		'has_key?': funcc { ...args -> args[0].invokeMethod('containsKey', args[1]) },
		'has_value?': funcc { ...args -> args[0].invokeMethod('containsValue', args[1]) },
		'is_code_kismet?': funcc { ...args -> args[0] instanceof KismetFunction || args[0] instanceof KismetMacro },
		get_func_code: funcc { ...args -> args[0] instanceof KismetFunction ? ((KismetFunction) args[0]).b :
										  args[0] instanceof KismetMacro ? ((KismetMacro) args[0]).b :
										  false },
		'disjoint?': funcc { ...args -> args[0].invokeMethod('disjoint', args[1]) },
		'intersect?': funcc { ...args -> !args[0].invokeMethod('disjoint', args[1]) },
		call: func { KismetObject... args -> args[0].call(args[1].inner() as Object[]) },
		range: funcc { ...args -> args[0]..args[1] },
		parse_independent_kismet: funcc { ...args ->
			String code = args[0].toString()
			args.length > 1 ? Kismet.parse(code, args[1] as Map) : Kismet.parse(code)
		},
		parse_kismet: funcc { ...args -> parse(args[0].toString()) },
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
		identity: funcc { ...args -> args[0] },
		flatten: funcc { ...args -> args[0].invokeMethod('flatten', null) },
		indices: funcc { ...args -> args[0].invokeMethod('getIndices', null) },
		find_index: func { KismetObject... args -> args[0].inner().invokeMethod('findIndexOf', args[1].&call) },
		find_index_after: func { KismetObject... args -> args[0].inner()
				.invokeMethod('findIndexOf', [args[1] as int, args[2].&call]) },
		find_last_index: func { KismetObject... args -> args[0].inner().invokeMethod('findLastIndexOf', args[1].&call) },
		find_last_index_after: func { KismetObject... args -> args[0].inner()
				.invokeMethod('findLastIndexOf', [args[1] as int, args[2].&call]) },
		find_indices: func { KismetObject... args -> args[0].inner().invokeMethod('findIndexValues', args[1].&call) },
		find_indices_after: func { KismetObject... args -> args[0].inner()
				.invokeMethod('findIndexValues', [args[1] as int, args[2].&call]) },
		intersect: funcc { ...args -> args[0].invokeMethod('intersect', args[1]) },
		split: funcc { ...args -> args[0].invokeMethod('split', args.drop(1)) as List },
		tokenize: funcc { ...args -> args[0].invokeMethod('tokenize', args.drop(1)) },
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

				@Override
				boolean hasNext() {
					this.i < siz - con
				}

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
		times_do: funcc { KismetObject... args ->
			def n = args[0].inner() as int
			List x = new ArrayList(n)
			for (int i = 0; i < n; ++i) x.add(args[1].invokeMethod('call', Kismet.model(i)))
			x
		},
		compose: func { KismetObject... args ->
			funcc { ...a ->
				def l = a
				for (int i = args.length - 1; i >= 0; --i) {
					l = args[i].invokeMethod('call', l)
				}
				l
			}
		},
		with_result: macro { Block c, Expression... exprs ->
			if (exprs.length == 0) return null
			String name = 'result'
			KismetObject value = Kismet.model(null)
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
			Block d = c.anonymousClone()
			d.context.directSet(name, value)
			code.evaluate(c)
			d.context.getProperty(name)
		},
		'number?': funcc { ...args -> args[0] instanceof Number },
		'|>': macro { Block c, Expression... args ->
			pipeForward(c, args[0].evaluate(c), args.drop(1).toList())
		},
		'<|': macro { Block c, Expression... args ->
			pipeBackward(c, args[0].evaluate(c), args.drop(1).toList())
		},
		gcd: funcc { ...args -> gcd(args[0] as Number, args[1] as Number) },
		lcm: funcc { ...args -> lcm(args[0] as Number, args[1] as Number) },
		reduce_ratio: funcc { ...args ->
			Tuple2 pair = args[0] as Tuple2
			def (Number a, Number b) = [pair.first as Number, pair.second as Number]
			Number gcd = gcd(a, b)
			(a, b) = [a.intdiv(gcd), b.intdiv(gcd)]
			new Tuple2(a, b)
		},
		repr_expr: funcc { ...args -> Expression.repr((Expression) args[0]) },
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
		},]
		defaultContext.'true?' = defaultContext.'yes?' = defaultContext.'on?' =
				defaultContext.'?' = defaultContext.bool
		defaultContext.'no?' = defaultContext.'off?' = defaultContext.'false?'
		defaultContext.'subsequence?' = defaultContext.'infix?'
		defaultContext.'subset?' = defaultContext.'has_all?'
		defaultContext.fold = defaultContext.reduce = defaultContext.inject
		defaultContext.length = defaultContext.size
		defaultContext.filter = defaultContext.select = defaultContext.find_all
		defaultContext.succ = defaultContext.next
		defaultContext.'all?' = defaultContext.'every?'
		defaultContext.'less?' = defaultContext.'<'
		defaultContext.'greater?' = defaultContext.'>'
		defaultContext.'less_equal?' = defaultContext.'<='
		defaultContext.'greater_equal?' = defaultContext.'>='
		for (e in toConvert) defaultContext.put(e.key, Kismet.model(e.value))
		defaultContext = defaultContext.asImmutable()
	}

	static String resolveName(Expression n, Block c, String doing) {
		String name
		if (n instanceof AtomExpression) name = ((AtomExpression) n).text
		else if (n instanceof NumberExpression) throw new UnexpectedSyntaxException("Cant $doing a number")
		else {
			KismetObject val = n.evaluate(c)
			if (val.inner() instanceof String) name = val.inner()
			else throw new UnexpectedValueException("Evaluated $doing wasnt a string")
		}
		name
	}

	static Block newCode(Block p, Expression[] exprs) {
		Block b = new Block()
		b.parent = p
		b.expression = exprs.length == 1 ? exprs[0] : new BlockExpression(exprs as List<Expression>)
		b.context = new Context(b)
		b
	}

	static Block newCode(Block p, Expression expr) {
		Block b = new Block()
		b.parent = p
		b.expression = expr
		b.context = new Context(b)
		b
	}

	@SuppressWarnings('GroovyVariableNotAssigned')
	static BlockExpression parse(String code) {
		BlockBuilder builder = new BlockBuilder(false)
		char[] arr = code.toCharArray()
		int len = arr.length
		int ln = 0
		int cl = 0
		for (int i = 0; i < len; ++i) {
			int c = (int) arr[i]
			if (c == 10) {
				++ln
				cl = 0
			} else ++cl
			try {
				builder.push(c)
			} catch (ex) {
				throw new LineColumnException(ex, ln, cl)
			}
		}
		builder.push(10)
		new BlockExpression(builder.expressions)
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

	static KismetObject pipeForward(Block c, KismetObject val, List<Expression> args) {
		for (exp in args) {
			if (exp instanceof CallExpression) {
				List<Expression> exprs = ((CallExpression) exp).expressions
				exprs.add(1, exp)
				val = new CallExpression(exprs).evaluate(c)
			} else if (exp instanceof BlockExpression) {
				val = pipeForward(c, val, ((BlockExpression) exp).content)
			} else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in |>')
		}
		val
	}

	static KismetObject pipeBackward(Block c, KismetObject val, List<Expression> args) {
		for (exp in args) {
			if (exp instanceof CallExpression) {
				List<Expression> exprs = ((CallExpression) exp).expressions
				exprs.add(exp)
				val = new CallExpression(exprs).evaluate(c)
			} else if (exp instanceof BlockExpression) {
				val = pipeForward(c, val, ((BlockExpression) exp).content)
			} else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in <|')
		}
		val
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
	
	static GroovyMacro macro(Closure c){
		new GroovyMacro(c)
	}

	static GroovyFunction func(Closure c){
		new GroovyFunction(false, c)
	}
	
	static GroovyFunction funcc(Closure c) {
		new GroovyFunction(true, c)
	}
}


@CompileStatic
class Block {
	Block parent
	Expression expression
	Context context

	KismetObject evaluate() { expression.evaluate(this) }
	KismetObject call() { evaluate() }
	
	Block anonymousClone(){
		Block b = new Block()
		b.parent = parent
		b.expression = expression
		b.context = new Context(b, new HashMap<>(context.getData()))
		b
	}
}

@CompileStatic
class Context {
	Block code
	Map<String, KismetObject> data = [:]

	Context(Block code = null, Map<String, KismetObject> data = [:]) {
		this.code = code
		setData data
	}

	KismetObject getProperty(String name){
		if (getData().containsKey(name)) getData()[name]
		else if (null != code?.parent)
			code.parent.context.getProperty(name)
		else throw new UndefinedVariableException(name)
	}
	
	KismetObject directSet(String name, KismetObject value){
		getData()[name] = value
	}
	
	KismetObject define(String name, KismetObject value){
		if (getData().containsKey(name))
			throw new VariableExistsException("Variable $name already exists")
		getData()[name] = value
	}
	
	KismetObject change(String name, KismetObject value){
		if (getData().containsKey(name))
			getData()[name] = value
		else if (null != code?.parent)
			code.parent.context.change(name, value)
		else throw new UndefinedVariableException(name)
	}
	
	def clone(){
		new Context(code, new HashMap<>(getData()))
	}
}

@InheritConstructors class KismetException extends Exception {}

@InheritConstructors class KismetAssertionError extends Error {}

@InheritConstructors class UndefinedVariableException extends KismetException {}

@InheritConstructors class VariableExistsException extends KismetException {}

@InheritConstructors class UnexpectedSyntaxException extends KismetException {}

@InheritConstructors class UnexpectedValueException extends KismetException {}

@CompileStatic class LineColumnException extends KismetException {
	int ln
	int cl

	LineColumnException(Throwable cause, int ln, int cl) {
		super("At line $ln column $cl", cause)
		this.ln = ln
		this.cl = cl
	}
}