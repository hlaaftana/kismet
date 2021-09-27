package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory

@CompileStatic
interface KismetCallable {
	IKismetObject call(Memory c, Expression... args)
}
