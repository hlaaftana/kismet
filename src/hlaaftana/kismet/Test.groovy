package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Prelude
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.RuntimeMemory

@CompileStatic
class Test {
	static main(args) {
		def parser = new Parser()
		parser.context = new Context(Kismet.DEFAULT_CONTEXT, [echo: (IKismetObject) Prelude.funcc(System.out.&println)])
		/*final text = new File('test.ksmt').text
		def p = parser.parse(text)
		println p.repr()
		println p.evaluate(parser.context)*/
		println Prelude.func(Type.NONE, Prelude.LIST_TYPE).relation(
				Prelude.func(NumberType.Int32, Type.NONE))
		if (true) for (f in ['binarysearch', 'compareignorecase', 'factorial', 'fibonacci', 'fizzbuzz', 'memoize']) {
			println "file: $f"
			final file = new File("Kismet/examples/${f}.ksmt")
			def p = parser.parse(file.text)
			println p
			def tc = Prelude.typed.child()
			def echofunc = new Function() {
				@Override
				IKismetObject call(IKismetObject... arg) {
					println arg[0]
					return Kismet.NULL
				}
			}
			tc.addVariable('echo', Prelude.func(Type.NONE, Type.ANY))
			def t = p.type(tc)
			println t
			def i = t.instruction
			println i
			def mem = new RuntimeMemory([Prelude.typed] as Memory[], tc.size())
			mem.memory[0] = echofunc
			i.evaluate(mem)
			//p.evaluate(parser.context.child())
		}
	}
}
