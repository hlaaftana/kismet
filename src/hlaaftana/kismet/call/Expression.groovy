package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.exceptions.KismetEvaluationException
import hlaaftana.kismet.exceptions.UndefinedVariableException
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.exceptions.UnexpectedTypeException
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.parser.StringEscaper
import hlaaftana.kismet.scope.AssignmentType
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.Prelude
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.FunctionType
import hlaaftana.kismet.type.ListType
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.StringType
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory

@CompileStatic
abstract class Expression {
	int ln, cl
	abstract IKismetObject evaluate(Context c)

	TypedExpression type(TypedContext tc, Type preferred) {
		throw new UnsupportedOperationException('Cannot turn ' + this + ' to typed')
	}

	TypedExpression type(TypedContext tc) { type(tc, Type.ANY) }

	List<Expression> getMembers() { [] }

	int size() { members.size() }

	Expression getAt(int i) { members[i] }

	Expression join(List<Expression> exprs) {
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
		if (steps.empty) throw new UnexpectedSyntaxException("Path without steps? Root is $root")
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
		for (step in steps) object = step.get(c, object)
		object
	}

	String repr() { root.repr() + steps.join('') }

	List<Expression> getMembers() {
		def result = new ArrayList<Expression>(steps.size() + 1)
		result.add(root)
		for (def s: steps) result.add(s.asExpr())
		result
	}

	Expression join(List<Expression> m) {
		if (m.size() == 1) return m.get(0)
		def result = new ArrayList<Step>(m.size() - 1)
		final s = steps
		assert s.size() == m.size(), "Members must be same size as joined expressions"
		for (int i = 1; i < m.size(); ++i) {
			result.add(s.get(i - 1).borrow(m.get(i)))
		}
		new PathExpression(m[0], result)
	}

	interface Step {
		IKismetObject get(Context c, IKismetObject object)
		IKismetObject set(Context c, IKismetObject object, IKismetObject value)
		Expression asExpr()
		Step borrow(Expression expr)
		Instruction instr(TypedContext ctx, Instruction before)
	}

	static class PropertyStep implements Step {
		String name

		PropertyStep(String name) {
			this.name = name
		}

		IKismetObject get(Context c, IKismetObject object) {
			object.kismetClass().propertyGet(object, name)
		}

		IKismetObject set(Context c, IKismetObject object, IKismetObject value) {
			object.kismetClass().propertySet(object, name, value)
		}

		String toString() { ".$name" }
		Expression asExpr() { new NameExpression(name) }
		PropertyStep borrow(Expression expr) { new PropertyStep(expr.toString()) }

