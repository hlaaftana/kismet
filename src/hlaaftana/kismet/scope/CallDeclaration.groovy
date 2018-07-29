package hlaaftana.kismet.scope

import hlaaftana.kismet.call.TypedExpression
import hlaaftana.kismet.type.Type

interface CallDeclaration {
	int getArgumentLength()
	Type getArgumentType(int index)
	Type getReturnType()
	String getName()
	int getNameHash()
	TypedContext.Variable getVariable()
	TypedExpression call(TypedContext tc, TypedExpression[] args)
}