package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import hlaaftana.kismet.*

@CompileStatic abstract class Expression { abstract KismetObject evaluate(Block c) }

@CompileStatic class AtomExpression extends Expression {
	String text

	AtomExpression(String t) { text = t }

	@Memoized Path getPath() { Path.parse(text) }

	KismetObject evaluate(Block c) {
		Kismet.model path.apply(c.context)
	}
}

@CompileStatic class BlockExpression extends Expression {
	List<Expression> content

	BlockExpression(List<Expression> exprs) { content = exprs }

	KismetObject evaluate(Block c) {
		KismetObject a = Kismet.model null
		for (e in content) a = e.evaluate(c)
		a
	}
}

@CompileStatic class CallExpression extends Expression {
	Expression value
	List<Expression> arguments

	CallExpression(List<Expression> expressions) {
		value = expressions[0]
		arguments = expressions.drop(1)
	}

	KismetObject evaluate(Block c) {
		KismetObject obj = value.evaluate(c)
		if (obj.inner() instanceof Macro)
			((Macro) obj.inner()).doCall(c, arguments.collect(Kismet.&model) as KismetObject[])
		else obj.call(arguments*.evaluate(c) as KismetObject[])
	}
}

@CompileStatic class LiteralExpression<T> extends Expression {
	T value

	KismetObject evaluate(Block c) {
		Kismet.model value
	}
}

@CompileStatic class NumberExpression extends LiteralExpression<Number> {
	NumberExpression(boolean type, StringBuilder[] arr) {
		StringBuilder x = arr[0]
		boolean t = false
		if ((t |= null != arr[1])) x.append('.').append(arr[1])
		if ((t |= null != arr[2])) x.append('e').append(arr[2])
		String r = x.toString()
		if (null == arr[3]) value = t ? new BigDecimal(r) : new BigInteger(r) else {
			if (type) {
				if (arr[3].length() == 0) value = new BigDecimal(r)
				else {
					int b = new Integer(arr[3].toString())
					if (b == 32) value = new Float(r)
					else if (b == 64) value = new Double(r)
					else throw new NumberFormatException("Invalid number of bits $b for explicit float")
				}
			} else {
				if (t) throw new NumberFormatException('Added exponent or fraction to explicit integer')
				else if (arr[3].length() == 0) value = new BigInteger(r)
				else {
					int b = new Integer(arr[3].toString())
					if (b == 8) value = new Byte(r)
					else if (b == 16) value = new Short(r)
					else if (b == 32) value = new Integer(r)
					else if (b == 64) value = new Long(r)
					else throw new NumberFormatException("Invalid number of bits $b for explicit integer")
				}
			}
		}
	}
}

@CompileStatic
class StringExpression extends LiteralExpression<String> {
	StringExpression(String v) {
		value = StringEscaper.unescapeSoda(v)
	}
}

@CompileStatic
abstract class ExprBuilder<T extends Expression> {
	abstract T push(int cp)
}

@CompileStatic class AtomBuilder extends ExprBuilder<AtomExpression> {
	StringBuilder last = new StringBuilder()
	boolean escaped = false
	boolean bracketed

	AtomBuilder(boolean b) { bracketed = b }

	AtomExpression push(int cp) {
		if ((bracketed && !escaped && cp == 41) || (!bracketed && Character.isWhitespace(cp)))
			return new AtomExpression(last.toString())
		if (escaped) { escaped = false; last.deleteCharAt(last.codePointCount(0, last.length())) }
		else escaped = cp == 92
		last.appendCodePoint(cp)
		(AtomExpression) null
	}
}

@CompileStatic class NumberBuilder extends ExprBuilder<NumberExpression> {
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

