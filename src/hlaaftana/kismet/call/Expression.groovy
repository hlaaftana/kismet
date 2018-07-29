package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.exceptions.KismetEvaluationException
import hlaaftana.kismet.exceptions.UndefinedVariableException
import hlaaftana.kismet.exceptions.UnexpectedTypeException
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.parser.StringEscaper
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.StringType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.RuntimeMemory

@CompileStatic
abstract class Expression {
	int ln, cl
	abstract IKismetObject evaluate(Context c)

	TypedExpression type(TypedContext tc, Type preferred) {
		throw new UnsupportedOperationException('Cannot turn ' + this + ' to typed')
	}

	TypedExpression type(TypedContext tc) { type(tc, Type.ANY) }

	Collection<Expression> getMembers() { [] }

	Expression getAt(int i) { members[i] }

	Expression join(Collection<Expression> exprs) {
		throw new UnsupportedOperationException("Cannot join exprs $exprs on class ${this.class}")
	}

	Expression percentize(Parser p) {
		new StaticExpression(this, p.context)
	}

	String repr() { "expr(${this.class})" }

	String toString() { repr() }
}

@CompileStatic
class PathExpression extends Expression {
	Expression root
	List<Step> steps

	PathExpression(Expression root, List<Step> steps) {
		this.root = root
		this.steps = steps
	}

	IKismetObject evaluate(Context c) {
		if (null == root || root instanceof NoExpression) {
			Kismet.model(new PathFunction(c, steps))
		} else {
			applySteps(c, root.evaluate(c), steps)
		}
	}

	static class PathFunction extends Function {
		Context context
		List<Step> steps

		PathFunction(Context context, List<Step> steps) {
			this.context = context
			this.steps = steps
		}

		IKismetObject call(IKismetObject... args) {
			applySteps(context, args[0], steps)
		}
	}

	static IKismetObject applySteps(Context c, IKismetObject object, List<Step> steps) {
		for (step in steps) object = step.apply(c, object)
		object
	}

	String repr() { root.repr() + steps.join('') }

	Collection<Expression> getMembers() {
		def result = new ArrayList<Expression>(steps.size() + 1)
		result.add(root)
		for (def s: steps) result.add(s.asExpr())
		result
	}

	PathExpression join(Collection<Expression> m) {
		def result = new ArrayList<Step>(m.size() - 1)
		final s = steps
		assert s.size() == m.size(), "Members must be same size as joined expressions"
		for (int i = 1; i < m.size(); ++i) {
			result.add(s[i - 1].borrow(m[i]))
		}
		new PathExpression(m[0], result)
	}

	interface Step {
		IKismetObject apply(Context c, IKismetObject object)
		Expression asExpr()
		Step borrow(Expression expr)
		Instruction instr(TypedContext ctx, Instruction before)
	}

	static class PropertyStep implements Step {
		String name

		PropertyStep(String name) {
			this.name = name
		}

		IKismetObject apply(Context c, IKismetObject object) {
			object.kismetClass().propertyGet(object, name)
		}

		String toString() { ".$name" }
		Expression asExpr() { new NameExpression(name) }
		PropertyStep borrow(Expression expr) { new PropertyStep(expr.toString()) }

		Instruction instr(TypedContext ctx, Instruction before) {
			new Instruction() {
				IKismetObject evaluate(RuntimeMemory context) {
					final val = before.evaluate(context)
					val.kismetClass().propertyGet(val, name)
				}
			}
		}
	}

	static class SubscriptStep implements Step {
		Expression expression

		SubscriptStep(Expression expression) {
			this.expression = expression
		}

		IKismetObject apply(Context c, IKismetObject object) {
			Kismet.model(object.kismetClass().subscriptGet(object, expression.evaluate(c)))
		}

		String toString() { ".[$expression]" }
		Expression asExpr() { expression }
		SubscriptStep borrow(Expression expr) { new SubscriptStep(expr) }

		Instruction instr(TypedContext ctx, Instruction before) {
			new Instruction() {
				IKismetObject evaluate(RuntimeMemory context) {
					final val = before.evaluate(context)
					val.kismetClass().subscriptGet(val, expression.type(ctx).instruction.evaluate(context))
				}
			}
		}
	}

	static class EnterStep implements Step {
		Expression expression

		EnterStep(Expression expression) {
			this.expression = expression
		}

		IKismetObject apply(Context c, IKismetObject object) {
			def ec = new EnterContext(c)
			ec.set('it', ec.object = object)
			expression.evaluate(ec)
		}

