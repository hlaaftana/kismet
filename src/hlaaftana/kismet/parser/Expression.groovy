package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.*

@CompileStatic abstract class Expression {
	abstract KismetObject evaluate(Block c)

	Expression percentize(Block p) {
		new StaticExpression(this, p)
	}

	static String repr(Expression expr) {
		if (expr instanceof AtomExpression) '(' +
				((AtomExpression) expr).text.replace(')', '\\)') + ')'
		else if (expr instanceof StringExpression) '"' + StringEscaper.escapeSoda(((StringExpression) expr).value.inner()) + '"'
		else if (expr instanceof NumberExpression) ((NumberExpression) expr).value.toString()
		else if (expr instanceof CallExpression)
			"call[${((CallExpression) expr).expressions.collect(this.&repr).join(', ')}]"
		else if (expr instanceof BlockExpression) 'block{\n' +
			((BlockExpression) expr).content.collect { '  '.concat(repr(it)) }.join('\r\n') + '}'
		else if (expr instanceof StaticExpression) {
			def a = (StaticExpression) expr
			"static($a.value, ${repr(a.expression) ?: "<NO EXPRESSION>"})"
		}
		else throw new IllegalArgumentException('Unknown expression type ' + expr.class)
	}
}

@CompileStatic class AtomExpression extends Expression {
	String text
	Path path

	AtomExpression(String t) { text = t; path = Path.parse(t) }
	AtomExpression(Path p) { text = p.raw; path = p }

	void setText(String t) {
		this.@text = t
		path = Path.parse(t)
	}

	KismetObject evaluate(Block c) {
		Kismet.model(path.expressions[0].raw.empty ? new PathFunction(path) : path.apply(c.context))
	}

	boolean equals(obj) {
		obj instanceof AtomExpression && ((AtomExpression) obj).text == text
	}

	static class PathFunction extends Function {
		Path path
		PathFunction(Path p) { path = p }

		@Override
		KismetObject call(KismetObject... args) {
			Kismet.model(path.apply(args[0]))
		}
	}
}

@CompileStatic class BlockExpression extends Expression {
	List<Expression> content

	BlockExpression(List<Expression> exprs) { content = exprs }

	KismetObject evaluate(Block c) {
		KismetObject a = Kismet.NULL
		for (e in content) a = e.evaluate(c)
		a
	}

	boolean equals(obj) { obj instanceof BlockExpression && obj.content == content }
}

@CompileStatic class CallExpression extends Expression {
	Expression value
	List<Expression> arguments

	CallExpression(List<Expression> expressions) {
		setValue(expressions[0])
		arguments = expressions.drop(1)
	}

	KismetObject evaluate(Block c) {
		KismetObject obj = value.evaluate(c)
		if (obj.inner() instanceof Macro)
			((Macro) obj.inner()).doCall(c, arguments.collect(Kismet.&model) as KismetObject[])
		else obj.call(arguments*.evaluate(c) as KismetObject[])
	}

	List<Expression> getExpressions() {
		List<Expression> x = new ArrayList<>()
		x.add(value)
		x.addAll(arguments)
		x
	}

	boolean equals(obj) {
		obj instanceof CallExpression && obj.expressions == expressions
	}
}

@CompileStatic class ConstantExpression<T> extends Expression {
	KismetObject<T> value

	void setValue(T obj) {
		value = Kismet.model(obj)
	}

	KismetObject evaluate(Block c) {
		value
	}
}

