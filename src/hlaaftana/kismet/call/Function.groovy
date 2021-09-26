package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.exceptions.CheckFailedException
import hlaaftana.kismet.exceptions.UndefinedSymbolException
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.lib.CollectionsIterators
import hlaaftana.kismet.lib.Functions
import hlaaftana.kismet.lib.Syntax
import hlaaftana.kismet.lib.Types
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.GenericType
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeBound
import hlaaftana.kismet.type.TypeRelation
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetTuple
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.RuntimeMemory

import static hlaaftana.kismet.call.ExprBuilder.block
import static hlaaftana.kismet.call.ExprBuilder.name

@CompileStatic
abstract class Function implements KismetCallable, IKismetObject<Function>, Nameable {
	boolean pure
	Type argumentTypes
	Type returnType
	String name

	boolean checkArgumentTypes(IKismetObject... args) {
		null == argumentTypes || argumentTypes.check(new KismetTuple(args))
	}

	static IKismetObject tryCall(Memory c, String name, IKismetObject[] args) {
		final v = c.get(name)
		if (v instanceof Function) v.call(args)
		else {
			def a = new IKismetObject[2]
			a[0] = v
			a[1] = new KismetTuple(args)
			((Function) c.get('call')).call(a)
		}
	}

	static IKismetObject callOrNull(Memory c, String name, IKismetObject[] args) {
		final v = c.get(name)
		if (v instanceof Function) v.call(args)
		else null
	}

	static final Function IDENTITY = new Function() {
		{
			setPure(true)
		}

		@CompileStatic
		IKismetObject call(IKismetObject... args) {
			args[0]
		}
	}
	static final Function NOP = new Function() {
		{
			setPure(true)
		}

		@CompileStatic
		IKismetObject call(IKismetObject... args) { Kismet.NULL }
	}

	IKismetObject call(Memory c, Expression... args) {
		final arr = new IKismetObject[args.length]
		for (int i = 0; i < arr.length; ++i) {
			arr[i] = args[i].evaluate(c)
		}
		this.call(arr)
	}

	abstract IKismetObject call(IKismetObject... args)

	Function plus(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('plus', [b(args).inner()] as Object[]))
			}
		}
	}

	Function minus(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('minus', [b(args).inner()] as Object[]))
			}
		}
	}

	Function multiply(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('multiply', [b(args).inner()] as Object[]))
			}
		}
	}

	Function div(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('div', [b(args).inner()]) as Object[])
			}
		}
	}

	Function mod(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('mod', [b(args).inner()] as Object[]))
			}
		}
	}

	Function pow(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('pow', [b(args).inner()] as Object[]))
			}
		}
	}

	Function pow(final int times) {
		Function t
		if (times < 0) {
			if (!(this instanceof Invertable))
				throw new IllegalArgumentException('Function does not implement Invertable')
			t = ((Invertable) this).inverse
		} else t = this
		final m = t
		final a = Math.abs(times)
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				if (a == 0) args[0]
				else {
					def r = m.call(args)
					for (int i = 1; i < a; ++i) {
						r = m.call(r)
					}
					r
				}
			}
		}
	}

	Function inner() { this }

	Closure toClosure() {
		return { ...args ->
			def a = new IKismetObject[args.length]
			for (int i = 0; i < a.length; ++i) a[i] = Kismet.model(args[i])
			this.call(a)
		}
	}

	String toString() { name ?: super.toString() }
}

@CompileStatic
interface Invertable {
	Function getInverse()
}

@CompileStatic
interface Nameable {
	String getName()
}

@CompileStatic
class Arguments {
	static final Arguments EMPTY = new Arguments(null)
	boolean enforceLength
	List<Parameter> parameters = new ArrayList<>()
	Parameter result

	Arguments(Collection<Expression> p) {
		final any = null != p && !p.empty
		enforceLength = any
		if (any) parse(p)
	}

	void parse(Collection<Expression> params) {
		for (e in params) {
			if (e instanceof NameExpression) parameters.add(new Parameter(((NameExpression) e).text, null))
			else if (e instanceof StringExpression)
				parameters.add(new Parameter(((StringExpression) e).value.inner(), null))
			else if (e instanceof BlockExpression) parse(e.members)
			else if (e instanceof CallExpression) parseCall(e.members)
			else parseCall([(Expression) e])
		}
	}

	void parseExpr(Parameter p = new Parameter(), Expression e) {
		if (e instanceof NameExpression) parameters.add(new Parameter(((NameExpression) e).text, null))
		else if (e instanceof StringExpression)
			parameters.add(new Parameter(((StringExpression) e).value.inner(), null))
		else if (e instanceof BlockExpression) for (x in e.members) parseExpr(p.clone(), x)
		else if (e instanceof CallExpression) parseCall(p, e.members)
		else parseCall(p, [(Expression) e])
	}

