package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.CannotOperateException
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
abstract class TypedTemplate implements KismetCallable, IKismetObject {
	abstract TypedExpression transform(TypedContext context, TypedExpression... args)

	IKismetObject call(Context c, Expression... args) {
		throw new CannotOperateException("use runtime", "typed template")
	}

	TypedTemplate inner() { this }
}
