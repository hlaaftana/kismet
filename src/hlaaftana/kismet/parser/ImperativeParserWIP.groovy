package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import hlaaftana.kismet.Context
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.KismetObject
import hlaaftana.kismet.UnexpectedSyntaxException
import hlaaftana.kismet.parser.CharacterToken.Kind as CharKind
import hlaaftana.kismet.parser.TextToken.Kind as TextKind

/*
def fibonacci(n) {
  for f = 0, s = 1; collect s in range(1, n) {
    f = s + (s = f)
  }
}

def fibonacci_collect(n) {
  f = 0; s = 1
  range(1, n) collect { f = s + (s = f) }
}

def fibonacci_imperative(n) {
  let return l = new_list(n) {
    s = 1
    f = i = 0
    while i < n {
      l.add(f = s + (s = f))
      incr i
    }
  }
}

fib100 := [
  1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377, 610, 987, 1597, 2584, 4181, 6765, 10946, 17711, 28657,
  46368, 75025, 121393, 196418, 317811, 514229, 832040, 1346269, 2178309, 3524578, 5702887, 9227465,
  14930352, 24157817, 39088169, 63245986, 102334155, 165580141, 267914296, 433494437, 701408733,
  1134903170, 1836311903, 2971215073, 4807526976, 7778742049, 12586269025, 20365011074, 32951280099,
  53316291173, 86267571272, 139583862445, 225851433717, 365435296162, 591286729879, 956722026041,
  1548008755920, 2504730781961, 4052739537881, 6557470319842, 10610209857723, 17167680177565,
  27777890035288, 44945570212853, 72723460248141, 117669030460994, 190392490709135, 308061521170129,
  498454011879264, 806515533049393, 1304969544928657, 2111485077978050, 3416454622906707,
  5527939700884757, 8944394323791464, 14472334024676221, 23416728348467685, 37889062373143906,
  61305790721611591, 99194853094755497, 160500643816367088, 259695496911122585, 420196140727489673,
  679891637638612258, 1100087778366101931, 1779979416004714189, 2880067194370816120, 4660046610375530309,
  7540113804746346429, 12200160415121876738, 19740274219868223167, 31940434634990099905, 51680708854858323072,
  83621143489848422977, 135301852344706746049, 218922995834555169026, 354224848179261915075]

for fun in [fibonacci, fibonacci_collect, fibonacci_imperative] {
  assert_is(fib100, fun(100))
}
*/

@CompileStatic
class ParserUpdate {
	static Expression parse(Context parserBlock, List<Token> tokens) {
		def list = new ArrayList<Expression>()
		LineRecording recording
		final iter = tokens.listIterator()
		while (iter.hasNext()) {
			final t = iter.next()
			if (null == recording && !(t instanceof CharacterToken && ((CharacterToken) t).kind == CharKind.NEWLINE ||
					((CharacterToken) t).kind == CharKind.SEMICOLON)) {
				recording = new LineRecording(t)
			} else {
				final r = recording.push(t)
				if (null != r) {
					recording = null
					list.add(r.expression)
					if (r.goBack) iter.previous()
				}
			}
		}
		list.size() == 1 ? list[0] : new BlockExpression(list)
	}

	static abstract class ExprRecording {
		List<Expression> expressions = new ArrayList<>()
		Expression last

		abstract Result push(Token t)

		void leftShift(Expression e) { expressions.add(last = e) }
		void mod(Expression e) { expressions[-1] = last = e }

		static class Result {
			Expression expression
			boolean goBack

			Result(Expression expression, boolean goBack = false) {
				this.expression = expression
				this.goBack = goBack
			}
		}
	}

	static class BlockRecording extends ExprRecording {
		LineRecording recording

		Result push(Token t) {
			if (null == recording) {
				if (t instanceof CharacterToken && ((CharacterToken) t).kind == CharKind.CLOSE_CURLY)
					return new Result(new BlockExpression(expressions))
				else recording = new LineRecording()
			}
			final result = recording.push(t)
			if (null != result) {
				recording = null
				this << result.expression
				if (result.goBack) return push(t)
			}
			null
		}
	}

	static class LineRecording extends ExprRecording {
		ExprRecording recording
		LineRecording(Token t) { push(t) }
		LineRecording() {}

		Expression finish() {

		}

