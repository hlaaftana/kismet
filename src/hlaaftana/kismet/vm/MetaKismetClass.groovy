package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.CannotOperateException

@CompileStatic
class MetaKismetClass implements IKismetClass {
	static final MetaKismetClass INSTANCE = new MetaKismetClass()
	static final ClassObject OBJECT = new ClassObject(INSTANCE)

	private MetaKismetClass() {}

	boolean isInstance(IKismetObject object) {
		object.kismetClass() == this
	}

	IKismetObject cast(IKismetObject object) {
		if (!isInstance(object)) throw new CannotOperateException('cast to class', 'non-class')
		object
	}

	String getName() { 'Class' }
}