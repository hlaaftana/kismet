package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.exceptions.UndefinedVariableException
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.scope.Prelude
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
class Optimizer {
	Parser parser
	boolean prelude, closure, pure, template

	Optimizer(Parser p) { parser = p }

	void on() {
		template = prelude = true
	}

	void off() {
		template = pure = closure = prelude = false
	}

	Expression optimize(Expression expr) {
		Collection<Expression> m
		if (expr instanceof CallExpression) {
			return optimize((CallExpression) expr)
		} else if ((m = expr.members).size() > 0) {
			def a = new ArrayList<Expression>(m.size())
			for (final ex : m) a.add(optimize(ex))
			return expr.join(a)
		}
		expr
	}

	Expression optimize(CallExpression expr) {
		if (expr.callValue instanceof NameExpression) {
			def text = ((NameExpression) expr.callValue).text
			IKismetObject func
			try {
				func = parser.context?.get(text)
			} catch (UndefinedVariableException ignored) {
			}
			if (null != func) {
				def inner = func.inner()
				int equalsType = 0
				if (template && inner instanceof Template) {
					final tmpl = (Template) inner
					Expression[] arguments
					if (tmpl.hungry) {
						arguments = new Expression[expr.arguments.size()]
						for (int i = 0; i < arguments.length; ++i) {
							arguments[i] = optimize(expr.arguments[i])
						}
					} else arguments = (Expression[]) expr.arguments.toArray()
					final result = ((Template) inner).transform(parser, arguments)
					return tmpl.optimized ? result : optimize(result)
				}
				if (inner instanceof Macro) {
					Expression currentExpression = expr
					switch (text) {
						case "change":
						case ":::=":
							++equalsType
						case "set_to":
						case "::=":
							++equalsType
						case "define":
						case ":=":
							++equalsType
						case "assign":
						case "=":
							final size = expr.arguments.size()
							def last = expr.arguments[size - 1]
							for (int i = size - 2; i >= 0; --i) {
								def name = expr.arguments[i]
								def atom = Prelude.toAtom(name)
								def orig = new CallExpression(expr.callValue, name, last)
								if (null != atom) switch (equalsType) {
									case 3: last = new ChangeExpression(orig, atom, last); break
									case 2: last = new ContextSetExpression(orig, atom, last); break
									case 1: last = new DefineExpression(orig, atom, last); break
									case 0: last = new AssignExpression(orig, atom, last); break
								} else if (name instanceof CallExpression)
									last = new DefineFunctionExpression(orig)
								else if (name instanceof PathExpression)
									last = new PathStepSetExpression(orig, (PathExpression) name)
								else throw new UnexpectedSyntaxException("During $text, got for name: $name")
							}
							if (last instanceof NameExpression)
								last = new DefineExpression(expr, ((NameExpression) last).text, NoExpression.INSTANCE)
							return (CallExpression) last
						case "defn": return new DefineFunctionExpression(expr)
						case "for": return new ForExpression(expr, parser.context, false, false)
						case "for<": return new ForExpression(expr, parser.context, false, true)
						case "&for": return new ForExpression(expr, parser.context, true, false)
						case "&for<": return new ForExpression(expr, parser.context, true, true)
						case "for:": return new ForEachExpression(expr, parser.context, false)
						case "&for:": return new ForEachExpression(expr, parser.context, true)
						case "check": return new CheckExpression(expr)
						default:
							if (closure && inner instanceof GroovyMacro)
								currentExpression = new ClosureMacroExpression(expr, inner)
							if (pure && ((Macro) inner).pure &&
									expr.arguments.every { it instanceof ConstantExpression })
								currentExpression = new StaticExpression(
										currentExpression, parser.context)
					}
					return currentExpression
				} else if (inner instanceof Function) {
					Expression currentExpression = expr
					switch (text) {
						case "identity":
							currentExpression = new IdentityExpression(expr); break
						case "do":
							currentExpression = new NopExpression(expr); break
						default:
							if (closure && inner instanceof GroovyFunction)
								currentExpression = new ClosureCallExpression(expr, inner)
							if (pure && ((Function) inner).pure &&
									expr.arguments.every { it instanceof ConstantExpression })
								currentExpression = new StaticExpression(
										currentExpression, parser.context)
					}
					return currentExpression
				}
			}
		}
		expr
	}

