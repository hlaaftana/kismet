package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import hlaaftana.kismet.Context
import hlaaftana.kismet.Function
import hlaaftana.kismet.Macro
import hlaaftana.kismet.StringEscaper

import hlaaftana.kismet.parser.CharacterToken.Kind as CharKind
import hlaaftana.kismet.parser.TextToken.Kind as TextKind
import static java.lang.Character.isWhitespace

/*
%% maybe comments like this %%

%% ideas:
function calls:
 [a b c d] => (a, b, c, d) = a(b, c, d) = a b, c, d
 a [b c d] = [a [b c d]] => a, (b, c, d) != a (b, c, d)
 [func] => func() = (func, ())
 [list a b c] => &(a, b, c)
 [set a b c] => #(a, b, c)
 [tuple a b c] => $(a, b, c)
 [map a b c d] => #&(a, b, c, d) = ##((a b), (c d))
 name(a) = a.name if name is declared

infix (with Operator interface)

(text) => `text`

to explain function call syntax sugar i will use these shorthands:
^f = f.precedence
fghijk... = KismetCallable
abcde = not KismetCallable
x = KismetCallable or not KismetCallable

x = x
x x = (x, x)
f g x = if ^g > ^f then (g, f, a) else (f, (g, a))
f a x = (f, a, x)
f a g x = if ^g > ^f then (f, (g, a, b)) else ()
f g a x = if ^g > ^f then ((g, f, a), x) else (f, (g, (a, x)))
f g h x = if ^g > ^f then ((g, f, h), x) else if ^h > ^g then (f, (h, g, x)) else (f, (g, (h, x)))
%%

mean = sum / size

def median(l) {
  s := size l
  d := s div 2
  if s.odd? { l[d] }
  else { half l[d] + l[prev d] }
}

def standard_deviation(l) {
  m := l.mean
  s := 0
  for a in l {
    s += sqr(a - m)
  }
  sqrt(s / prev(l.size))
}

def skewness(l) {
  l.(mean - median) * 3 / l.standard_deviation
}
*/

@CompileStatic
class UFCSParser {
	static Expression parseSingle(Context context, List<Token> tokens) {
		Expression last
		List<Expression> exprs = []
        null
	}

	static BlockExpression parse(Context context, List<Token> tokens) {

	}
}

@CompileStatic
class UFCSTokenizer {
	static List<Token> tokenize(String code) {
		def list = new ArrayList<Token>()
		TokenRecording recording
		Character numSign
		boolean comment = false
		for (int i = 0; i < code.length(); ++i) {
			final c = code.charAt(i)
			if (comment) {
				if ((c == (char) '%') && (code.charAt(i + 1) == (char) '%')) {
					comment = false
					i += 2
				}
				continue
			}
			else if ((c == (char) '%') && (code.charAt(i + 1) == (char) '%') &&
					(null == recording || !recording.consumeComment())) {
				comment = true
				i += 2
				continue
			}
			if (null != numSign) {
				recording = isDigit(c) ? new NumberRecording(numSign) : new NameRecording(numSign)
				numSign = null
			}
			if (null == recording) {
				final CharKind charKind
				if (null != (charKind = CharKind.find(c))) list.add(new CharacterToken(charKind))
				else if ((c == (char) '\'') || c == (char) '"') recording = new QuoteRecording(TextKind.STRING, c)
				else if (c == (char) '`') recording = new QuoteRecording(TextKind.NAME, c)
				else if ((c == (char) '+') || c == (char) '-') numSign = (Character) c
				else if (isDigit(c)) recording = new NumberRecording(c)
				else if (!isWhitespace(c)) recording = new NameRecording(c)
			} else {
				final r = recording.push(c)
				if (null != r) {
					recording = null
					list.add(r.token)
					if (r.goBack) --i
				}
			}
		}
		list
	}

	static boolean isDigit(char c) { (c >= (char) '0') && c <= (char) '9' }

	static abstract class TokenRecording {
		StringBuilder builder = new StringBuilder()

		abstract Result push(char c)
		boolean consumeComment() { false }

		void leftShift(char c) { builder.append(c) }

		TextToken finish(TextToken.Kind kind) {
			final r = new TextToken(kind: kind, text: builder.toString())
			r
		}

