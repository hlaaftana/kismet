package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.Module

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class Prelude extends NativeModule {
	static final List<NativeModule> defaultModules = [
            new Syntax(), new Reflection(), new Modules(), new Types(),
            new Functions(), new Logic(), new Errors(), new Numbers(),
            new Comparison(), new Strings(), new CollectionsIterators(),
            new RandomModule(), new Json(), new Times()
	].asImmutable()

	Prelude() {
		super("prelude")
		for (Module mod : defaultModules) {
			typedContext.heritage.add(mod.typedContext)
			defaultContext = new Context(mod.defaultContext, defaultContext.getVariables())
		}
	}
}








