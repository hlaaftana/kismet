package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.GroovyFunction
import hlaaftana.kismet.scope.IteratorIterable
import hlaaftana.kismet.type.NumberType

@CompileStatic
class KismetModels {
	static IKismetObject KISMET_NULL = new WrapperKismetObject(null)

	static IKismetObject modelCollection(Collection col) {
		new WrapperKismetObject(col)
	}

	static IKismetObject model(obj) {
		if (null == obj) KISMET_NULL
		else if (obj instanceof IKismetObject) obj
		else if (obj instanceof Number) NumberType.from((Number) obj).instantiate(obj)
		else if (obj instanceof Boolean) new KismetBoolean(obj.booleanValue())
		else if (obj instanceof Character) new KChar(obj.charValue())
		else if (obj instanceof CharSequence) new KismetString((CharSequence) obj)
		else if (obj instanceof Collection) modelCollection((Collection) obj)
		else if (obj.getClass().isArray()) modelCollection(obj as List)
		else if (obj instanceof Closure) new GroovyFunction((Closure) obj)
		else if (obj instanceof Iterator) model((Object) new IteratorIterable((Iterator) obj))
		else new WrapperKismetObject(obj)
	}
}
