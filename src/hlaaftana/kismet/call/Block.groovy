package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.Context
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
class Block {
	Expression expression
	Context context

	Block(Expression expr, Context context = new Context()) {
		expression = expr
		this.context = context
	}

	IKismetObject evaluate() { expression.evaluate(context) }

	IKismetObject call() { evaluate() }

	Block child() {
		new Block(expression, new Context(context))
	}
}