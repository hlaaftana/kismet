package hlaaftana.kismet.scope

import groovy.transform.CompileStatic

@CompileStatic
class KismetModuleSpace<Handle> {
    Map<Handle, KismetModule<Handle>> modules = new HashMap<>()
}
