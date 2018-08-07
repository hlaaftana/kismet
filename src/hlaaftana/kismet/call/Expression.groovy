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
import hlaaftana.kismet.type.GenericType
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetString
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.WrapperKismetObject

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
			new PathFunction(c, steps)
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
		TypedExpression type(TypedContext ctx, TypedExpression before)
		TypedExpression typeSet(TypedContext ctx, TypedExpression before, TypedExpression value)
	}

	static class PropertyStep implements Step {
		String name

		PropertyStep(String name) {
			this.name = name
		}

		IKismetObject get(Context c, IKismetObject object) {
			final n = Function.callOrNull(c, getterName(name), object)
			if (null != n) return n
			Function.tryCall(c, '.property', object, new KismetString(name))
		}

		IKismetObject set(Context c, IKismetObject object, IKismetObject value) {
			final n = Function.callOrNull(c, setterName(name), object, value)
			if (null != n) return n
			Function.tryCall(c, '.property=', object, new KismetString(name), value)
		}

		static String getterName(String prop) {
			def res = new char[prop.length() + 1]
			res[0] = (char) '.'
			prop.getChars(0, prop.length(), res, 1)
			String.valueOf(res)
		}

		static String setterName(String prop) {
			def res = new char[prop.length() + 2]
			res[0] = (char) '.'
			prop.getChars(0, prop.length(), res, 1)
			res[res.length - 1] = (char) '='
			String.valueOf(res)
		}

		String toString() { getterName(name) }
		Expression asExpr() { new NameExpression(name) }
		PropertyStep borrow(Expression expr) { new PropertyStep(expr.toString()) }

		TypedExpression type(TypedContext ctx, TypedExpression before) {
			def m = ctx.find(getterName(name), Prelude.func(Type.ANY, before.type))
			if (null != m) return new TypedCallExpression(new VariableExpression(m), [before] as TypedExpression[],
					((GenericType) m.variable.type)[1])
			m = ctx.findThrow('.property', Prelude.func(Type.ANY, before.type, Prelude.STRING_TYPE))
			new TypedCallExpression(new VariableExpression(m), [before, new TypedStringExpression(name)] as TypedExpression[],
					((GenericType) m.variable.type)[1])
		}

		TypedExpression typeSet(TypedContext ctx, TypedExpression before, TypedExpression value) {
			def m = ctx.find(setterName(name), Prelude.func(Type.ANY, before.type, value.type))
			if (null != m) return new TypedCallExpression(new VariableExpression(m), [before, value] as TypedExpression[],
					((GenericType) m.variable.type)[1])
			m = ctx.findThrow('.property=', Prelude.func(Type.ANY, before.type, Prelude.STRING_TYPE, value.type))
			new TypedCallExpression(new VariableExpression(m), [before, new TypedStringExpression(name), value] as TypedExpression[],
					((GenericType) m.variable.type)[1])
		}
	}

	static class SubscriptStep implements Step {
		Expression expression

		SubscriptStep(Expression expression) {
			this.expression = expression
		}

		IKismetObject get(Context c, IKismetObject object) {
			Function.tryCall(c, '.[]', object, expression.evaluate(c))
		}

		IKismetObject set(Context c, IKismetObject object, IKismetObject value) {
			Function.tryCall(c, '.[]=', object, expression.evaluate(c), value)
		}

		String toString() { ".[$expression]" }
		Expression asExpr() { expression }
		SubscriptStep borrow(Expression expr) { new SubscriptStep(expr) }

		TypedExpression type(TypedContext ctx, TypedExpression before) {
			def key = expression.type(ctx)
			def m = ctx.findThrow('.[]', Prelude.func(Type.ANY, before.type, key.type))
			new TypedCallExpression(new VariableExpression(m), [before, key] as TypedExpression[],
					((GenericType) m.variable.type)[1])
		}

		TypedExpression typeSet(TypedContext ctx, TypedExpression before, TypedExpression value) {
			def key = expression.type(ctx)
			def m = ctx.findThrow('.[]=', Prelude.func(Type.ANY, before.type, key.type, value.type))
			new TypedCallExpression(new VariableExpression(m), [before, key, value] as TypedExpression[],
					((GenericType) m.variable.type)[1])
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
					Function.tryCall(this, '.property', object, new KismetString(name))
				}
			}
		}
		Expression asExpr() { expression }
		EnterStep borrow(Expression expr) { new EnterStep(expr) }

		TypedExpression type(TypedContext ctx, TypedExpression before) {
			throw new UnsupportedOperationException('unsupported')
		}

		TypedExpression typeSet(TypedContext ctx, TypedExpression before, TypedExpression value) {
			throw new UnsupportedOperationException('unsupported')
		}
	}

	int size() { 1 + steps.size() }

	TypedExpression type(TypedContext tc, Type preferred) {
		TypedExpression result = root.type(tc)
		for (s in steps) result = s.type(tc, result)
		result
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
		new VariableExpression(tc.findThrow(text, preferred))
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
	@Override DiveExpression join(List<Expression> a) { new DiveExpression(a[0]) }

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
			final arr = new IKismetObject[arguments.size() + 1]
			arr[0] = obj
			for (int i = 0; i < arr.length; ++i) arr[i+1] = arguments[i].evaluate(c)
			((Function) c.get('call')).call(arr)
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
		TypedContext.VariableReference m
		if (callValue instanceof NameExpression && null != (m = tc.findStatic(callValue.text, Prelude.TEMPLATE_TYPE))) {
			return ((Template) ((TypedContext.StaticVariable) m.variable).value)
					.transform(null, arguments.toArray(new Expression[0])).type(tc, preferred)
		} else if (callValue instanceof NameExpression && null != (m = tc.findStatic(callValue.text, Prelude.TYPE_CHECKER_TYPE))) {
			return ((TypeChecker) ((TypedContext.StaticVariable) m.variable).value)
					.transform(tc, arguments.toArray(new Expression[0]))
		}
		def args = new TypedExpression[arguments.size()]
		def argtypes = new Type[args.length]
		for (int i = 0; i < args.length; ++i) argtypes[i] = (args[i] = arguments.get(i).type(tc)).type
		def fn = Prelude.func(preferred, argtypes)
		if (callValue instanceof NameExpression && null != (m = tc.find(callValue.text, fn))) {
			new TypedCallExpression(new VariableExpression(m), args, ((GenericType) m.variable.type)[1])
		} else {
			def calltyped = callValue.type(tc)
			if (calltyped.type.relation(fn).assignableTo) {
				new TypedCallExpression(calltyped, args, ((GenericType) calltyped.type)[1])
			} else {
				def typs = new Type[args.length + 1]
				typs[0] = calltyped.type
				System.arraycopy(argtypes, 0, typs, 1, argtypes.length)
				def cc = tc.findThrow('call', Prelude.func(preferred, new TupleType(typs)))
				new TypedCallExpression(new VariableExpression(cc), args, ((GenericType) cc.variable.type)[1])
			}
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
		new WrapperKismetObject(members*.evaluate(c))
	}

	TypedExpression type(TypedContext tc, Type preferred) {
		if (!preferred.relation(Prelude.LIST_TYPE).assignableTo)
			throw new UnexpectedTypeException('Tried to infer list expression as non-list type '.concat(preferred.toString()))
		def bound = preferred instanceof GenericType ? ((GenericType) preferred)[0] : Type.ANY
		def arr = new TypedExpression[members.size()]
		for (int i = 0; i < arr.length; ++i) arr[i] = members.get(i).type(tc, bound)
		new Typed(arr)
	}

	static class Typed extends TypedExpression {
		TypedExpression[] members

		Typed(TypedExpression[] members) {
			this.members = members
		}

		Type getType() {
			def bound = Type.ANY
			for (final m : members) {
				def rel = bound.relation(m.type)
				if (rel.sub) bound = m.type
				else if (rel.none) throw new UnexpectedTypeException('Type ' + m.type + ' is incompatible with list with bound ' + bound)
			}
			new GenericType(Prelude.LIST_TYPE, bound)
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
				new WrapperKismetObject(arr)
			}
		}

		boolean isRuntimeOnly() {
			for (final m : members) if (m.runtimeOnly) return true
			false
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
		new WrapperKismetObject(new Tuple<IKismetObject>(arr))
	}

	TypedExpression type(TypedContext tc, Type preferred) {
		if (!preferred.relation(TupleType.ANY).assignableTo)
			throw new UnexpectedTypeException('Tried to infer tuple expression as non-tuple type '.concat(preferred.toString()))
		final bounds = preferred instanceof TupleType ? preferred.bounds : (Type[]) null
		if (null != bounds && members.size() != bounds.length)
			throw new UnexpectedTypeException("Tuple expression length ${members.size()} did not match expected tuple type length $len")
		def arr = new TypedExpression[members.size()]
		for (int i = 0; i < arr.length; ++i) arr[i] = members.get(i).type(tc, null == bounds ? Type.ANY : bounds[i])
		new Typed(arr)
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

		boolean isRuntimeOnly() {
			for (final m : members) if (m.runtimeOnly) return true
			false
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

		boolean isRuntimeOnly() {
			for (final m : members) if (m.runtimeOnly) return true
			false
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

		boolean isRuntimeOnly() {
			for (final m : members) if (m.runtimeOnly) return true
			false
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
			toSet.set(c, val, value)
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
			def rh = p.steps.last()
			rh.typeSet(tc, new PathExpression(p.root, p.steps.init()).type(tc), val)
		} else if (left instanceof NumberExpression) {
			def b = left.value.inner().intValue()
			def var = tc.getVariable(b)
			if (null == var) var = new TypedContext.Variable(null, b, preferred)
			else if (!var.type.relation(preferred).assignableTo)
				throw new UnexpectedTypeException("Variable number $b had type $var.type, not preferred type $preferred")
			new VariableSetExpression(var.ref(), val)
		} else {
			final lhi = left.type(tc).instruction
			final rhi = val.instruction
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
		if (!preferred.relation(Prelude.STRING_TYPE).assignableFrom)
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