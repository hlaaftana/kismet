package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import hlaaftana.kismet.*

@CompileStatic abstract class Expression {
	abstract KismetObject evaluate(Context c)

	Expression percentize(Context p) {
		new StaticExpression(this, p)
	}

	String repr() { "expr(${this.class})" }
}

@CompileStatic class PathExpression extends Expression {
	String text
	Path path

	PathExpression(String t) { text = t; path = Path.parse(t) }
	PathExpression(Path p) { text = p.raw; path = p }

	String repr() { text }

	void setText(String t) {
		this.@text = t
		path = Path.parse(t)
	}

	KismetObject evaluate(Context c) {
		Kismet.model(path.expressions[0].raw.empty ? new PathFunction(path) : path.apply(c))
	}

	boolean equals(obj) {
		obj instanceof PathExpression && ((PathExpression) obj).text == text
	}

	static class PathFunction extends Function {
		Path path
		PathFunction(Path p) { path = p }

		@Override
		KismetObject call(KismetObject... args) {
			Kismet.model(path.apply(args[0]))
		}
	}
}

@CompileStatic class PathExpression2 extends Expression {
	Expression root
	List<Step> steps

	PathExpression2(Expression root, List<Step> steps) {
		this.root = root
		this.steps = steps
	}

	KismetObject evaluate(Context c) {
		if (null == root) {
			Kismet.model(new Function() {
				@Override
				KismetObject call(KismetObject... args) {
					KismetObject last = args[0]
					for (step in steps) last = step.apply(c, last)
					last
				}
			})
		} else {
			KismetObject last = root.evaluate(c)
			for (step in steps) last = step.apply(c, last)
			last
		}
	}

	String repr() { root.repr() + steps.join(', ') }

	interface Step {
		KismetObject apply(Context c, KismetObject object)
	}

	static class PropertyStep implements Step {
		String name

		PropertyStep(String name) {
			this.name = name
		}

		KismetObject apply(Context c, KismetObject object) {
			object.getProperty(name)
		}

		String toString() { ".$name" }
	}

	static class SubscriptStep implements Step {
		Expression expression

		SubscriptStep(Expression expression) {
			this.expression = expression
		}

		KismetObject apply(Context c, KismetObject object) {
			Kismet.model(object.inner().invokeMethod('getAt', expression.evaluate(c).inner()))
		}

		String toString() { ".subscript[${expression.repr()}]" }
	}
}

@CompileStatic @EqualsAndHashCode class NameExpression extends Expression {
	String text

	NameExpression(String text) { this.text = text }

	KismetObject evaluate(Context c) {
		c.get(text)
	}

	String repr() { text }
}

@CompileStatic class BlockExpression extends Expression {
	List<Expression> content

	String repr() { 'block{\n' +
			content*.repr().join('\r\n').readLines().collect('  '.&concat).join('\r\n') + '\r\n}' }

	BlockExpression(List<Expression> exprs) { content = exprs }

	KismetObject evaluate(Context c) {
		KismetObject a = Kismet.NULL
		for (e in content) a = e.evaluate(c)
		a
	}

	boolean equals(obj) { obj instanceof BlockExpression && obj.content == content }
}

@CompileStatic class CallExpression extends Expression {
	Expression callValue
	List<Expression> arguments

	CallExpression(List<Expression> expressions) {
		setCallValue(expressions[0])
		arguments = expressions.drop(1)
	}

	String repr() { "[${callValue.repr()}](${arguments*.repr().join(', ')})" }

	KismetObject evaluate(Context c) {
		KismetObject obj = callValue.evaluate(c)
		if (obj.inner() instanceof KismetCallable) {
			final arr = new Expression[arguments.size()]
			for (int i = 0; i < arr.length; ++i) arr[i] = arguments[i]
			((KismetCallable) obj.inner()).call(c, arr)
		} else {
			final arr = new KismetObject[arguments.size()]
			for (int i = 0; i < arr.length; ++i) arr[i] = arguments[i].evaluate(c)
			obj.call(arr)
		}
	}

	List<Expression> getExpressions() {
		def r = new ArrayList<Expression>(1 + arguments.size())
		if (callValue != null) r.add(callValue)
		r.addAll(arguments)
		r
	}

	boolean equals(obj) {
		obj instanceof CallExpression && obj.expressions == expressions
	}
}

@CompileStatic trait ConstantExpression<T> {
	KismetObject<T> value

	String repr() { "const($value)" }

	void setValue(T obj) {
		value = Kismet.model(obj)
	}

