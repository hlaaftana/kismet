package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory

@CompileStatic
abstract class TypeChecker implements KismetCallable, IKismetObject {
	abstract TypedExpression transform(TypedContext context, Expression... args)

	IKismetObject call(Memory c, Expression... args) {
		transform(null, args).instruction.evaluate(c)
	}

	TypeChecker inner() { this }
}
