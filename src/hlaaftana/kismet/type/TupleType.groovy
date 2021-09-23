package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import hlaaftana.kismet.call.TypedTemplate
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetTuple

@CompileStatic
@EqualsAndHashCode
class TupleType extends GenericType {
	static final SingleType BASE = new SingleType('Tuple') {
		boolean check(IKismetObject obj) { obj instanceof KismetTuple }
	}
	Type varargs

	TupleType() { super(BASE) }

	TupleType(Type[] bounds) {
		super(BASE, bounds)
	}

	String toString() { "Tuple[${arguments.join(', ')}" + (null == varargs ? "]" : ", $varargs...]") }

	boolean isIndefinite() { null != varargs }

	boolean check(IKismetObject obj) {
		if (obj instanceof KismetTuple && (null == varargs ? obj.size() == arguments.length : obj.size() >= arguments.length)) {
			for (int i = 0; i < obj.size(); ++i) {
				if (!getAt(i).check(obj)) return false
			}
			true
		} else false
	}

	TypeBound.Variance varianceAt(int i) {
		TypeBound.Variance.COVARIANT
	}

	TupleType withVarargs(Type varargs) {
		this.varargs = varargs
		this
	}

	Type getAt(int i) {
		i >= arguments.length ? varargs : arguments[i]
	}
}
