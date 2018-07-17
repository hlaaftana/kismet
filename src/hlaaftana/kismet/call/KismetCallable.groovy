package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
interface KismetCallable {
	IKismetObject call(Context c, Expression... args)
}
