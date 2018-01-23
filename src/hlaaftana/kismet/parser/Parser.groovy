package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import hlaaftana.kismet.Context
import hlaaftana.kismet.LineColumnException
import hlaaftana.kismet.UnexpectedSyntaxException

@CompileStatic
class Parser {
	@SuppressWarnings('GroovyVariableNotAssigned')
	static BlockExpression parse(Context parserBlock, String code) {
		BlockBuilder builder = new BlockBuilder(parserBlock, false)
		char[] arr = code.toCharArray()
		int len = arr.length
		int ln = 1
		int cl = 0
		for (int i = 0; i < len; ++i) {
			int c = (int) arr[i]
			if (c == 10) {
				++ln
				cl = 0
			} else ++cl
			try {
				builder.push(c)
			} catch (ex) {
				throw new LineColumnException(ex, ln, cl)
			}
		}
		builder.push(10)
		new BlockExpression(builder.expressions)
	}

	@CompileStatic
	static class BlockBuilder extends ExprBuilder<BlockExpression> {
		List<Expression> expressions = []
		CallBuilder last = null
		boolean lastPercent = false
		boolean bracketed

		BlockBuilder(Context c, boolean b) { parserContext = c; bracketed = b }

		@Override
		BlockExpression doPush(int cp) {
			if (cp == 125 && bracketed && (null == last || (last.endOnDelim && !(last.anyBlocks())))) {
				if (null != last) {
					CallExpression x = last.doPush(10)
					if (null == x)
						throw new UnexpectedSyntaxException('Last call in block was bracketed but was not closed')
					Expression a = !last.bracketed && !x.arguments ? x.value : x
					expressions.add(last.percent ? a.percentize(parserContext) : a)
				}
				return new BlockExpression(expressions)
			} else if (null == last) {
				if (cp == 91) last = new CallBuilder(parserContext, true)
				else if (cp == 37) lastPercent = true
				else if (!Character.isWhitespace(cp)) (last = new CallBuilder(parserContext, false)).push(cp)
				if (lastPercent && null != last) last.percent = !(lastPercent = false)
			} else {
				CallExpression x = last.doPush(cp)
				if (null != x) {
					Expression a = !last.bracketed && !x.arguments ? x.value : x
					expressions.add(last.percent ? a.percentize(parserContext) : a)
					last = null
				}
			}
			bracketed && cp == 125 && null == last ? new BlockExpression(expressions) : null
		}
	}

	@CompileStatic
	static class CallBuilder extends ExprBuilder<CallExpression> {
		List<Expression> expressions = []
		ExprBuilder last = null
		boolean lastPercent = false
		boolean bracketed

		CallBuilder(Context c, boolean b) { parserContext = c; bracketed = b }

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
				else if (cp == 40) last = new PathBuilder(parserContext, true)
				else if (cp == 91) last = new CallBuilder(parserContext, true)
				else if (cp == 123) last = new BlockBuilder(parserContext, true)
				else if ((cp > 47 && cp < 58) || cp == 46) (last = new NumberBuilder()).push(cp)
				else if (cp == 34 || cp == 39) last = new StringExprBuilder(cp)
				else if (!Character.isWhitespace(cp)) (last = new PathBuilder(parserContext, false)).push(cp)
				if (lastPercent && null != last) last.percent = !(lastPercent = false)
			} else {
				Expression x = last.push(cp)
				if (null != x) {
					expressions.add(x)
					last = (ExprBuilder) null
				}
			}
			(CallExpression) null
		}

		boolean isEndOnDelim() {
			!( last instanceof StringExprBuilder
			|| (last instanceof CallBuilder && ((CallBuilder) last).bracketed)
			|| last instanceof BlockBuilder
			|| (last instanceof PathBuilder && ((PathBuilder) last).bracketed))
		}

		boolean anyBlocks() {
			last instanceof BlockBuilder || (last instanceof CallBuilder && ((CallBuilder) last).anyBlocks())
		}
	}

	@CompileStatic
	abstract static class ExprBuilder<T extends Expression> {
		Context parserContext
		boolean percent = false

		abstract T doPush(int cp)

		Expression push(int cp) {
			T x = doPush(cp)
			null == x ? x : percent ? x.percentize(parserContext) : x
		}
	}

	@CompileStatic
	static class NumberBuilder extends ExprBuilder<NumberExpression> {
		static String[] stageNames = ['number', 'fraction', 'exponent', 'number type bits']
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
	static class PathBuilder extends ExprBuilder<PathExpression> {
		StringBuilder last = new StringBuilder()
		boolean escaped = false
		boolean bracketed

		PathBuilder(Context c, boolean b) { parserContext = c; bracketed = b }

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
	static class StringExprBuilder extends ExprBuilder<StringExpression> {
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

		StringExpression push(int cp) {
			doPush(cp)
		}
	}
}