	static class FakeCallExpression extends CallExpression {
		FakeCallExpression(CallExpression original) {
			super(original.members)
		}

		String repr() { "fake" + super }
	}

	static class IdentityExpression extends FakeCallExpression {
		Expression expression

		IdentityExpression(CallExpression original) {
			super(original)
			expression = original.arguments[0]
			while (expression instanceof CallExpression &&
					((CallExpression) expression).callValue instanceof NameExpression &&
					((NameExpression) ((CallExpression) expression).callValue).text == 'identity') {
				expression = ((CallExpression) expression).arguments[0]
			}
		}

		IKismetObject evaluate(Context c) {
			expression.evaluate(c)
		}

		String repr() { "identity(${arguments.join(', ')})" }
	}

	static class PathStepSetExpression extends FakeCallExpression {
		Expression value
		PathExpression.Step step
		Expression toSet

		PathStepSetExpression(CallExpression original, PathExpression path) {
			super(original)
			value = new PathExpression(path.root, path.steps.init())
			step = path.steps.last()
			toSet = arguments.last()
		}

		IKismetObject evaluate(Context c) {
			final v = value.evaluate(c)
			if (step instanceof PathExpression.SubscriptStep)
				return v.kismetClass().subscriptSet(v, ((PathExpression.SubscriptStep) step).expression.evaluate(c), toSet.evaluate(c))
			else if (step instanceof PathExpression.PropertyStep)
				return v.kismetClass().propertySet(v, ((PathExpression.PropertyStep) step).name, toSet.evaluate(c))
			else println "Unknown pathstep"
			Kismet.NULL
		}
	}

	static class CheckExpression extends FakeCallExpression {
		Expression value
		Collection<Expression> branches
		String name = 'it'

		CheckExpression(CallExpression original) {
			super(original)
			value = original.arguments[0]
			if (value instanceof DefineExpression) name = ((DefineExpression) value).name
			branches = new ArrayList<>(original.arguments.size() - 1)
			addBranches(original.arguments.tail())
		}

		void addBranches(Collection<Expression> orig) {
			def iter = orig.iterator()
			while (iter.hasNext()) {
				def a = iter.next()
				if (iter.hasNext()) {
					if (a instanceof CallExpression) {
						def ses = new ArrayList<Expression>(((CallExpression) a).members)
						ses.add(1, new NameExpression(name))
						branches.add new CallExpression(ses)
					} else if (a instanceof NameExpression) {
						final text = ((NameExpression) a).text
						branches.add new CallExpression(new NameExpression(Prelude.isAlpha(text) ? text + '?' : text),
								new NameExpression(name))
					} else if (a instanceof BlockExpression) {
						addBranches(((BlockExpression) a).members)
						continue
					} else branches.add(new CallExpression(new NameExpression('is?'), new NameExpression(name), a))
					branches.add(iter.next())
				} else branches.add(a)
			}
		}

		IKismetObject evaluate(Context c) {
			c = c.child()
			c.set(name, value.evaluate(c))
			def iter = branches.iterator()
			while (iter.hasNext()) {
				def a = iter.next()
				if (iter.hasNext()) {
					def b = iter.next()
					if (a.evaluate(c)) return b.evaluate(c)
				} else return a.evaluate(c)
			}
			Kismet.NULL
		}
	}

	static class DefineFunctionExpression extends FakeCallExpression {
		String name
		KismetFunction.Arguments args
		BlockExpression block

		DefineFunctionExpression(CallExpression original) {
			super(original)
			def a = original.arguments[0]
			if (a instanceof NameExpression) {
				name = ((NameExpression) a).text
				args = new KismetFunction.Arguments(null)
			} else if (a instanceof CallExpression) {
				name = ((NameExpression) ((CallExpression) a).callValue).text
				args = new KismetFunction.Arguments(((CallExpression) a).arguments)
			}
			block = new BlockExpression(original.arguments.tail())
		}

		IKismetObject<KismetFunction> evaluate(Context c) {
			def f = new KismetFunction()
			f.name = name
			f.arguments = args
			f.block = c.child(block)
			c.define(name, Kismet.model(f))
		}

		String repr() { "defn(${arguments.join(', ')})" }
	}

