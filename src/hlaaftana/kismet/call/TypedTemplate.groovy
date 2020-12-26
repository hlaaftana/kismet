package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.CannotOperateException
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory

@CompileStatic
abstract class TypedTemplate implements KismetCallable, IKismetObject {
	abstract TypedExpression transform(TypedContext context, TypedExpression... args)

	IKismetObject call(Memory c, Expression... args) {
		throw new CannotOperateException("use runtime", "typedContext template")
	}

	TypedTemplate inner() { this }
}
