package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.lib.Prelude
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetModels

@CompileStatic
class Kismet {
	static final IKismetObject NULL = KismetModels.KISMET_NULL
	static Prelude PRELUDE = new Prelude()
	static Context DEFAULT_CONTEXT = PRELUDE.defaultContext

	static IKismetObject model(x) {
		KismetModels.model(x)
	}
}