		Instruction instr(TypedContext ctx, Instruction before) {
			new Instruction() {
				IKismetObject evaluate(Memory context) {
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

		IKismetObject get(Context c, IKismetObject object) {
			object.kismetClass().subscriptGet(object, expression.evaluate(c))
		}

		IKismetObject set(Context c, IKismetObject object, IKismetObject value) {
			object.kismetClass().subscriptSet(object, expression.evaluate(c), value)
		}

		String toString() { ".[$expression]" }
		Expression asExpr() { expression }
		SubscriptStep borrow(Expression expr) { new SubscriptStep(expr) }

		Instruction instr(TypedContext ctx, Instruction before) {
			new Instruction() {
				IKismetObject evaluate(Memory context) {
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

		IKismetObject get(Context c, IKismetObject object) {
			def ec = new EnterContext(c)
			ec.set('it', ec.object = object)
			expression.evaluate(ec)
		}

		IKismetObject set(Context c, IKismetObject object, IKismetObject value) {
			throw new UnsupportedOperationException('unsupported')
		}

		String toString() { ".{$expression}" }

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

	int size() { 1 + steps.size() }

	// replace with symbol calls later
	TypedExpression type(TypedContext tc, Type preferred) {
		Instruction result = root.type(tc).instruction
		for (s in steps) result = s.instr(tc, result)
		new BasicTypedExpression(preferred, result)
	}
}

@CompileStatic
class OnceExpression extends Expression {
	Expression inner
	IKismetObject value

	OnceExpression(Expression inner) {
		this.inner = inner
	}

	IKismetObject evaluate(Context c) {
		if (null == value) value = inner.evaluate(c)
		value
	}

	TypedExpression type(TypedContext tc, Type preferred) {
		return super.type(tc, preferred)
	}

	List<Expression> getMembers() { [inner] }
	int size() { 1 }
	Expression getAt(int i) { i == 0 ? inner : null }

	Expression join(List<Expression> exprs) {
		new OnceExpression(exprs.get(0))
	}

	String repr() { "once($inner)" }
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
		def vr = tc.findAny(text, preferred, failed)
		if (null != vr) return new VariableExpression(vr)
		def msgbase = new StringBuilder("Could not find variable \"").append(text)
			.append("\" for type ").append(preferred.toString()).toString()
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

	List<Expression> getMembers() { [inner] }
	int size() { 1 }
	DiveExpression join(Collection<Expression> a) { new DiveExpression(a[0]) }

	TypedDiveExpression type(TypedContext tc, Type preferred) {
		final child = tc.child()
		new TypedDiveExpression(child, inner.type(child, preferred))
	}
}

@CompileStatic
class VariableModifyExpression extends Expression {
	String name
	Expression expression
	AssignmentType type

	VariableModifyExpression(AssignmentType type, String name, Expression expression) {
		this.type = type
		this.name = name
		this.expression = expression
	}

	TypedExpression type(TypedContext tc, Type preferred) {
		def v = expression.type(tc, preferred)
		new VariableSetExpression(type.set(tc, name, v.type), v)
	}

	IKismetObject evaluate(Context c) {
		def v = expression.evaluate(c)
		type.set(c, name, v)
		v
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

	String repr() { "call(${members.join(', ')})" }

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

	int size() { arguments.size() + 1 }

	CallExpression join(List<Expression> exprs) {
		new CallExpression(exprs)
	}
	
	TypedExpression type(TypedContext tc, Type preferred) {
		def args = new TypedExpression[arguments.size()]
		for (int i = 0; i < args.length; ++i) args[i] = arguments.get(i).type(tc)
		TypedContext.VariableReference m
		if (callValue instanceof NameExpression && null != (m =
				tc.findCall(((NameExpression) callValue).text, args, preferred))) {
			new TypedCallExpression(new VariableExpression(m), args, ((FunctionType) m.variable.type).returnType)
		} else {
			new TypedCallExpression(callValue.type(tc), args, Type.ANY)
		}
	}
}

@CompileStatic
class ListExpression extends Expression {
	List<Expression> members

	ListExpression(List<Expression> members) {
		this.members = members
	}

	String repr() { "[${members.join(', ')}]" }

	IKismetObject evaluate(Context c) {
		Kismet.model(members*.evaluate(c))
	}

	static class Typed extends TypedExpression {
		TypedExpression[] members

		Typed(TypedExpression[] members) {
			this.members = members
		}

		Type getType() {
			def typ = new ListType()
			for (final m : members) typ.feed(m.type)
			typ
		}

		Instruction getInstruction() { new Instr(members) }

		static class Instr extends Instruction {
			Instruction[] members

			Instr(Instruction[] members) {
				this.members = members
			}

			Instr(TypedExpression[] zro) {
				members = new Instruction[zro.length]
				for (int i = 0; i < zro.length; ++i) members[i] = zro[i].instruction
			}

			IKismetObject evaluate(Memory context) {
				def arr = new ArrayList<IKismetObject>(members.length)
				for (int i = 0; i < members.length; ++i) arr.add(members[i].evaluate(context))
				Kismet.model(arr)
			}
		}
	}
}

@CompileStatic
class TupleExpression extends Expression {
	List<Expression> members

	TupleExpression(List<Expression> members) {
		this.members = members
	}

	String repr() { "(${members.join(', ')})" }

	IKismetObject evaluate(Context c) {
		def arr = new IKismetObject[members.size()]
		for (int i = 0; i < arr.length; ++i) arr[i] = members.get(i).evaluate(c)
		Kismet.model(new Tuple<IKismetObject>(arr))
	}

	static class Typed extends TypedExpression {
		TypedExpression[] members

		Typed(TypedExpression[] members) {
			this.members = members
		}

		Type getType() {
			def arr = new Type[members.length]
			for (int i = 0; i < arr.length; ++i) arr[i] = members[i].type
			new TupleType(arr)
		}

		Instruction getInstruction() { new Instr(members) }

		static class Instr extends Instruction {
			Instruction[] members

			Instr(Instruction[] members) {
				this.members = members
			}

			Instr(TypedExpression[] zro) {
				members = new Instruction[zro.length]
				for (int i = 0; i < zro.length; ++i) members[i] = zro[i].instruction
			}

			IKismetObject evaluate(Memory context) {
				def arr = new IKismetObject[members.length]
				for (int i = 0; i < arr.length; ++i) arr[i] = members[i].evaluate(context)
				Kismet.model(new Tuple<IKismetObject>(arr))
			}
		}
	}
}

@CompileStatic
class SetExpression extends Expression {
	List<Expression> members

	SetExpression(List<Expression> members) {
		this.members = members
	}

	String repr() { "{${members.join(', ')}}" }

	IKismetObject evaluate(Context c) {
		def arr = new HashSet<IKismetObject>(members.size())
		for (m in members) arr.add(m.evaluate(c))
		Kismet.model(arr)
	}

	static class Typed extends TypedExpression {
		TypedExpression[] members

		Typed(TypedExpression[] members) {
			this.members = members
		}

		Type getType() {
			def arr = new Type[members.length]
			for (int i = 0; i < arr.length; ++i) arr[i] = members[i].type
			new TupleType(arr)
		}

		Instruction getInstruction() { new Instr(members) }

		static class Instr extends Instruction {
			Instruction[] members

			Instr(Instruction[] members) {
				this.members = members
			}

			Instr(TypedExpression[] zro) {
				members = new Instruction[zro.length]
				for (int i = 0; i < zro.length; ++i) members[i] = zro[i].instruction
			}

			IKismetObject evaluate(Memory context) {
				def arr = new HashSet<IKismetObject>(members.size())
				for (final m : members) arr.add(m.evaluate(context))
				Kismet.model(arr)
			}
		}
	}
}

@CompileStatic
class MapExpression extends Expression {
	List<ColonExpression> members

	MapExpression(List<ColonExpression> members) {
		this.members = members
	}

	String repr() { "{#${members.join(', ')}}" }

	IKismetObject evaluate(Context c) {
		def arr = new HashMap<Object, IKismetObject>(members.size())
		for (m in members) arr.put(m.left.evaluate(c).inner(), m.right.evaluate(c))
		Kismet.model(arr)
	}

	static class Typed extends TypedExpression {
		TypedExpression[] members

		Typed(TypedExpression[] members) {
			this.members = members
		}

		Type getType() {
			def arr = new Type[members.length]
			for (int i = 0; i < arr.length; ++i) arr[i] = members[i].type
			new TupleType(arr)
		}

		Instruction getInstruction() { new Instr(members) }

		static class Instr extends Instruction {
			Instruction[] members

			Instr(Instruction[] members) {
				this.members = members
			}

			Instr(TypedExpression[] zro) {
				members = new Instruction[zro.length]
				for (int i = 0; i < zro.length; ++i) members[i] = zro[i].instruction
			}

			IKismetObject evaluate(Memory context) {
				def arr = new HashSet<IKismetObject>(members.size())
				for (final m : members) arr.add(m.evaluate(context))
				Kismet.model(arr)
			}
		}
	}
}

@CompileStatic
class ColonExpression extends Expression {
	Expression left, right

	ColonExpression(Expression left, Expression right) {
		this.left = left
		this.right = right
	}

	IKismetObject evaluate(Context c) {
		def value = right.evaluate(c)
		if (left instanceof StringExpression)
			AssignmentType.ASSIGN.set(c, ((StringExpression) left).value.inner(), value)
		else if (left instanceof NameExpression)
			AssignmentType.ASSIGN.set(c, ((NameExpression) left).text, value)
		else if (left instanceof PathExpression) {
			def steps = ((PathExpression) left).steps
			def toApply = steps.init()
			def toSet = steps.last()
			IKismetObject val = PathExpression.applySteps(c, ((PathExpression) left).root.evaluate(c), toApply)
			if (toSet instanceof PathExpression.PropertyStep)
				val.kismetClass().propertySet(val, ((PathExpression.PropertyStep) toSet).name, value)
			else if (toSet instanceof PathExpression.SubscriptStep)
				val.kismetClass().subscriptSet(val, ((PathExpression.SubscriptStep) toSet).expression.evaluate(c), value)
		} else throw new UnexpectedSyntaxException("Left hand side of colon $left")
		value
	}

	TypedExpression type(TypedContext tc, Type preferred) {
		def val = right.type(tc, preferred) // no need to check if it satisfies because it will check itself
		def atom = Prelude.toAtom(left)
		if (null != atom) {
			def var = tc.find(atom)
			if (null == var) var = tc.addVariable(atom, preferred).ref()
			else if (!var.variable.type.relation(preferred).assignableTo)
				throw new UnexpectedTypeException("Variable with name $atom had type $var.variable.type, not preferred type $preferred")
			new VariableSetExpression(var, val)
		} else if (left instanceof PathExpression) {
			final p = (PathExpression) left
			def ea = new PathExpression(p.root, p.steps.init()).type(tc).instruction
			def vali = val.instruction
			def rh = p.steps.last()
			Instruction instr
			if (rh instanceof PathExpression.PropertyStep) {
				def text = ((PathExpression.PropertyStep) rh).name
				instr = new Instruction() {
					IKismetObject evaluate(Memory context) {
						def v = ea.evaluate(context)
						v.kismetClass().propertySet(v, text, vali.evaluate(context))
					}
				}
			} else if (rh instanceof PathExpression.SubscriptStep) {
				def ex = ((PathExpression.SubscriptStep) rh).expression.type(tc).instruction
				instr = new Instruction() {
					IKismetObject evaluate(Memory context) {
						def v = ea.evaluate(context)
						v.kismetClass().subscriptSet(v, ex.evaluate(context), vali.evaluate(context))
					}
				}
			} else throw new UnsupportedOperationException('cant set path step ' + rh)
			new BasicTypedExpression(preferred, instr)
		} else if (left instanceof NumberExpression) {
			def b = left.value.inner().intValue()
			def var = tc.getVariable(b)
			if (null == var) var = new TypedContext.Variable(null, b, preferred)
			else if (!var.type.relation(preferred).assignableTo)
				throw new UnexpectedTypeException("Variable number $b had type $var.type, not preferred type $preferred")
			new VariableSetExpression(var.ref(), val)
		} else {
			def lhi = left.type(tc).instruction
			def rhi = val.instruction
			new BasicTypedExpression(preferred, new Instruction() {
				@Override
				IKismetObject evaluate(Memory context) {
					((TypedContext.VariableReference) lhi.evaluate(context).inner()).set(context, rhi.evaluate(context))
				}
			})
		}
	}

	List<Expression> getMembers() { [left, right] }

	Expression join(List<Expression> exprs) {
		new ColonExpression(exprs[0], exprs[1])
	}

	Expression getAt(int i) { i == 0 ? left : i == 1 ? right : null }

	int size() { 2 }
	String toString() { "$left: $right" }
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
		if (null != arr[4] && arr[4].length() % 2 == 1) {
			x.insert(0, (char) '-')
		}
		if (null != arr[1]) {
			x.append((char) '.').append(arr[1]); t = true
		}
		if (null != arr[2]) {
			x.append((char) 'e').append(arr[2]); t = true
		}
		String r = x.toString()
		Number v
		if (null == arr[3])
			v = t ? new BigDecimal(r) : new BigInteger(r)
		else if (type) {
			if (arr[3].length() == 0) v = new BigDecimal(r)
			else {
				int b = Integer.valueOf(arr[3].toString())
				if (b == 32) v = new Float(r)
				else if (b == 64) v = new Double(r)
				else throw new NumberFormatException("Invalid number of bits $b for explicit float")
			}
		} else if (t) {
			v = new BigDecimal(r)
			if (arr[3].length() == 0) v = v.toBigInteger()
			else {
				int b = Integer.valueOf(arr[3].toString())
				if (b == 8) v = v.byteValue()
				else if (b == 16) v = v.shortValue()
				else if (b == 32) v = v.intValue()
				else if (b == 64) v = v.longValue()
				else throw new NumberFormatException("Invalid number of bits $b for explicit integer")
			}
		} else if (arr[3].length() == 0) v = new BigInteger(r)
		else {
			int b = Integer.valueOf(arr[3].toString())
			if (b == 8) v = new Byte(r)
			else if (b == 16) v = new Short(r)
			else if (b == 32) v = new Integer(r)
			else if (b == 64) v = new Long(r)
			else throw new NumberFormatException("Invalid number of bits $b for explicit integer")
		}
		setValue(v)
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

				IKismetObject evaluate(Memory context) {
					context.get(index)
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
@Singleton(property = 'INSTANCE')
class NoExpression extends Expression {
	String repr() { "noexpr" }

	IKismetObject evaluate(Context c) {
		Kismet.NULL
	}

	TypedNoExpression type(TypedContext tc, Type preferred) {
		TypedNoExpression.INSTANCE
	}
}