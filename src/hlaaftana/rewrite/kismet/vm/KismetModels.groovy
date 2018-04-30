package hlaaftana.rewrite.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.rewrite.kismet.call.GroovyFunction
import hlaaftana.rewrite.kismet.scope.IteratorIterable
import hlaaftana.rewrite.kismet.scope.Prelude

@CompileStatic
class KismetModels {
	static IKismetObject KISMET_NULL = new WrapperKismetObject(null, Prelude.defaultContext.Null)

	static IKismetObject<WrapperKismetClass> model(WrapperKismetClass x) { x.object }

	static <T> IKismetObject<T> model(IKismetObject<T> obj) {
		null == obj ? KISMET_NULL : obj
	}

	static IKismetObject<GroovyFunction> model(Closure c) { model(new GroovyFunction(c)) }

	static <T> IKismetObject<IteratorIterable<T>> model(Iterator<T> it) {
		model((Object) new IteratorIterable<T>(it))
	}

	static <T extends Number> KismetNumber<T> model(T num) {
		(KismetNumber<T>) KismetNumber.from(num)
	}

	static KChar model(char c) { new KChar(c) }

	static KismetString model(CharSequence seq) {
		new KismetString(seq)
	}

	static IKismetObject model(obj) {
		null == obj ? KISMET_NULL :
				obj.class.array ? model(obj as List) :
						new WrapperKismetObject(obj, WrapperKismetClass.from(obj.class).object ?: Prelude.defaultContext.Native)
	}
}
