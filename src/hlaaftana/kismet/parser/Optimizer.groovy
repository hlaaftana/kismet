package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.BlockExpression
import hlaaftana.kismet.call.CallExpression
import hlaaftana.kismet.call.ConstantExpression
import hlaaftana.kismet.call.Expression
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.call.GroovyFunction
import hlaaftana.kismet.call.GroovyMacro
import hlaaftana.kismet.call.KismetFunction
import hlaaftana.kismet.call.Macro
import hlaaftana.kismet.call.NameExpression
import hlaaftana.kismet.call.NoExpression
import hlaaftana.kismet.call.NumberExpression
import hlaaftana.kismet.call.PathExpression
import hlaaftana.kismet.call.StaticExpression
import hlaaftana.kismet.call.StringExpression
import hlaaftana.kismet.call.Template
import hlaaftana.kismet.Kismet
import hlaaftana.rewrite.kismet.call.*
import hlaaftana.kismet.exceptions.UndefinedVariableException
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.exceptions.UnexpectedValueException
import hlaaftana.kismet.scope.Prelude
import hlaaftana.kismet.vm.Context
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.WrapperKismetObject

import java.math.RoundingMode

@CompileStatic
class Optimizer {
	Parser parser

	Optimizer(Parser p) { parser = p }

	Expression optimize(Expression expr) {
		if (expr instanceof CallExpression) {
			return optimize((CallExpression) expr)
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
				if (parser.fillTemplate && inner instanceof Template && ((Template) inner).constant)
					return optimize(((Template) inner).transform(expr.arguments as Expression[]))
				if (inner instanceof Macro) {
					Expression currentExpression = expr
					switch (text) {
						case "don't":
							return NoExpression.INSTANCE
						case "round":
							def p = expr.arguments[1]
							RoundExpression rounder
							if (null != p && p instanceof NameExpression)
								rounder = new RoundExpression(parser.context, expr, (NameExpression) p)
							else rounder = new RoundExpression(parser.context, expr)
							return rounder
						case "binary": return new BinaryExpression(expr)
						case "octal": return new OctalExpression(expr)
						case "hex": return new HexExpression(expr)
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
								def orig = new CallExpression([expr.callValue, name, last])
								if (null != atom) switch (equalsType) {
									case 3: last = new ChangeExpression(orig, atom, last); break
									case 2: last = new ContextSetExpression(orig, atom, last); break
									case 1: last = new DefineExpression(orig, atom, last); break
									case 0: last = new AssignExpression(orig, atom, last); break
								} else if (name instanceof CallExpression)
									last = new DefineFunctionExpression(orig)
								else if (name instanceof PathExpression)
									last = new PathStepSetExpression(orig, (PathExpression) name)
								else throw new UnexpectedSyntaxException("During $text, got for name: ${name.repr()}")
							}
							if (last instanceof NameExpression)
								last = new DefineExpression(expr, ((NameExpression) last).text, NoExpression.INSTANCE)
							return (CallExpression) last
						case "fn": return new FunctionExpression(expr)
						case "defn": return new DefineFunctionExpression(expr)
						case "fn*": return new FunctionSpreadExpression(expr)
						case "incr": return optimize(new CallExpression([new NameExpression('='), expr.arguments[0],
								new CallExpression([new NameExpression('next'), expr.arguments[0]])]))
						case "decr": return optimize(new CallExpression([new NameExpression('='), expr.arguments[0],
								new CallExpression([new NameExpression('prev'), expr.arguments[0]])]))
						case "for": return new ForExpression(expr, parser.context, false, false)
						case "for<": return new ForExpression(expr, parser.context, false, true)
						case "&for": return new ForExpression(expr, parser.context, true, false)
						case "&for<": return new ForExpression(expr, parser.context, true, true)
						case "for:": return new ForEachExpression(expr, parser.context, false)
						case "&for:": return new ForEachExpression(expr, parser.context, true)
						case "check": return new CheckExpression(expr)
						default:
							if (parser.optimizeClosure && inner instanceof GroovyMacro)
								currentExpression = new ClosureMacroExpression(expr, inner)
							if (parser.optimizePure && ((Macro) inner).pure &&
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
							if (parser.optimizeClosure && inner instanceof GroovyFunction)
								currentExpression = new ClosureCallExpression(expr, inner)
							if (parser.optimizePure && ((Function) inner).pure &&
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
			super(original.expressions)
		}

		String repr() { "fake" + super.repr() }
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

		String repr() { "identity(${arguments*.repr().join(', ')})" }
	}

	static class PathStepExpression extends FakeCallExpression {
		Expression value
		PathExpression.Step step

		PathStepExpression(CallExpression original, PathExpression path) {
			super(original)
			value = new PathExpression(path.root, path.steps.init())
			step = path.steps.last()
		}

		IKismetObject evaluate(Context c) {
			step.apply(c, value.evaluate(c))
		}
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
		List<Expression> branches
		String name = 'it'

		CheckExpression(CallExpression original) {
			super(original)
			value = original.arguments[0]
			if (value instanceof DefineExpression) name = ((DefineExpression) value).name
			branches = new ArrayList<>(original.arguments.size() - 1)
			addBranches(original.arguments.tail())
		}

		void addBranches(List<Expression> orig) {
			def iter = orig.iterator()
			while (iter.hasNext()) {
				def a = iter.next()
				if (iter.hasNext()) {
					if (a instanceof CallExpression) {
						def ses = new ArrayList<Expression>(((CallExpression) a).expressions)
						ses.add(1, new NameExpression(name))
						branches.add new CallExpression(ses)
					} else if (a instanceof NameExpression) {
						final text = ((NameExpression) a).text
						branches.add new CallExpression([new NameExpression(Prelude.isAlpha(text) ? text + '?' : text),
								new NameExpression(name)] as List<Expression>)
					} else if (a instanceof BlockExpression) {
						addBranches(((BlockExpression) a).content)
						continue
					} else branches.add(new CallExpression([new NameExpression('is?'), new NameExpression(name), a]))
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

	static class RoundExpression extends FakeCallExpression {
		private static Map<String, RoundingMode> roundingModes = [
				'^': RoundingMode.CEILING, 'v': RoundingMode.FLOOR,
				'^0': RoundingMode.UP, 'v0': RoundingMode.DOWN,
				'/^': RoundingMode.HALF_UP, '/v': RoundingMode.HALF_DOWN,
				'/2': RoundingMode.HALF_EVEN, '!': RoundingMode.UNNECESSARY
		].asImmutable()

		RoundingMode mode
		Expression modeExpression

		RoundingMode getRoundingMode(Context c) {
			if (null == mode) roundingMode(c, modeExpression)
			else mode
		}

		static RoundingMode roundingMode(Context c, Expression expr) {
			def name = c.eval(expr).toString()
			def val = roundingModes[name]
			if (null == val) throw new UnexpectedValueException("Unknown rounding mode $name")
			val
		}

		Integer scale
		Expression scaleExpression

		int getScale(Context c) {
			if (null == scale) c.eval(scaleExpression).inner() as Integer
			else scale
		}

		Number cached

		RoundExpression(Context c, CallExpression original, NameExpression path = null) {
			super(original)
			if (null != path) {
				def name = path.text
				mode = roundingModes[name]
				if (null == mode) throw new UnexpectedValueException("Unknown rounding mode $name")
				modeExpression = path
			} else modeExpression = original.arguments[1]
			if (modeExpression instanceof ConstantExpression) mode = roundingMode(c, modeExpression)
			scaleExpression = original.arguments[2]
			if (null == scaleExpression) scale = 0
			if (scaleExpression instanceof ConstantExpression) scale = c.eval(scaleExpression).inner() as Integer
			if (callValue instanceof NumberExpression)
				cached = round(c, ((NumberExpression) callValue).evaluate(c).inner())
		}

		Number round(Context c, Number number) {
			if (null == modeExpression) number = number as BigDecimal
			if (number instanceof BigDecimal)
				((BigDecimal) number).setScale(getScale(c), getRoundingMode(c) ?: RoundingMode.HALF_UP).stripTrailingZeros()
			else if (number instanceof BigInteger
					|| number instanceof Integer
					|| number instanceof Long) number
			else if (callValue instanceof Float) (Number) Math.round(number.floatValue())
			else (Number) Math.round(number.doubleValue())
		}

		IKismetObject evaluate(Context c) {
			Kismet.model(null == cached ? round(c, callValue.evaluate(c).inner() as Number) : cached)
		}
	}

	static class HexExpression extends FakeCallExpression implements ConstantExpression<BigInteger> {
		HexExpression(CallExpression original) {
			super(original)
			if (original.arguments.empty)
				throw new UnexpectedSyntaxException('[hex] is for literals, otherwise use [from_base x 16]')
			def a = original.arguments[0], b = original.arguments[1]
			StringBuilder string = new StringBuilder()
			if (a instanceof NumberExpression) string.append(((NumberExpression) a).value.toString())
			else if (a instanceof NameExpression) string.append(((NameExpression) a).text)
			else throw new UnexpectedSyntaxException('[hex] is for literals, otherwise use [from_base x 16]')
			if (null != b && b instanceof NameExpression) string.append(((NameExpression) b).text)
			else throw new UnexpectedSyntaxException('[hex] is for literals, otherwise use [from_base x 16]')
			try {
				setValue(new BigInteger(string.toString(), 16))
			} catch (NumberFormatException ignored) {
				throw new UnexpectedSyntaxException('[hex] is for literals, otherwise use [from_base x 16]')
			}
		}

		String repr() { "hex($value)" }
	}

	static class OctalExpression extends FakeCallExpression implements ConstantExpression<BigInteger> {
		OctalExpression(CallExpression original) {
			super(original)
			if (original.arguments.empty)
				throw new UnexpectedSyntaxException('[octal] is for literals, otherwise use [from_base x 8]')
			if (original.arguments[0] instanceof NumberExpression) try {
				setValue new BigInteger(((NumberExpression) original.arguments[0]).value.toString(), 8)
			} catch (NumberFormatException ignored) {
				throw new UnexpectedSyntaxException('[octal] is for literals, otherwise use [from_base x 8]')
			} else throw new UnexpectedSyntaxException('[octal] is for literals, otherwise use [from_base x 8]')
		}

		String repr() { "octal($value)" }
	}

	static class BinaryExpression extends FakeCallExpression implements ConstantExpression<BigInteger> {
		BinaryExpression(CallExpression original) {
			super(original)
			if (original.arguments.empty)
				throw new UnexpectedSyntaxException('[binary] is for literals, otherwise use [from_base x 2]')
			if (original.arguments[0] instanceof NumberExpression) try {
				setValue new BigInteger(((NumberExpression) original.arguments[0]).value.toString(), 2)
			} catch (NumberFormatException ignored) {
				throw new UnexpectedSyntaxException('[binary] is for literals, otherwise use [from_base x 2]')
			} else throw new UnexpectedSyntaxException('[binary] is for literals, otherwise use [from_base x 2]')
		}

		String repr() { "binary($value)" }
	}

	static class FunctionExpression extends FakeCallExpression {
		KismetFunction.Arguments args
		Expression block

		FunctionExpression(CallExpression original) {
			super(original)
			def a = original.arguments[0]
			if (original.arguments.size() == 1) {
				args = KismetFunction.Arguments.EMPTY
				block = a
			} else {
				args = new KismetFunction.Arguments(a instanceof CallExpression ?
						((CallExpression) a).expressions : a instanceof BlockExpression ?
						((BlockExpression) a).content : null)
				block = new BlockExpression(original.arguments.tail())
			}
		}

		IKismetObject<KismetFunction> evaluate(Context c) {
			def f = new KismetFunction()
			f.arguments = args
			f.block = c.child(block)
			Kismet.model(f)
		}

		String repr() { "fn(${arguments*.repr().join(', ')})" }
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

		String repr() { "defn(${arguments*.repr().join(', ')})" }
	}

	static class FunctionSpreadExpression extends FakeCallExpression {
		FunctionExpression inner

		FunctionSpreadExpression(CallExpression original) {
			super(original)
			def call = new CallExpression((List<Expression>) Collections.emptyList())
			call.callValue = new NameExpression('fn')
			call.arguments = original.arguments
			inner = new FunctionExpression(call)
		}

		IKismetObject evaluate(Context c) {
			Kismet.model(new Function() {
				Function function = FunctionSpreadExpression.this.inner.evaluate(c).inner()

				@Override
				IKismetObject call(IKismetObject... args) {
					function.call(((WrapperKismetObject) args[0]).as(List) as IKismetObject[])
				}
			})
		}

		String repr() { "fn*(${arguments*.repr().join(', ')})" }
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

		String repr() { "define[$name, ${expression.repr()}]" }
	}

	static class ChangeExpression extends VariableModifyExpression {
		ChangeExpression(CallExpression original, String name, Expression expression) {
			super(original, name, expression)
		}

		IKismetObject evaluate(Context c) {
			c.change(name, c.eval(expression))
		}

		String repr() { "change[$name, ${expression.repr()}]" }
	}

	static class AssignExpression extends VariableModifyExpression {
		AssignExpression(CallExpression original, String name, Expression expression) {
			super(original, name, expression)
		}

		IKismetObject evaluate(Context c) {
			c.assign(name, c.eval(expression))
		}

		String repr() { "assign[$name, ${expression.repr()}]" }
	}

	static class ContextSetExpression extends VariableModifyExpression {
		ContextSetExpression(CallExpression original, String name, Expression expression) {
			super(original, name, expression)
		}

		IKismetObject evaluate(Context c) {
			c.set(name, c.eval(expression))
		}

		String repr() { "set_to[$name, ${expression.repr()}]" }
	}

	static class NopExpression extends FakeCallExpression {
		NopExpression(CallExpression original) {
			super(original)
		}

		IKismetObject evaluate(Context c) {
			for (arg in arguments) c.eval(arg)
			Kismet.NULL
		}

		String repr() { "nop(${arguments*.repr().join(', ')})" }
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

		String repr() { "gfunc[${callValue.repr()}](${arguments*.repr().join(', ')})" }
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

		String repr() { "gmacro[${callValue.repr()}](${arguments*.repr().join(', ')})" }
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
			def exprs = first instanceof CallExpression ? ((CallExpression) first).expressions : [first]
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
			(collect ? '&' : '') + 'for' + (less ? '<' : '') + '(' + arguments*.repr().join(', ') + ')'
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
			def exprs = first instanceof CallExpression ? ((CallExpression) first).expressions : [first]
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
					final cf = ((CallExpression) f).expressions
					indexName(cf[0])
					indexStart(c, cf[cf.size() > 1 ? 1 : 0])
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
				indexStart = (int) ((WrapperKismetObject) expr.evaluate(c)).as(int)
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
				if (collect) result.add(block.evaluate(c).inner())
				else block.evaluate(c)
				i++
			}
			Kismet.model(result)
		}

		String repr() { (collect ? '&' : '') + 'for:' + '(' + arguments*.repr().join(', ') + ')' }
	}
}