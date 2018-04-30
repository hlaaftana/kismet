package hlaaftana.rewrite.kismet

import groovy.transform.CompileStatic
import hlaaftana.rewrite.kismet.call.Block
import hlaaftana.rewrite.kismet.parser.Parser
import hlaaftana.rewrite.kismet.scope.Prelude
import hlaaftana.rewrite.kismet.vm.Context
import hlaaftana.rewrite.kismet.vm.IKismetObject
import hlaaftana.rewrite.kismet.vm.KismetModels

@CompileStatic
class Kismet {
	static final IKismetObject NULL = KismetModels.KISMET_NULL
	static Context DEFAULT_CONTEXT = new Context(null, new HashMap(Prelude.defaultContext))

	static Block parse(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		new Block(new Parser(context: ctxt).parse(code), ctxt)
	}

	static IKismetObject eval(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		parse(code, ctxt).evaluate()
	}

	static IKismetObject model(x) {
		null == x ? NULL : (IKismetObject) ((Object) KismetModels).invokeMethod('model', x)
	}
}
