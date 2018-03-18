package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import hlaaftana.kismet.Context
import hlaaftana.kismet.Function
import hlaaftana.kismet.GroovyFunction
import hlaaftana.kismet.GroovyMacro
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.KismetFunction
import hlaaftana.kismet.KismetInner
import hlaaftana.kismet.KismetObject
import hlaaftana.kismet.Macro
import hlaaftana.kismet.ParseException
import hlaaftana.kismet.Template
import hlaaftana.kismet.UndefinedVariableException
import hlaaftana.kismet.UnexpectedSyntaxException
import hlaaftana.kismet.UnexpectedValueException

import java.math.RoundingMode

@CompileStatic
class Parser {
	Context context
	int ln = 1, cl = 0
	boolean optimizePrelude
	boolean optimizeClosure
	boolean optimizePure
	boolean fillTemplate
	boolean path2 = true
	String commentStart = ';;'

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
				if (!comment && builder.warrior)
					comment = Arrays.copyOfRange(arr, i, i+commentStart.length()) == commentStart.toCharArray()
			}
			if (comment) continue
			try {
				builder.push(c)
			} catch (ex) {
				throw new ParseException(ex, ln, cl)
			}
		}
		toBlock(builder.finish())
	}

	static BlockExpression toBlock(Expression expr) {
		expr instanceof BlockExpression ? (BlockExpression) expr : new BlockExpression([expr])
	}

	abstract class ExprBuilder<T extends Expression> {
		boolean percent = false
		boolean goBack = false

		abstract T doPush(int cp)

		Expression push(int cp) {
			fulfillResult(doPush(cp))
		}

		Expression fulfillResult(T x) {
			null == x ? x : percent ? x.percentize(context) : x
		}

		T doFinish() { throw new UnsupportedOperationException('Can\'t finish') }

		Expression finish() {
			fulfillResult(doFinish())
		}

		boolean waitingForDelim() { false }
		boolean isWarrior() { false }
	}

	class BlockBuilder extends ExprBuilder<BlockExpression> {
		List<Expression> expressions = []
		CallBuilder last = null
		boolean lastPercent = false
		boolean bracketed
		boolean requireSeparator = false
		char separator = (char) ';'
		char bracket = (char) '}'
		boolean isCallArgs

		BlockBuilder(boolean b) { bracketed = b }

		@Override
		BlockExpression doPush(int cp) {
			final lastNull = null == last
			if (bracketed && cp == bracket && (lastNull || (last.endOnDelim && !last.anyBlocks()))) {
				return doFinish()
			} else if (cp == separator && (lastNull || (last.endOnDelim && !last.anyBlocks()))) {
				def x = last?.finish()
				add x
				last = null
			} else if (lastNull) {
				if (cp == 91) last = new CallBuilder(true)
				else if (cp == 37) lastPercent = true
				else if (!Character.isWhitespace(cp)) {
					if (requireSeparator) {
						last = new CallBuilder(true)
						last.bracket = separator
						last.push(cp)
					} else (last = new CallBuilder(false)).push(cp)
				}
				if (lastPercent && last != null) last.percent = !(lastPercent = false)
			} else {
				CallExpression x = last.doPush(cp)
				if (null != x) {
					add x
					final back = last.goBack
					last = null
					if (back) return doPush(cp)
				}
			}
			bracketed && cp == bracket && null == last ? doFinish() : null
		}

		boolean isWarrior() {
			null == last || last.warrior
		}

		boolean waitingForDelim() { bracketed }

		BlockExpression doFinish() {
			if (last != null) {
				add last.finish()
				last = null
			}
			new BlockExpression(expressions)
		}

		void add(Expression x) {
			if (null == x) return
			if (x instanceof CallExpression) {
				Expression a = (!last.bracketed || last.bracket == separator) &&
						!((CallExpression) x).arguments ? ((CallExpression) x).callValue : x
				expressions.add(last.percent ? a.percentize(context) :
						a instanceof CallExpression && optimizePrelude ?
								last.optimize((CallExpression) a) : a)
			} else expressions.add(x)
		}
	}

	class CallBuilder extends ExprBuilder<CallExpression> {
		List<Expression> expressions = []
		ExprBuilder last = null
		boolean lastPercent = false
		boolean bracketed
		char bracket

		CallBuilder(boolean b, char bracket = ((char) ']')) { bracketed = b; this.bracket = bracket }

		@Override
		CallExpression doPush(int cp) {
			if ((bracketed ? cp == bracket : (cp == 10 || cp == 13)) && endOnDelim) {
				return doFinish()
			} else if (null == last) {
				if (bracketed && cp == bracket) return new CallExpression(expressions)
				else if (cp == 37) lastPercent = true
				else if (cp == 40) {
					final b = new BlockBuilder(true)
					b.bracket = (char) ')'
					b.requireSeparator = true
					last = b
				}
				else if (cp == 91) last = new CallBuilder(true)
				else if (cp == 123) last = new BlockBuilder(true)
				else if (cp > 47 && cp < 58) (last = new NumberBuilder()).push(cp)
				else if (cp == 34 || cp == 39) last = new StringExprBuilder(cp)
				else if (cp == ((char) '`')) last = new QuoteAtomBuilder()
				else if (cp == ((char) '.'))
					(last = new PathBuilder(expressions.empty ? null : expressions.pop())).push(cp)
				else if (!Character.isWhitespace(cp)) (last = path2 ? new NameBuilder() : new DumbPathBuilder(false)).push(cp)
				if (lastPercent && null != last) last.percent = !(lastPercent = false)
			} else {
				Expression x = last.push(cp)
				if (null != x) {
					if (last instanceof NameBuilder && cp == ((char) '[')) {
						(last = new PathBuilder(x)).push(cp)
					} else {
						add x
						final back = last.goBack
						last = null
						if (back && cp != ((char) '(')) return doPush(cp)
					}
					if (cp == ((char) '(')) {
						def bb = new BlockBuilder(true)
						bb.bracket = (char) ')'
						bb.separator = (char) ','
						bb.isCallArgs = true
						bb.requireSeparator = true
						last = bb
					}
				}
			}
			(CallExpression) null
		}

		@Override
		Expression fulfillResult(CallExpression x) {
			if (null == x) return x
			Expression r = x
			if (percent) r = r.percentize(context)
			else if (optimizePrelude) r = optimize(x)
			r
		}

		void add(Expression x) {
			if (last instanceof BlockBuilder && ((BlockBuilder) last).isCallArgs) {
				final p = expressions.pop()
				List<Expression> t = new ArrayList<>()
				if (p instanceof PathExpression) {
					final pe = (PathExpression) p
					final r = pe.steps.last()
					if (r instanceof PathExpression.PropertyStep)
						t.add(new NameExpression(((PathExpression.PropertyStep) r).name))
					else if (r instanceof PathExpression.SubscriptStep)
						t.add(((PathExpression.SubscriptStep) r).expression)
					else throw new UnexpectedSyntaxException('Unknown step thing')
					t.add new PathExpression(pe.root, pe.steps.init())
				} else t.add p
				final call = new CallExpression(t + ((BlockExpression) x).content)
				x = optimizePrelude ? optimize(call) : call
			}
			expressions.add(x)
		}

		CallExpression doFinish() {
			if (last != null) {
				add last.finish()
				last = null
			}
			new CallExpression(expressions)
		}

		boolean isEndOnDelim() {
			last == null || !last.waitingForDelim()
		}

		boolean isWarrior() {
			last == null || last.warrior
		}

		boolean anyBlocks() {
			last instanceof BlockBuilder || (last instanceof CallBuilder && ((CallBuilder) last).anyBlocks())
		}

		boolean waitingForDelim() { bracketed }

		Expression optimize(CallExpression expr) {
			if (expr.callValue instanceof NameExpression) {
				def text = ((NameExpression) expr.callValue).text
				KismetObject func
				try {
					func = context?.get(text)
				} catch (UndefinedVariableException ignored) {}
				if (null != func) {
					def inner = func.inner()
					int equalsType = 0
					if (inner instanceof Template && fillTemplate)
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
								rounder = new RoundExpression(context, expr, (NameExpression) p)
							else rounder = new RoundExpression(context, expr)
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
								def atom = KismetInner.toAtom(name)
								def orig = new CallExpression([expr.callValue, name, last])
								if (null != atom) switch (equalsType) {
									case 3: last = new ChangeExpression(orig, atom, last); break
									case 2: last = new ContextSetExpression(orig, atom, last); break
									case 1: last = new DefineExpression(orig, atom, last); break
									case 0: last = new AssignExpression(orig, atom, last); break
								} else if (name instanceof CallExpression)
									last = new DefineFunctionExpression(orig)
								else if (name instanceof PathExpression)
									last = new PathStepExpression(orig, (PathExpression) name)
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
						case "for": return new ForExpression(expr, context, false, false)
						case "for<": return new ForExpression(expr, context, false, true)
						case "&for": return new ForExpression(expr, context, true, false)
						case "&for<": return new ForExpression(expr, context, true, true)
						case "for:": return new ForEachExpression(expr, context, false)
						case "&for:": return new ForEachExpression(expr, context, true)
						case "check": return new CheckExpression(expr)
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

			KismetObject evaluate(Context c) {
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

			KismetObject evaluate(Context c) {
				step.apply(c, value.evaluate(c))
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
							branches.add new CallExpression([new NameExpression(KismetInner.isAlpha(text) ? text + '?' : text),
									new NameExpression(name)] as List<Expression>)
						} else if (a instanceof BlockExpression) {
							addBranches(((BlockExpression) a).content)
							continue
						} else branches.add(new CallExpression([new NameExpression('is?'), new NameExpression(name), a]))
						branches.add(iter.next())
					} else branches.add(a)
				}
			}

			KismetObject evaluate(Context c) {
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
				if (a instanceof NameExpression) {
					name = ((NameExpression) a).text
					args = new KismetFunction.Arguments(null)
				} else if (a instanceof CallExpression) {
					name = ((NameExpression) ((CallExpression) a).callValue).text
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
				call.callValue = new NameExpression('fn')
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

			KismetObject evaluate(Context c) {
				c.define(name, c.eval(expression))
			}

			String repr() { "define[$name, ${expression.repr()}]" }
		}

		static class ChangeExpression extends VariableModifyExpression {
			ChangeExpression(CallExpression original, String name, Expression expression) {
				super(original, name, expression)
			}

			KismetObject evaluate(Context c) {
				c.change(name, c.eval(expression))
			}

			String repr() { "change[$name, ${expression.repr()}]" }
		}

		static class AssignExpression extends VariableModifyExpression {
			AssignExpression(CallExpression original, String name, Expression expression) {
				super(original, name, expression)
			}

			KismetObject evaluate(Context c) {
				c.assign(name, c.eval(expression))
			}

			String repr() { "assign[$name, ${expression.repr()}]" }
		}

		static class ContextSetExpression extends VariableModifyExpression {
			ContextSetExpression(CallExpression original, String name, Expression expression) {
				super(original, name, expression)
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

			KismetObject evaluate(Context c) {
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

			String repr() { (collect ? '&' : '') + 'for' + (less ? '<' : '') + '(' + arguments*.repr().join(', ') + ')' }
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
					final atom = KismetInner.toAtom(exprs[0])
					if (atom == null) nameExpr = exprs[0]
					else name = atom
				} else if (size == 2) {
					final atom = KismetInner.toAtom(exprs[0])
					if (atom == null) nameExpr = exprs[0]
					else name = atom

					if (exprs[1] instanceof ConstantExpression)
						iterator = KismetInner.toIterator(c.eval(exprs[1]).inner())
					else iterExpr = exprs[1]
				} else if (size >= 3) {
					final f = exprs[0]
					if (f instanceof CallExpression) {
						final cf = ((CallExpression) f).expressions
						indexName(cf[0])
						indexStart(c, cf[cf.size() > 1 ? 1 : 0])
					} else indexName(f)

					final atom = KismetInner.toAtom(exprs[1])
					if (atom == null) nameExpr = exprs[1]
					else name = atom

					if (exprs[2] instanceof ConstantExpression)
						iterator = KismetInner.toIterator(c.eval(exprs[2]).inner())
					else iterExpr = exprs[2]
				}
				def tail = arguments.tail()
				block = tail.size() == 1 ? tail[0] : new BlockExpression(tail)
			}

			private void indexName(Expression expr) {
				final atom = KismetInner.toAtom(expr)
				if (atom == null) indexNameExpr = expr
				else indexName = atom
			}

			private void indexStart(Context c, Expression expr) {
				if (expr instanceof ConstantExpression) indexStart = (int) expr.evaluate(c).as(int)
				else indexStartExpr = expr
			}

			KismetObject evaluate(Context c) {
				int i = indexStartExpr == null ? indexStart : indexStartExpr.evaluate(c).inner() as int
				final iter = iterExpr == null ? iterator : KismetInner.toIterator(iterExpr.evaluate(c).inner())
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
			else { goBack = true; return new NumberExpression(type, arr) }
			(NumberExpression) null
		}

		NumberExpression doFinish() {
			new NumberExpression(type, arr)
		}
	}

	class DumbPathBuilder extends ExprBuilder {
		StringBuilder last = new StringBuilder()
		boolean escaped = false
		boolean bracketed

		DumbPathBuilder(boolean b) { bracketed = b }

		Expression doPush(int cp) {
			if ((bracketed && !escaped && cp == 41) || (!bracketed && Character.isWhitespace(cp)))
				return convert(last.toString())
			if (escaped) {
				escaped = false
				if (cp == 41) last.deleteCharAt(last.length() - 1)
			}
			else escaped = cp == 92
			last.appendCodePoint(cp)
			(Expression) null
		}

		Expression convert(String str) {
			def path = new DumbParser.Path(str)
			def ex = path.expressions
			NameExpression root = null
			if (!str.startsWith('.') && ex[0] instanceof DumbParser.Path.PropertyPathStep) {
				root = new NameExpression(ex[0].raw)
				if (ex.size() == 1) return root
				ex = ex.drop(1)
			}
			def newEx = new ArrayList<PathExpression.Step>(ex.size())
			for (e in ex) {
				if (e instanceof DumbParser.Path.SubscriptPathStep) {
					newEx.add(new PathExpression.SubscriptStep(new StaticExpression(((DumbParser.Path.SubscriptPathStep) e).val)))
				} else if (e instanceof DumbParser.Path.PropertyPathStep) {
					newEx.add(new PathExpression.PropertyStep(((DumbParser.Path.PropertyPathStep) e).raw))
				} else throw new UnexpectedSyntaxException("Unknown path step $e")
			}
			new PathExpression(root, newEx)
		}

		boolean waitingForDelim() { bracketed }

		Expression doFinish() {
			convert(last.toString())
		}
	}

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

		NameExpression doFinish() {
			new NameExpression(builder.toString())
		}
	}

	class PathBuilder extends ExprBuilder<PathExpression> {
		Expression root
		List<PathExpression.Step> steps = []
		ExprBuilder last
		boolean inPropertyQueue

		PathBuilder(Expression root) {
			this.root = root
		}

		@Override
		PathExpression doPush(int cp) {
			if (inPropertyQueue) {
				inPropertyQueue = false
				if (cp == ((char) '[')) { last = new CallBuilder(true); return null }
				else if (cp == ((char) '`')) { last = new QuoteAtomBuilder(); return null }
				else last = new NameBuilder()
			}
			if (null != last) {
				def e = last.push(cp)
				if (null != e) {
					add e
					final back = last.goBack
					last = null
					if (back) return doPush(cp)
				}
			} else {
				if (cp == ((char) '.')) inPropertyQueue = true
				else if (cp == ((char) '[')) last = new CallBuilder(true)
				else {
					goBack = true
					return new PathExpression(root, steps)
				}
			}
			null
		}

		void add(Expression e) {
			if (e instanceof NameExpression) {
				steps.add(new PathExpression.PropertyStep(((NameExpression) e).text))
			} else if (e instanceof CallExpression) {
				Expression a
				final j = (CallExpression) e
				if (j.arguments.empty) a = j.callValue
				else a = e
				steps.add(new PathExpression.SubscriptStep(a))
			} else throw new UnexpectedSyntaxException("Unkonown path expression type ${e.class}")
		}

		boolean waitingForDelim() { last == null || last.waitingForDelim() }

		PathExpression doFinish() {
			if (null != last) {
				add last.finish()
				last = null
			}
			new PathExpression(root, steps)
		}
	}

	class StringExprBuilder extends ExprBuilder<StringExpression> {
		StringBuilder last = new StringBuilder()
		boolean escaped = false
		int quote

		StringExprBuilder(int q) {
			quote = q
		}

		StringExpression doPush(int cp) {
			if (!escaped && cp == quote)
				return new StringExpression(last.toString())
			escaped = !escaped && cp == ((char) '\\')
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
				case 'parser': result = new StaticExpression(x, Parser.this); break
			}
			result
		}

		StringExpression doFinish() {
			new StringExpression(last.toString())
		}

		boolean waitingForDelim() { true }
		boolean isWarrior() { true }
	}

	class QuoteAtomBuilder extends ExprBuilder<NameExpression> {
		StringBuilder last = new StringBuilder()
		boolean escaped = false

		NameExpression doPush(int cp) {
			if (!escaped && cp == ((char) '`'))
				return new NameExpression(last.toString())
			escaped = !escaped && cp == ((char) '\\')
			last.appendCodePoint(cp)
			(NameExpression) null
		}

		NameExpression doFinish() {
			new NameExpression(last.toString())
		}

		boolean waitingForDelim() { true }
		boolean isWarrior() { true }
	}
}
