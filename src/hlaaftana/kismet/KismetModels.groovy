package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.obj.KChar
import hlaaftana.kismet.obj.KismetNumber
import hlaaftana.kismet.obj.KismetString

@CompileStatic
class KismetModels {
	static IKismetObject KISMET_NULL = new KismetObject(null, KismetInner.defaultContext.Null)

	static IKismetObject<KismetClass> model(KismetClass x) { x.object }
	static <T> IKismetObject<T> model(IKismetObject<T> obj) { null == obj ? KISMET_NULL : obj }
	static IKismetObject<GroovyFunction> model(Closure c){ model(new GroovyFunction(c)) }
	static <T> IKismetObject<IteratorIterable<T>> model(Iterator<T> it) { model((Object) new IteratorIterable<T>(it)) }
	static <T extends Number> KismetNumber<T> model(T num) { (KismetNumber<T>) KismetNumber.from(num) }
	static KChar model(char c) { new KChar(c) }
	static KismetString model(CharSequence seq) { new KismetString(seq) }

	static IKismetObject model(obj) {
		null == obj ? KISMET_NULL :
			obj.class.array ? model(obj as List) :
				new KismetObject(obj, KismetClass.from(obj.class).object ?: KismetInner.defaultContext.Native)
	}
}
