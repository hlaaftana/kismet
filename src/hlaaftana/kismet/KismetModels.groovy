package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
class KismetModels {
	static KismetObject KISMET_NULL = new KismetObject(null, KismetInner.defaultContext.Null)

	static KismetObject model(KismetClass x) { x.object }
	static KismetObject model(KismetObject obj) { obj }
	static KismetObject model(Closure c){ model(new GroovyFunction(c)) }

	static KismetObject model(File f){
		model(new Expando(name: f.name) )
	}

	static KismetObject model(obj){
		null == obj ? KISMET_NULL :
			obj.class.array ? model(obj as List) :
				new KismetObject(obj, KismetClass.from(obj.class).object ?: KismetInner.defaultContext.Native)
	}
}
