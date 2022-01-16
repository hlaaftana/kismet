package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.Memory

@CompileStatic
abstract class Module {
    abstract TypedContext typeContext()
    abstract Context getDefaultContext()
    abstract Memory run()
}
