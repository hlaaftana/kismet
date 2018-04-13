package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.ParensParser
import hlaaftana.kismet.parser.DumbParser
import hlaaftana.kismet.parser.Parser

@CompileStatic
class Kismet {
	static final IKismetObject NULL = KismetModels.KISMET_NULL
	static Context DEFAULT_CONTEXT = new Context(null, new HashMap(KismetInner.defaultContext))

	static Block parse(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		new Block(new Parser(context: ctxt).parse(code), ctxt)
	}
	
	static IKismetObject eval(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		parse(code, ctxt).evaluate()
	}

	static Block parseParens(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		new Block(ParensParser.parse(code), ctxt)
	}

	static IKismetObject evalParens(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		parseParens(code, ctxt).evaluate()
	}

	static Block parseDumb(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		new Block(DumbParser.parse(ctxt, code), ctxt)
	}

	static IKismetObject evalDumb(String code, Context ctxt = new Context(DEFAULT_CONTEXT)) {
		parseParens(code, ctxt).evaluate()
	}

	static IKismetObject model(x) { null == x ? NULL : (IKismetObject) ((Object) KismetModels).invokeMethod('model', x) }
}
