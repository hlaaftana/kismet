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
abstract class PathStepExpression extends Expression {
	Expression root
	abstract Expression getRight()
	int size() { 2 }
	List<Expression> getMembers() { [root, right] }
	abstract IKismetObject evaluateSet(Memory c, IKismetObject value)
	abstract TypedExpression type(TypedContext ctx, TypeBound preferred)
	abstract TypedExpression typeSet(TypedContext ctx, TypedExpression value, TypeBound preferred)
	TypedExpression typeSet(TypedContext tc, TypedExpression value) {
		typeSet(tc, value, +Type.ANY)
	}
}

@CompileStatic
class PathStepSetExpression extends Expression {
	PathStepExpression step
	Expression value

	PathStepSetExpression(PathStepExpression step, Expression value) {
		this.step = step
		this.value = value
	}

	IKismetObject evaluate(Memory c) {
		step.evaluateSet(c, value.evaluate(c))
	}

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		step.typeSet(tc, value.type(tc, preferred), preferred)
	}

	String toString() { "$step = $value" }
}

@CompileStatic
class PropertyExpression extends PathStepExpression {
	String name

	PropertyExpression(Expression root, String name) {
		this.root = root
		this.name = name
	}

	Expression getRight() { name(name) }
	Expression join(List<Expression> exprs) { new PropertyExpression(exprs[0], exprs[1].toString()) }

	IKismetObject evaluate(Memory c) {
		def val = root.evaluate(c)
		final n = Function.callOrNull(c, getterName(name), val)
		if (null != n) return n
		final n2 = Function.callOrNull(c, name, val)
		if (null != n2) return n2
		Function.tryCall(c, '.property', val, new KismetString(name))
	}

	IKismetObject evaluateSet(Memory c, IKismetObject value) {
		def val = root.evaluate(c)
		final n = Function.callOrNull(c, setterName(name), val, value)
		if (null != n) return n
		Function.tryCall(c, '.property=', val, new KismetString(name), value)
	}