		String toString() { ".($expression)" }

		@InheritConstructors
		static class EnterContext extends Context {
			IKismetObject object

			IKismetObject get(String name) {
				try {
					super.get(name)
				} catch (UndefinedVariableException ignored) {
					object.kismetClass().propertyGet(object, name)
				}
			}
		}
		Expression asExpr() { expression }
		EnterStep borrow(Expression expr) { new EnterStep(expr) }

		Instruction instr(TypedContext ctx, Instruction before) {
			throw new UnsupportedOperationException('unsupported')
		}
	}

	// replace with symbol calls later
	TypedExpression type(TypedContext tc, Type preferred) {
		Instruction result = root.type(tc).instruction
		for (s in steps) result = s.instr(tc, result)
		new BasicTypedExpression(preferred, result)
	}
}

@CompileStatic
class NameExpression extends Expression {
	String text

	NameExpression(String text) { this.text = text }

	IKismetObject evaluate(Context c) {
		c.get(text)
	}

	String repr() { text }

	VariableExpression type(TypedContext tc, Type preferred) {
		def failed = new ArrayList<String>()
		def vr = tc.getAny(text, preferred, failed)
		if (null != vr) return new VariableExpression(vr)
		def msgbase = "Could not find variable \"$text\" for type $preferred"
		if (!failed.empty) {
			def msg = new String[failed.size() + 1]
			msg[0] = msgbase.concat(", but for these types instead:")
			for (int i = 1; i < msg.length; ++i) msg[i--] = failed.get(i++)
			msgbase = String.join('\n  ', msg)
		}
		throw new UndefinedVariableException(msgbase)
	}
}

@CompileStatic
class DiveExpression extends Expression {
	Expression inner

	DiveExpression(Expression inner) {
		this.inner = inner
	}

	String repr() { "dive[$inner]" }

	IKismetObject evaluate(Context c) {
		c = c.child()
		inner.evaluate(c)
	}

	Collection<Expression> getMembers() { [inner] }
	DiveExpression join(Collection<Expression> a) { new DiveExpression(a[0]) }

	TypedDiveExpression type(TypedContext tc, Type preferred) {
		final child = tc.child()
		new TypedDiveExpression(child, inner.type(child, preferred))
	}
}

@CompileStatic
class BlockExpression extends Expression {
	List<Expression> members

	String repr() {
		'{\n' + members.join('\r\n').readLines().collect('  '.&concat).join('\r\n') + '\r\n}'
	}

	BlockExpression(List<Expression> exprs) { members = exprs }

	IKismetObject evaluate(Context c) {
		IKismetObject a = Kismet.NULL
		for (e in members) a = e.evaluate(c)
		a
	}

	BlockExpression join(List<Expression> exprs) {
		new BlockExpression(exprs)
	}

	SequentialExpression type(TypedContext tc, Type preferred) {
		def arr = new TypedExpression[members.size()]
		int i = 0
		for (; i < arr.length - 1; ++i) arr[i] = members.get(i).type(tc)
		arr[i] = members.get(i).type(tc, preferred)
		new SequentialExpression(arr)
	}
}

@CompileStatic
class CallExpression extends Expression {
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

	String repr() { "[${members.join(', ')}]" }

	CallExpression plus(List<Expression> mem) {
		new CallExpression(members + mem)
	}

	CallExpression plus(CallExpression mem) {
		new CallExpression(members + mem.members)
	}

	Expression getAt(int i) {
		i < 0 ? this[arguments.size() + i + 1] : i == 0 ? callValue : arguments[i - 1]
	}

	IKismetObject evaluate(Context c) {
		if (null == callValue) return Kismet.NULL
		IKismetObject obj = callValue.evaluate(c)
		if (obj.inner() instanceof KismetCallable) {
			((KismetCallable) obj.inner()).call(c, arguments.toArray(new Expression[0]))
		} else {
			final arr = new IKismetObject[arguments.size()]
			for (int i = 0; i < arr.length; ++i) arr[i] = arguments[i].evaluate(c)
			obj.kismetClass().call(obj, arr)
		}
	}

	List<Expression> getMembers() {
		def r = new ArrayList<Expression>(1 + arguments.size())
		if (callValue != null) r.add(callValue)
		r.addAll(arguments)
		r
	}

	CallExpression join(List<Expression> exprs) {
		new CallExpression(exprs)
	}
	
