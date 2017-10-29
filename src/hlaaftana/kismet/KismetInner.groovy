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
			List: new KismetClass(List, 'List').object,
			Map: new KismetClass(Map, 'Map').object,
			Expression: new KismetClass(Expression, 'Expression').object,
			Block: new KismetClass(Block, 'Block').object,
			Function: new KismetClass(Function, 'Function').object,
			Macro: new KismetClass(Macro, 'Macro').object,
			Native: new KismetClass(Object, 'Native').object,
			Regex: new KismetClass(Pattern, 'Regex').object,
			Range: new KismetClass(Range, 'Range').object,
			Pair: new KismetClass(Tuple2, 'Pair').object,
			Iterator: new KismetClass(Iterator, 'Iterator').object]

	static {
		Map<String, Object> toConvert = [
		true: true, false: false, null: null,
		yes: true, no: false, on: true, off: false,
		class: func { KismetObject... a -> a[0].kclass() },
		class_for_name: funcc { ...a -> KismetClass.instances.groupBy { it.name }[a[0].toString()] },
		instance_of: func { KismetObject... a -> a.drop(1).any { a[0].kclass() == it } },
		as: func { KismetObject... a -> a[0].as((Class) a[1].inner()) },
		is: funcc { ...args -> args.inject { a, b -> a == b } },
		'isn\'t': funcc { ...args -> args.inject { a, b -> a != b } },
		same: funcc { ...a -> a[0].is(a[1]) },
		not_same: funcc { ...a -> !a[0].is(a[1]) },
		in: funcc { ...a -> a[0] in a[1] },
		not_in: funcc { ...a -> !(a[0] in a[1]) },
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
		pos: funcc { ...a -> a[0].invokeMethod 'positive', null },
		neg: funcc { ...a -> a[0].invokeMethod 'negative', null },
		abs: funcc { ...a -> Math.invokeMethod('abs', a[0]) },
		'+': funcc { ...args -> args.sum() },
		'-': funcc { ...args -> args.inject { a, b -> a.invokeMethod 'minus', b } },
		'*': funcc { ...args -> args.inject { a, b -> a.invokeMethod 'multiply', b } },
		'/': funcc { ...args -> args.inject { a, b -> a.invokeMethod 'div', b } },
		div: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'intdiv', b } },
		mod: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'mod', b } },
		pow: funcc { ...args -> args.inject { a, b -> a.invokeMethod 'power', b } },
		sum: funcc { ...args -> args[0].invokeMethod('sum', null) },
		product: funcc { ...args -> args[0].inject { a, b -> a.invokeMethod 'multiply', b } },
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
		has_next: funcc { ...args -> args[0].invokeMethod('hasNext', null) },
		next: funcc { ...args -> args[0].invokeMethod('next', null) },
		list: funcc { ...args -> args.toList() },
		set: funcc { ...args -> args.toList().toSet() },
		pair: funcc { ...args -> new Tuple2(args[0], args[1]) },
		to_list: funcc { ...args -> args[0].invokeMethod('toList', null) },
		to_set: funcc { ...args -> args[0].invokeMethod('toSet', null) },
		map: funcc { ...args ->
			Map map = [:]
			Iterator iter = args.iterator()
			while (iter.hasNext()) {
				def a = iter.next()
				if (iter.hasNext()) map.put(a, iter.next())
			}
			map
		},
		spread: funcc { ...args -> args[0].invokeMethod('toSpreadMap', null) },
		size: funcc { ...a -> a[0].invokeMethod('size', null) },
		keys: funcc { ...a -> a[0].invokeMethod('keySet', null).invokeMethod('toList', null) },
		values: funcc { ...a -> a[0].invokeMethod('values', null) },
		reverse: funcc { ...a -> a[0].invokeMethod('reverse', null) },
		format: funcc { ...args -> String.invokeMethod('format', args) },
		'::=': macro { Block c, Expression... x ->
			c.context.directSet(resolveName(x[0], c, 'direct set'), x[1].evaluate(c))
		},
		':=': macro { Block c, Expression... x ->
			c.context.define(resolveName(x[0], c, 'define'), x[1].evaluate(c))
		},
		'=': macro { Block c, Expression... x ->
			KismetObject value
			if (x[0] instanceof StringExpression)
				c.context.change(((StringExpression) x[0]).value, value = x[1].evaluate(c))
			else if (x[0] instanceof AtomExpression)
				if (((AtomExpression) x[0]).path.parsedExpressions.size() == 1)
					c.context.change(((AtomExpression) x[0]).path.raw, value = x[1].evaluate(c))
				else {
					def a = ((AtomExpression) x[0]).path.dropLastAndLast()
					def (Path.PathExpr last, Path p) = [a.first, a.second]
					KismetObject val = Kismet.model p.apply(c.context)
					if (last instanceof Path.PropertyPathExpr)
						val.invokeMethod('putAt', [last.raw, value = x[1].evaluate(c)])
					else val.invokeMethod('putAt', [((Path.SubscriptPathExpr) last).value, value = x[1].evaluate(c)])
				}
			else throw new UnexpectedSyntaxException('Did not expect expression type for first argument of = to be '
						+ (x[0].class.simpleName - 'Expression'))
			value
		},
		fn: macro { Block c, Expression... exprs ->
			new KismetFunction(Kismet.model(newCode(c, exprs)))
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
		block: macro { Block c, Expression... exprs ->
			newCode(c, exprs)
		},
		let: macro { Block c, Expression... exprs ->
			Expression cnt = exprs[0]
			Block b = newCode(c, exprs.drop(1))
			if (cnt instanceof CallExpression) {
				CallExpression ex = (CallExpression) cnt
				Iterator<Expression> defs = ([ex.value] + ex.arguments).iterator()
				while (defs.hasNext()) {
					Expression n = defs.next()
					if (!defs.hasNext()) break
					String name
					if (n instanceof AtomExpression) name = ((AtomExpression) n).path.raw
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
		combinations: funcc { ...args -> args[0].invokeMethod('combinations', null) },
		permutations: funcc { ...args -> args[0].invokeMethod('permutations', null) },
		any: func { KismetObject... args -> args[0].inner().any(args[1].&call) },
		every: func { KismetObject... args -> args[0].inner().every(args[1].&call) },
		find: func { KismetObject... args -> args[0].inner().find(args[1].&call) },
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
		add_all: funcc { ...args -> args[0].invokeMethod('addAll', args[1]) },
		remove: funcc { ...args -> args[0].invokeMethod('remove', args[1]) },
		remove_all: funcc { ...args -> args[0].invokeMethod('removeAll', args[1]) },
		put: funcc { ...args -> args[0].invokeMethod('put', [args[1], args[2]]) },
		put_all: funcc { ...args -> args[0].invokeMethod('putAll', args[1]) },
		retain_all: funcc { ...args -> args[0].invokeMethod('retainAll', args[1]) },
		contains: funcc { ...args -> args[0].invokeMethod('contains', args[1]) },
		contains_all: funcc { ...args -> args[0].invokeMethod('containsAll', args[1]) },
		disjoint: funcc { ...args -> args[0].invokeMethod('disjoint', args[1]) },
		call: func { KismetObject... args -> args[0].call(args[1].inner() as Object[]) },
		range: funcc { ...args -> args[0]..args[1] },
		parse_independent_kismet: funcc { ...args -> Kismet.parse(args[0].toString()) },
		parse_path: funcc { ...args -> Path.parse(args[0].toString()) },
		apply_path: funcc { ...args -> ((Path) args[0]).apply(args[1]) },
		sort: funcc { ...args -> args[0].invokeMethod('sort', null) },
		sorted: funcc { ...args -> args[0].invokeMethod('sort', false) },
		sort_via: func { KismetObject... args -> args[0].inner().invokeMethod('sort', args[1].&call) },
		sorted_via: func { KismetObject... args -> args[0].inner().invokeMethod('sort', [false, args[1].&call]) },
		head: funcc { ...args -> args[0].invokeMethod('head', null) },
		tail: funcc { ...args -> args[0].invokeMethod('tail', null) },
		init: funcc { ...args -> args[0].invokeMethod('init', null) },
		last: funcc { ...args -> args[0].invokeMethod('last', null) },
		first: funcc { ...args -> args[0].invokeMethod('first', null) },
		immutable: funcc { ...args -> args[0].invokeMethod('asImmutable', null) },
		identity: funcc { ...args -> args[0] },
		flatten: funcc { ...args -> args[0].invokeMethod('flatten', null) },
		indices: funcc { ...args -> args[0].invokeMethod('getIndices', null) },
		intersect: funcc { ...args -> args[0].invokeMethod('intersect', args[1]) },
		split: funcc { ...args -> args[0].invokeMethod('split', args.drop(1)) },
		partition: func { KismetObject... args -> args[0].inner().invokeMethod('split', args[1].&call) },
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
		]
		defaultContext.reduce = defaultContext.inject
		defaultContext.length = defaultContext.size
		defaultContext.filter = defaultContext.select = defaultContext.find_all
		defaultContext.succ = defaultContext.next
		for (e in toConvert) defaultContext.put(e.key, Kismet.model(e.value))
		defaultContext = defaultContext.asImmutable()
	}

	static String resolveName(Expression n, Block c, String doing) {
		String name
		if (n instanceof AtomExpression) name = ((AtomExpression) n).path.raw
		else if (n instanceof NumberExpression) throw new UnexpectedSyntaxException("Cant $doing a number")
		else {
			KismetObject val = n.evaluate(c)
			if (val.inner() instanceof String) name = val.inner()
			else throw new UnexpectedValueException("Evaluated first expression of $doing wasnt a string")
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