package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.lib.Functions
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeBound
import hlaaftana.kismet.vm.IKismetObject

/*
defiter [range a b] {
  i: a
  while [<= i b] {
    yield i
    incr i
  }
}

for it [range 1 5] {
  echo it
}
;; new scope for { echo it }, add `it`
;; type { echo it }
;; new scope for iterator block
;; add `yield` template to iterator, injects the typed { echo it }
;; instantiate iterator block
;; this will probably not work at runtime because the scopes are alien

let [a: 1, b: 1] {
  i: a
  while [<= i b] {
    let [it: i] {
      echo it
    }
    incr i
  }
}

dive {
  a: 1
  b: 5

  i: a
  while [<= i b] {
    {
      it: i
      echo it
    }
    incr i
  }
}
 */

@CompileStatic
class KismetIterator {
	Expression inner

	IKismetObject iterate(Context c, Expression toCall) {
		c.set('yield', new YieldTemplate(toCall))
		inner.evaluate(c)
	}

	TypedExpression generate(TypedContext tc, Expression toCall, Type preferred) {
		tc.addVariable('yield', new YieldTemplate(toCall), Functions.TEMPLATE_TYPE)
		inner.type(tc, new TypeBound(preferred))
	}

	static class YieldTemplate extends Template {
		Expression toCall

		YieldTemplate(Expression toCall) {
			this.toCall = toCall
		}

		Expression transform(Parser parser, Expression... args) {
			new ColonExpression(toCall, args[0])
		}
	}
}


