package hlaaftana.kismet.parser

import groovy.transform.CompileStatic
import hlaaftana.kismet.Context

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
  over l, a {
    s += sqr(a - m)
  }
  sqrt(s / prev(l.size))
}

def skewness(l) {
  l.(mean - median) * 3 / l.standard_deviation
}
*/

@CompileStatic
class UFCSParserWIP {
	static Expression parse(Context context, List<Token> tokens) {
		List<Expression> call = []

		Expression current
		ExpressionRecording recording
		for (t in tokens) {
			if (null == recording) {

			}
		}
	}

	static abstract class ExpressionRecording {
		abstract Result push(Token c)

		static class Result {
			Expression expression
			boolean goBack

			Result(Expression expression, boolean goBack) {
				this.expression = expression
				this.goBack = goBack
			}
		}
	}

	static class CallRecording extends ExpressionRecording {
		List list
		Kind kind

		enum Kind { PARENS_LIST, PARENS_PREFIX, NO_PARENS_LIST, NO_PARENS_PREFIX, INFIX }

		Result push(Token c) {

		}
	}

	static class PropertyRecording extends ExpressionRecording {
		Result push(Token c) {

		}
	}
}