package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.call.*
import hlaaftana.kismet.exceptions.ParseException
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.vm.Context

@CompileStatic
class Parser {
	Optimizer optimizer = new Optimizer(this)
	Context context
	int ln = 1, cl = 0
	String commentStart = ';;'

	@SuppressWarnings('GroovyVariableNotAssigned')
	BlockExpression parse(String code) {
		BlockBuilder builder = new BlockBuilder(this, false)
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
					comment = Arrays.copyOfRange(arr, i, i + commentStart.length()) == commentStart.toCharArray()
			}
			if (comment) continue
			try {
				builder.push(c)
			} catch (ex) {
				throw new ParseException(ex, ln, cl)
			}
		}
		toBlock(optimizer.optimize(builder.finish()))
	}

	static BlockExpression toBlock(Expression expr) {
		expr instanceof BlockExpression ? (BlockExpression) expr : new BlockExpression([expr])
	}

	// non-static clcasses break this entire file in stub generation
	abstract static class ExprBuilder<T extends Expression> {
		Parser parser
		int ln, cl
		boolean percent = false
		boolean goBack = false

		ExprBuilder(Parser p) {
			parser = p
			ln = parser.ln
			cl = parser.cl
		}

		abstract T doPush(int cp)

		Expression push(int cp) {
			fulfillResult(doPush(cp))
		}

		Expression fulfillResult(T x) {
			final ex = percent ? x?.percentize(parser) : x
			if (null != ex) {
				ex.ln = ln
				ex.cl = cl
			}
			ex
		}

		T doFinish() { throw new UnsupportedOperationException('Can\'t finish') }

		Expression finish() {
			fulfillResult(doFinish())
		}

		boolean waitingForDelim() { false }

		boolean isWarrior() { false }
	}

	static class BlockBuilder extends ExprBuilder<BlockExpression> {
		List<Expression> expressions = []
		CallBuilder last = null
		boolean lastPercent = false
		boolean bracketed
		boolean requireSeparator = false
		char separator = (char) ';'
		char bracket = (char) '}'
		boolean isCallArgs

		BlockBuilder(Parser p, boolean b) { super(p); bracketed = b }

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
				if (cp == ((char) '[')) last = new CallBuilder(parser, true)
				else if (cp == ((char) '%')) lastPercent = true
				else if (!Character.isWhitespace(cp)) {
					if (requireSeparator) {
						last = new CallBuilder(parser, true)
						last.bracket = separator
						last.push(cp)
					} else (last = new CallBuilder(parser, false)).push(cp)
				}
				if (lastPercent && last != null) {
					last.percent = true
					lastPercent = false
				}
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
				expressions.add(last.percent ? a.percentize(parser) :
						a instanceof CallExpression && parser.optimizer.prelude ?
								parser.optimizer.optimize((CallExpression) a) : a)
			} else expressions.add(x)
		}
	}

	static class CallBuilder extends ExprBuilder<CallExpression> {
		List<Expression> expressions = []
		ExprBuilder last = null
		boolean lastPercent = false
		boolean bracketed
		char bracket

		CallBuilder(Parser p, boolean b, char bracket = ((char) ']')) { super(p); bracketed = b; this.bracket = bracket }

		@Override
		CallExpression doPush(int cp) {
			if ((bracketed ? cp == bracket : (cp == 10 || cp == 13)) && endOnDelim) {
				return doFinish()
			} else if (null == last) {
				if (bracketed && cp == bracket) return new CallExpression(expressions)
				else if (cp == ((char) '%')) lastPercent = true
				else if (cp == ((char) '(')) {
					final b = new BlockBuilder(parser, true)
					b.bracket = (char) ')'
					b.requireSeparator = true
					last = b
				} else if (cp == ((char) '[')) last = new CallBuilder(parser, true)
				else if (cp == ((char) '{')) last = new BlockBuilder(parser, true)
				else if (cp > 47 && cp < 58) (last = new NumberBuilder(parser)).push(cp)
				else if (cp == ((char) '"') || cp == ((char) '\'')) last = new StringExprBuilder(parser, cp)
				else if (cp == ((char) '`')) last = new QuoteAtomBuilder(parser)
				else if (cp == ((char) '.'))
					(last = new PathBuilder(parser, expressions.empty ? null : expressions.pop())).push(cp)
				else if (!Character.isWhitespace(cp)) (last = new NameBuilder(parser)).push(cp)
				if (lastPercent && null != last) {
					last.percent = true
					lastPercent = false
				}
			} else {
				Expression x = last.push(cp)
				if (null != x) {
					if (last instanceof NameBuilder && cp == ((char) '[')) {
						(last = new PathBuilder(parser, x)).push(cp)
					} else {
						add x
						final back = last.goBack
						last = null
						if (back && cp != ((char) '(')) return doPush(cp)
					}
					if (cp == ((char) '(')) {
						def bb = new BlockBuilder(parser, true)
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
			if (percent) r = r.percentize(parser)
			//else if (parser.optimizer.prelude) r = parser.optimizer.optimize(x)
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
				x = new CallExpression(t + ((BlockExpression) x).content)
				// x = parser.optimizer.prelude ? parser.optimizer.optimize(call) : call
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
	}

	@InheritConstructors
	static class NumberBuilder extends ExprBuilder<NumberExpression> {
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
				if (stage == 0) {
					if (newlyStage) arr[0].append((char) '0')
					init 1
				} else throw new NumberFormatException('Tried to put fraction after ' + stageNames[stage])
			} else if (!newlyStage && (cp == 101 || cp == 69)) {
				if (stage < 2) init 2
				else throw new NumberFormatException('Tried to put exponent after ' + stageNames[stage])
			} else if ((up = Character.toUpperCase(cp)) == 73 || up == 70) {
				if (stage == 3) throw new NumberFormatException('Tried to put number type getBits after number type getBits')
				else {
					type = up == 70
					init 3
				}
			} else if (newlyStage && stage != 3) throw new NumberFormatException('Started number but wasnt number')
			else {
				goBack = true; return new NumberExpression(type, arr)
			}
			(NumberExpression) null
		}

		NumberExpression doFinish() {
			new NumberExpression(type, arr)
		}
	}

	static class NameBuilder extends ExprBuilder<NameExpression> {
		StringBuilder builder = new StringBuilder()

		NameBuilder(Parser p) { super(p) }

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

	static class PathBuilder extends ExprBuilder<PathExpression> {
		Expression root
		List<PathExpression.Step> steps = []
		ExprBuilder last
		boolean inPropertyQueue

		PathBuilder(Parser p, Expression root) {
			super(p)
			this.root = root
		}

		@Override
		PathExpression doPush(int cp) {
			if (inPropertyQueue) {
				inPropertyQueue = false
				if (cp == ((char) '[')) {
					last = new CallBuilder(parser, true); return null
				} else if (cp == ((char) '(')) {
					final b = new BlockBuilder(parser, true)
					b.bracket = (char) ')'
					b.requireSeparator = true
					last = b
					return null
				} else if (cp == ((char) '`')) {
					last = new QuoteAtomBuilder(parser); return null
				} else last = new NameBuilder(parser)
			}
			if (null != last) {
				def e = last.push(cp)
				if (null != e) {
					add(e)
					final back = last.goBack
					last = null
					if (back) return doPush(cp)
				}
			} else {
				if (cp == ((char) '.')) inPropertyQueue = true
				else if (cp == ((char) '[')) last = new CallBuilder(parser, true)
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
				final j = (CallExpression) e
				steps.add(new PathExpression.SubscriptStep(j.arguments.empty ? j.callValue : e))
			} else if (e instanceof BlockExpression) {
				steps.add(new PathExpression.EnterStep(e))
			} else throw new UnexpectedSyntaxException("Unkonown path expression type ${e.class}")
		}

		boolean waitingForDelim() { last != null && last.waitingForDelim() }

		PathExpression doFinish() {
			if (null != last) {
				add last.finish()
				last = null
			}
			new PathExpression(root, steps)
		}
	}

	static class StringExprBuilder extends ExprBuilder<StringExpression> {
		StringBuilder last = new StringBuilder()
		boolean escaped = false
		int quote

		StringExprBuilder(Parser p, int q) {
			super(p)
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
				case 'optimize_prelude': parser.optimizer.prelude = true; break
				case '!optimize_prelude': parser.optimizer.prelude = false; break
				case '?optimize_prelude':
					result = new StaticExpression(x, parser.optimizer.prelude)
					break
				case 'optimize_closure': parser.optimizer.closure = true; break
				case '!optimize_closure': parser.optimizer.closure = false; break
				case '?optimize_closure':
					result = new StaticExpression(x, parser.optimizer.closure)
					break
				case 'optimize_pure': parser.optimizer.pure = true; break
				case '!optimize_pure': parser.optimizer.pure = false; break
				case '?optimize_pure':
					result = new StaticExpression(x, parser.optimizer.pure)
					break
				case 'fill_templates': parser.optimizer.template = true; break
				case '!fill_templates': parser.optimizer.template = false; break
				case '?fill_templates':
					result = new StaticExpression(x, parser.optimizer.template)
					break
				case 'optimize': parser.optimizer.on(); break
				case '!optimize': parser.optimizer.off(); break
				case '?optimize':
					result = new StaticExpression(x, parser.optimizer.template ||
							parser.optimizer.pure || parser.optimizer.closure || parser.optimizer.prelude)
					break
				case 'parser': result = new StaticExpression(x, parser); break
			}
			result
		}

		StringExpression doFinish() {
			new StringExpression(last.toString())
		}

		boolean waitingForDelim() { true }

		boolean isWarrior() { true }
	}

	@InheritConstructors
	static class QuoteAtomBuilder extends ExprBuilder<NameExpression> {
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
