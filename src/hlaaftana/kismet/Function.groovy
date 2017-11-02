package hlaaftana.kismet

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.parser.AtomExpression
import hlaaftana.kismet.parser.BlockExpression
import hlaaftana.kismet.parser.CallExpression
import hlaaftana.kismet.parser.Expression
import hlaaftana.kismet.parser.NumberExpression
import hlaaftana.kismet.parser.StringExpression

abstract class Function implements KismetCallable {}

@CompileStatic
class KismetFunction extends Function {
	Arguments a
	KismetObject<Block> b

	KismetFunction(Expression[] params, KismetObject<Block> b) {
		a = new Arguments(params)
		this.b = b
	}

	KismetObject call(KismetObject... args){
		Block c = b.inner().anonymousClone()
		a.setArgs(c, args)
		c()
	}

	static class Arguments {
		boolean doDollars
		boolean enforceLength
		List<Parameter> parameters = []
		int last = 0

		Arguments(Expression[] p) { enforceLength = !(doDollars = p.length == 0); parse(p) }

		def parse(Expression[] params) {
			for (e in params) {
				if (e instanceof AtomExpression) parameters.add(new Parameter(name: ((AtomExpression) e).text, index: last++))
				else if (e instanceof StringExpression)
					  parameters.add(new Parameter(name: ((StringExpression) e).value, index: last++))
				else if (e instanceof BlockExpression) parse(((BlockExpression) e).content as Expression[])
				else if (e instanceof CallExpression) parseCall(((CallExpression) e).expressions)
			}
		}

		def parseCall(Map p = [:], List<Expression> exprs) {
			p = new HashMap(p)
			BlockExpression block = null
			for (e in exprs) {
				if (e instanceof AtomExpression) p.name = ((AtomExpression) e).text
				else if (e instanceof StringExpression) p.name = ((StringExpression) e).value
				else if (e instanceof BlockExpression) block = (BlockExpression) e
				else if (e instanceof NumberExpression) p.index = ((NumberExpression) e).value.intValue()
				else if (e instanceof CallExpression) {
					CallExpression b = (CallExpression) e
					if (b.value instanceof AtomExpression) {
						def xx = ((AtomExpression) b.value).text
						if (xx == '*') {
							if (!b.arguments.empty && b.arguments[0] instanceof NumberExpression) {
								last += (p.slice = ((NumberExpression) b.arguments[0]).value.intValue()) - 1
							} else p.slice = (last = 0) - 1
						} else if (xx == 'check' || xx == 'transform') {
							def n = xx + 's', v = p.get(n)
							if (null == v) p.put n, b.arguments
							else ((List) v).addAll(b.arguments)
						}
						else if (null == p.topLevelChecks) p.topLevelChecks = [b]
						else ((List) p.topLevelChecks).add(b)
					} else if (b.value instanceof CallExpression) {
						CallExpression d = (CallExpression) b.value
						parseCall(p + [slice: -1, index: 0], d.expressions)
						for (c in d.arguments) {
							if (c instanceof AtomExpression) {
								def t = ((AtomExpression) c).text
								if (t == '$') doDollars = !doDollars
								else if (t == '#') enforceLength = !enforceLength
								else throw new UnexpectedSyntaxException('My bad but i dont know how to handle atom '
									+ t + ' in meta-argument call')
							} else throw new UnexpectedSyntaxException('My bad but i dont know how to handle ' +
								Expression.repr(c) + ' in meta-argument call')
						}
					}
					else throw new UnexpectedSyntaxException('Call in function arguments with a non-atom-or-call function value?')
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

		void setArgs(Block c, KismetObject[] args) {
			if (doDollars) {
				for (int it = 0; it < args.length; ++it) {
					c.context.directSet('$'.concat(String.valueOf(it)), args[it])
				}
				c.context.directSet('$all', Kismet.model(args.toList()))
			}
			if (enforceLength) {
				boolean variadic = false
				int max = 0
				for (p in parameters) {
					if (p.index + p.slice > max) max = p.index + p.slice
					if (p.slice < 0) variadic = true
				}
				if (variadic ? max < args.length : max != args.length)
					throw new CheckFailedException("Got argument length $args.length which wasn't " +
							(variadic ? '>= ' : '== ') + max)
			}
			for (p in parameters) {
				def value = p.slice == 0 ? args[p.index] :
							p.slice == -1 ? args.drop(p.index).toList() :
							args.toList().subList(p.index, p.index + p.slice)
				value = Kismet.model(value)
				for (t in p.transforms) {
					c.context.directSet(p.name, value)
					value = t.evaluate(c)
				}
				c.context.directSet(p.name, value)
			}
			for (p in parameters)
				for (ch in p.checks)
					if (!ch.evaluate(c))
						throw new CheckFailedException("Check ${Expression.repr(ch)} failed for $p.name " +
							c.context.getProperty(p.name))
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
					def n = ((AtomExpression) x.value).text
					List<Expression> exprs = new ArrayList<>()
					exprs.add new AtomExpression(n + '?')
					exprs.add new AtomExpression(name)
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

	KismetObject call(KismetObject... args){
		Kismet.model(cc(convert ? args*.inner() as Object[] : args))
	}

	def cc(...args) {
		x.invokeMethod('call', args)
	}
}
