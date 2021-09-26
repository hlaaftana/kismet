package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.lib.Functions
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeRelation
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory

@CompileStatic
abstract class Instructor implements IKismetObject {
	Type argumentTypes
	Type returnType

	abstract IKismetObject call(Memory m, Instruction... args)

	Instructor inner() { this }
}

