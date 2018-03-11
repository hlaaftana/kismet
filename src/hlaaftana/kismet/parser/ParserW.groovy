package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import hlaaftana.kismet.Context
import hlaaftana.kismet.Function
import hlaaftana.kismet.GroovyFunction
import hlaaftana.kismet.GroovyMacro
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.KismetFunction
import hlaaftana.kismet.KismetObject
import hlaaftana.kismet.Macro
import hlaaftana.kismet.ParseException
import hlaaftana.kismet.Path
import hlaaftana.kismet.UndefinedVariableException
import hlaaftana.kismet.UnexpectedSyntaxException
import hlaaftana.kismet.UnexpectedValueException

import java.math.RoundingMode

@CompileStatic
class ParserW {
	Context context
	int ln = 1, cl = 0
	boolean optimizePrelude
	boolean optimizeClosure
	boolean optimizePure
	boolean path2

	@SuppressWarnings('GroovyVariableNotAssigned')
	BlockExpression parse(String code) {
		BlockBuilder builder = new BlockBuilder(false)
		char[] arr = code.toCharArray()
		int len = arr.length
		boolean comment
		for (int i = 0; i < len; ++i) {
			int c = (int) arr[i]
			if (c == 10) {
				++ln
				cl = 0
				comment = false
			} else {
				++cl
				if (!comment) comment = c == ((char) ';') && builder.acceptsComment()
			}
			if (comment) continue
			try {
				builder.push(c)
			} catch (ex) {
				throw new ParseException(ex, ln, cl)
			}
		}
		builder.push(10)
		new BlockExpression(builder.expressions)
	}

	abstract class ExprBuilder<T extends Expression> {
		boolean percent = false
		boolean goBack = false

		abstract T doPush(int cp)

		Expression push(int cp) {
			T x = doPush(cp)
			null == x ? x : percent ? x.percentize(context) : x
		}
	}

	class BlockBuilder extends ExprBuilder<BlockExpression> {
		List<Expression> expressions = []
		CallBuilder last = null
		boolean lastPercent = false
		boolean bracketed

		BlockBuilder(boolean b) { bracketed = b }

		@Override
		BlockExpression doPush(int cp) {
			if (cp == 125 && bracketed && (null == last || (last.endOnDelim && !(last.anyBlocks())))) {
				if (null != last) {
					CallExpression x = last.doPush(10)
					if (null == x)
						throw new UnexpectedSyntaxException('Last call in block was bracketed but was not closed')
					Expression a = !last.bracketed && !x.arguments ? x.callValue : x
					expressions.add(last.percent ? a.percentize(context) :
							x instanceof CallExpression && optimizePrelude ?
									last.optimize((CallExpression) a) : a)
				}
				return new BlockExpression(expressions)
			} else if (null == last) {
				if (cp == 91) last = new CallBuilder(true)
				else if (cp == 37) lastPercent = true
				else if (!Character.isWhitespace(cp)) (last = new CallBuilder(false)).push(cp)
				if (lastPercent && null != last) last.percent = !(lastPercent = false)
			} else {
				CallExpression x = last.doPush(cp)
				if (null != x) {
					Expression a = !last.bracketed && !x.arguments ? x.callValue : x
					expressions.add(last.percent ? a.percentize(context) :
							a instanceof CallExpression && optimizePrelude ?
							last.optimize((CallExpression) a) : a)
					final back = last.goBack
					last = null
					if (back) return doPush(cp)
				}
			}
			bracketed && cp == 125 && null == last ? new BlockExpression(expressions) : null
		}

		boolean acceptsComment() {
			null == last ? true : last.acceptsComment()
		}
	}

	@CompileStatic
	class CallBuilder extends ExprBuilder<CallExpression> {
		List<Expression> expressions = []
		ExprBuilder last = null
		boolean lastPercent = false
		boolean bracketed

		CallBuilder(boolean b) { bracketed = b }

