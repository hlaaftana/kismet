package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet

@CompileStatic
class KismetModuleSpace<Handle> {
    Map<Handle, KismetModule<Handle>> modules = new HashMap<>()
    List<Module> defaultDependencies = [(Module) Kismet.PRELUDE]
}
