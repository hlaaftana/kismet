package hlaaftana.rewrite.kismet.vm

import groovy.transform.CompileStatic

@CompileStatic
class MetaKismetClass<T extends IKismetObject<IKismetClass>> extends BasicClass<T> {
	static
	final MetaKismetClass INSTANCE = new MetaKismetClass()
	static final ClassObject OBJECT = new ClassObject(INSTANCE)

	private MetaKismetClass() {
		super('Class')
	}

	IKismetObject call(T obj, IKismetObject[] args) {
		((IKismetClass) obj.inner()).construct(args)
	}
}