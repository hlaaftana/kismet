package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
interface KismetCallable {
	KismetObject call(KismetObject... args)
}