		@Override
		CallExpression doPush(int cp) {
			if ((bracketed ? cp == 93 : (cp == 10 || cp == 13)) && endOnDelim) {
				if (null != last) {
					Expression x = last.push(10)
					if (null == x)
						throw new IllegalStateException('Call was supposed to end with a ], last builder ' +
								'which is nonbracketed path or num was pushed a newline but returned null ' + last)
					expressions.add(x)
				}
				return new CallExpression(expressions)
			} else if (null == last) {
				if (bracketed && cp == 93) return new CallExpression(expressions)
				else if (cp == 37) lastPercent = true
				else if (cp == 40) last = new PathBuilder(true)
				else if (cp == 91) last = new CallBuilder(true)
				else if (cp == 123) last = new BlockBuilder(true)
				else if (cp > 47 && cp < 58) (last = new NumberBuilder()).push(cp)
				else if (cp == 34 || cp == 39) last = new StringExprBuilder(cp)
				else if (cp == ((char) '`')) last = new QuoteAtomBuilder()
				else if (cp == ((char) '.'))
					(last = new PathBuilder2(expressions.empty ? null : expressions.pop())).push(cp)
				else if (!Character.isWhitespace(cp)) (last = path2 ? new NameBuilder() : new PathBuilder(false)).push(cp)
				if (lastPercent && null != last) last.percent = !(lastPercent = false)
			} else {
				Expression x = last.push(cp)
				if (null != x) {
					if (last instanceof NameBuilder && cp == ((char) '[')) {
						(last = new PathBuilder2(x)).push(cp)
					} else {
						expressions.add(x)
						final back = last.goBack
						last = null
						if (back) return doPush(cp)
					}
				}
			}
			(CallExpression) null
		}

		@Override
		Expression push(int cp) {
			def x = doPush(cp)
			if (null == x) return x
			if (percent) x = x.percentize(context)
			else if (optimizePrelude) x = optimize(x)
			x
		}

		boolean isEndOnDelim() {
			!( last instanceof StringExprBuilder || last instanceof QuoteAtomBuilder
					|| (last instanceof CallBuilder && ((CallBuilder) last).bracketed)
					|| last instanceof BlockBuilder
					|| (last instanceof PathBuilder && ((PathBuilder) last).bracketed))
		}

		boolean acceptsComment() {
			!(last instanceof StringExprBuilder || last instanceof QuoteAtomBuilder)
		}

		boolean anyBlocks() {
			last instanceof BlockBuilder || (last instanceof CallBuilder && ((CallBuilder) last).anyBlocks())
		}