	NumberExpression push(int cp) {
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

@CompileStatic class StringExprBuilder extends ExprBuilder<StringExpression> {
	StringBuilder last = new StringBuilder()
	boolean escaped = true
	int quote

	StringExprBuilder(int q) {
		quote = q
	}

	StringExpression push(int cp) {
		if (!escaped && cp == quote) return new StringExpression(last.toString())
		if (escaped) escaped = false
		else escaped = cp == 92
		last.appendCodePoint(cp)
		(StringExpression) null
	}
}

@CompileStatic class CallBuilder extends ExprBuilder<CallExpression> {
	List<Expression> expressions = []
	ExprBuilder last = null
	boolean bracketed

	CallBuilder(boolean b) { bracketed = b }

	@Override
	CallExpression push(int cp) {
		if ((bracketed ? cp == 93 : (cp == 10 || cp == 13)) && endOnDelim) {
			if (null != last) {
				Expression x = last.push(10)
				if (null == x)
					throw new IllegalStateException('Call was supposed to end with a ], last builder ' +
							'which is nonbracketed atom or num was pushed a newline but returned null ' + last)
				expressions.add(x)
			}
			return new CallExpression(expressions)
		} else if (null == last) {
			if (bracketed && cp == 93) return new CallExpression(expressions)
			else if (cp == 40) last = new AtomBuilder(true)
			else if (cp == 91) last = new CallBuilder(true)
			else if (cp == 123) last = new BlockBuilder(true)
			else if ((cp > 47 && cp < 58) || cp == 46) (last = new NumberBuilder()).push(cp)
			else if (cp == 34 || cp == 39) last = new StringExprBuilder(cp)
			else if (!Character.isWhitespace(cp)) (last = new AtomBuilder(false)).push(cp)
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
		|| (last instanceof AtomBuilder && ((AtomBuilder) last).bracketed))
	}

	boolean anyBlocks() {
		last instanceof BlockBuilder || (last instanceof CallBuilder && ((CallBuilder) last).anyBlocks())
	}
}

@CompileStatic class BlockBuilder extends ExprBuilder<BlockExpression> {
	List<Expression> expressions = []
	CallBuilder last = null
	boolean bracketed

	BlockBuilder(boolean b) { bracketed = b }

	@Override
	BlockExpression push(int cp) {
		if (cp == 125 && bracketed && (null == last || (last.endOnDelim && !(last.anyBlocks())))) {
			if (null != last) {
				CallExpression x = last.push(10)
				if (null == x)
					throw new UnexpectedSyntaxException('Last call in block was bracketed but was not closed')
				expressions.add(!last.bracketed && !x.arguments ? x.value : x)
			}
			return new BlockExpression(expressions)
		} else if (null == last) {
			if (cp == 91) last = new CallBuilder(true)
			else if (!Character.isWhitespace(cp)) (last = new CallBuilder(false)).push(cp)
		} else {
			CallExpression x = last.push(cp)
			if (null != x) {
				expressions.add(!last.bracketed && !x.arguments ? x.value : x)
				last = null
			}
		}
		bracketed && cp == 125 && null == last ? new BlockExpression(expressions) : null
	}
}

class ExprTest {
	@CompileStatic
	static printTree(Expression expr, int indent = 0) {
		print ' ' * indent
		switch (expr) {
			case BlockExpression: println 'Block:'; for (e in ((BlockExpression) expr).content) printTree(e, indent + 2); break
			case CallExpression: println 'Call:'; printTree(((CallExpression) expr).value, indent + 2)
				for (e in ((CallExpression) expr).arguments) printTree(e, indent + 2); break
			case AtomExpression: println "Atom: ${((AtomExpression) expr).path.raw}"; break
			case LiteralExpression: println "${expr.class.simpleName - "Expression"}: ${((LiteralExpression) expr).value}"
		}
	}

	static main(args) {
		long time = System.currentTimeMillis()
		printTree KismetInner.parse(new File('old/lang/newkismetsyntax.txt').text)
		//printTree KismetInner.parse("{[aba ba a ba {[x y {[z d][d][ag ga ]}]}]}")
		println System.currentTimeMillis() - time
	}
}