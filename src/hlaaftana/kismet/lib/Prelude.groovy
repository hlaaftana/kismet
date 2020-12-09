package hlaaftana.kismet.lib


import groovy.transform.CompileStatic
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class Prelude extends LibraryModule {
	TypedContext typed = new TypedContext("prelude")
	Context defaultContext = new Context()
	static final List<LibraryModule> defaultModules = [
		new Syntax(), new Reflection(), new Types(),
		new Functions(), new Logic(), new Errors(), new Numbers(),
		new Cmp(), new Strings(), new CollectionsIterators(),
		new RandomModule(), new Json(), new Times()
	].asImmutable()

	Prelude() {
		for (LibraryModule mod : defaultModules) {
			typed.heritage.add(mod.typed)
			defaultContext = new Context(mod.defaultContext, defaultContext.getVariables())
		}
	}
}








