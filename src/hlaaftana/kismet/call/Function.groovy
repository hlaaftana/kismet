package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.exceptions.CheckFailedException
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.Prelude
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.GenericType
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.RuntimeMemory

@CompileStatic
abstract class Function implements KismetCallable, IKismetObject<Function> {
	boolean pure
	int precedence

	static IKismetObject tryCall(Memory c, String name, IKismetObject[] args) {
		final v = c.get(name)
		if (v instanceof Function) v.call(args)
		else {
			def a = new IKismetObject[args.length + 1]
			a[0] = v
			for (int i = 0; i < a.length; ++i) a[i + 1] = args[i]
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

	IKismetObject call(Context c, Expression... args) {
		final arr = new IKismetObject[args.length]
		for (int i = 0; i < arr.length; ++i) {
			arr[i] = args[i].evaluate(c)
		}
		call(arr)
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
class KismetFunction extends Function implements Nameable {
	Block block
	Arguments arguments = Arguments.EMPTY
	String name = 'anonymous'

	KismetFunction(Context c, boolean named, Expression[] args) {
		final first = args[0]
		if (args.length == 1) {
			block = c.child(first)
		} else {
			final f = first.members ?: [first]
			if (named) {
				name = ((NameExpression) f[0]).text
				arguments = new Arguments(f.tail())
			} else {
				arguments = new Arguments(f)
			}
			block = c.childBlock(args.tail())
		}
	}

	KismetFunction(Arguments arguments, Block block) {
		this.arguments = arguments
		this.block = block
	}

	KismetFunction() {}

	IKismetObject call(IKismetObject... args) {
		Block c = block.child()
		arguments.setArgs(c.context, args)
		c()
	}
}

@CompileStatic
class FunctionDefineExpression extends Expression {
	String name
	Arguments arguments
	Expression expression

	FunctionDefineExpression(Expression[] args) {
		final first = args[0]
		final f = first.members ?: [first]
		name = ((NameExpression) f[0]).text
		arguments = new Arguments(f.tail())
		expression = args.length == 1 ? null : new BlockExpression(args.tail().toList())
	}

	FunctionDefineExpression(String name = null, Arguments arguments, Expression expr) {
		this.name = name
		this.arguments = arguments
		this.expression = expr
	}

	FunctionDefineExpression() {}

	IKismetObject evaluate(Context c) {
		def result = new KismetFunction()
		result.name = name
		result.arguments = arguments
		result.block = c.child(expression)
		c.set(name, result)
		result
	}

	TypedExpression type(TypedContext tc, Type preferred) {
		def fnb = tc.child()
		def block = expression.type(fnb, preferred)
		final typ = Prelude.func(block.type, arguments.fill(fnb))
		final var = tc.addVariable(name, typ)
		new VariableSetExpression(var.ref(), new BasicTypedExpression(typ, new Instruction() {
			final Instruction inner = block.instruction
			final int stackSize = fnb.size()

			IKismetObject evaluate(Memory context) {
				new TypedFunction([context] as Memory[], inner, stackSize, name)
			}
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
		final f = first.members ?: [first]
		if (named) {
			name = ((NameExpression) f[0]).text
			arguments = new Arguments(f.tail())
		} else {
			arguments = new Arguments(f)
		}
		expression = args.length == 1 ? null : new BlockExpression(args.tail().toList())
	}

	FunctionExpression(String name = null, Arguments arguments, Expression expr) {
		this.name = name
		this.arguments = arguments
		this.expression = expr
	}

	FunctionExpression() {}

	IKismetObject evaluate(Context c) {
		def result = new KismetFunction()
		result.name = name
		result.arguments = arguments
		result.block = c.child(expression)
		result
	}

	TypedExpression type(TypedContext tc, Type preferred) {
		def fnb = tc.child()
		def block = expression.type(fnb, preferred)
		final typ = Prelude.func(block.type, arguments.fill(fnb))
		new BasicTypedExpression(typ, new Instruction() {
			final Instruction inner = block.instruction
			final int stackSize = fnb.size()

			IKismetObject evaluate(Memory context) {
				new TypedFunction([context] as Memory[], inner, stackSize, name)
			}
		}, false)
	}
}

@CompileStatic
class Arguments {
	static final Arguments EMPTY = new Arguments(null)
	boolean enforceLength
	List<Parameter> parameters = new ArrayList<>()

	Arguments(Collection<Expression> p) {
		final any = null != p && !p.empty
		enforceLength = any
		if (any) parse(p)
	}

	void parse(Collection<Expression> params) {
		for (e in params) {
			if (e instanceof NameExpression) parameters.add(new Parameter(name: ((NameExpression) e).text))
			else if (e instanceof StringExpression)
				parameters.add(new Parameter(name: ((StringExpression) e).value.inner()))
			else if (e instanceof BlockExpression) parse(e.members)
			else if (e instanceof CallExpression) parseCall(e.members)
			else parseCall([(Expression) e])
		}
	}

	void parseExpr(Parameter p = new Parameter(), Expression e) {
		if (e instanceof NameExpression) parameters.add(new Parameter(name: ((NameExpression) e).text))
		else if (e instanceof StringExpression)
			parameters.add(new Parameter(name: ((StringExpression) e).value.inner()))
		else if (e instanceof BlockExpression) for (x in e.members) parseExpr(p.clone(), x)
		else if (e instanceof CallExpression) parseCall(p, e.members)
		else parseCall(p, [(Expression) e])
	}

	void parseCall(Parameter p = new Parameter(), Collection<Expression> exprs) {
		BlockExpression block = null
		for (e in exprs) {
			if (e instanceof NameExpression) p.name = e.text
			else if (e instanceof StringExpression) p.name = e.value.inner()
			else if (e instanceof BlockExpression) block = e
			else if (e instanceof ColonExpression) {
				p.name = Prelude.toAtom(e.left)
				if (null == p.name) throw new UnexpectedSyntaxException("Weird left hand side of colon expression " +
					"for method parameter " + e.left)
				p.typeExpression = e.right
			} else throw new UnexpectedSyntaxException('Weird argument expression ' + e)
		}
		if (null == block) parameters.add(p)
		else for (c in block.members) parseCall(p.clone(), ((CallExpression) c).members)
	}

	void setArgs(Context c, IKismetObject[] args) {
		def args2 = new Object[args.length]
		System.arraycopy(args, 0, args2, 0, args2.length)
		final lis = new Tuple(args2)
		c.set('_all', Kismet.model(lis))
		if (enforceLength && parameters.size() != args.length)
			throw new CheckFailedException("Got argument length $args.length which wasn't ${parameters.size()}")
		for (int i = 0; i < parameters.size(); ++i) {
			c.set(parameters.get(i).name, args[i])
		}
	}

	Type[] fill(TypedContext tc) {
		def pt = new Type[parameters.size()]
		for (int i = 0; i < parameters.size(); ++i) {
			final p = parameters.get(i)
			tc.addVariable(p.name, pt[i] = p.getType(tc))
		}
		tc.addVariable('_all', new TupleType(pt))
		pt
	}

	static class Parameter {
		String name
		Expression typeExpression

		Type getType(TypedContext tc) {
			def expr = typeExpression.type(tc)
			if (!expr.type.relation(Prelude.META_TYPE).assignableTo) expr.type
			else (Type) expr.instruction.evaluate(tc).inner()
		}

		Parameter clone() { new Parameter(name: name, typeExpression: typeExpression) }
	}
}

@CompileStatic
class TypedFunction extends Function implements Nameable {
	Memory[] context
	Instruction instruction
	int stackSize
	String name

	TypedFunction(Memory[] context, Instruction instruction, int stackSize, String name) {
		this.context = context
		this.instruction = instruction
		this.stackSize = stackSize
		this.name = name
	}

	IKismetObject call(IKismetObject... args) {
		def mem = new RuntimeMemory(context, stackSize)
		System.arraycopy(args, 0, mem.memory, 0, args.length)
		mem.memory[args.length] = Kismet.model(new Tuple(args))
		instruction.evaluate(mem)
	}

	String toString() { "typed function $name" }
}

/*class Arguments {
	static final Arguments EMPTY = new Arguments(null)
	boolean doDollars, enforceLength
	List<Parameter> parameters = []
	int last = 0

	Arguments(Collection<Expression> p) {
		final any = null != p && !p.empty
		enforceLength = any
		doDollars = !any
		if (any) parse(p)
	}

	def parse(Collection<Expression> params) {
		for (e in params) {
			if (e instanceof NameExpression) parameters.add(new Parameter(name: ((NameExpression) e).text, index: last++))
			else if (e instanceof StringExpression)
				parameters.add(new Parameter(name: ((StringExpression) e).value.inner(), index: last++))
			else if (e instanceof BlockExpression) parse(((BlockExpression) e).members)
			else if (e instanceof CallExpression) parseCall(((CallExpression) e).members)
			else println "ignored expression in functoin arguments: $e"
		}
	}

	// rewrite better please
	def parseCall(Map p = [:], Collection<Expression> exprs) {
		p = new HashMap(p)
		BlockExpression block = null
		for (e in exprs) {
			if (e instanceof NameExpression) p.name = ((NameExpression) e).text
			else if (e instanceof StringExpression) p.name = ((StringExpression) e).value.inner()
			else if (e instanceof BlockExpression) block = (BlockExpression) e
			else if (e instanceof NumberExpression) p.index = ((NumberExpression) e).value.inner().intValue()
			else if (e instanceof CallExpression) {
				CallExpression b = (CallExpression) e
				if (b.callValue instanceof NameExpression) {
					def xx = ((NameExpression) b.callValue).text
					if (xx == 'slice') {
						// in the words of intellij, "too many negations"
						if (b.arguments.empty)
							p.slice = (last = 0) - 1
						else {
							int i = b.arguments[0] instanceof NumberExpression ?
									((NumberExpression) b.arguments[0]).value.inner().intValue() + 1 :
									b.arguments[0] instanceof NameExpression ?
											Integer.valueOf(((NameExpression) b.arguments[0]).text) :
											0
							last += (p.slice = i) - 1
						}
					} else if (xx == 'index') {
						if (b.arguments.empty)
							p.index = last = 0
						else {
							int i = b.arguments[0] instanceof NumberExpression ?
									((NumberExpression) b.arguments[0]).value.inner().intValue() + 1 :
									b.arguments[0] instanceof NameExpression ?
											Integer.valueOf(((NameExpression) b.arguments[0]).text) + 1 :
											0
							p.index = last = i
						}
					} else if (xx == 'check' || xx == 'transform') {
						def n = xx + 's', v = p.get(n)
						if (null == v) p.put n, b.arguments
						else ((List) v).addAll(b.arguments)
					} else if (null == p.topLevelChecks) p.topLevelChecks = [b]
					else ((List) p.topLevelChecks).add(b)
				} else if (b.callValue instanceof CallExpression) {
					CallExpression d = (CallExpression) b.callValue
					parseCall(p + [slice: -1, index: 0], d.members)
					for (c in d.arguments) {
						if (c instanceof NameExpression) {
							def t = ((NameExpression) c).text
							if (t == '$') doDollars = !doDollars
							else if (t == '#') enforceLength = !enforceLength
							else throw new UnexpectedSyntaxException('My bad but i dont know how to handle path '
										+ t + ' in meta-argument call')
						} else throw new UnexpectedSyntaxException('My bad but i dont know how to handle ' +
								c.repr() + ' in meta-argument call')
					}
				} else throw new UnexpectedSyntaxException('Call in function arguments with a non-path-or-call function callValue?')
			}
		}
		if (!p.containsKey('index')) p.index = last++
		if (null == block) {
			Parameter x = new Parameter()
			x.index = (int) p.index
			x.name = (p.name ?: "\$$x.index").toString()
			x.slice = (int) (null == p.slice ? 0 : p.slice)
			x.transforms = (List<Expression>) (null == p.transforms ? [] : p.transforms)
			x.checks = (List<Expression>) (null == p.checks ? [] : p.checks)
			if (p.containsKey('topLevelChecks')) x.topLevelChecks = (List<CallExpression>) p.topLevelChecks
			parameters.add(x)
		} else for (c in block.members) parseCall(p, ((CallExpression) c).members)
	}

	void setArgs(Context c, IKismetObject[] args) {
		def args2 = new Object[args.length]
		System.arraycopy(args, 0, args2, 0, args2.length)
		final lis = new Tuple(args2)
		if (doDollars) {
			for (int it = 0; it < args.length; ++it) {
				c.set('$'.concat(String.valueOf(it)), args[it])
			}
			c.set('$all', Kismet.model(lis))
		}
		if (enforceLength) {
			boolean variadic = false
			int max = 0
			for (p in parameters) {
				if (p.index + p.slice + 1 > max) max = p.index + p.slice + 1
				if (p.slice < 0) variadic = true
			}
			if (variadic ? args.length < max : max != args.length)
				throw new CheckFailedException("Got argument length $args.length which wasn't " +
						(variadic ? '>= ' : '== ') + max)
		}
		for (p in parameters) {
			def value = p.slice == 0 ? args[p.index < 0 ? args.length + p.index : p.index] :
					Kismet.model(lis[p.index..(p.slice < 0 ? p.slice : p.index + p.slice)])
			for (t in p.transforms) {
				c.set(p.name, value)
				value = t.evaluate(c)
			}
			c.set(p.name, value)
		}
		for (p in parameters)
			for (ch in p.checks)
				if (!ch.evaluate(c))
					throw new CheckFailedException("Check $ch failed for $p.name ${c.get(p.name)}")
	}

	static class Parameter {
		String name
		List<Expression> checks = [], transforms = []
		int index, slice

		void setTopLevelChecks(List<CallExpression> r) {
			for (x in r) {
				def n = ((NameExpression) x.callValue).text
				def exprs = new ArrayList<Expression>()
				exprs.add new NameExpression(Prelude.isAlpha(n) ? n + '?' : n)
				exprs.add new NameExpression(name)
				exprs.addAll x.arguments
				checks.add new CallExpression(exprs)
			}
		}
	}
}*/

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
