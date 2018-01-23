package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.OldParser
import hlaaftana.kismet.parser.Parser

@CompileStatic
class Kismet {
	static final KismetObject NULL = KismetModels.KISMET_NULL
	static Context DEFAULT_CONTEXT = new Context(null, new HashMap(KismetInner.defaultContext))

	static Block parse(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		new Block(Parser.parse(ctxt, code), ctxt)
	}
	
	static KismetObject eval(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		parse(code, ctxt).evaluate()
	}

	static Block parseOld(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		new Block(OldParser.parse(code), ctxt)
	}

	static KismetObject evalOld(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		parseOld(code, ctxt).evaluate()
	}

	static KismetObject model(x) { null == x ? NULL : (KismetObject) ((Object) KismetModels).invokeMethod('model', x) }
}
