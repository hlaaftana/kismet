package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
class Kismet {
	static KismetObject NULL = KismetModels.KISMET_NULL

	static Block parse(String code, Map ctxt = new HashMap(KismetInner.defaultContext)){
		Block x = new Block()
		x.context = new Context(x, ctxt)
		parse(code, x)
	}

	static Block parse(String code, Block parent) {
		Block b = new Block()
		b.context = new Context(b)
		b.parent = parent
		b.expression = KismetInner.parse(b, code)
		b
	}
	
	static eval(String code, Map ctxt = new HashMap(KismetInner.defaultContext)){
		parse(code, ctxt).evaluate()
	}

	static eval(String code, Block parent) { parse(code, parent).evaluate() }

	static KismetObject model(x){ null == x ? NULL : (KismetObject) ((Object) KismetModels).invokeMethod('model', x) }
}
