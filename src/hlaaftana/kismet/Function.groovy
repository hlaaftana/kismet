package hlaaftana.kismet

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.parser.NameExpression
import hlaaftana.kismet.parser.BlockExpression
import hlaaftana.kismet.parser.CallExpression
import hlaaftana.kismet.parser.Expression
import hlaaftana.kismet.parser.NumberExpression
import hlaaftana.kismet.parser.StringExpression

@CompileStatic
abstract class Function implements KismetCallable {
	boolean pure
	int precedence

	static final Function IDENTITY = new Function() {
		{ setPure(true) }

		@CompileStatic
		IKismetObject call(IKismetObject... args) {
			args[0]
		}
	}
	static final Function NOP = new Function() {
		{ setPure(true) }

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

	def plus(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('plus', b(args).inner()))
			}
		}
	}

	def minus(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('minus', b(args).inner()))
			}
		}
	}

	def multiply(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('multiply', b(args).inner()))
			}
		}
	}

	def div(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('div', b(args).inner()))
			}
		}
	}

	def mod(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('mod', b(args).inner()))
			}
		}
	}

	def pow(final Function b) {
		new Function() {
			@CompileStatic
			IKismetObject call(IKismetObject... args) {
				Kismet.model(Function.this.call(args).inner().invokeMethod('pow', b(args).inner()))
			}
		}
	}

	def pow(final int times) {
		Function t
		if (times < 0) {
			if (!(this instanceof Invertable))
				throw new IllegalArgumentException('Function does not implement Inversable')
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
class KismetMethod extends Function implements Nameable {
	String name = 'anonymousMethod'
	List<KismetFunction> functions = []

	IKismetObject call(IKismetObject... args) {
		for (f in functions) {
			Context c
			try {
				c = f.block.context
				f.arguments.setArgs(c, args)
			} catch (CheckFailedException ignored) {
				continue
			}
			return f.block.expression.evaluate(c)
		}
		throw new CheckFailedException("No method matched for $name with arguments $args")
	}
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
			final f = first instanceof CallExpression ? ((CallExpression) first).expressions :
					first instanceof BlockExpression ? ((BlockExpression) first).content :
							[first]
			if (named) {
				name = ((NameExpression) f[0]).text
				arguments = new Arguments(f.tail())
			} else {
				arguments = new Arguments(f)
			}
			block = c.child(args.tail())
		}
	}

	KismetFunction() {}

	IKismetObject call(IKismetObject... args){
		Block c = block.child()
		arguments.setArgs(c.context, args)
		c()
	}

	static class Arguments {
		static final Arguments EMPTY = new Arguments(null)
		boolean doDollars
		boolean enforceLength
		List<Parameter> parameters = []
		int last = 0

		Arguments(List<Expression> p) {
			final any = null == p ? false : !p.empty
			enforceLength = any
			doDollars = !any
			if (any) parse(p)
		}

		def parse(List<Expression> params) {
			for (e in params) {
				if (e instanceof NameExpression) parameters.add(new Parameter(name: ((NameExpression) e).text, index: last++))
				else if (e instanceof StringExpression)
					  parameters.add(new Parameter(name: ((StringExpression) e).value.inner(), index: last++))
				else if (e instanceof BlockExpression) parse(((BlockExpression) e).content)
				else if (e instanceof CallExpression) parseCall(((CallExpression) e).expressions)
			}
		}

		def parseCall(Map p = [:], List<Expression> exprs) {
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
							if (!b.arguments.empty) {
								int i = b.arguments[0] instanceof NumberExpression ?
										((NumberExpression) b.arguments[0]).value.inner().intValue() + 1 :
										b.arguments[0] instanceof NameExpression ?
												new Integer(((NameExpression) b.arguments[0]).text) :
												0
								last += (p.slice = i) - 1
							} else p.slice = (last = 0) - 1
						} else if (xx == 'index') {
							if (!b.arguments.empty) {
								int i = b.arguments[0] instanceof NumberExpression ?
										((NumberExpression) b.arguments[0]).value.inner().intValue() + 1 :
										b.arguments[0] instanceof NameExpression ?
										new Integer(((NameExpression) b.arguments[0]).text) + 1 :
										0
								p.index = last = i
							} else p.index = last = 0
						} else if (xx == 'check' || xx == 'transform') {
							def n = xx + 's', v = p.get(n)
							if (null == v) p.put n, b.arguments
							else ((List) v).addAll(b.arguments)
						}
						else if (null == p.topLevelChecks) p.topLevelChecks = [b]
						else ((List) p.topLevelChecks).add(b)
					} else if (b.callValue instanceof CallExpression) {
						CallExpression d = (CallExpression) b.callValue
						parseCall(p + [slice: -1, index: 0], d.expressions)
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
					}
					else throw new UnexpectedSyntaxException('Call in function arguments with a non-path-or-call function callValue?')
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
				if (null != p.topLevelChecks) x.topLevelChecks = (List<CallExpression>) p.topLevelChecks
				parameters.add(x)
			}
			else for (c in block.content) parseCall(p, ((CallExpression) c).expressions)
		}

		void setArgs(Context c, IKismetObject[] args) {
			List<IKismetObject> lis = args.toList()
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
				def value = p.slice == 0 ? lis[p.index] : lis[p.index .. (p.slice < 0 ? p.slice : p.index + p.slice)]
				value = Kismet.model(value)
				for (t in p.transforms) {
					c.set(p.name, value)
					value = t.evaluate(c)
				}
				c.set(p.name, value)
			}
			for (p in parameters)
				for (ch in p.checks)
					if (!ch.evaluate(c))
						throw new CheckFailedException("Check ${ch.repr()} failed for $p.name " +
							c.get(p.name))
		}

		static class Parameter {
			String name
			List<Expression> checks = []
			List<Expression> transforms = []
			int index
			int slice

			@SuppressWarnings('GroovyUnusedDeclaration')
			void setTopLevelChecks(List<CallExpression> r) {
				for (x in r) {
					def n = ((NameExpression) x.callValue).text
					List<Expression> exprs = new ArrayList<>()
					exprs.add new NameExpression(KismetInner.isAlpha(n) ? n + '?' : n)
					exprs.add new NameExpression(name)
					exprs.addAll x.arguments
					checks.add new CallExpression(exprs)
				}
			}
		}
	}
}

@InheritConstructors
@CompileStatic
class CheckFailedException extends Exception {}

@CompileStatic
class GroovyFunction extends Function {
	boolean convert = true
	Closure x

	GroovyFunction(boolean convert = true, Closure x) {
		this.convert = convert
		this.x = x
	}

	IKismetObject call(IKismetObject... args){
		Kismet.model(cc(convert ? args*.inner() as Object[] : args))
	}

	def cc(...args) {
		null == args ? x.call() : x.invokeMethod('call', args)
	}
}
