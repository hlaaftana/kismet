package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
class KismetModels {
	static KismetObject KISMET_NULL = new KismetObject(null, KismetInner.defaultContext.Null)

	static KismetObject<KismetClass> model(KismetClass x) { x.object }
	static <T> KismetObject<T> model(KismetObject<T> obj) { null == obj ? KISMET_NULL : obj }
	static KismetObject<GroovyFunction> model(Closure c){ model(new GroovyFunction(c)) }
	static <T> KismetObject<IteratorIterable<T>> model(Iterator<T> it) { model((Object) new IteratorIterable<T>(it)) }

	static KismetObject model(obj){
		null == obj ? KISMET_NULL :
			obj.class.array ? model(obj as List) :
				new KismetObject(obj, KismetClass.from(obj.class).object ?: KismetInner.defaultContext.Native)
	}
}
