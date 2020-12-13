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