	static class VariableModifyExpression extends FakeCallExpression {
		String name
		Expression expression

		VariableModifyExpression(CallExpression original, String name, Expression expression) {
			super(original)
			this.name = name
			this.expression = expression
		}
	}

	static class DefineExpression extends VariableModifyExpression {
		DefineExpression(CallExpression original, String name, Expression expression) {
			super(original, name, expression)
		}

		IKismetObject evaluate(Context c) {
			c.define(name, c.eval(expression))
		}

		String repr() { "define[$name, $expression]" }
	}

	static class ChangeExpression extends VariableModifyExpression {
		ChangeExpression(CallExpression original, String name, Expression expression) {
			super(original, name, expression)
		}

		IKismetObject evaluate(Context c) {
			c.change(name, c.eval(expression))
		}

		String repr() { "change[$name, $expression]" }
	}

	static class AssignExpression extends VariableModifyExpression {
		AssignExpression(CallExpression original, String name, Expression expression) {
			super(original, name, expression)
		}

		IKismetObject evaluate(Context c) {
			c.assign(name, c.eval(expression))
		}

		String repr() { "assign[$name, $expression]" }
	}

	static class ContextSetExpression extends VariableModifyExpression {
		ContextSetExpression(CallExpression original, String name, Expression expression) {
			super(original, name, expression)
		}

		IKismetObject evaluate(Context c) {
			c.set(name, c.eval(expression))
		}

		String repr() { "set_to[$name, $expression]" }
	}

	static class NopExpression extends FakeCallExpression {
		NopExpression(CallExpression original) {
			super(original)
		}

		IKismetObject evaluate(Context c) {
			for (arg in arguments) c.eval(arg)
			Kismet.NULL
		}

		String repr() { "nop(${arguments.join(', ')})" }
	}

	static class ClosureCallExpression extends FakeCallExpression {
		GroovyFunction function

		ClosureCallExpression(CallExpression original, GroovyFunction function) {
			super(original)
			this.function = function
		}

		IKismetObject evaluate(Context c) {
			function.call(c, arguments as Expression[])
		}

		String repr() { "gfunc[$callValue](${arguments.join(', ')})" }
	}

	static class ClosureMacroExpression extends FakeCallExpression {
		GroovyMacro macro

		ClosureMacroExpression(CallExpression original, GroovyMacro macro) {
			super(original)
			this.macro = macro
		}

		IKismetObject evaluate(Context c) {
			Kismet.model(arguments.empty ? macro.x.call(c) : macro.x.call(c, arguments as Expression[]))
		}

		String repr() { "gmacro[$callValue](${arguments.join(', ')})" }
	}

	static class ForExpression extends FakeCallExpression {
		String name = 'it'
		int bottom = 1, top = 0, step = 1
		Expression nameExpr, bottomExpr, topExpr, stepExpr
		Expression block
		boolean collect = true
		boolean less = false

		ForExpression(CallExpression original, Context c, boolean collect, boolean less) {
			super(original)
			def first = original.arguments[0]
			def exprs = first instanceof CallExpression ? ((CallExpression) first).members : [first]
			final size = exprs.size()
			this.collect = collect
			this.less = less
			if (less) bottom = 0
			if (size == 0) {
				top = 2
				step = 0
			} else if (size == 1) {
				if (exprs[0] instanceof ConstantExpression)
					top = c.eval(exprs[0]).inner() as int
				else topExpr = exprs[0]
			} else if (size == 2) {
				if (exprs[0] instanceof ConstantExpression)
					bottom = c.eval(exprs[0]).inner() as int
				else bottomExpr = exprs[0]

				if (exprs[1] instanceof ConstantExpression)
					top = c.eval(exprs[1]).inner() as int
				else topExpr = exprs[1]
			} else {
				if (exprs[0] instanceof StringExpression)
					name = (String) c.eval(exprs[0]).inner()
				else if (exprs[0] instanceof NameExpression) {
					name = ((NameExpression) exprs[0]).text
				} else nameExpr = exprs[0]

				if (exprs[1] instanceof ConstantExpression)
					bottom = c.eval(exprs[1]).inner() as int
				else bottomExpr = exprs[1]

				if (exprs[2] instanceof ConstantExpression)
					top = c.eval(exprs[2]).inner() as int
				else topExpr = exprs[2]

				if (exprs[3] instanceof ConstantExpression)
					step = c.eval(exprs[3]).inner() as int
				else stepExpr = exprs[3]
			}
			def tail = arguments.tail()
			block = tail.size() == 1 ? tail[0] : new BlockExpression(tail)
		}

