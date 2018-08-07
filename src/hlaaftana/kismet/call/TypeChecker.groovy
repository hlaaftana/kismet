package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
abstract class TypeChecker implements KismetCallable, IKismetObject {
	abstract TypedExpression transform(TypedContext context, Expression... args)

	IKismetObject call(Context c, Expression... args) {
		transform(null, args).instruction.evaluate(c)
	}

	TypeChecker inner() { this }
}