	KismetObject<T> evaluate(Context c) {
		value
	}
}

@CompileStatic class NumberExpression extends Expression implements ConstantExpression<Number> {
	String repr() { value.toString() }

	NumberExpression(boolean type, StringBuilder[] arr) {
		StringBuilder x = arr[0]
		boolean t = false
		if (null != arr[1]) { x.append((char) '.').append(arr[1]); t = true }
		if (null != arr[2]) { x.append((char) 'e').append(arr[2]); t = true }
		String r = x.toString()
		if (null == arr[3]) setValue(t ? new BigDecimal(r) : new BigInteger(r)) else {
			if (type) {
				if (arr[3].length() == 0) setValue(new BigDecimal(r))
				else {
					int b = new Integer(arr[3].toString())
					if (b == 1) setValue(-new BigDecimal(r))
					else if (b == 32) setValue(new Float(r))
					else if (b == 33) setValue(-new Float(r))
					else if (b == 64) setValue(new Double(r))
					else if (b == 65) setValue(-new Double(r))
					else throw new NumberFormatException("Invalid number of bits $b for explicit float")
				}
			} else {
				if (t) {
					def v = new BigDecimal(r)
					if (arr[3].length() == 0) setValue(v.toBigInteger())
					else {
						int b = new Integer(arr[3].toString())
						if (b == 1) setValue(-v.toBigInteger())
						else if (b == 8) setValue(v.byteValue())
						else if (b == 9) setValue(-v.byteValue())
						else if (b == 16) setValue(v.shortValue())
						else if (b == 17) setValue(-v.shortValue())
						else if (b == 32) setValue(v.intValue())
						else if (b == 33) setValue(-v.intValue())
						else if (b == 64) setValue(v.longValue())
						else if (b == 65) setValue(-v.longValue())
						else throw new NumberFormatException("Invalid number of bits $b for explicit integer")
					}
				}
				else if (arr[3].length() == 0) setValue(new BigInteger(r))
				else {
					int b = new Integer(arr[3].toString())
					if (b == 1) setValue(-new BigInteger(r))
					else if (b == 8) setValue(new Byte(r))
					else if (b == 9) setValue(-new Byte(r))
					else if (b == 16) setValue(new Short(r))
					else if (b == 17) setValue(-new Short(r))
					else if (b == 32) setValue(new Integer(r))
					else if (b == 33) setValue(-new Integer(r))
					else if (b == 64) setValue(new Long(r))
					else if (b == 65) setValue(-new Long(r))
					else throw new NumberFormatException("Invalid number of bits $b for explicit integer")
				}
			}
		}
	}

	NumberExpression(Number v) { setValue(v) }

	NumberExpression(String x) {
		Parser.NumberBuilder b = new Parser.NumberBuilder()
		char[] a = x.toCharArray()
		for (int i = 0; i < a.length; ++i) b.doPush((int) a[i])
		value = b.doPush(32).value.inner()
	}

	PathExpression percentize(Context p) {
		new PathExpression(new Path(new Path.SubscriptPathStep(value.inner().intValue())))
	}

	boolean equals(obj) {
		obj instanceof NumberExpression && obj.value.inner() == value.inner()
	}
}

@CompileStatic
class StringExpression extends Expression implements ConstantExpression<String> {
	String raw
	Exception exception

	String toString() { "\"${StringEscaper.escapeSoda(raw)}\"" }

	StringExpression(String v) {
		try {
			setValue(StringEscaper.unescapeSoda(raw = v))
		} catch (ex) { exception = ex }
	}

	PathExpression percentize(Context p) {
		new PathExpression(new Path(new Path.PropertyPathStep(raw)))
	}

	boolean equals(obj) {
		obj instanceof StringExpression && obj.value.inner() == value.inner()
	}

	KismetObject<String> evaluate(Context c) {
		if (null == exception) value
		else throw exception
	}
}

@CompileStatic
class StaticExpression<T extends Expression> extends Expression implements ConstantExpression<Object> {
	T expression

	String expr() { expression ? "static[${expression.repr()}]($value)" : "static($value)" }

	StaticExpression(T ex = null, KismetObject val) {
		expression = ex
		value = val
	}

	StaticExpression(T ex = null, val) {
		expression = ex
		setValue(val)
	}

	StaticExpression(T ex = null, Context c) {
		this(ex, ex.evaluate(c))
	}
}

@CompileStatic class NoExpression extends Expression {
	String repr() { "noexpr" }

	KismetObject evaluate(Context c) {
		Kismet.NULL
	}
}