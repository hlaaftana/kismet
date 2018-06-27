package hlaaftana.kismet.call

import hlaaftana.kismet.vm.Context
import hlaaftana.kismet.vm.IKismetObject

trait Template implements KismetCallable {
	boolean isConstant() { true }

	abstract Expression transform(Expression... args)

	IKismetObject call(Context c, Expression... args) {
		transform(args).evaluate(c)
	}
}
