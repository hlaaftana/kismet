package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
class KismetIterator {
	Expression inner

	IKismetObject iterate(Context c, Expression toCall) {
		c.set('yield', new YieldTemplate(toCall))
		inner.evaluate(c)
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


