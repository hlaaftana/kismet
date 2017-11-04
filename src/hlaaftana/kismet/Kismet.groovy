package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
class Kismet {
	static KismetObject NULL = KismetModels.KISMET_NULL

	static Block parse(String code, Map ctxt = new HashMap(KismetInner.defaultContext)){
		Block b = new Block()
		b.context = new Context(b, ctxt)
		b.expression = KismetInner.parse(code)
		b
	}
	
	static eval(String code, Map ctxt = new HashMap(KismetInner.defaultContext)){
		parse(code, ctxt).evaluate()
	}

	static KismetObject model(x){ null == x ? NULL : (KismetObject) ((Object) KismetModels).invokeMethod('model', x) }
}