	TypedCallExpression type(TypedContext tc, Type preferred) {
		def args = new TypedExpression[arguments.size()]
		for (int i = 0; i < args.length; ++i) args[i] = arguments.get(i).type(tc)
		TypedContext.DeclarationReference m
		if (callValue instanceof NameExpression && null != (m =
				tc.findCall(((NameExpression) callValue).text, args, preferred))) {
			new DeclarationCallExpression(m, args)
		} else {
			new ValueCallExpression(callValue.type(tc), args)
		}
	}
}

@CompileStatic
class ConstantExpression<T> extends Expression {
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

@CompileStatic
class NumberExpression extends ConstantExpression<Number> {
	String repr() { value.toString() }

	NumberExpression(boolean type, StringBuilder[] arr) {
		StringBuilder x = arr[0]
		boolean t = false
		if (null != arr[1]) {
			x.append((char) '.').append(arr[1]); t = true
		}
		if (null != arr[2]) {
			x.append((char) 'e').append(arr[2]); t = true
		}
		String r = x.toString()
		if (null == arr[3]) setValue(t ? new BigDecimal(r) : new BigInteger(r)) else {
			if (type) {
				if (arr[3].length() == 0) setValue(new BigDecimal(r))
				else {
					int b = Integer.valueOf(arr[3].toString())
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
						int b = Integer.valueOf(arr[3].toString())
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
				} else if (arr[3].length() == 0) setValue(new BigInteger(r))
				else {
					int b = Integer.valueOf(arr[3].toString())
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
		Parser.NumberBuilder b = new Parser.NumberBuilder(null)
		char[] a = x.toCharArray()
		for (int i = 0; i < a.length; ++i) b.doPush((int) a[i])
		setValue b.doFinish().value.inner()
	}

	TypedNumberExpression type(TypedContext tc, Type preferred) {
		def result = new TypedNumberExpression(value.inner())
		def rel = result.type.relation(preferred)
		if (rel.none)
			throw new UnexpectedTypeException("Preferred non-number type $preferred for literal with number $result.number")
		else if (rel.super)
			result.number = ((NumberType) preferred).instantiate(result.number).inner()
		result
	}

	VariableIndexExpression percentize(Parser p) {
		new VariableIndexExpression(value.inner().intValue())
	}

	static class VariableIndexExpression extends Expression {
		int index

		VariableIndexExpression(int index) {
			this.index = index
		}

		@Override
		IKismetObject evaluate(Context c) {
			final x = c.@variables[index]
			if (x) x.value
			else throw new KismetEvaluationException(this, "No variable at index $index")
		}

		Typed type(TypedContext tc, Type preferred) {
			new Typed(preferred, index)
		}

		static class Typed extends BasicTypedExpression {
			int index

			Typed(Type type, int index) {
				super(type, new Inst(index))
				this.index = index
			}

			static class Inst extends Instruction {
				int index

				Inst(int index) {
					this.index = index
				}

				IKismetObject evaluate(RuntimeMemory context) {
					context.memory[index]
				}
			}
		}
	}
}

@CompileStatic
class StringExpression extends ConstantExpression<String> {
	String raw
	Exception exception

	String toString() { "\"${StringEscaper.escape(raw)}\"" }

	StringExpression(String v) {
		try {
			setValue(StringEscaper.unescape(raw = v))
		} catch (ex) {
			exception = ex
		}
	}

	NameExpression percentize(Parser p) {
		new NameExpression(raw)
	}

	IKismetObject<String> evaluate(Context c) {
		if (null == exception) value
		else throw exception
	}

	TypedStringExpression type(TypedContext tc, Type preferred) {
		final str = evaluate(null).inner()
		if (!preferred.relation(StringType.INSTANCE).assignableFrom)
			throw new UnexpectedTypeException("Preferred non-string type $preferred for literal with string \"${StringEscaper.escape(str)}\"")
		new TypedStringExpression(str)
	}
}

@CompileStatic
class StaticExpression<T extends Expression> extends ConstantExpression<Object> {
	T expression

	String repr() { expression ? "static[$expression]($value)" : "static($value)" }

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

	TypedExpression type(TypedContext tc, Type preferred) {
		new BasicTypedExpression(preferred, new IdentityInstruction(value))
	}
}

@CompileStatic
class NoExpression extends Expression {
	static final NoExpression INSTANCE = new NoExpression()

	private NoExpression() {}

	String repr() { "noexpr" }

	IKismetObject evaluate(Context c) {
		Kismet.NULL
	}

	TypedNoExpression type(TypedContext tc, Type preferred) {
		TypedNoExpression.INSTANCE
	}
}