		static class Result {
			TextToken token
			boolean goBack

			Result(TextToken token, boolean goBack) {
				this.token = token
				this.goBack = goBack
			}
		}
	}

	static abstract class GeneralTokenRecording extends TokenRecording {
		TextToken.Kind kind

		GeneralTokenRecording(TextToken.Kind kind) {
			this.kind = kind
		}

		TextToken finish() { finish(kind) }
	}

	// backtick, single and double quote
	static class QuoteRecording extends GeneralTokenRecording {
		char quote
		boolean escaped = false

		QuoteRecording(TextToken.Kind kind, char quote) {
			super(kind)
			this.quote = quote
		}

		boolean consumeComment() { true }

		Result push(char c) {
			if (escaped) {
				this << c
				escaped = false
			} else if (c == quote) return new Result(finish(), false)
			else if (c == (char) '\\') escaped = true
			else this << c
			null
		}
	}

	static class NumberRecording extends GeneralTokenRecording {
		int stage = 1
		boolean needsMore

		NumberRecording(char c) {
			super(TextKind.NUMBER)
			this << c
		}

		Result push(char c) {
			final isDot = c == (char) '.'
			if (isDot) {
				if (stage != 1) return new Result(finish(), true)
			} else if (isWhitespace(c) || null != CharKind.find(c))
				return new Result(finish(), true)
			this << c
			if (stage == 1) {
				if (c == (char) '.') { stage = 2; needsMore = true }
				else if ((c == (char) 'e') || c == (char) 'E') { stage = 3; needsMore = true }
				else if ((c == (char) 'i') || (c == (char) 'I') || (c == (char) 'f') || c == (char) 'F') stage = 4
				else if (!isDigit(c) || c != (char) '_') return new Result(finish(), true)
			} else if (stage == 2) {
				needsMore = false
				if ((c == (char) 'e') || c == (char) 'E') { stage = 3; needsMore = true }
				else if ((c == (char) 'i') || (c == (char) 'I') || (c == (char) 'f') || c == (char) 'F') stage = 4
				else if (!isDigit(c) || c != (char) '_') return new Result(finish(), true)
			} else if (stage == 3) {
				if (!(isDigit(c) || (needsMore && ((c == (char) '+') || c == (char) '-')))) return new Result(finish(), true)
				needsMore = false
			} else if (stage == 4) {
				if (!isDigit(c)) return new Result(finish(), true)
			}
			null
		}
	}

	static class NameRecording extends GeneralTokenRecording {
		NameRecording(char c) {
			super(TextKind.NAME)
			this << c
		}

		@Override
		Result push(char c) {
			if (isWhitespace(c) || null != CharKind.find(c))
				return new Result(finish(), true)
			this << c
			null
		}
	}
}

@CompileStatic
class Module {
	List<Module> imported = new ArrayList<>()
	Map<String, Function> functions = new HashMap<>()
	Map<String, Macro> macros = new HashMap<>()

	Function func(String name) {
		final x = functions[name]
		if (null != x) return x
		for (m in imported) {
			final y = m.func(name)
			if (null != y) return y
		}
		null
	}

	Macro mac(String name) {
		final x = macros[name]
		if (null != x) return x
		for (m in imported) {
			final y = m.mac(name)
			if (null != y) return y
		}
		null
	}
}

@CompileStatic
abstract class Token {}

@CompileStatic
class CharacterToken extends Token {
	Kind kind

	CharacterToken(Kind kind) {
		this.kind = kind
	}

	enum Kind {
		DOT((char) '.'), COMMA((char) ','),
		OPEN_PAREN((char) '('), CLOSE_PAREN((char) ')'),
		OPEN_BRACK((char) '['), CLOSE_BRACK((char) ']'),
		OPEN_CURLY((char) '{'), CLOSE_CURLY((char) '}'),
		NEWLINE((char) '\n')

		final char character
		Kind(final char c) { character = c }

		static Kind find(char c) {
			for (k in values()) if (k.character == c) return k
			null
		}
	}

	String toString() { kind.toString() }
}

@CompileStatic
class TextToken extends Token {
	Kind kind
	String text

	enum Kind { NAME, NUMBER, STRING }

	String toString() { "$kind(\"${StringEscaper.escapeSoda(text)}\")" }
}