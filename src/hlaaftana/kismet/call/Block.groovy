package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory

@CompileStatic
class Block {
	Expression expression
	Memory context

	Block(Expression expr, Memory context = new Context()) {
		expression = expr
		this.context = context
	}

	IKismetObject evaluate() { expression.evaluate(context) }

	IKismetObject call() { evaluate() }

	Block child() {
		new Block(expression, new Context(context))
	}
}