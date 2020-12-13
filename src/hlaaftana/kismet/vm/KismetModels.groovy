package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.GroovyFunction
import hlaaftana.kismet.lib.IteratorIterable
import hlaaftana.kismet.type.NumberType

@CompileStatic
class KismetModels {
	static IKismetObject KISMET_NULL = new WrapperKismetObject(null)

	static IKismetObject modelCollection(Collection col) {
		new WrapperKismetObject(col)
	}

	static IKismetObject[] collectModeled(Collection coll) {
		def res = new IKismetObject[coll.size()]
		def iter = coll.iterator()
		for (int i = 0; i < res.length; ++i) res[i] = Kismet.model(iter.next())
		res
	}

	static IKismetObject model(Object obj) {
		if (null == obj) KISMET_NULL
		else if (obj instanceof IKismetObject) (IKismetObject) obj
		else if (obj instanceof Number) NumberType.from((Number) obj).instantiate(obj)
		else if (obj instanceof Boolean) KismetBoolean.from(((Boolean) obj).booleanValue())
		else if (obj instanceof Character) new KChar(((Character) obj).charValue())
		else if (obj instanceof CharSequence) new KismetString((CharSequence) obj)
		else if (obj instanceof Tuple) new KismetTuple(collectModeled((Tuple) obj))
		else if (obj.getClass().isArray()) modelCollection(obj as List)
		else if (obj instanceof Collection) modelCollection((Collection) obj)
		else if (obj instanceof Closure) new GroovyFunction((Closure) obj)
		else if (obj instanceof Iterator) new IteratorIterable((Iterator) obj)
		else new WrapperKismetObject(obj)
	}
}
