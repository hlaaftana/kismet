package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.exceptions.*
import hlaaftana.kismet.lib.CollectionsIterators
import hlaaftana.kismet.lib.Functions
import hlaaftana.kismet.lib.Strings
import hlaaftana.kismet.lib.Syntax
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.parser.StringEscaper
import hlaaftana.kismet.scope.AssignmentType
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.*
import hlaaftana.kismet.vm.*

import static hlaaftana.kismet.call.ExprBuilder.*

@CompileStatic
abstract class Expression implements IKismetObject<Expression> {
	int ln, cl
	abstract IKismetObject evaluate(Memory c)

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		throw new UnsupportedOperationException('Cannot turn ' + this + ' to typedContext')
	}

	TypedExpression type(TypedContext tc) { type(tc, +Type.ANY) }

	List<Expression> getMembers() { [] }

	int size() { members.size() }

	Expression getAt(int i) { members[i] }

	Expression join(List<Expression> exprs) {
		throw new UnsupportedOperationException("Cannot join exprs $exprs on class ${this.class}")
	}

	Expression percentize(Parser p) {
		new StaticExpression(this, p.memory)
	}

	Expression inner() { this }

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

	IKismetObject evaluate(Memory c) {
		if (null == root || root instanceof NoExpression) {
			new PathFunction(c, steps)
		} else {
			applySteps(c, root.evaluate(c), steps)
		}
	}

	static class PathFunction extends Function {
		Memory context
		List<Step> steps

		PathFunction(Memory context, List<Step> steps) {
			this.context = context
			this.steps = steps
		}

		IKismetObject call(IKismetObject... args) {
			applySteps(context, args[0], steps)
		}
	}

	static IKismetObject applySteps(Memory c, IKismetObject object, List<Step> steps) {
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
		assert s.size() + 1 == m.size(), "Members must be same size as joined expressions"
		for (int i = 1; i < m.size(); ++i) {
			result.add(s.get(i - 1).borrow(m.get(i)))
		}
		new PathExpression(m[0], result)
	}

	interface Step {
		IKismetObject get(Memory c, IKismetObject object)
		IKismetObject set(Memory c, IKismetObject object, IKismetObject value)
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

		IKismetObject get(Memory c, IKismetObject object) {
			final n = Function.callOrNull(c, getterName(name), object)
			if (null != n) return n
			Function.tryCall(c, '.property', object, new KismetString(name))
		}

		IKismetObject set(Memory c, IKismetObject object, IKismetObject value) {
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
		Expression asExpr() { name(name) }
		PropertyStep borrow(Expression expr) { new PropertyStep(expr.toString()) }

		TypedExpression type(TypedContext ctx, TypedExpression before) {
			try {
				return call(name(getterName(name)), new TypedWrapperExpression(before)).type(ctx)
			} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {}
			call(name('.property'), new TypedWrapperExpression(before), string(name)).type(ctx)
		}

		TypedExpression typeSet(TypedContext ctx, TypedExpression before, TypedExpression value) {
			try {
				return call(name(setterName(name)), new TypedWrapperExpression(before), new TypedWrapperExpression(value)).type(ctx)
			} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {}
			call(name('.property='), new TypedWrapperExpression(before), string(name), new TypedWrapperExpression(value)).type(ctx)
		}
	}

	static class SubscriptStep implements Step {
		Expression expression

		SubscriptStep(Expression expression) {
			this.expression = expression
		}

		IKismetObject get(Memory c, IKismetObject object) {
			Function.tryCall(c, '.[]', object, expression.evaluate(c))
		}

		IKismetObject set(Memory c, IKismetObject object, IKismetObject value) {
			Function.tryCall(c, '.[]=', object, expression.evaluate(c), value)
		}

		String toString() { ".[$expression]" }
		Expression asExpr() { expression }
		SubscriptStep borrow(Expression expr) { new SubscriptStep(expr) }

		TypedExpression type(TypedContext ctx, TypedExpression before) {
			def key = expression.type(ctx)
			call(name('.[]'), new TypedWrapperExpression(before), new TypedWrapperExpression(key)).type(ctx)
		}

		TypedExpression typeSet(TypedContext ctx, TypedExpression before, TypedExpression value) {
			def key = expression.type(ctx)
			call(name('.[]='), new TypedWrapperExpression(before), new TypedWrapperExpression(key),
					new TypedWrapperExpression(value)).type(ctx)
		}
	}

	static class EnterStep implements Step {
		Expression expression

		EnterStep(Expression expression) {
			this.expression = expression
		}

		IKismetObject get(Memory c, IKismetObject object) {
			def ec = new EnterContext((Context) c)
			ec.set('it', ec.object = object)
			expression.evaluate(ec)
		}

		IKismetObject set(Memory c, IKismetObject object, IKismetObject value) {
			throw new UnsupportedOperationException('unsupported curly dots')
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

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		TypedExpression result = root.type(tc)
		def newSteps = new ArrayList<Step>(steps.size())
		for (s in steps) {
			newSteps.add(s)
			result = s.type(tc, result).withOriginal(new PathExpression(root, newSteps))
		}
		if (!preferred.relation(result.type).assignableFrom)
			throw new UnexpectedTypeException("path expression $this is not $preferred")
		result.withOriginal(this)
	}
}

@CompileStatic
class TypedWrapperExpression extends Expression {
	TypedExpression inner

	TypedWrapperExpression(TypedExpression inner) {
		this.inner = inner
	}

	IKismetObject evaluate(Memory c) {
		inner.instruction.evaluate(c)
	}

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		inner
	}

	String repr() {
		"typedContext($inner)"
	}
}

