package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.InheritConstructors
import hlaaftana.kismet.*

@CompileStatic abstract class Expression {
	abstract IKismetObject evaluate(Context c)

	Expression percentize(Context p) {
		new StaticExpression(this, p)
	}

	String repr() { "expr(${this.class})" }

	String toString() { repr() }
}

@CompileStatic class DumbPathExpression extends Expression {
	String text
	DumbParser.Path path

	DumbPathExpression(String t) { text = t; path = DumbParser.Path.parse(t) }

	DumbPathExpression(DumbParser.Path p) { text = p.raw; path = p }

	String repr() { text }

	void setText(String t) {
		this.@text = t
		path = DumbParser.Path.parse(t)
	}

	IKismetObject evaluate(Context c) {
		Kismet.model(path.expressions[0].raw.empty ? new PathFunction(path) : path.apply(c))
	}

	boolean equals(obj) {
		obj instanceof DumbPathExpression && ((DumbPathExpression) obj).text == text
	}

	static class PathFunction extends Function {
		DumbParser.Path path
		PathFunction(DumbParser.Path p) { path = p }

		@Override
		IKismetObject call(IKismetObject... args) {
			Kismet.model(path.apply(args[0]))
		}
	}
}

@CompileStatic class PathExpression extends Expression {
	Expression root
	List<Step> steps

	PathExpression(Expression root, List<Step> steps) {
		this.root = root
		this.steps = steps
	}

	IKismetObject evaluate(Context c) {
		if (null == root) {
			Kismet.model(new Function() {
				@Override
				IKismetObject call(IKismetObject... args) {
					applySteps(c, args[0], steps)
				}
			})
		} else {
			applySteps(c, root.evaluate(c), steps)
		}
	}

	static IKismetObject applySteps(Context c, IKismetObject object, List<Step> steps) {
		for (step in steps) object = step.apply(c, object)
		object
	}

	String repr() { root.repr() + steps.join('') }

	interface Step {
		IKismetObject apply(Context c, IKismetObject object)
	}

	static class PropertyStep implements Step {
		String name

		PropertyStep(String name) {
			this.name = name
		}

		IKismetObject apply(Context c, IKismetObject object) {
			object.propertyGet(name)
		}

		String toString() { ".$name" }
	}

	static class SubscriptStep implements Step {
		Expression expression

		SubscriptStep(Expression expression) {
			this.expression = expression
		}

		IKismetObject apply(Context c, IKismetObject object) {
			Kismet.model(object.inner().invokeMethod('getAt', expression.evaluate(c).inner()))
		}

		String toString() { ".[${expression.repr()}]" }
	}

	static class EnterStep implements Step {
		Expression expression

		EnterStep(Expression expression) {
			this.expression = expression
		}

		@Override
		IKismetObject apply(Context c, IKismetObject object) {
			def ec = new EnterContext(c)
			ec.set('it', ec.object = object)
			expression.evaluate(ec)
		}

		String toString() { ".(${expression.repr()})" }

		@InheritConstructors
		static class EnterContext extends Context {
			IKismetObject object

			IKismetObject get(String name) {
				try {
					super.get(name)
				} catch (UndefinedVariableException ignored) {
					object.propertyGet(name)
				}
			}
		}
	}
}

@CompileStatic @EqualsAndHashCode class NameExpression extends Expression {
	String text

	NameExpression(String text) { this.text = text }

	IKismetObject evaluate(Context c) {
		c.get(text)
	}

	String repr() { text }
}

@CompileStatic class BlockExpression extends Expression {
	List<Expression> content

	String repr() { '{\n' +
			content*.repr().join('\r\n').readLines().collect('  '.&concat).join('\r\n') + '\r\n}' }

	BlockExpression(List<Expression> exprs) { content = exprs }

	IKismetObject evaluate(Context c) {
		IKismetObject a = Kismet.NULL
		for (e in content) a = e.evaluate(c)
		a
	}

	boolean equals(obj) { obj instanceof BlockExpression && ((BlockExpression) obj).content == content }
}

@CompileStatic class CallExpression extends Expression {
	Expression callValue
	List<Expression> arguments = []

	CallExpression(List<Expression> expressions) {
		if (null == expressions || expressions.empty) return
		setCallValue(expressions[0])
		arguments = expressions.tail()
	}

	CallExpression(Expression... exprs) {
		if (null == exprs || exprs.length == 0) return
		callValue = exprs[0]
		arguments = exprs.tail().toList()
	}

	CallExpression() {}

	String repr() { "[${expressions*.repr().join(', ')}]" }

	IKismetObject evaluate(Context c) {
		if (null == callValue) return Kismet.NULL
		IKismetObject obj = callValue.evaluate(c)
		if (obj.inner() instanceof KismetCallable) {
			final arr = new Expression[arguments.size()]
			for (int i = 0; i < arr.length; ++i) arr[i] = arguments[i]
			((KismetCallable) obj.inner()).call(c, arr)
		} else {
			final arr = new IKismetObject[arguments.size()]
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

@CompileStatic class CallSubstituteExpression extends CallExpression {
	Expression inner

	CallSubstituteExpression(Expression inner) {
		this.inner = inner
	}

	@Override
	IKismetObject evaluate(Context c) {
		inner.evaluate(c)
	}
}

@CompileStatic trait ConstantExpression<T> {
	IKismetObject<T> value

	String repr() { "const($value)" }

	void setValue(T obj) {
		value = Kismet.model(obj)
	}

	IKismetObject<T> evaluate(Context c) {
		value
		//Kismet.model(value.inner())
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
		DumbParser.NumberBuilder b = new DumbParser.NumberBuilder()
		char[] a = x.toCharArray()
		for (int i = 0; i < a.length; ++i) b.doPush((int) a[i])
		value = b.doPush(32).value.inner()
	}

	DumbPathExpression percentize(Context p) {
		new DumbPathExpression(new DumbParser.Path(new DumbParser.Path.SubscriptPathStep(value.inner().intValue())))
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

	DumbPathExpression percentize(Context p) {
		new DumbPathExpression(new DumbParser.Path(new DumbParser.Path.PropertyPathStep(raw)))
	}

	boolean equals(obj) {
		obj instanceof StringExpression && obj.value.inner() == value.inner()
	}

	IKismetObject<String> evaluate(Context c) {
		if (null == exception) value
		else throw exception
	}
}

@CompileStatic
class StaticExpression<T extends Expression> extends Expression implements ConstantExpression<Object> {
	T expression

	String repr() { expression ? "static[${expression.repr()}]($value)" : "static($value)" }

	StaticExpression(T ex = null, IKismetObject val) {
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
	static final NoExpression INSTANCE = new NoExpression()

	private NoExpression() {}

	String repr() { "noexpr" }

	IKismetObject evaluate(Context c) {
		Kismet.NULL
	}
}