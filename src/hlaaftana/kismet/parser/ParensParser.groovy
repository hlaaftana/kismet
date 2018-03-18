package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import hlaaftana.kismet.UnexpectedSyntaxException

@CompileStatic
class ParensParser {
	static List<String> separateLines(String code){
		int currentQuote = 0
		boolean escaped = false
		int parentheses = 0
		StringBuilder last
		List<StringBuilder> a = [last = new StringBuilder()]
		int len = code.codePointCount(0, code.length())
		for (int i = 0; i < len; ++i){
			int x = code.codePointAt(i)
			if (0 == currentQuote)
				if (x == 39 || x == 34) { currentQuote = x; last.appendCodePoint(x) } // ' or "
				else if (x == 40) { ++parentheses; last.appendCodePoint(x) } // (
				else if (x == 41) { --parentheses; last.appendCodePoint(x) } // )
				else if (parentheses == 0) {
					if (x == 92) {
						escaped = true
						last.appendCodePoint(x)
					} else if (x == 59 || x == 13 || x == 10) { // ; \r \n
						if (!escaped) {
							if (len <= i + 1) ++i
							else {
								// \r \n
								if (code.codePointAt(i + 1) in [11, 13]) ++i
								a.add(last = new StringBuilder())
							}
						} else {
							int w = 0
							if (len <= i + 1 || (w = code.codePointAt(i + 1))
									== 10 || w == 13) ++i // \r \n
							escaped = false
						}
					} else {
						last.appendCodePoint(x)
						escaped = false
					}
				}
				else last.appendCodePoint(x)
			else {
				if (x == 92) escaped = true
				else if (!escaped && x == currentQuote) currentQuote = 0
				else escaped = false
				last.appendCodePoint(x)
			}
		}
		List<String> liens = []
		for (sb in a) {
			String r = sb.toString()
			if (!r.empty) liens.add(r)
		}
		liens
	}

	static Expression parse(String code) {
		def lines = separateLines(code)
		if (lines.size() <= 1)
			parseExpression(lines[0])
		else {
			def expressions = []
			for (it in lines) expressions.add parseExpression(it)
			new BlockExpression(expressions)
		}
	}

	static Expression parseCall(String raw) {
		List<Expression> expressions = []
		StringBuilder last = new StringBuilder()
		int parens = 0
		boolean escaped = false
		int currentQuote = 0
		for (x in raw.codePoints().toArray()) {
			if (parens == 0)
				if (currentQuote != 0) {
					last.appendCodePoint(x)
					if (!escaped) {
						if (x == 92)
							escaped = true
						else if (x == currentQuote) {
							currentQuote = 0
							expressions.add parseExpression(last.toString())
						}
					}
				} else if (x == 39 || x == 34) {
					expressions.add parseExpression(last.toString())
					last = new StringBuilder()
					last.appendCodePoint(x)
					currentQuote = x
				} else if (x == 40) {
					expressions.add parseExpression(last.toString())
					last = new StringBuilder()
					++parens
				} else if (Character.isWhitespace(x)) {
					if (last.length() != 0) {
						expressions.add parseExpression(last.toString())
						last = new StringBuilder()
					}
				} else last.appendCodePoint(x)
			else if (currentQuote != 0) {
				if (!escaped) if (x == 92) escaped = true
				else if (x == currentQuote) currentQuote = 0
				last.appendCodePoint(x)
			} else if (x == 40) {
				++parens
				last.appendCodePoint(x)
			} else if (x == 41) {
				--parens
				if (parens == 0) {
					expressions.add parse(last.toString())
					last = new StringBuilder()
				} else last.appendCodePoint(x)
			} else last.appendCodePoint(x)
		}
		if (parens != 0) throw new UnexpectedSyntaxException('Forgot to close parentheses')
		else expressions.add parseExpression(last.toString())
		Iterator iter = expressions.iterator()
		boolean forceCall = false
		while (iter.hasNext()) if (iter.next() instanceof NoExpression) { forceCall = true; iter.remove() }
		if (forceCall || expressions.size() != 1) new CallExpression(expressions)
		else expressions[0]
	}

	static Expression parseExpression(String code){
		Expression ex
		char q
		if (null == code || (code = code.trim()).empty)
			ex = NoExpression.INSTANCE
		else if (code.number) ex = new NumberExpression(code)
		else if (code.length() >= 2 && (q = code.charAt(0)) == code.charAt(code.length() - 1) &&
				(q == ((char) '"') || q == (char) "'"))
			ex = new StringExpression(code.substring(1, code.length() - 1))
		else if ((code.contains('(') && code.contains(')')) ||
				code.codePoints().toArray().any(Character.&isWhitespace))
			ex = parseCall(code)
		else ex = DumbParser.PathBuilder.convert(code)
		ex
	}
}