		Result push(Token t) {
			if (null == recording) {
				int expamt = expressions.size()
				if (expamt == 0) {
					if (t instanceof TextToken) {
						final tt = (TextToken) t
						final k = tt.kind
						final tx = tt.text
						if (k == TextKind.NAME) this << new NameExpression(tx)
						else if (k == TextKind.STRING) this << new StringExpression(tx)
						else if (k == TextKind.NUMBER) this << parseNumber(tx)
					} else if (t instanceof CharacterToken) {
						final ct = (CharacterToken) t
						final k = ct.kind
						if (k == CharKind.NEWLINE || k == CharKind.SEMICOLON) return new Result(NoExpression.INSTANCE)
						else if (k == CharKind.DOT) throw new UnexpectedSyntaxException('Dot in start of line?')
						else if (k == CharKind.OPEN_PAREN) recording = new ListRecording(CharKind.CLOSE_PAREN)
						else if (k == CharKind.OPEN_BRACK) recording = new ListRecording(CharKind.CLOSE_BRACK)
						else if (k == CharKind.OPEN_CURLY) recording = new BlockRecording()
						else if (k == CharKind.CLOSE_PAREN) throw new UnexpectedSyntaxException('Closing parens in start of line?')
						else if (k == CharKind.CLOSE_BRACK) throw new UnexpectedSyntaxException('Closing bracks in start of line?')
						else if (k == CharKind.CLOSE_CURLY) throw new UnexpectedSyntaxException('Closing curlys in start of line?')
					}
				} else {
					if (t instanceof TextToken) {
						final tt = (TextToken) t
						final k = tt.kind
						final tx = tt.text
						if (k == TextKind.NAME) this << new NameExpression(tx)
						else if (k == TextKind.STRING) this << new StringExpression(tx)
						else if (k == TextKind.NUMBER) this << parseNumber(tx)
					} else if (t instanceof CharacterToken) {
						final ct = (CharacterToken) t
						final k = ct.kind
						if (k == CharKind.NEWLINE || k == CharKind.SEMICOLON) return new Result(NoExpression.INSTANCE)
						else if (k == CharKind.DOT) throw new UnexpectedSyntaxException('Dot in start of line?')
						else if (k == CharKind.OPEN_PAREN) recording = new ListRecording(CharKind.CLOSE_PAREN)
						else if (k == CharKind.OPEN_BRACK) recording = new ListRecording(CharKind.CLOSE_BRACK)
						else if (k == CharKind.OPEN_CURLY) recording = new BlockRecording()
						else if (k == CharKind.CLOSE_PAREN) throw new UnexpectedSyntaxException('Closing parens in start of line?')
						else if (k == CharKind.CLOSE_BRACK) throw new UnexpectedSyntaxException('Closing bracks in start of line?')
						else if (k == CharKind.CLOSE_CURLY) throw new UnexpectedSyntaxException('Closing curlys in start of line?')
					}
				}
			} else {
				final result = recording.push(t)
				if (null != result) {
					recording = null
					this << result.expression
					if (result.goBack) return push(t)
				}
			}
			null
		}
	}

	static class ListRecording extends ExprRecording {
		LineRecording recording
		CharKind closer
		boolean commaAdded = false

		ListRecording(CharKind o) { closer = o }

		Result push(Token t) {
			if (null == recording) recording = new LineRecording()
			if (null == recording.recording && t instanceof CharacterToken && ((CharacterToken) t).kind == closer) {

			}
			null
		}
	}

	static NumberExpression parseNumber(String text) {
		def explicitFloat, floats
		explicitFloat = floats = false
		Integer index = null
		final cs = text.chars
		for (int i = 0; i < cs.length; ++i) {
			final c = cs[i]
			if (!floats && (c == ((char) '.') || c == ((char) 'e') || c == ((char) 'E'))) floats = true
			else if ((explicitFloat = c == ((char) 'f') || c == ((char) 'F')) ||
				c == ((char) 'i') || c == ((char) 'I')) { index = i; break }
		}
		if (null == index) return new NumberExpression(floats ? new BigDecimal(text) : new BigInteger(text))
		final str = text.substring(0, index), bitstr = text.substring(index + 1)
		if (bitstr.empty) return new NumberExpression(explicitFloat ? new BigDecimal(str) : new BigInteger(str))
		int bits
		try {
			bits = Integer.valueOf(bitstr)
		} catch (NumberFormatException ignored) {
			throw new UnexpectedSyntaxException("Invalid bit number in number string $text: $bitstr")
		}
		Number num
		if (explicitFloat) {
			if (bits == 32) num = Float.valueOf(str)
			else if (bits == 64) num = Double.valueOf(str)
			else if (bits == 1) num = -new BigDecimal(str)
			else if (bits == 33) num = -Float.valueOf(str)
			else if (bits == 65) num = -Double.valueOf(str)
			else throw new UnexpectedSyntaxException("Unknown float bit number in number string $text: $bitstr")
		} else {
			if (bits == 8) num = Byte.valueOf(str)
			else if (bits == 16) num = Short.valueOf(str)
			else if (bits == 32) num = Integer.valueOf(str)
			else if (bits == 64) num = Long.valueOf(str)
			else if (bits == 1) num = -new BigInteger(str)
			else if (bits == 9) num = -Byte.valueOf(str)
			else if (bits == 17) num = -Short.valueOf(str)
			else if (bits == 33) num = -Integer.valueOf(str)
			else if (bits == 65) num = -Long.valueOf(str)
			else throw new UnexpectedSyntaxException("Unknown integer bit number in number string $text: $bitstr")
		}
		new NumberExpression(num)
	}
}

@CompileStatic
class TupleExpression extends Expression {
	List<Expression> expressions

	TupleExpression(List<Expression> expressions) {
		this.expressions = expressions
	}

	@Override
	KismetObject evaluate(Context c) {
		final arr = new KismetObject[expressions.size()]
		for (int i = 0; i < arr.length; ++i) arr[i] = expressions[i].evaluate(c)
		Kismet.model(new Tuple(arr))
	}
}

@CompileStatic
class ListExpression extends Expression {
	List<Expression> expressions

	ListExpression(List<Expression> expressions) {
		this.expressions = expressions
	}

	@Override
	KismetObject evaluate(Context c) {
		Kismet.model(expressions*.evaluate(c))
	}
}