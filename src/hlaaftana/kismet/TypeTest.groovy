package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.Prelude
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.RuntimeMemory

@CompileStatic
class TypeTest {
	static main(args) {
		println Prelude.func(NumberType.Int32, Type.ANY).relation(Prelude.func(Type.ANY, Prelude.LIST_TYPE))
	}
}