@CompileStatic class NumberExpression extends ConstantExpression<Number> {
	NumberExpression(boolean type, StringBuilder[] arr) {
		StringBuilder x = arr[0]
		boolean t = false
		if (null != arr[1]) { x.append('.').append(arr[1]); t = true }
		if (null != arr[2]) { x.append('e').append(arr[2]); t = true }
		String r = x.toString()
		if (null == arr[3]) setValue(t ? new BigDecimal(r) : new BigInteger(r)) else {
			if (type) {
				if (arr[3].length() == 0) setValue(new BigDecimal(r))
				else {
					int b = new Integer(arr[3].toString())
					if (b == 1) setValue(-new BigDecimal(r))
					else if (b == 32) setValue(new Float(r))
					else if (b == 33) setValue(-new Float(r))
					else if (b == 64) setValue(new Double(r))
					else if (b == 65) setValue(-new Double(r))
					else throw new NumberFormatException("Invalid number of bits $b for explicit float")
				}
			} else {
				if (t) {
					def v = new BigDecimal(r)
					if (arr[3].length() == 0) setValue(v.toBigInteger())
					else {
						int b = new Integer(arr[3].toString())
						if (b == 1) setValue(-v.toBigInteger())
						else if (b == 8) setValue(v.byteValue())
						else if (b == 9) setValue(-v.byteValue())
						else if (b == 16) setValue(v.shortValue())
						else if (b == 17) setValue(-v.shortValue())
						else if (b == 32) setValue(v.intValue())
						else if (b == 33) setValue(-v.intValue())
						else if (b == 64) setValue(v.longValue())
						else if (b == 65) setValue(-v.longValue())
						else throw new NumberFormatException("Invalid number of bits $b for explicit integer")
					}
				}
				else if (arr[3].length() == 0) setValue(new BigInteger(r))
				else {
					int b = new Integer(arr[3].toString())
					if (b == 1) setValue(-new BigInteger(r))
					else if (b == 8) setValue(new Byte(r))
					else if (b == 9) setValue(-new Byte(r))
					else if (b == 16) setValue(new Short(r))
					else if (b == 17) setValue(-new Short(r))
					else if (b == 32) setValue(new Integer(r))
					else if (b == 33) setValue(-new Integer(r))
					else if (b == 64) setValue(new Long(r))
					else if (b == 65) setValue(-new Long(r))
					else throw new NumberFormatException("Invalid number of bits $b for explicit integer")
				}
			}
		}
	}

	NumberExpression(Number v) { setValue(v) }

	NumberExpression(String x) {
		NumberBuilder b = new NumberBuilder()
		char[] a = x.toCharArray()
		for (int i = 0; i < a.length; ++i) b.doPush((int) a[i])
		value = b.doPush(32).value
	}

	NumberExpression percentize(Block p) {
		new NumberExpression(value.inner().div(100))
	}

	boolean equals(obj) {
		obj instanceof NumberExpression && obj.value.inner() == value.inner()
	}
}

@CompileStatic
class StringExpression extends ConstantExpression<String> {
	String raw
	Exception exception

	StringExpression(String v) {
		try {
			setValue(StringEscaper.unescapeSoda(raw = v))
		} catch (ex) { exception = ex }
	}

	StringExpression percentize(Block p) {
		throw new UndefinedBehaviourException('Percentizing string expression is undefined behaviour as of now')
	}

	boolean equals(obj) {
		obj instanceof StringExpression && obj.value.inner() == value.inner()
	}

	KismetObject<String> evaluate(Block c) {
		if (null == exception) value
		else throw exception
	}
}

@CompileStatic
class StaticExpression<T extends Expression> extends ConstantExpression<Object> {
	T expression

	StaticExpression(T ex = null, KismetObject val) {
		expression = ex
		value = val
	}

	StaticExpression(T ex = null, val) {
		expression = ex
		setValue(val)
	}

	StaticExpression(T ex = null, Block c) {
		this(ex, ex.evaluate(c))
	}
}

@CompileStatic
abstract class ExprBuilder<T extends Expression> {
	Block parserBlock
	boolean percent = false

	abstract T doPush(int cp)

	Expression push(int cp) {
		T x = doPush(cp)
		null == x ? x : percent ? x.percentize(parserBlock) : x
	}
}

@CompileStatic class AtomBuilder extends ExprBuilder<AtomExpression> {
	StringBuilder last = new StringBuilder()
	boolean escaped = false
	boolean bracketed