	void parseCall(Parameter p = new Parameter(), Collection<Expression> exprs) {
		BlockExpression block = null
		if (exprs.size() == 2 && exprs[0] instanceof NameExpression &&
				((NameExpression) exprs[0]).text == 'returns') {
			final sec = exprs[1]
			if (sec instanceof ColonExpression) {
				final left = sec.left
				if (left instanceof NameExpression) {
					result = new Parameter()
					result.name = left.text
					result.typeExpression = sec.right
				} else throw new UnexpectedSyntaxException('left hand side of return parameter colon expression has to be the result variable name')
			} else {
				result = new Parameter()
				result.typeExpression = sec
			}
			return
		}
		for (e in exprs) {
			if (e instanceof NameExpression) p.name = e.text
			else if (e instanceof StringExpression) p.name = e.value.inner()
			else if (e instanceof BlockExpression) block = e
			else if (e instanceof ColonExpression) {
				p.name = Syntax.toAtom(e.left)
				if (null == p.name) throw new UnexpectedSyntaxException("Weird left hand side of colon expression " +
						"for method parameter " + e.left)
				p.typeExpression = e.right
			} else throw new UnexpectedSyntaxException('Weird argument expression ' + e)
		}
		if (null == block) parameters.add(p)
		else for (c in block.members) parseCall(p.clone(), ((CallExpression) c).members)
	}

	void setArgs(Memory c, IKismetObject[] args) {
		c.set('_all', new KismetTuple(args))
		if (enforceLength && parameters.size() != args.length)
			throw new CheckFailedException("Got argument length $args.length which wasn't ${parameters.size()}")
		for (int i = 0; i < parameters.size(); ++i) {
			c.set(parameters.get(i).name, args[i])
		}
	}

	Type[] fill(TypedContext tc) {
		if (enforceLength) {
			def pt = new Type[parameters.size()]
			for (int i = 0; i < parameters.size(); ++i) {
				final p = parameters.get(i)
				tc.addVariable(p.name, pt[i] = p.getType(tc))
			}
			tc.addVariable('_all', new TupleType(pt))
			pt
		} else {
			tc.addVariable('_all', CollectionsIterators.LIST_TYPE)
			null
		}
	}

	static class Parameter {
		String name
		Expression typeExpression

		Parameter() {}

		Parameter(String n, Expression te) {
			name = n
			typeExpression = te
		}

		Type getType(TypedContext tc) {
			if (null == typeExpression) return Type.ANY
			def expr = typeExpression.type(tc)
			if (!expr.type.relation(Types.META_TYPE).assignableTo) expr.type
			else (Type) expr.instruction.evaluate(tc).inner()
		}

		Parameter clone() { new Parameter(name, typeExpression) }
	}
}

@CompileStatic
class KismetFunction extends Function {
	Block block
	Arguments arguments = Arguments.EMPTY
	String name = 'anonymous'

	IKismetObject call(IKismetObject... args) {
		Block c = block.child()
		arguments.setArgs(c.context, args)
		c()
	}

	TypeRelation checkType(Type result, Type... args) {
		TypeRelation.equal()
	}
}

@CompileStatic
class FunctionDefineExpression extends Expression {
	String name
	Arguments arguments
	Expression body

	FunctionDefineExpression(Expression[] args) {
		final first = args[0]
		final f = first.members ?: [first]
		name = ((NameExpression) f[0]).text
		arguments = new Arguments(f.tail())
		body = args.length == 1 ? null : block(args.tail())
	}

	FunctionDefineExpression(String name, Arguments arguments, Expression body) {
		this.name = name
		this.arguments = arguments
		this.body = body
	}

	IKismetObject evaluate(Memory c) {
		def result = new KismetFunction()
		result.name = name
		result.arguments = arguments
		result.block = new Block(body, new Context(c))
		c.set(name, result)
		result
	}

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		def fnb = tc.child()
		fnb.label = "function " + name
		def args = arguments.fill(fnb)
		def typ = Functions.func(Type.ANY, args)
		def expr = body
		if (null != arguments.result) {
			def returnType = arguments.result.getType(fnb)
			typ.arguments[1] = returnType
			final resultVar = arguments.result.name
			if (null != resultVar) {
				fnb.addVariable(resultVar, returnType)
				expr = block(expr, name(resultVar))
			}
		}
		def var = tc.addVariable(name, typ)
		def block = expr.type(fnb, new TypeBound(typ.arguments[1]))
		if (null == arguments.result) {
			typ.arguments[1] = block.type
		}
		new VariableSetExpression(var.ref(), new BasicTypedExpression(typ, new Instruction() {
			final Instruction inner = block.instruction
			final int stackSize = fnb.size()

			IKismetObject evaluate(Memory context) {
				def res = new TypedFunction([context] as Memory[], inner, stackSize, name)
				res.argumentTypes = typ.arguments[0]
				res.returnType = typ.arguments[1]
				res
			}

			String toString() { "func $stackSize:\n  $inner" }
		}, false))
	}
}