	TypedExpression type(TypedContext ctx, TypeBound preferred) {
		def val = root.type(ctx)
		try {
			return call(name(getterName(name)), new TypedWrapperExpression(val)).type(ctx, preferred)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {}
		try {
			return call(name(name), new TypedWrapperExpression(val)).type(ctx, preferred)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {}
		try {
			return call(name('.property'), new TypedWrapperExpression(val), string(name)).type(ctx, preferred)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {
			throw new UndefinedSymbolException('No property ' + name +
				' fitting type bound ' + preferred +
				' for value ' + root + ' with type ' + val.type)
		}
	}

	TypedExpression typeSet(TypedContext ctx, TypedExpression value, TypeBound preferred) {
		def val = root.type(ctx)
		try {
			return call(name(setterName(name)), new TypedWrapperExpression(val),
				new TypedWrapperExpression(value)).type(ctx, preferred)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {}
		try {
			return call(name('.property='), new TypedWrapperExpression(val), string(name),
				new TypedWrapperExpression(value)).type(ctx, preferred)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {
			throw new UndefinedSymbolException('No property ' + name +
				' fitting type bound ' + preferred +
				' for value ' + root + ' with type ' + val.type)
		}
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

	String toString() { root.toString() + '.' + name }
}

@CompileStatic
class SubscriptExpression extends PathStepExpression {
	Expression expression

	SubscriptExpression(Expression root, Expression expression) {
		this.root = root
		this.expression = expression
	}

	Expression getRight() { expression }
	Expression join(List<Expression> exprs) { new SubscriptExpression(exprs[0], exprs[1]) }

	IKismetObject evaluate(Memory c) {
		Function.tryCall(c, '.[]', root.evaluate(c), expression.evaluate(c))
	}

	IKismetObject evaluateSet(Memory c, IKismetObject value) {
		Function.tryCall(c, '.[]=', root.evaluate(c), expression.evaluate(c), value)
	}

	String toString() { root.toString() + ".[$expression]" }

	TypedExpression type(TypedContext ctx, TypeBound preferred) {
		call(name('.[]'), root, expression).type(ctx, preferred)
	}

	TypedExpression typeSet(TypedContext ctx, TypedExpression value, TypeBound preferred) {
		call(name('.[]='), root, expression, new TypedWrapperExpression(value))
			.type(ctx, preferred)
	}
}

@CompileStatic
class EnterExpression extends PathStepExpression {
	Expression expression

	EnterExpression(Expression root, Expression expression) {
		this.root = root
		this.expression = expression
	}

	Expression getRight() { expression }
	Expression join(List<Expression> exprs) { new EnterExpression(exprs[0], exprs[1]) }

	IKismetObject evaluate(Memory c) {
		def ec = new EnterContext((Context) c)
		ec.set('it', ec.object = root.evaluate(c))
		expression.evaluate(ec)
	}

	IKismetObject evaluateSet(Memory c, IKismetObject value) {
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

	TypedExpression type(TypedContext ctx, TypeBound preferred) {
		throw new UnsupportedOperationException('unsupported')
	}

	TypedExpression typeSet(TypedContext ctx, TypedExpression value, TypeBound preferred) {
		throw new UnsupportedOperationException('unsupported')
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

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		try {
			new VariableExpression(tc.findThrow(text, preferred)).<VariableExpression>withOriginal(this)
		} catch (UndefinedSymbolException ex) {
			if (preferred.type.relation(Functions.FUNCTION_TYPE).assignableTo) {
				def overloads = tc.findOverloads(text, +preferred)
				if (!overloads.empty) {
					def os = new TypedExpression[overloads.size()]
					for (int i = 0; i < os.length; ++i) os[i] = new VariableExpression(overloads.get(i))
					new OverloadResolverExpression(os)
				} else throw ex
			} else throw ex
		}
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
		TypedExpression cv

		// template
		try {
			cv = callValue.type(tc, -Functions.TEMPLATE_TYPE)
		} catch (UnexpectedTypeException | UndefinedSymbolException ignored) {}
		if (null != cv && cv.type == Functions.TEMPLATE_TYPE)
			return ((Template) cv.instruction.evaluate(tc))
					.transform(null,
						arguments.<Expression>toArray(
							new Expression[arguments.size()]))
					.type(tc, preferred)
					.withOriginal(this)

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
			return x
				.transform(tc, args)
				.withOriginal(this)
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

		String toString() { "[" + members.join(', ') + "]" }

		Type getType() {
			def types = new ArrayList<Type>(members.length)
			for (final m : members) {
				types.add(m.type)
				//throw new UnexpectedTypeException('Type ' + m.type + ' is incompatible with list with bound ' + bound)
			}
			new GenericType(CollectionsIterators.LIST_TYPE, new UnionType(types).reduced())
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

			String toString() { "[" + members.join(', ') + "]" }

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

		String toString() { "tuple(${members.join(', ')})" }

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

			String toString() { "tuple(${members.join(', ')})" }

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

		String toString() { "{${members.join(', ')}}" }

		Type getType() {
			def types = new ArrayList<Type>(members.length)
			for (final m : members) {
				types.add(m.type)
				//throw new UnexpectedTypeException('Type ' + m.type + ' is incompatible with list with bound ' + bound)
			}
			new GenericType(CollectionsIterators.SET_TYPE, new UnionType(types).reduced())
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

			String toString() { "{${members.join(', ')}}" }

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
			def keyTypes = new ArrayList<Type>(keys.length)
			def valueTypes = new ArrayList<Type>(values.length)
			for (int i = 0; i < keys.length; ++i) {
				keyTypes.add(keys[i].type)
				valueTypes.add(values[i].type)
				//throw new UnexpectedTypeException('Type ' + m.type + ' is incompatible with list with bound ' + bound)
			}
			new GenericType(CollectionsIterators.MAP_TYPE,
				new UnionType(keyTypes).reduced(),
				new UnionType(valueTypes).reduced())
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
		else if (left instanceof PathStepExpression) {
			left.evaluateSet(c, right.evaluate(c))
		} else throw new UnexpectedSyntaxException("Left hand side of colon $left")
		value
	}

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		def val = right.type(tc, preferred) // no need to check if it satisfies because it will check itself
		def atom = Syntax.toAtom(left)
		if (null != atom) {
			def var = AssignmentType.ASSIGN.set(tc, atom, val.type)
			new VariableSetExpression(var, val).withOriginal(this)
		} else if (left instanceof PathStepExpression) {
			left.typeSet(tc, val).withOriginal(this)
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