package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
abstract class ContextFunction implements KismetCallable {
	boolean pure
	int precedence

	IKismetObject call(Context c, Expression... args) {
		final arr = new IKismetObject[args.length]
		for (int i = 0; i < arr.length; ++i) {
			arr[i] = args[i].evaluate(c)
		}
		call(c, arr)
	}

	abstract IKismetObject call(Context c, IKismetObject... args)
}