@CompileStatic
class FunctionExpression extends Expression {
	String name
	Arguments arguments
	Expression expression

	FunctionExpression(boolean named, Expression[] args) {
		final first = args[0]
		final f = first instanceof ColonExpression ? [(Expression) first] : first.members ?: [first]
		def len = args.length
		if (named) {
			name = ((NameExpression) f[0]).text
			arguments = new Arguments(f.tail())
			len -= 2
		} else if (args.length > 1) {
			arguments = new Arguments(f)
			--len
		} else {
			arguments = new Arguments(null)
		}
		expression = len > 1 ? block(args.tail()) : args[args.length - 1]
	}

	FunctionExpression(String name = null, Arguments arguments, Expression expr) {
		this.name = name
		this.arguments = arguments
		this.expression = expr
	}

	FunctionExpression() {}

	IKismetObject evaluate(Memory c) {
		def result = new KismetFunction()
		result.name = name
		result.arguments = arguments
		result.block = new Block(expression, new Context(c))
		result
	}

	TypedExpression type(TypedContext tc, TypeBound preferred) {
		def fnb = tc.child()
		fnb.label = null == name ? "anonymous function" : "function " + name
		def args = arguments.fill(fnb)
		def nullArgs = null == args
		def typ = nullArgs ?
			new GenericType(Functions.FUNCTION_TYPE, TupleType.BASE, Type.ANY) :
				Functions.func(Type.ANY, args)
		def expr = expression
		if (null != arguments.result) {
			def returnType = arguments.result.getType(fnb)
			typ.arguments[1] = returnType
			final resultVar = arguments.result.name
			if (null != resultVar) {
				fnb.addVariable(resultVar, returnType)
				expr = block(expr, name(resultVar))
			}
		}
		def block = expr.type(fnb, new TypeBound(typ.arguments[1]))
		final fnbSize = fnb.size()
		final instr = block.instruction
		new BasicTypedExpression(typ, new Instruction() {
			final Instruction inner = instr
			final int stackSize = fnbSize

			IKismetObject evaluate(Memory context) {
				def res = new TypedFunction([context] as Memory[], inner, stackSize, name, nullArgs)
				res.argumentTypes = typ.arguments[0]
				res.returnType = typ.arguments[1]
				res
			}
		}, false)
	}
}

@CompileStatic
class TypedFunction extends Function {
	Memory[] context
	Instruction instruction
	boolean noArgs
	int stackSize

	TypedFunction(Memory[] context, Instruction instruction, int stackSize, String name, boolean noArgs = false) {
		this.context = context
		this.instruction = instruction
		this.stackSize = stackSize
		this.name = name
		this.noArgs = noArgs
	}

	IKismetObject call(IKismetObject... args) {
		def mem = new RuntimeMemory(context, stackSize)
		System.arraycopy(args, 0, mem.memory, 0, args.length)
		mem.memory[noArgs ? 0 : args.length] = new KismetTuple(args)
		instruction.evaluate(mem)
	}

	String toString() { "typed function $name" }
}

@CompileStatic
class GroovyFunction extends Function {
	boolean convert = true
	Closure x

	GroovyFunction(boolean convert = true, Closure x) {
		this.convert = convert
		this.x = x
	}

	IKismetObject call(IKismetObject... args) {
		Kismet.model(cc(convert ? args*.inner() as Object[] : args))
	}

	def cc(... args) {
		null == args ? x.call() : x.invokeMethod('call', args)
	}
}

@CompileStatic
class OverloadDispatchFunction extends Function {
	Function[] overloads

	OverloadDispatchFunction(Function[] overloads) {
		this.overloads = overloads
	}

	IKismetObject call(IKismetObject... args) {
		/*println overloads
		println args*/
		for (final o : overloads) {
			if (o.checkArgumentTypes(args)) return o.call(args)
		}
		throw new UndefinedSymbolException('no overloads accept arguments ' + args)
	}
}