		Expression optimize(CallExpression expr) {
			if (expr.callValue instanceof PathExpression) {
				def path = ((PathExpression) expr.callValue).path.expressions
				if (path.size() == 1) {
					def text = path[0].raw
					KismetObject func
					try {
						func = context?.get(text)
					} catch (UndefinedVariableException ignored) {}
					if (null != func) {
						def inner = func.inner()
						if (inner instanceof Macro) {
							Expression currentExpression = expr
							switch (text) {
							case "don't":
								return new NoExpression()
							case "round":
								def p = expr.arguments[1]
								RoundExpression rounder
								if (null != p && p instanceof PathExpression)
									rounder = new RoundExpression(context, expr, (PathExpression) p)
								else rounder = new RoundExpression(context, expr)
								return rounder
							case "binary": return new BinaryExpression(expr)
							case "octal": return new OctalExpression(expr)
							case "hex": return new HexExpression(expr)
							case "change":
							case ":::=":
								List<Path.PathStep> patho
								if (expr.arguments[0] instanceof PathExpression &&
									(patho = ((PathExpression) expr.arguments[0]).path.expressions).size() == 1)
									return new ChangeExpression(expr, patho[0].raw)
								else if (expr.arguments[0] instanceof StringExpression)
									return new ChangeExpression(expr, ((StringExpression) expr.arguments[0]).value.inner())
								break
							case "set_to":
							case "::=":
								List<Path.PathStep> patho
								if (expr.arguments[0] instanceof PathExpression &&
										(patho = ((PathExpression) expr.arguments[0]).path.expressions).size() == 1)
									return new ContextSetExpression(expr, patho[0].raw)
								else if (expr.arguments[0] instanceof StringExpression)
									return new ContextSetExpression(expr, ((StringExpression) expr.arguments[0]).value.inner())
								break
							case "define":
							case ":=":
								List<Path.PathStep> patho
								if (expr.arguments[0] instanceof PathExpression &&
										(patho = ((PathExpression) expr.arguments[0]).path.expressions).size() == 1)
									return new DefineExpression(expr, patho[0].raw)
								else if (expr.arguments[0] instanceof StringExpression)
									return new DefineExpression(expr, ((StringExpression) expr.arguments[0]).value.inner())
								break
							case "assign":
							case "=":
								List<Path.PathStep> patho
								if (expr.arguments[0] instanceof PathExpression &&
										(patho = ((PathExpression) expr.arguments[0]).path.expressions).size() == 1)
									return new AssignExpression(expr, patho[0].raw)
								else if (expr.arguments[0] instanceof StringExpression)
									return new AssignExpression(expr, ((StringExpression) expr.arguments[0]).value.inner())
								break
							case "fn": return new FunctionExpression(expr)
							case "defn": return new DefineFunctionExpression(expr)
							case "fn*": return new FunctionSpreadExpression(expr)
							case "incr": return new CallExpression([atom('='), expr.arguments[0],
									new CallExpression([atom('next'), expr.arguments[0]])])
							case "decr": return new CallExpression([atom('='), expr.arguments[0],
									new CallExpression([atom('prev'), expr.arguments[0]])])
							case "for": return new ForExpression(expr, context)
							case "for<": return new ForLessExpression(expr, context)
							case "&for": return new CollectForExpression(expr, context)
							case "&for<": return new CollectForLessExpression(expr, context)
							default:
								if (optimizeClosure && inner instanceof GroovyMacro)
									currentExpression = new ClosureMacroExpression(expr, inner)
								if (optimizePure && ((Macro) inner).pure &&
										expr.arguments.every { it instanceof ConstantExpression })
									currentExpression = new StaticExpression(
										currentExpression, context)
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
								if (optimizeClosure && inner instanceof GroovyFunction)
									currentExpression = new ClosureCallExpression(expr, inner)
								if (optimizePure && ((Function) inner).pure &&
										expr.arguments.every { it instanceof ConstantExpression })
									currentExpression = new StaticExpression(
										currentExpression, context)
							}
							return currentExpression
						}
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
						((CallExpression) expression).callValue instanceof PathExpression &&
						((PathExpression) ((CallExpression) expression).callValue).text == 'identity') {
					expression = ((CallExpression) expression).arguments[0]
				}
			}

			KismetObject evaluate(Context c) {
				expression.evaluate(c)
			}

			String repr() { "identity(${arguments*.repr().join(', ')})" }
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

			RoundExpression(Context c, CallExpression original, PathExpression path = null) {
				super(original)
				if (null != path) {
					def name = path.path.raw
					mode = roundingModes[name]
					if (null == mode) throw new UnexpectedValueException("Unknown rounding mode $name")
					modeExpression = path
				}
				else modeExpression = original.arguments[1]
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

			KismetObject evaluate(Context c) {
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
				else if (a instanceof PathExpression) string.append(((PathExpression) a).text)
				else throw new UnexpectedSyntaxException('[hex] is for literals, otherwise use [from_base x 16]')
				if (null != b && b instanceof PathExpression) string.append(((PathExpression) b).text)
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

			KismetObject<KismetFunction> evaluate(Context c) {
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
				if (a instanceof PathExpression) {
					name = ((PathExpression) a).text
					args = new KismetFunction.Arguments(null)
				} else if (a instanceof CallExpression) {
					name = ((PathExpression) ((CallExpression) a).callValue).text
					args = new KismetFunction.Arguments(((CallExpression) a).arguments)
				}
				block = new BlockExpression(original.arguments.tail())
			}

			KismetObject<KismetFunction> evaluate(Context c) {
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
				call.callValue = atom('fn')
				call.arguments = original.arguments
				inner = new FunctionExpression(call)
			}

			KismetObject evaluate(Context c) {
				Kismet.model(new Function() {
					Function function = FunctionSpreadExpression.this.inner.evaluate(c).inner()

					@Override
					KismetObject call(KismetObject... args) {
						function.call(args[0].as(List) as KismetObject[])
					}
				})
			}

			String repr() { "fn*(${arguments*.repr().join(', ')})" }
		}

		static class DefineExpression extends FakeCallExpression {
			String name
			Expression expression

			DefineExpression(CallExpression original, String name) {
				super(original)
				this.name = name
				expression = original.arguments[1]
			}

			KismetObject evaluate(Context c) {
				c.define(name, c.eval(expression))
			}

			String repr() { "define[$name, ${expression.repr()}]" }
		}

		static class ChangeExpression extends FakeCallExpression {
			String name
			Expression expression

			ChangeExpression(CallExpression original, String name) {
				super(original)
				this.name = name
				expression = original.arguments[1]
			}

			KismetObject evaluate(Context c) {
				c.change(name, c.eval(expression))
			}

			String repr() { "change[$name, ${expression.repr()}]" }
		}

		static class AssignExpression extends FakeCallExpression {
			String name
			Expression expression

			AssignExpression(CallExpression original, String name) {
				super(original)
				this.name = name
				expression = original.arguments[1]
			}

			KismetObject evaluate(Context c) {
				c.assign(name, c.eval(expression))
			}

			String repr() { "assign[$name, ${expression.repr()}]" }
		}

		static class ContextSetExpression extends FakeCallExpression {
			String name
			Expression expression

			ContextSetExpression(CallExpression original, String name) {
				super(original)
				this.name = name
				expression = original.arguments[1]
			}

			KismetObject evaluate(Context c) {
				c.set(name, c.eval(expression))
			}

			String repr() { "set_to[$name, ${expression.repr()}]" }
		}

		static class NopExpression extends FakeCallExpression {
			NopExpression(CallExpression original) {
				super(original)
			}

			KismetObject evaluate(Context c) {
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

			KismetObject evaluate(Context c) {
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

			KismetObject evaluate(Context c) {
				Kismet.model(arguments.empty ? macro.x.call(c) : macro.x.call(c, arguments as Expression[]))
			}

			String repr() { "gmacro[${callValue.repr()}](${arguments*.repr().join(', ')})" }
		}

		static class ForExpression extends FakeCallExpression {
			String name = 'it'
			int bottom = 1, top = 0, step = 1
			Expression nameExpr, bottomExpr, topExpr, stepExpr
			Expression block

			ForExpression(CallExpression original, Context c) {
				super(original)
				def first = original.arguments[0]
				def exprs = first instanceof CallExpression ? ((CallExpression) first).expressions : [first]
				final size = exprs.size()
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
					else if (exprs[0] instanceof PathExpression &&
							(((PathExpression) exprs[0]).path.expressions.size() == 1)) {
						name = ((PathExpression) exprs[0]).text
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

			KismetObject evaluate(Context c) {
				int b = bottomExpr == null ? bottom : bottomExpr.evaluate(c).inner() as int
				final top = topExpr == null ? top : topExpr.evaluate(c).inner() as int
				final step = stepExpr == null ? step : stepExpr.evaluate(c).inner() as int
				final name = nameExpr == null ? name : nameExpr.evaluate(c).toString()
				for (; b <= top; b += step) {
					def k = c.child()
					k.set(name, Kismet.model(b))
					block.evaluate(c)
				}
				Kismet.NULL
			}

			String repr() { "for(${arguments*.repr().join(', ')})" }
		}

		static class CollectForExpression extends FakeCallExpression {
			String name = 'it'
			int bottom = 1, top = 0, step = 1
			Expression nameExpr, bottomExpr, topExpr, stepExpr
			Expression block

			CollectForExpression(CallExpression original, Context c) {
				super(original)
				def first = original.arguments[0]
				def exprs = first instanceof CallExpression ? ((CallExpression) first).expressions : [first]
				final size = exprs.size()
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
					else if (exprs[0] instanceof PathExpression &&
							(((PathExpression) exprs[0]).path.expressions.size() == 1)) {
						name = ((PathExpression) exprs[0]).text
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

			KismetObject evaluate(Context c) {
				int b = bottomExpr == null ? bottom : bottomExpr.evaluate(c).inner() as int
				final top = topExpr == null ? top : topExpr.evaluate(c).inner() as int
				final step = stepExpr == null ? step : stepExpr.evaluate(c).inner() as int
				final name = nameExpr == null ? name : nameExpr.evaluate(c).toString()
				def result = new ArrayList()
				for (; b <= top; b += step) {
					def k = c.child()
					k.set(name, Kismet.model(b))
					result.add(block.evaluate(c).inner())
				}
				Kismet.model(result)
			}

			String repr() { "&for(${arguments*.repr().join(', ')})" }
		}

		static class ForLessExpression extends FakeCallExpression {
			String name = 'it'
			int bottom = 0, top = 0, step = 1
			Expression nameExpr, bottomExpr, topExpr, stepExpr
			Expression block

			ForLessExpression(CallExpression original, Context c) {
				super(original)
				def first = original.arguments[0]
				def exprs = first instanceof CallExpression ? ((CallExpression) first).expressions : [first]
				final size = exprs.size()
				if (size == 0) {
					top = 1
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
					else if (exprs[0] instanceof PathExpression &&
							(((PathExpression) exprs[0]).path.expressions.size() == 1)) {
						name = ((PathExpression) exprs[0]).text
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

			KismetObject evaluate(Context c) {
				int b = bottomExpr == null ? bottom : bottomExpr.evaluate(c).inner() as int
				final top = topExpr == null ? top : topExpr.evaluate(c).inner() as int
				final step = stepExpr == null ? step : stepExpr.evaluate(c).inner() as int
				final name = nameExpr == null ? name : nameExpr.evaluate(c).toString()
				for (; b < top; b += step) {
					def k = c.child()
					k.set(name, Kismet.model(b))
					block.evaluate(c)
				}
				Kismet.NULL
			}

			String repr() { "for<(${arguments*.repr().join(', ')})" }
		}

		static class CollectForLessExpression extends FakeCallExpression {
			String name = 'it'
			int bottom = 0, top, step = 1
			Expression nameExpr, bottomExpr, topExpr, stepExpr
			Expression block

			CollectForLessExpression(CallExpression original, Context c) {
				super(original)
				def first = original.arguments[0]
				def exprs = first instanceof CallExpression ? ((CallExpression) first).expressions : [first]
				final size = exprs.size()
				if (size == 0) {
					top = 1
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
					else if (exprs[0] instanceof PathExpression &&
							(((PathExpression) exprs[0]).path.expressions.size() == 1)) {
						name = ((PathExpression) exprs[0]).text
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

			KismetObject evaluate(Context c) {
				int b = bottomExpr == null ? bottom : bottomExpr.evaluate(c).inner() as int
				final top = topExpr == null ? top : topExpr.evaluate(c).inner() as int
				final step = stepExpr == null ? step : stepExpr.evaluate(c).inner() as int
				final name = nameExpr == null ? name : nameExpr.evaluate(c).toString()
				def result = new ArrayList()
				for (; b < top; b += step) {
					def k = c.child()
					k.set(name, Kismet.model(b))
					result.add(block.evaluate(c).inner())
				}
				Kismet.model(result)
			}

			String repr() { "&for<(${arguments*.repr().join(', ')})" }
		}
	}

	@CompileStatic
	class NumberBuilder extends ExprBuilder<NumberExpression> {
		static final String[] stageNames = ['number', 'fraction', 'exponent', 'number type bits']
		StringBuilder[] arr = [new StringBuilder(), null, null, null]
		int stage = 0
		boolean newlyStage = true
		boolean type

		def init(int s) {
			stage = s
			arr[s] = new StringBuilder()
			newlyStage = true
		}

		NumberExpression doPush(int cp) {
			int up
			if (cp > 47 && cp < 58) {
				newlyStage = false
				arr[stage].appendCodePoint(cp)
			} else if (cp == 46) {
				if (stage == 0) { if (newlyStage) { arr[0].append('0') }; init 1 }
				else throw new NumberFormatException('Tried to put fraction after ' + stageNames[stage])
			} else if (!newlyStage && (cp == 101 || cp == 69)) {
				if (stage < 2) init 2
				else throw new NumberFormatException('Tried to put exponent after ' + stageNames[stage])
			} else if ((up = Character.toUpperCase(cp)) == 73 || up == 70) {
				if (stage == 3) throw new NumberFormatException('Tried to put number type bits after number type bits')
				else {
					type = up == 70
					init 3
				}
			} else if (newlyStage && stage != 3) throw new NumberFormatException('Started number but wasnt number')
			else return new NumberExpression(type, arr)
			(NumberExpression) null
		}
	}

	@CompileStatic
	class PathBuilder extends ExprBuilder<PathExpression> {
		StringBuilder last = new StringBuilder()
		boolean escaped = false
		boolean bracketed

		PathBuilder(boolean b) { bracketed = b }

		PathExpression doPush(int cp) {
			if ((bracketed && !escaped && cp == 41) || (!bracketed && Character.isWhitespace(cp)))
				return new PathExpression(last.toString())
			if (escaped) {
				escaped = false
				if (cp == 41) last.deleteCharAt(last.length() - 1)
			}
			else escaped = cp == 92
			last.appendCodePoint(cp)
			(PathExpression) null
		}
	}

	@CompileStatic
	class NameBuilder extends ExprBuilder<NameExpression> {
		StringBuilder builder = new StringBuilder()

		static boolean isNotIdentifier(int cp) {
			Character.isWhitespace(cp) || cp == ((char) '.') || cp == ((char) '[') ||
				cp == ((char) '(') || cp == ((char) '{') || cp == ((char) ']') ||
				cp == ((char) ')') || cp == ((char) '}')
		}

		@Override
		NameExpression doPush(int cp) {
			if (isNotIdentifier(cp)) {
				goBack = true
				return new NameExpression(builder.toString())
			}
			builder.appendCodePoint(cp)
			null
		}
	}

	@CompileStatic
	class PathBuilder2 extends ExprBuilder<PathExpression2> {
		Expression root
		List<PathExpression2.Step> steps = []
		ExprBuilder last
		boolean inPropertyQueue

		PathBuilder2(Expression root) {
			this.root = root
		}

		@Override
		PathExpression2 doPush(int cp) {
			if (inPropertyQueue) {
				inPropertyQueue = false
				if (cp == ((char) '[')) { last = new CallBuilder(true); return null }
				else if (cp == ((char) '`')) { last = new QuoteAtomBuilder(); return null }
				else last = new NameBuilder()
			}
			if (null != last) {
				def e = last.push(cp)
				if (null != e) {
					if (e instanceof NameExpression) {
						steps.add(new PathExpression2.PropertyStep(((NameExpression) e).text))
					} else if (e instanceof CallExpression) {
						Expression a
						final j = (CallExpression) e
						if (j.arguments.empty) a = j.callValue
						else a = e
						steps.add(new PathExpression2.SubscriptStep(a))
					} else throw new UnexpectedSyntaxException("Unkonown path expression type ${e.class}")
					final back = last.goBack
					last = null
					if (back) return doPush(cp)
				}
			} else {
				if (cp == ((char) '.')) inPropertyQueue = true
				else if (cp == ((char) '[')) last = new CallBuilder(true)
				else {
					goBack = true
					return new PathExpression2(root, steps)
				}
			}
			null
		}
	}

	@CompileStatic
	class StringExprBuilder extends ExprBuilder<StringExpression> {
		StringBuilder last = new StringBuilder()
		boolean escaped = false
		int quote

		StringExprBuilder(int q) {
			quote = q
		}

		StringExpression doPush(int cp) {
			if (!escaped && cp == quote) return new StringExpression(last.toString())
			if (escaped) escaped = false
			else escaped = cp == 92
			last.appendCodePoint(cp)
			(StringExpression) null
		}

		Expression push(int cp) {
			def x = doPush(cp)
			null == x ? x : percent ? percentize(x) : x
		}

		Expression percentize(StringExpression x) {
			def text = x.value.inner()
			Expression result = x
			for (t in text.tokenize()) switch (t) {
				case 'optimize_prelude': optimizePrelude = true; break
				case '!optimize_prelude': optimizePrelude = false; break
				case '?optimize_prelude':
					result = new StaticExpression(x, optimizePrelude)
					break
				case 'optimize_closure': optimizeClosure = true; break
				case '!optimize_closure': optimizeClosure = false; break
				case '?optimize_closure':
					result = new StaticExpression(x, optimizeClosure)
					break
				case 'optimize_pure': optimizePure = true; break
				case '!optimize_pure': optimizePure = false; break
				case '?optimize_pure':
					result = new StaticExpression(x, optimizePure)
					break
				case 'optimize': optimizePure = optimizePrelude = true; break
				case '!optimize': optimizePure = optimizeClosure = optimizePrelude = false; break
				case '?optimize':
					result = new StaticExpression(x, optimizePure || optimizeClosure || optimizePrelude)
					break
				case 'parser': result = new StaticExpression(x, ParserW.this); break
			}
			result
		}
	}

	@CompileStatic
	class QuoteAtomBuilder extends ExprBuilder<NameExpression> {
		StringBuilder last = new StringBuilder()
		boolean escaped = false

		NameExpression doPush(int cp) {
			if (!escaped && cp == ((char) '`'))
				return new NameExpression(last.toString())
			if (escaped) escaped = false
			else escaped = cp == 92
			last.appendCodePoint(cp)
			(NameExpression) null
		}
	}

	static PathExpression atom(String name) {
		def x = new PathExpression(new Path(new Path.PropertyPathStep(name)))
		x.text = x.path.raw = name
		x
	}
}