@CompileStatic
class OnceExpression extends Expression {
	Expression inner
	IKismetObject value

	OnceExpression(Expression inner) {
		this.inner = inner
	}

	IKismetObject evaluate(Memory c) {
		if (null == value) value = inner.evaluate(c)
		value
	}

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		new TypedOnceExpression(inner.type(tc, preferred)).withOriginal(this)
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

	IKismetObject evaluate(Memory c) {
		c.get(text)
	}

	String repr() { text }

	VariableExpression type(TypedContext tc, TypeBound preferred) {
		new VariableExpression(tc.findThrow(text, preferred)).<VariableExpression>withOriginal(this)
	}
}

@CompileStatic
class DiveExpression extends Expression {
	Expression inner

	DiveExpression(Expression inner) {
		this.inner = inner
	}

	String repr() { "dive[$inner]" }

	IKismetObject evaluate(Memory c) {
		c = new Context(c)
		inner.evaluate(c)
	}

	List<Expression> getMembers() { [inner] }
	int size() { 1 }
	@Override DiveExpression join(List<Expression> a) { new DiveExpression(a[0]) }

	TypedDiveExpression type(TypedContext tc, TypeBound preferred) {
		final child = tc.child()
		new TypedDiveExpression(child, inner.type(child, preferred)).<TypedDiveExpression>withOriginal(this)
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

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		def v = expression.type(tc, preferred)
		new VariableSetExpression(type.set(tc, name, v.type), v).withOriginal(this)
	}

