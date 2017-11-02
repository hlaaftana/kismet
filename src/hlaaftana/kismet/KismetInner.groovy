package hlaaftana.kismet

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.parser.*

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
	Boolean: new KismetClass(boolean, 'Boolean').object,
	Int8: new KismetClass(byte, 'Int8').object,
	Int16: new KismetClass(short, 'Int16').object,
	Int32: new KismetClass(int, 'Int32').object,
	Int64: new KismetClass(long, 'Int64').object,
	Float32: new KismetClass(float, 'Float32').object,
	Float64: new KismetClass(double, 'Float64').object,
	Character: new KismetClass(char, 'Character').object,
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
	RNG: new KismetClass(Random, 'RNG').object]

	static {
		// TODO: add date crap
		Map<String, Object> toConvert = [
		true: true, false: false, null: null,
		yes: true, no: false, on: true, off: false,
		class: func { KismetObject... a -> a[0].kclass },
		class_for_name: funcc { ...a -> KismetClass.instances.groupBy { it.name }[a[0].toString()] },
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
		get_variable: macro { Block c, Expression... exprs -> c.context.getProperty(exprs[0].evaluate(c).toString()) },
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
		'zero?': funcc { ...args -> args[0] == 0 },
		'one?': funcc { ...args -> args[0] == 1 },
		'even?': funcc { ...args -> args[0].invokeMethod('mod', 2) == 0 },
		'odd?': funcc { ...args -> args[0].invokeMethod('mod', 2) != 0 },
		'divisible_by?': funcc { ...args -> args[0].invokeMethod('mod', args[1]) == 0 },
		'integer?': funcc { ...args -> args[0].invokeMethod('mod', 1) == 0 },
		absolute: funcc { ...a -> Math.invokeMethod('abs', a[0]) },
		'+': funcc { ...args -> args.sum() },
		'-': funcc { ...args -> args.inject { a, b -> a.invokeMethod 'minus', b } },
		'*': funcc { ...args -> args.inject { a, b -> a.invokeMethod 'multiply', b } },
		'/': funcc { ...args -> args.inject { a, b -> a.invokeMethod 'div', b } },
		div: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'intdiv', b } },
		mod: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'mod', b } },
		pow: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'power', b } },
		sum: funcc { ...args -> args[0].invokeMethod('sum', null) },
		product: funcc { ...args -> args[0].inject { a, b -> a.invokeMethod 'multiply', b } },
		new_rng: funcc { ...args -> args.length > 0 ? new Random(args[0] as long) : new Random() },
		random_integer: funcc { ...args ->
			Random rand = args[0] as Random
			BigInteger max = args[1] as BigInteger
			BigInteger min = args.length > 2 ? args[3] as BigInteger : 0
			byte[] bytes = (max - min).toByteArray()
			rand.nextBytes(bytes)
			new BigInteger(bytes) + min
		},
		// TODO: fix lazy crap here
		random_int8_list_from_reference: funcc { ...args ->
			byte[] bytes = args[1] as byte[]
			(args[0] as Random).nextBytes(bytes)
			bytes as List<Byte>
		},
		random_int32_between_low_and_high: funcc { ...args -> (args[0] as Random).nextInt() },
		random_int64_between_low_and_high: funcc { ...args -> (args[0] as Random).nextLong() },
		random_float32_between_0_and_1: funcc { ...args -> (args[0] as Random).nextFloat() },
		random_float64_between_0_and_1: funcc { ...args -> (args[0] as Random).nextDouble() },
		random_bool: funcc { ...args -> (args[0] as Random).nextBoolean() },
		next_gaussian: funcc { ...args -> (args[0] as Random).nextGaussian() },
		replace: funcc { ...args -> args[0].toString().replace(args[1].toString(),
				args.length > 2 ? args[2].toString() : '') },
		replace_all: funcc { ...args ->
			def replacement = args.length > 2 ? args[2].toString() : ''
			args[1] instanceof Pattern ?
				args[0].toString().replaceAll(((Pattern) args[1]), replacement) :
				args[0].toString().replaceAll(args[1].toString(), replacement) },
		replace_first: funcc { ...args ->
			def replacement = args.length > 2 ? args[2].toString() : ''
			args[1] instanceof Pattern ?
					args[0].toString().replaceFirst(((Pattern) args[1]), replacement) :
					args[0].toString().replaceFirst(args[1].toString(), replacement) },
		quote_regex: funcc { ...args -> Pattern.quote(args[0].toString()) },
		codepoint_iterator: funcc { ...args -> args[0].toString().codePoints().iterator() },
		char_iterator: funcc { ...args -> args[0].toString().chars().iterator() },
		chars: funcc { ...args -> args[0].toString().chars.toList() },
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
		string: func { KismetObject... a -> a[0].as String },
		int: func { KismetObject... a -> a[0].as BigInteger },
		int8: func { KismetObject... a -> a[0].as byte },
		int16: func { KismetObject... a -> a[0].as short },
		int32: func { KismetObject... a -> a[0].as int },
		int64: func { KismetObject... a -> a[0].as long },
		char: func { KismetObject... a -> a[0].as char },
		float: func { KismetObject... a -> a[0].as BigDecimal },
		float32: func { KismetObject... a -> a[0].as float },
		float64: func { KismetObject... a -> a[0].as double },
		iterator: funcc { ...args -> args[0].iterator() },
		list_iterator: funcc { ...args -> args[0].invokeMethod('listIterator', null) },
		has_next: funcc { ...args -> args[0].invokeMethod('hasNext', null) },
		next: funcc { ...args -> args[0].invokeMethod('next', null) },
		has_prev: funcc { ...args -> args[0].invokeMethod('hasPrevious', null) },
		prev: funcc { ...args -> args[0].invokeMethod('previous', null) },
		new_list: funcc { ...args -> new ArrayList(args[0] as int) },
		list: funcc { ...args -> args.toList() },
		new_set: funcc { ...args -> args.length > 1 ? new HashSet(args[0] as int, args[1] as float) : new HashSet(args[0] as int) },
		set: funcc { ...args -> args.toList().toSet() },
		pair: funcc { ...args -> new Tuple2(args[0], args[1]) },
		to_list: funcc { ...args -> args[0].invokeMethod('toList', null) },
		to_set: funcc { ...args -> args[0].invokeMethod('toSet', null) },
		to_pair: funcc { ...args -> new Tuple2(args[0].invokeMethod('getAt', 0), args[0].invokeMethod('getAt', 1)) },
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
		knit: func { KismetObject... args -> (args[0].inner() as List).transpose()
				.collect { args[1].invokeMethod('call', it as Object[]) } },
		transpose: funcc { ...args -> (args[0] as List).transpose() },
		'unique?': funcc { ...args -> args[0].invokeMethod('size', null) ==
					args[0].invokeMethod('unique', false).invokeMethod('size', null) },
		'unique!': funcc { ...args -> args[0].invokeMethod('unique', null) },
		unique: funcc { ...args -> args[0].invokeMethod('unique', false) },
		'unique_via?': func { KismetObject... args -> args[0].inner().invokeMethod('size', null) ==
			args[0].inner().invokeMethod('unique', [false, args[1].&call]) },
		'unique_via!': func { KismetObject... args -> args[0].inner().invokeMethod('unique', args[1].&call) },
		unique_via: func { KismetObject... args -> args[0].inner().invokeMethod('unique', [false, args[1].&call]) },
		spread: funcc { ...args -> args[0].invokeMethod('toSpreadMap', null) },
		size: funcc { ...a -> a[0].invokeMethod('size', null) },
		keys: funcc { ...a -> a[0].invokeMethod('keySet', null).invokeMethod('toList', null) },
		values: funcc { ...a -> a[0].invokeMethod('values', null) },
		reverse: funcc { ...a -> a[0].invokeMethod('reverse', null) },
		sprintf: funcc { ...args -> String.invokeMethod('format', args) },
		expr_type: funcc { ...args -> args[0] instanceof Expression ?
			(args[0].class.simpleName - 'Expression').uncapitalize() : null },
		capitalize: funcc { ...args -> args[0].toString().capitalize() },
		uncapitalize: funcc { ...args -> args[0].toString().uncapitalize() },
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
			else Collections.invokeMethod('indexOfSubList', [args[1] as List, args[0] as List]) == 0
		},
		'suffix?': funcc { ...args ->
			if (args[1] instanceof String) ((String) args[1]).endsWith(args[0].toString())
			else {
				def a = args[0] as List
				def b = args[1] as List
				Collections.invokeMethod('lastIndexOfSubList', [b, a]) == b.size() - a.size()
			}
		},
		'infix?': funcc { ...args ->
			if (args[1] instanceof String) ((String) args[1]).contains(args[0].toString())
			else Collections.invokeMethod('indexOfSubList', [args[1] as List, args[0] as List]) != -1
		},
		'subsequence?': funcc { ...args -> args[1].invokeMethod('containsAll', args[0]) },
		'rotate!': funcc { ...args ->
			List x = (List) args[0]
			Collections.rotate(x, args[1] as int)
			x
		},
		rotate: funcc { ...args ->
			List x = new ArrayList(args[0] as List)
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
			else if (x.inner() instanceof Expression) ((Expression) x.inner()).evaluate(c)
			else if (x.inner() instanceof Path) ((Path) x.inner()).apply(c.context)
			else if (x.inner() instanceof String) newCode(c, parse((String) x.inner())).evaluate()
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
			KismetObject x = Kismet.model null
			while (a.hasNext()) {
				x = a.next().evaluate(c)
				if (a.hasNext() && x) return a.next().evaluate(c)
			}
			x
		},
		unless_chain: macro { Block c, Expression... ab ->
			Iterator<Expression> a = ab.iterator()
			KismetObject x = Kismet.model null
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
			while (exprs[0].evaluate(c)) j = b.evaluate()
			j
		},
		until: macro { Block c, Expression... exprs ->
			Block b = newCode(c, exprs.drop(1))
			KismetObject j = Kismet.model(null)
			while (!exprs[0].evaluate(c)) j = b.evaluate()
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
		clear: funcc { ...args -> args[0].invokeMethod('clear', null) },
		put: funcc { ...args -> args[0].invokeMethod('put', [args[1], args[2]]) },
		put_all: funcc { ...args -> args[0].invokeMethod('putAll', args[1]) },
		retain_all: funcc { ...args -> args[0].invokeMethod('retainAll', args[1]) },
		retain_any: func { KismetObject... args -> args[0].inner().invokeMethod('retainAll', args[1].&call) },
		'has?': funcc { ...args -> args[0].invokeMethod('contains', args[1]) },
		'has_all?': funcc { ...args -> args[0].invokeMethod('containsAll', args[1]) },
		'has_key?': funcc { ...args -> args[0].invokeMethod('containsKey', args[1]) },
		'has_value?': funcc { ...args -> args[0].invokeMethod('containsValue', args[1]) },
		'is_code_kismet?': funcc { ...args -> args[0] instanceof KismetFunction || args[0] instanceof KismetMacro },
		func_to_block: funcc { ...args -> args[0] instanceof KismetFunction ? ((KismetFunction) args[0]).b :
										  args[0] instanceof KismetMacro ? ((KismetMacro) args[0]).b :
										  false },
		'disjoint?': funcc { ...args -> args[0].invokeMethod('disjoint', args[1]) },
		'intersect?': funcc { ...args -> !args[0].invokeMethod('disjoint', args[1]) },
		call: func { KismetObject... args -> args[0].call(args[1].inner() as Object[]) },
		range: funcc { ...args -> args[0]..args[1] },
		parse_independent_kismet: funcc { ...args -> Kismet.parse(args[0].toString()) },
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
		drop: funcc { ...args -> args[0].invokeMethod('drop', args[1] as int) },
		drop_right: funcc { ...args -> args[0].invokeMethod('dropRight', args[1] as int) },
		drop_while: func { KismetObject... args -> args[0].inner().invokeMethod('dropWhile', args[1].&call) },
		take: funcc { ...args -> args[0].invokeMethod('take', args[1] as int) },
		take_right: funcc { ...args -> args[0].invokeMethod('takeRight', args[1] as int) },
		take_while: func { KismetObject... args -> args[0].inner().invokeMethod('takeWhile', args[1].&call) },
		each_combination: func { KismetObject... args -> args[0].inner().invokeMethod('eachCombination', args[1].&call) },
		each_permutation: func { KismetObject... args -> args[0].inner().invokeMethod('eachPermutation', args[1].&call) },
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
		}]
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