	AtomBuilder(Block c, boolean b) { parserBlock = c; bracketed = b }

	AtomExpression doPush(int cp) {
		if ((bracketed && !escaped && cp == 41) || (!bracketed && Character.isWhitespace(cp)))
			return new AtomExpression(last.toString())
		if (escaped) {
			escaped = false
			if (cp == 41) last.deleteCharAt(last.length() - 1)
		}
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

@CompileStatic @InheritConstructors class UndefinedBehaviourException extends KismetException {}

@CompileStatic class StringExprBuilder extends ExprBuilder<StringExpression> {
	StringBuilder last = new StringBuilder()
	boolean escaped = false
	int quote

	StringExprBuilder(int q) {
		quote = q
	}

	void setPercent(boolean x) {
		if (x) throw new UndefinedBehaviourException('Percent for string expression is undefined behaviour as of now')
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

@CompileStatic class CallBuilder extends ExprBuilder<CallExpression> {
	List<Expression> expressions = []
	ExprBuilder last = null
	boolean lastPercent = false
	boolean bracketed

	CallBuilder(Block c, boolean b) { parserBlock = c; bracketed = b }

	@Override
	CallExpression doPush(int cp) {
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
			else if (cp == 37) lastPercent = true
			else if (cp == 40) last = new AtomBuilder(parserBlock, true)
			else if (cp == 91) last = new CallBuilder(parserBlock, true)
			else if (cp == 123) last = new BlockBuilder(parserBlock, true)
			else if ((cp > 47 && cp < 58) || cp == 46) (last = new NumberBuilder()).push(cp)
			else if (cp == 34 || cp == 39) last = new StringExprBuilder(cp)
			else if (!Character.isWhitespace(cp)) (last = new AtomBuilder(parserBlock, false)).push(cp)
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
		|| (last instanceof AtomBuilder && ((AtomBuilder) last).bracketed))
	}

	boolean anyBlocks() {
		last instanceof BlockBuilder || (last instanceof CallBuilder && ((CallBuilder) last).anyBlocks())
	}
}

@CompileStatic class BlockBuilder extends ExprBuilder<BlockExpression> {
	List<Expression> expressions = []
	CallBuilder last = null
	boolean lastPercent = false
	boolean bracketed

	BlockBuilder(Block c, boolean b) { parserBlock = c; bracketed = b }

	@Override
	BlockExpression doPush(int cp) {
		if (cp == 125 && bracketed && (null == last || (last.endOnDelim && !(last.anyBlocks())))) {
			if (null != last) {
				CallExpression x = last.doPush(10)
				if (null == x)
					throw new UnexpectedSyntaxException('Last call in block was bracketed but was not closed')
				Expression a = !last.bracketed && !x.arguments ? x.value : x
				expressions.add(last.percent ? a.percentize(parserBlock) : a)
			}
			return new BlockExpression(expressions)
		} else if (null == last) {
			if (cp == 91) last = new CallBuilder(parserBlock, true)
			else if (cp == 37) lastPercent = true
			else if (!Character.isWhitespace(cp)) (last = new CallBuilder(parserBlock, false)).push(cp)
			if (lastPercent && null != last) last.percent = !(lastPercent = false)
		} else {
			CallExpression x = last.doPush(cp)
			if (null != x) {
				Expression a = !last.bracketed && !x.arguments ? x.value : x
				expressions.add(last.percent ? a.percentize(parserBlock) : a)
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
			case AtomExpression: println "Atom: ${((AtomExpression) expr).text}"; break
			case ConstantExpression: println "${expr.class.simpleName - "Expression"}: ${((ConstantExpression) expr).value}"
		}
	}

	static main(args) {
		long time = System.currentTimeMillis()
		printTree Kismet.parse(new File('old/lang/newkismetsyntax.txt').text).expression
		//printTree KismetInner.parse("{[aba ba a ba {[x y {[z d][d][ag ga ]}]}]}")
		println System.currentTimeMillis() - time
	}
}