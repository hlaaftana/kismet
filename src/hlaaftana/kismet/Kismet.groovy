package hlaaftana.kismet

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

@CompileStatic
class Kismet {
	static Block parse(String code, Map ctxt = new HashMap(KismetInner.defaultContext)){
		Block b = new Block()
		b.context = new Context(b, ctxt)
		b.expression = KismetInner.parse(code)
		b
	}
	
	static eval(String code, Map ctxt = new HashMap(KismetInner.defaultContext)){
		parse(code, ctxt).evaluate()
	}

	@CompileDynamic
	static KismetObject model(x){ KismetModels.model(x) }
}
