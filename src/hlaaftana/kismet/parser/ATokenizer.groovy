package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import hlaaftana.kismet.StringEscaper
import hlaaftana.kismet.parser.CharacterToken.Kind as CharKind
import hlaaftana.kismet.parser.TextToken.Kind as TextKind

import static java.lang.Character.isWhitespace

@CompileStatic
class ATokenizer {
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

		QuoteRecording(TextKind kind, char quote) {
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
		NEWLINE((char) '\n'), BACKSLASH((char) '\\'),
		SEMICOLON((char) ';'), COLON((char) ':')

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