	IKismetObject evaluate(Memory c) {
		def v = expression.evaluate(c)
		type.set((Context) c, name, v)
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

	IKismetObject evaluate(Memory c) {
		IKismetObject a = Kismet.NULL
		for (e in members) a = e.evaluate(c)
		a
	}

	BlockExpression join(List<Expression> exprs) {
		new BlockExpression(exprs)
	}

	SequentialExpression type(TypedContext tc, TypeBound preferred) {
		def arr = new TypedExpression[members.size()]
		int i = 0
		for (; i < arr.length - 1; ++i) arr[i] = members.get(i).type(tc)
		arr[i] = members.get(i).type(tc, preferred)
		new SequentialExpression(arr).<SequentialExpression>withOriginal(this)
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

	IKismetObject evaluate(Memory c) {
		if (null == callValue) return Kismet.NULL
		IKismetObject obj = callValue.evaluate(c)
		if (obj.inner() instanceof KismetCallable) {
			((KismetCallable) obj.inner()).call(c, arguments.<Expression>toArray(new Expression[arguments.size()]))
		} else {
			final arr = new IKismetObject[2]
			arr[0] = obj
			def arg = new IKismetObject[arr.length]
			for (int i = 0; i < arr.length; ++i) arg[i] = arguments[i].evaluate(c)
			arr[1] = new KismetTuple(arg)
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

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		/*TypedContext.VariableReference m
		if (callValue instanceof NameExpression && null != (m = tc.findStatic(callValue.text, Prelude.TEMPLATE_TYPE))) {
			return ((Template) m.variable.value)
					.transform(null, arguments.toArray(new Expression[0])).type(tc, preferred)
		} else if (callValue instanceof NameExpression && null != (m = tc.findStatic(callValue.text, Prelude.TYPE_CHECKER_TYPE))) {
			return ((TypeChecker) m.variable.value)
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
		}*/

		TypedExpression cv

		// template
		try {
			cv = callValue.type(tc, -Functions.TEMPLATE_TYPE)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {}
		if (null != cv && cv.type == Functions.TEMPLATE_TYPE)
			return ((Template) cv.instruction.evaluate(tc))
					.transform(null, arguments.<Expression>toArray(new Expression[arguments.size()])).type(tc, preferred).withOriginal(this)

		// type checker
		try {
			cv = callValue.type(tc, -Functions.TYPE_CHECKER_TYPE)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {}
		if (null != cv && cv.type == Functions.TYPE_CHECKER_TYPE)
			return ((TypeChecker) cv.instruction.evaluate(tc))
					.transform(tc, arguments.<Expression>toArray(new Expression[arguments.size()])).withOriginal(this)

		// runtime args
		def args = new TypedExpression[arguments.size()]
		def argtypes = new Type[args.length]
		for (int i = 0; i < args.length; ++i) argtypes[i] = (args[i] = arguments.get(i).type(tc)).type

		// typedContext template
		final typedTmpl = Functions.typedTmpl(preferred.type, argtypes)
		try {
			cv = callValue.type(tc, -typedTmpl)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {
			if (callValue.repr() == 'parameter_type')
				((Exception) ignored).printStackTrace()
		}
		if (null != cv) {
			def x = ((TypedTemplate) cv.instruction.evaluate(tc))
			return x.transform(tc, args).withOriginal(this)
		}

		// instructor
		final inr = Functions.instr(preferred.type, argtypes)
		try {
			cv = callValue.type(tc, -inr)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {}
		if (null != cv) return new InstructorCallExpression(cv, args, cv.type instanceof SingleType ?
			Type.ANY : ((GenericType) cv.type)[1]).withOriginal(this)

		// function
		final fn = Functions.func(preferred.type, argtypes)
		try {
			cv = callValue.type(tc, -fn)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {}
		if (null != cv) return new TypedCallExpression(cv, args, cv.type instanceof SingleType ?
			Type.ANY : ((GenericType) cv.type)[1]).withOriginal(this)

		if (callValue instanceof NameExpression) {
			// TODO: dynamic dispatch
		}

		cv = callValue.type(tc)
		// TODO: change the 'call' signature in Prelude after all untyped functions are typedContext (held back by generics)
		// println "WARNING: $this WILL USE 'call' FUNCTION"
		def callFnType = Functions.FUNCTION_TYPE.generic(new TupleType(cv.type, new TupleType(argtypes)), preferred.type)
		def cc = tc.find('call', -callFnType)
		if (null == cc) throw new UndefinedSymbolException('Could not find overload for ' + repr() + ' as (' + argtypes.join(', ') + ') -> ' + preferred)
		new TypedCallExpression(new VariableExpression(cc), [cv, new TupleExpression.Typed(args)] as TypedExpression[],
			cc.variable.type instanceof SingleType ? Type.ANY : ((GenericType) cc.variable.type)[1]).withOriginal(this)
	}
}

@CompileStatic
abstract class CollectionExpression extends Expression {
	abstract SingleType getBaseType()

	TypeBound match(TypeBound preferred) {
		def rel = preferred.relation(baseType)
		if (rel.none)
			throw new UnexpectedTypeException('Failed to infer collection type, base: ' + baseType + ', bound: ' + preferred)
		preferred * (rel.sub && preferred.type instanceof GenericType ? ((GenericType) preferred.type)[0] : Type.ANY)
	}
}

@CompileStatic
class ListExpression extends CollectionExpression {
	List<Expression> members

	ListExpression(List<Expression> members) {
		this.members = members
	}

	String repr() { "[${members.join(', ')}]" }

	IKismetObject evaluate(Memory c) {
		Kismet.model(members*.evaluate(c)*.inner())
	}

	SingleType getBaseType() { CollectionsIterators.LIST_TYPE }

	Expression join(List<Expression> exprs) {
		new ListExpression(exprs)
	}

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		final bound = match(preferred)
		def arr = new TypedExpression[members.size()]
		for (int i = 0; i < arr.length; ++i) arr[i] = members.get(i).type(tc, bound)
		new Typed(arr).withOriginal(this)
	}

	static class Typed extends TypedExpression {
		TypedExpression[] members

		Typed(TypedExpression[] members) {
			this.members = members
		}

		Type getType() {
			def bound = Type.NONE
			for (final m : members) {
				def rel = bound.relation(m.type)
				if (rel.sub) bound = m.type
				else if (rel.none) throw new UnexpectedTypeException('Type ' + m.type + ' is incompatible with list with bound ' + bound)
			}
			new GenericType(CollectionsIterators.LIST_TYPE, bound)
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
				def arr = new ArrayList<Object>(members.length)
				for (int i = 0; i < members.length; ++i) arr.add(members[i].evaluate(context).inner())
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
class TupleExpression extends CollectionExpression {
	List<Expression> members

	TupleExpression(List<Expression> members) {
		this.members = members
	}

	String repr() { "(${members.join(', ')})" }

	Expression join(List<Expression> exprs) {
		new TupleExpression(exprs)
	}

	IKismetObject evaluate(Memory c) {
		def arr = new IKismetObject[members.size()]
		for (int i = 0; i < arr.length; ++i) arr[i] = members.get(i).evaluate(c)
		new KismetTuple(arr)
	}

	SingleType getBaseType() { TupleType.BASE }

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		def rel = preferred.relation(baseType)
		if (rel.none)
			throw new UnexpectedTypeException('Tried to infer tuple expression as non-tuple type '.concat(preferred.toString()))
		final bounds = rel.sub && preferred.type instanceof TupleType ? ((TupleType) preferred.type).arguments : (Type[]) null
		if (null != bounds && members.size() != bounds.length)
			throw new UnexpectedTypeException("Tuple expression length ${members.size()} did not match expected tuple type length $bounds.length")
		def arr = new TypedExpression[members.size()]
		for (int i = 0; i < arr.length; ++i) arr[i] = members.get(i).type(tc, preferred * (null == bounds ? Type.ANY : bounds[i]))
		new Typed(arr).withOriginal(this)
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
				new KismetTuple(arr)
			}
		}

		boolean isRuntimeOnly() {
			for (final m : members) if (m.runtimeOnly) return true
			false
		}
	}
}

@CompileStatic
class SetExpression extends CollectionExpression {
	List<Expression> members

	SetExpression(List<Expression> members) {
		this.members = members
	}

	String repr() { "{${members.join(', ')}}" }

	Expression join(List<Expression> exprs) {
		new SetExpression(exprs)
	}

	IKismetObject evaluate(Memory c) {
		def arr = new HashSet<Object>(members.size())
		for (m in members) arr.add(m.evaluate(c).inner())
		Kismet.model(arr)
	}

	SingleType getBaseType() { CollectionsIterators.SET_TYPE }

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		final bound = match(preferred)
		def arr = new TypedExpression[members.size()]
		for (int i = 0; i < arr.length; ++i) arr[i] = members.get(i).type(tc, bound)
		new Typed(arr).withOriginal(this)
	}

	static class Typed extends TypedExpression {
		TypedExpression[] members

		Typed(TypedExpression[] members) {
			this.members = members
		}

		Type getType() {
			def bound = Type.NONE
			for (final m : members) {
				def rel = bound.relation(m.type)
				if (rel.sub) bound = m.type
				else if (rel.none) throw new UnexpectedTypeException('Type ' + m.type + ' is incompatible with set with bound ' + bound)
			}
			new GenericType(CollectionsIterators.SET_TYPE, bound)
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
				def arr = new HashSet<Object>(members.size())
				for (final m : members) arr.add(m.evaluate(context).inner())
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
class MapExpression extends CollectionExpression {
	List<ColonExpression> members

	MapExpression(List<ColonExpression> members) {
		this.members = members
	}

	String repr() { "{#${members.join(', ')}}" }

	Expression join(List<Expression> exprs) {
		def arr = new ArrayList<ColonExpression>()
		for (e in exprs)
			if (e instanceof ColonExpression)
				arr.add((ColonExpression) e)
			else throw new UnexpectedSyntaxException('tried to join non colon expression')
		new MapExpression(arr)
	}

	IKismetObject evaluate(Memory c) {
		def arr = new HashMap<Object, Object>(members.size())
		for (m in members) arr.put(m.left.evaluate(c).inner(), m.right.evaluate(c).inner())
		Kismet.model(arr)
	}

	SingleType getBaseType() { CollectionsIterators.MAP_TYPE }

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		def rel = preferred.relation(baseType)
		if (rel.none)
			throw new UnexpectedTypeException('Tried to infer map expression as non-map type '.concat(preferred.toString()))
		def kbound = preferred * (rel.sub && preferred.type instanceof GenericType ? ((GenericType) preferred.type)[0] : Type.ANY),
			vbound = preferred * (rel.sub && preferred.type instanceof GenericType ? ((GenericType) preferred.type)[1] : Type.ANY)
		def key = new TypedExpression[members.size()], val = new TypedExpression[members.size()]
		for (int i = 0; i < key.length; ++i) {
			def col = members.get(i)
			key[i] = col.left.type(tc, kbound)
			val[i] = col.right.type(tc, vbound)
		}
		new Typed(key, val).withOriginal(this)
	}

	static class Typed extends TypedExpression {
		TypedExpression[] keys, values

		Typed(TypedExpression[] keys, TypedExpression[] values) {
			this.keys = keys
			this.values = values
		}

		Type getType() {
			def key = Type.NONE, value = Type.NONE
			for (int i = 0; i < keys.length; ++i) {
				def krel = key.relation(keys[i].type)
				if (krel.sub) key = keys[i].type
				else if (krel.none) throw new UnexpectedTypeException('Type ' + keys[i].type + ' is incompatible with map with key bound ' + key)
				def vrel = value.relation(values[i].type)
				if (vrel.sub) value = values[i].type
				else if (vrel.none) throw new UnexpectedTypeException('Type ' + values[i].type + ' is incompatible with map with value bound ' + value)
			}
			new GenericType(CollectionsIterators.MAP_TYPE, key, value)
		}

		Instruction getInstruction() { new Instr(keys, values) }

		static class Instr extends Instruction {
			Instruction[] keys, values

			Instr(Instruction[] keys, Instruction[] values) {
				this.keys = keys
				this.values = values
			}

			Instr(TypedExpression[] key, TypedExpression[] val) {
				this(new Instruction[key.length], new Instruction[val.length])
				for (int i = 0; i < key.length; ++i) keys[i] = key[i].instruction
				for (int i = 0; i < val.length; ++i) values[i] = val[i].instruction
			}

			IKismetObject evaluate(Memory context) {
				def arr = new HashMap<Object, Object>(keys.length)
				for (int i = 0; i < keys.length; ++i) arr.put(keys[i].evaluate(context).inner(), values[i].evaluate(context).inner())
				Kismet.model(arr)
			}
		}

		boolean isRuntimeOnly() {
			for (int i = 0; i < keys.length; ++i) if (keys[i].runtimeOnly || values[i].runtimeOnly) return true
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

	IKismetObject evaluate(Memory c) {
		def value = right.evaluate(c)
		if (left instanceof StringExpression)
			AssignmentType.ASSIGN.set((Context) c, ((StringExpression) left).value.inner(), value)
		else if (left instanceof NameExpression)
			AssignmentType.ASSIGN.set((Context) c, ((NameExpression) left).text, value)
		else if (left instanceof PathExpression) {
			def steps = ((PathExpression) left).steps
			def toApply = steps.init()
			def toSet = steps.last()
			IKismetObject val = PathExpression.applySteps(c, ((PathExpression) left).root.evaluate(c), toApply)
			toSet.set(c, val, value)
		} else throw new UnexpectedSyntaxException("Left hand side of colon $left")
		value
	}

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		def val = right.type(tc, preferred) // no need to check if it satisfies because it will check itself
		def atom = Syntax.toAtom(left)
		if (null != atom) {
			def var = AssignmentType.ASSIGN.set(tc, atom, val.type)
			new VariableSetExpression(var, val).withOriginal(this)
		} else if (left instanceof PathExpression) {
			final p = (PathExpression) left
			def rh = p.steps.last()
			rh.typeSet(tc, (p.steps.size() == 1 ? p.root : new PathExpression(p.root, p.steps.init())).type(tc), val)
				.withOriginal(this)
		} else if (left instanceof NumberExpression) {
			// TODO: ranges with this syntax
			def b = left.value.inner().intValue()
			def var = tc.getVariable(b)
			if (null == var) var = new TypedContext.Variable(null, b, preferred.type)
			else if (!preferred.relation(var.type).assignableFrom)
				throw new UnexpectedTypeException("Variable number $b had type $var.type, not preferred type $preferred")
			new VariableSetExpression(var.ref(), val).withOriginal(this)
		} else {
			final lh = left.type(tc), lhi = lh.instruction
			final rhi = val.instruction
			new BasicTypedExpression(preferred.type, new Instruction() {
				@Override
				IKismetObject evaluate(Memory context) {
					((TypedContext.VariableReference) lhi.evaluate(context).inner()).set(context, rhi.evaluate(context))
				}
			}, lh.runtimeOnly || val.runtimeOnly).withOriginal(this)
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

	ConstantExpression() {}

	ConstantExpression(IKismetObject<T> value) {
		this.value = value
	}

	String repr() { "const($value)" }

	void setValue(T obj) {
		value = Kismet.model(obj)
	}

	IKismetObject<T> evaluate(Memory c) {
		value
		//Kismet.model(value.inner())
	}
}

@CompileStatic
class NumberExpression extends ConstantExpression<Number> {
	String repr() { value.toString() }

	NumberExpression() {}

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
		super.@value = KismetNumber.from(v)
	}

	NumberExpression(Number v) { super.@value = KismetNumber.from(v) }

	NumberExpression(String x) {
		Parser.NumberBuilder b = new Parser.NumberBuilder(null)
		char[] a = x.toCharArray()
		for (int i = 0; i < a.length; ++i) b.doPush((int) a[i])
		super.@value = KismetNumber.from(b.doFinish().value.inner())
	}

	TypedNumberExpression type(TypedContext tc, TypeBound preferred) {
		def result = new TypedNumberExpression(value.inner())
		if (preferred.any) return result
		def rel = preferred.relation(result.type)
		if (rel.none)
			throw new UnexpectedTypeException("Preferred non-number type $preferred for literal with number $result.number")
		else if (rel.sub)
			result.number = ((NumberType) preferred.type).instantiate(result.number).inner()
		result.<TypedNumberExpression>withOriginal(this)
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
		IKismetObject evaluate(Memory c) {
			final x = ((Context) c).@variables[index]
			if (x) x.value
			else throw new KismetEvaluationException(this, "No variable at index $index")
		}

		Typed type(TypedContext tc, TypeBound preferred) {
			new Typed(preferred.type, index).<Typed>withOriginal(this)
		}

		static class Typed extends BasicTypedExpression {
			int index

			Typed(Type type, int index) {
				super(type, new Inst(index), false)
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
			super.@value = new KismetString(StringEscaper.unescape(raw = v))
		} catch (ex) {
			exception = ex
		}
	}

	NameExpression percentize(Parser p) {
		new NameExpression(raw)
	}

	IKismetObject<String> evaluate(Memory c) {
		if (null == exception) value
		else throw exception
	}

	TypedStringExpression type(TypedContext tc, TypeBound preferred) {
		final str = evaluate(null).inner()
		if (!preferred.relation(Strings.STRING_TYPE).assignableFrom)
			throw new UnexpectedTypeException("Preferred non-string type $preferred for literal with string \"${StringEscaper.escape(str)}\"")
		new TypedStringExpression(str).<TypedStringExpression>withOriginal(this)
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

	StaticExpression(T ex = null, Memory c) {
		this(ex, ex.evaluate(c))
	}

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		new TypedConstantExpression(preferred.type, value).withOriginal(this)
	}
}

@CompileStatic
@Singleton(property = 'INSTANCE')
class NoExpression extends Expression {
	String repr() { "noexpr" }

	IKismetObject evaluate(Memory c) {
		Kismet.NULL
	}

	TypedNoExpression type(TypedContext tc, TypeBound preferred) {
		TypedNoExpression.INSTANCE
	}
}