		IKismetObject evaluate(Context c) {
			int b = bottomExpr == null ? bottom : bottomExpr.evaluate(c).inner() as int
			final top = topExpr == null ? top : topExpr.evaluate(c).inner() as int
			final step = stepExpr == null ? step : stepExpr.evaluate(c).inner() as int
			final name = nameExpr == null ? name : nameExpr.evaluate(c).toString()
			def result = collect ? new ArrayList() : (ArrayList) null
			for (; less ? b < top : b <= top; b += step) {
				def k = c.child()
				k.set(name, Kismet.model(b))
				if (collect) result.add(block.evaluate(c).inner())
				else block.evaluate(c)
			}
			Kismet.model(result)
		}

		String repr() {
			(collect ? '&' : '') + 'for' + (less ? '<' : '') + '(' + arguments.join(', ') + ')'
		}
	}

	static class ForEachExpression extends FakeCallExpression {
		String indexName
		int indexStart = 0
		String name = 'it'
		Iterator iterator = Collections.emptyIterator()
		Expression indexNameExpr, indexStartExpr, nameExpr, iterExpr
		Expression block
		boolean collect = true

		ForEachExpression(CallExpression original, Context c, boolean collect) {
			super(original)
			def first = original.arguments[0]
			def exprs = first instanceof CallExpression ? ((CallExpression) first).members : [first]
			final size = exprs.size()
			this.collect = collect
			if (size == 1) {
				final atom = Prelude.toAtom(exprs[0])
				if (atom == null) nameExpr = exprs[0]
				else name = atom
			} else if (size == 2) {
				final atom = Prelude.toAtom(exprs[0])
				if (atom == null) nameExpr = exprs[0]
				else name = atom

				if (exprs[1] instanceof ConstantExpression)
					iterator = Prelude.toIterator(c.eval(exprs[1]).inner())
				else iterExpr = exprs[1]
			} else if (size >= 3) {
				final f = exprs[0]
				if (f instanceof CallExpression) {
					final fc = (CallExpression) f
					indexName(fc.callValue)
					indexStart(c, fc.arguments.size() > 1 ? fc.arguments[0] : fc.callValue)
				} else indexName(f)

				final atom = Prelude.toAtom(exprs[1])
				if (atom == null) nameExpr = exprs[1]
				else name = atom

				if (exprs[2] instanceof ConstantExpression)
					iterator = Prelude.toIterator(c.eval(exprs[2]).inner())
				else iterExpr = exprs[2]
			}
			def tail = arguments.tail()
			block = tail.size() == 1 ? tail[0] : new BlockExpression(tail)
		}

		private void indexName(Expression expr) {
			final atom = Prelude.toAtom(expr)
			if (atom == null) indexNameExpr = expr
			else indexName = atom
		}

		private void indexStart(Context c, Expression expr) {
			if (expr instanceof ConstantExpression)
				indexStart = expr.evaluate(c) as int
			else indexStartExpr = expr
		}

		IKismetObject evaluate(Context c) {
			int i = indexStartExpr == null ? indexStart : indexStartExpr.evaluate(c).inner() as int
			final iter = iterExpr == null ? iterator : Prelude.toIterator(iterExpr.evaluate(c).inner())
			final iName = indexNameExpr == null ? indexName : indexNameExpr.evaluate(c).toString()
			final name = nameExpr == null ? name : nameExpr.evaluate(c).toString()
			def result = collect ? new ArrayList() : (ArrayList) null
			while (iter.hasNext()) {
				final it = iter.next()
				def k = c.child()
				k.set(name, Kismet.model(it))
				if (null != iName) k.set(iName, Kismet.model(i))
				if (collect) result.add(block.evaluate(k).inner())
				else block.evaluate(k)
				i++
			}
			Kismet.model(result)
		}

		String repr() { (collect ? '&' : '') + 'for:(' + arguments.join(', ') + ')' }
	}
}