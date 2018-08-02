package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
class FunctionType implements Type {
	TupleType parameters
	Type returnType

	FunctionType(TupleType parameters, Type returnType) {
		this.parameters = parameters
		this.returnType = returnType
	}

	String toString() { "Function[$returnType]${parameters.elements.join(', ')}" }

	TypeRelation relation(Type other) {
		if (other instanceof FunctionType) {
			TypeRelation max = returnType.relation(other.returnType)
			if (max.none) return TypeRelation.none()
			def paramrel = parameters.relation(other.parameters)
			if (paramrel.none) return TypeRelation.none()
			if (paramrel.value > max.value) max = paramrel
			max
		} else TypeRelation.none()
	}

	boolean equals(obj) { obj instanceof FunctionType && parameters == obj.parameters && returnType == obj.returnType }
}
