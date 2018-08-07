package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.Prelude
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
class KismetIterator {
	Expression inner

	IKismetObject iterate(Context c, Expression toCall) {
		c.set('yield', new YieldTemplate(toCall))
		inner.evaluate(c)
	}

	TypedExpression generate(TypedContext tc, Expression toCall, Type preferred) {
		tc.addStaticVariable('yield', new YieldTemplate(toCall), Prelude.TEMPLATE_TYPE)
		inner.type(tc, preferred)
	}

	static class YieldTemplate extends Template {
		Expression toCall

		YieldTemplate(Expression toCall) {
			this.toCall = toCall
		}

		Expression transform(Parser parser, Expression... args) {
			def exprs = new ArrayList<Expression>(args.length + 1)
			exprs.add(toCall)
			exprs.addAll(args)
			new CallExpression(exprs)
		}
	}
}


