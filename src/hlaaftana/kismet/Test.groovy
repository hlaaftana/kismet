package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.call.TypedExpression
import hlaaftana.kismet.call.TypedStringExpression
import hlaaftana.kismet.call.TypedTemplate
import hlaaftana.kismet.lib.Functions
import hlaaftana.kismet.lib.Strings
import hlaaftana.kismet.lib.Types
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.GenericType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.RuntimeMemory

@CompileStatic
class Test {
	static final Function echo = new Function() {
		@Override
		IKismetObject call(IKismetObject... arg) {
			println "OUTPUT: " + arg[0]
			return Kismet.NULL
		}
	}, analyze = new Function() {
		@Override
		IKismetObject call(IKismetObject... args) {
			println "CLASS: " + args[0].getClass()
			println "INNER VALUE: " + args[0].inner()
			return Kismet.NULL
		}
	}

	static final TypedTemplate type_relation = new TypedTemplate() {
		static Type toType(Type type) {
			type instanceof GenericType && type.base == Types.META_TYPE ? type.arguments[0] : type
		}

		TypedExpression transform(TypedContext context, TypedExpression... args) {
			new TypedStringExpression(toType(args[0].type).relation(toType(args[1].type)).toString())
		}
	}, explain_typed = new TypedTemplate() {
		@Override
		TypedExpression transform(TypedContext context, TypedExpression... args) {
			println "TYPED EXPLANATION: " +  args[0].toString()
			args[0]
		}
	}

	static void run(Parser parser, String text) {
		def p = parser.parse(text)
		def tc = Kismet.PRELUDE.typed.child()
		tc.addVariable('echo', echo, Functions.func(Type.NONE, Type.ANY))
		tc.addVariable('analyze', analyze, Functions.func(Type.NONE, Type.ANY))
		tc.addVariable('type_relation', type_relation, Functions.typedTmpl(Strings.STRING_TYPE, Types.META_TYPE, Types.META_TYPE))
		tc.addVariable('explain_typed', explain_typed, Functions.typedTmpl(Type.NONE, Type.ANY))
		def t = p.type(tc)
		def i = t.instruction
		def mem = new RuntimeMemory([Kismet.PRELUDE.typed] as Memory[], tc.size())
		mem.memory[0] = echo
		mem.memory[1] = analyze
		mem.memory[2] = type_relation
		mem.memory[3] = explain_typed
		i.evaluate(mem)
	}

	static main(args) {
		def parser = new Parser()
		parser.context = new Context(Kismet.DEFAULT_CONTEXT, [echo: (IKismetObject) echo, analyze: (IKismetObject) analyze])
		def passed = [], failed = []
		for (f in new File('examples').list()) {
			println "file: $f"
			final file = new File("examples/" + f)
			try {
				run(parser, file.text)
				println "passed: $f"
				passed.add(f)
			} catch (ex) {
				ex.printStackTrace()
				println "failed: $f"
				failed.add(f)
			}
		}
		def paslen = passed.size(), failen = failed.size(), total = paslen + failen
		println "passed: $paslen/$total"
		if (paslen != 0) println "passed files: ${passed.join(', ')}"
		println "failed: $failen/$total"
		if (failen != 0) println "failed files: ${failed.join(', ')}"
	}
}
