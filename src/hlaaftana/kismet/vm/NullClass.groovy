package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.CannotOperateException
import hlaaftana.kismet.type.MetaType
import hlaaftana.kismet.type.NoType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeRelation

@CompileStatic
class NullClass implements IKismetClass, IKismetObject<NullClass> {
	static final NullClass INSTANCE = new NullClass()
	static final IKismetObject OBJECT = new IKismetObject() {
		IKismetClass kismetClass() { INSTANCE }
		Type getType() { NoType.INSTANCE }
		def inner() { null }
	}
	private NullClass() {}

	boolean isInstance(IKismetObject object) {
		object.inner() == null
	}

	String getName() { 'Null' }
	IKismetObject cast(IKismetObject object) { OBJECT }

	IKismetClass kismetClass() { MetaKismetClass.INSTANCE }
	NullClass inner() { INSTANCE }

	IKismetObject propertyGet(IKismetObject obj, String name) {
		throw new CannotOperateException('get property ' + name, 'null')
	}

	IKismetObject propertySet(IKismetObject obj, String name, IKismetObject value) {
		throw new CannotOperateException('set property ' + name + ' to ' + value, 'null')
	}

	IKismetObject subscriptGet(IKismetObject obj, IKismetObject key) {
		throw new CannotOperateException('get subscript ' + key, 'null')
	}

	IKismetObject subscriptSet(IKismetObject obj, IKismetObject key, IKismetObject value) {
		throw new CannotOperateException('set subscript ' + key + ' to ' + value, 'null')
	}

	IKismetObject call(IKismetObject obj, IKismetObject[] args) {
		throw new CannotOperateException('call object', 'null')
	}

	IKismetObject construct(IKismetObject[] args) {
		throw new CannotOperateException('construct object', 'null')
	}

	Type getType() { new MetaType(this) }

	TypeRelation relation(Type other) {
		TypeRelation.subtype(Integer.MAX_VALUE)
	}
}