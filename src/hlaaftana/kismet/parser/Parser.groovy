package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.call.*
import hlaaftana.kismet.call.PathExpression.Step
import hlaaftana.kismet.exceptions.ParseException
import hlaaftana.kismet.scope.Context

@CompileStatic
class Parser {
	Optimizer optimizer = new Optimizer(this)
	Context context
	int ln = 1, cl = 0
	String commentStart = ';;'

	BlockExpression parse(String code) {
		toBlock(optimizer.optimize(parseAST(code)))
	}

	@SuppressWarnings('GroovyVariableNotAssigned')
	Expression parseAST(String code) {
		BlockBuilder builder = new BlockBuilder(this)
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
				if (!comment && !builder.overrideComments)
					comment = Arrays.copyOfRange(arr, i, i + commentStart.length()) == commentStart.toCharArray()
			}
			if (comment) continue
			try {
				builder.push(c)
			} catch (ex) {
				throw new ParseException(ex, ln, cl)
			}
		}
		builder.finish()
	}

	static BlockExpression toBlock(Expression expr) {
		expr instanceof BlockExpression ? (BlockExpression) expr : new BlockExpression([expr])
	}

	// non-static classes break this entire file in stub generation
	abstract static class ExprBuilder<T extends Expression> {
		Parser parser
		int ln, cl
		boolean percent = false
		boolean goBack = false

		ExprBuilder(Parser p) {
			parser = p
			if (null != p) {
				ln = parser.ln
				cl = parser.cl
			}
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

		boolean isOverrideComments() { false }

		abstract boolean isReady()
	}

	@InheritConstructors
	static abstract class RecorderBuilder<T extends Expression> extends ExprBuilder<T> {
		abstract ExprBuilder getLast()
	}

	static class BracketBuilder extends RecorderBuilder {
		List<Expression> expressions = []
		LineBuilder last = null
		boolean lastPercent = false
		boolean commad = false

		BracketBuilder(Parser p) { super(p) }

		@Override
		Expression doPush(int cp) {
			final lastNull = null == last
			if (cp == ((char) ']') && (lastNull || last.ready)) {
				return doFinish()
			} else if (cp == ((char) ',') && (lastNull || last.ready)) {
				commad = true
				def x = last?.finish()
				if (null != x) expressions.add(x)
				last = null
			} else if (lastNull) {
				if (cp == ((char) '%')) lastPercent = true
				else if (!Character.isWhitespace(cp)) {
					last = new LineBuilder(parser, true)
					if (lastPercent) {
						last.lastPercent = true
						lastPercent = false
					}
					last.push(cp)
				}
			} else {
				def x = last.push(cp)
				if (null != x) {
					expressions.add(x)
					final back = last.goBack
					last = null
					if (back) return doPush(cp)
				}
			}
			(Expression) null
		}

		boolean isOverrideComments() {
			null != last && last.overrideComments
		}

		boolean isReady() { false }

		Expression doFinish() {
			if (last != null) {
				def x = last.finish()
				if (null != x) expressions.add(x)
				last = null
			}
			def es = expressions.size()
			if (es == 0) new ListExpression(Collections.<Expression>emptyList())
			else if (commad || es > 1) new ListExpression(expressions)
			else {
				def expr = expressions.get(0)
				expr instanceof CallExpression ? expr : new CallExpression(expr)
			}
		}
	}

	static class ParenBuilder extends RecorderBuilder {
		List<Expression> expressions = []
		LineBuilder last = null
		boolean commad = false

		ParenBuilder(Parser p) { super(p) }

		@Override
		Expression doPush(int cp) {
			final lastNull = null == last
			if (cp == ((char) ')') && (lastNull || last.ready)) {
				return doFinish()
			} else if (cp == ((char) ',') && (lastNull || last.ready)) {
				commad = true
				def x = last?.finish()
				if (null != x) expressions.add(x)
				last = null
			} else if (lastNull) {
				if (!Character.isWhitespace(cp)) {
					last = new LineBuilder(parser, true)
					last.push(cp)
				}
			} else {
				def x = last.push(cp)
				if (null != x) {
					expressions.add(x)
					final back = last.goBack
					last = null
					if (back) return doPush(cp)
				}
			}
			(Expression) null
		}

		boolean isOverrideComments() {
			null != last && last.overrideComments
		}

		boolean isReady() { false }

		Expression doFinish() {
			if (last != null) {
				def x = last.finish()
				if (null != x) expressions.add(x)
				last = null
			}
			def es = expressions.size()
			if (es == 0) new TupleExpression(Collections.<Expression>emptyList())
			else if (commad || es > 1) new TupleExpression(expressions)
			else expressions.get(0)
		}
	}

	static class CurlyBuilder extends RecorderBuilder {
		List<Expression> expressions = []
		LineBuilder last = null
		boolean commad = false, first = true, map = false

		CurlyBuilder(Parser p) { super(p) }

		@Override
		Expression doPush(int cp) {
			if (first) {
				first = false
				if ((map = commad = cp == ((char) '#'))) return (Expression) null
			}
			final lastNull = null == last
			if (cp == ((char) '}') && (lastNull || last.ready)) {
				return doFinish()
			} else if (cp == ((char) ',') && (lastNull || last.ready)) {
				commad = true
				if (null != last) {
					def x = last.finish()
					expressions.add(x)
					last = null
				}
			} else if (lastNull) {
				if (!Character.isWhitespace(cp)) {
					last = new LineBuilder(parser, commad)
					last.push(cp)
				}
			} else {
				def x = last.push(cp)
				if (null != x) {
					expressions.add(x)
					final back = last.goBack
					last = null
					if (back) return doPush(cp)
				}
			}
			(Expression) null
		}

		boolean isOverrideComments() {
			null != last && last.overrideComments
		}

		boolean isReady() { false }

		Expression doFinish() {
			if (last != null) {
				def x = last.finish()
				if (null != x) expressions.add(x)
				last = null
			}
			def es = expressions.size()
			if (map) {
				def result = new ArrayList<ColonExpression>(expressions.size())
				for (e in expressions) {
					if (e instanceof ColonExpression) result.add((ColonExpression) e)
					else if (e instanceof NameExpression) result.add(new ColonExpression(new StringExpression(e.toString()), e))
					else result.add(new ColonExpression(e, e))
				}
				new MapExpression(result)
			} else if (es == 0) new SetExpression(Collections.<Expression>emptyList())
			else if (commad) new SetExpression(expressions)
			else new BlockExpression(expressions)
		}
	}

	static class BlockBuilder extends RecorderBuilder {
		List<Expression> expressions = []
		LineBuilder last = null

		BlockBuilder(Parser p) { super(p) }

		@Override
		Expression doPush(int cp) {
			if (null == last) {
				if (!Character.isWhitespace(cp)) {
					(last = new LineBuilder(parser, false)).push(cp)
				}
			} else {
				Expression x = last.push(cp)
				if (null != x) {
					add x
					final back = last.goBack
					last = null
					if (back) return doPush(cp)
				}
			}
			(Expression) null
		}

		boolean isOverrideComments() {
			null != last && last.overrideComments
		}

		boolean isReady() { null == last || last.ready }

		BlockExpression doFinish() {
			if (last != null) {
				add last.finish()
				last = null
			}
			new BlockExpression(expressions)
		}

		void add(Expression x) {
			if (null == x) return
			expressions.add(x)
		}
	}

	static class LineBuilder extends RecorderBuilder {
		List<Expression> whitespaced = new ArrayList<>()
		List<List<Expression>> semicoloned = [whitespaced]
		ExprBuilder last = null
		boolean lastPercent = false
		boolean eagerEnd = false
		boolean ignoreNewline = false

		LineBuilder(Parser p, boolean ignoreNewline = false) {
			super(p)
			this.ignoreNewline = ignoreNewline
		}

		@Override
		Expression doPush(int cp) {
			if (!ignoreNewline && (cp == 10 || cp == 13) && ready) {
				return doFinish()
			} else if (null == last) {
				if (cp == ((char) ';'))
					if (eagerEnd) return doFinish()
					else semicoloned.add(whitespaced = new ArrayList<>())
				else if (cp == ((char) '%')) lastPercent = true
				else if (cp == ((char) '('))
					last = new ParenBuilder(parser)
				else if (cp == ((char) '[')) last = new BracketBuilder(parser)
				else if (cp == ((char) '{')) last = new CurlyBuilder(parser)
				else if (cp > 47 && cp < 58) (last = new NumberBuilder(parser)).push(cp)
				else if (cp == ((char) '"') || cp == ((char) '\'')) last = new StringExprBuilder(parser, cp)
				else if (cp == ((char) '`')) last = new QuoteAtomBuilder(parser)
				else if (cp == ((char) '.')) {
					(last = new PathBuilder(parser, whitespaced.pop())).push(cp)
				} else if (!NameBuilder.isNotIdentifier(cp)) (last = new NameBuilder(parser)).push(cp)
				if (lastPercent && null != last) {
					last.percent = true
					lastPercent = false
				}
			} else {
				Expression x = last.push(cp)
				if (null != x) {
					if (cp == ((char) '[') || cp == ((char) '(') || cp == ((char) ':')) {
						(last = new PathBuilder(parser, x)).push(cp)
					} else {
						add x
						final back = last.goBack
						last = null
						if (back && cp != ((char) '(')) return doPush(cp)
					}
				}
			}
			(Expression) null
		}

		Expression fulfillResult(Expression x) {
			if (null == x) return x
			Expression r = x
			if (percent) r = r.percentize(parser)
			r
		}

		void add(Expression x) {
			whitespaced.add(x)
		}

		Expression doFinish() {
			if (last != null) {
				add last.finish()
				last = null
			}
			final semsiz = semicoloned.size()
			Expression result
			if (semsiz > 1) {
				if (semicoloned.get(semsiz - 1).empty) semicoloned.remove(semsiz - 1)
				def lm = new ArrayList<Expression>(semicoloned.size())
				for (e in semicoloned) lm.add(form(e))
				result = new BlockExpression(lm)
			} else result = form(whitespaced)
			result
		}

		static Expression form(List<Expression> zib) {
			final s = zib.size()
			if (s > 1) {
				new CallExpression(zib)
			} else if (s == 1) {
				zib.get(0)
			} else NoExpression.INSTANCE
		}

		boolean isOverrideComments() {
			null != last && last.overrideComments
		}

		boolean isReady() { null == last || last.ready }
	}

	@InheritConstructors
	static class NumberBuilder extends ExprBuilder<NumberExpression> {
		static final String[] stageNames = ['number', 'fraction', 'exponent', 'number type bits', 'negative suffix'] as String[]
		StringBuilder[] arr = [new StringBuilder(), null, null, null, null] as StringBuilder[]
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
			} else if (cp == ((char) '.')) {
				if (stage == 0) {
					if (newlyStage) arr[0].append((char) '0')
					init 1
				} else throw new NumberFormatException('Tried to put fraction after ' + stageNames[stage])
			} else if (!newlyStage && (cp == ((char) 'e') || cp == ((char) 'E'))) {
				if (stage < 2) init 2
				else throw new NumberFormatException('Tried to put exponent after ' + stageNames[stage])
			} else if ((up = Character.toUpperCase(cp)) == ((char) 'I') || up == ((char) 'F')) {
				if (stage == 3) throw new NumberFormatException('Tried to put number type bits after number type bits')
				else {
					type = up == ((char) 'F')
					init 3
				}
			} else if (up == ((char) 'N')) {
				if (stage != 4) init 4
				arr[4].append(cp)
			} else if (newlyStage && stage != 3 && stage != 4) throw new NumberFormatException('Started number but wasnt number')
			else {
				goBack = true; return new NumberExpression(type, arr)
			}
			(NumberExpression) null
		}

		NumberExpression doFinish() {
			new NumberExpression(type, arr)
		}

		boolean isReady() { !newlyStage || stage == 3 }
	}

	static class NameBuilder extends ExprBuilder<NameExpression> {
		StringBuilder builder = new StringBuilder()

		NameBuilder(Parser p) { super(p) }

		static boolean isNotIdentifier(int cp) {
			Character.isWhitespace(cp) || cp == ((char) '.') || cp == ((char) '[') ||
					cp == ((char) '(') || cp == ((char) '{') || cp == ((char) ']') ||
					cp == ((char) ')') || cp == ((char) '}') || cp == ((char) ',') ||
					cp == ((char) ':')
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

		boolean isReady() { true }
	}

	static class PathBuilder extends RecorderBuilder {
		Expression root
		List<Step> steps = []
		Kind kind
		ExprBuilder last = null
		boolean inPropertyQueue

		PathBuilder(Parser p, Expression root) {
			super(p)
			this.root = root
		}

		@Override
		Expression doPush(int cp) {
			if (inPropertyQueue) {
				inPropertyQueue = false
				if (cp == ((char) '[')) {
					kind = Kind.SUBSCRIPT
					last = new BracketBuilder(parser)
					return null
				} else if (cp == ((char) '(')) {
					kind = Kind.CALL
					last = new ParenBuilder(parser)
					return null
				} else if (cp == ((char) '{')) {
					kind = Kind.BLOCK
					last = new CurlyBuilder(parser)
					return null
				} else if (cp == ((char) '`')) {
					kind = Kind.PROPERTY
					last = new QuoteAtomBuilder(parser)
					return null
				} else if (!Character.isWhitespace(cp)) {
					kind = Kind.PROPERTY
					last = new NameBuilder(parser)
				} else {
					inPropertyQueue = true
					return (PathExpression) null
				}
			}
			if (null != last) {
				def e = last.push(cp)
				if (null != e) {
					add(e)
					final back = last.goBack
					last = null
					kind = null
					if (back) return doPush(cp)
				}
			} else {
				if (cp == ((char) '.')) inPropertyQueue = true
				else if (cp == ((char) '[')) {
					kind = Kind.SUBSCRIPT
					last = new BracketBuilder(parser)
				} else if (cp == ((char) '(')) {
					kind = Kind.CALL
					last = new ParenBuilder(parser)
				} else if (cp == ((char) ':')) {
					kind = Kind.COLON
					(last = new LineBuilder(parser, true)).eagerEnd = true
				} else {
					goBack = true
					return steps.empty ? root : new PathExpression(root, steps)
				}
			}
			null
		}

		void add(Expression e) {
			if (kind == Kind.CALL) {
				def mems = e instanceof TupleExpression ? e.members : Collections.singletonList(e)
				final ss = steps.size()
				if (ss == 0) {
					def list = new ArrayList<Expression>(1 + mems.size())
					list.add(root)
					list.addAll(mems)
					root = new CallExpression(list)
				} else {
					def list = new ArrayList<Expression>(2 + mems.size())
					list.add(steps.get(ss - 1).asExpr())
					list.add(ss == 1 ? root : new PathExpression(root, steps.init()))
					list.addAll(mems)
					root = new CallExpression(list)
				}
				steps.clear()
			} else if (kind == Kind.COLON) {
				final ss = steps.size()
				def lhs = ss == 0 ? root : new PathExpression(root, new ArrayList<>(steps))
				root = new ColonExpression(lhs, e)
				steps.clear()
			} else steps.add(kind.toStep(e))
		}

		boolean isReady() { !inPropertyQueue && (null == last || last.ready) }

		Expression doFinish() {
			if (null != last) {
				add last.finish()
				last = null
			}
			steps.empty ? root : new PathExpression(root, steps)
		}

		enum Kind {
			PROPERTY {
				Step toStep(Expression expr) {
					new PathExpression.PropertyStep(((NameExpression) expr).text)
				}
			}, SUBSCRIPT {
				Step toStep(Expression expr) {
					new PathExpression.SubscriptStep(expr instanceof CallExpression &&
							expr.arguments.empty ? expr.callValue : expr)
				}
			}, CALL, BLOCK {
				Step toStep(Expression expr) {
					new PathExpression.EnterStep(expr)
				}
			}, COLON

			Step toStep(Expression expr) { throw new UnsupportedOperationException('Cannot convert to step path step kind ' + this) }
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
				case 'fill_templates': parser.optimizer.template = true; break
				case '!fill_templates': parser.optimizer.template = false; break
				case '?fill_templates':
					result = new StaticExpression(x, parser.optimizer.template)
					break
				case 'optimize': parser.optimizer.on(); break
				case '!optimize': parser.optimizer.off(); break
				case '?optimize':
					result = new StaticExpression(x, parser.optimizer.template ||
							parser.optimizer.closure || parser.optimizer.prelude)
					break
				case 'parser': result = new StaticExpression(x, parser); break
			}
			result
		}

		StringExpression doFinish() {
			new StringExpression(last.toString())
		}

		boolean isReady() { false }

		boolean isOverrideComments() { true }
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

		boolean isReady() { false }

		boolean isOverrideComments() { true }
	}
}
