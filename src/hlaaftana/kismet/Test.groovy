package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.*
import hlaaftana.kismet.exceptions.UnexpectedTypeException
import hlaaftana.kismet.lib.Functions
import hlaaftana.kismet.lib.NativeModule
import hlaaftana.kismet.lib.Strings
import hlaaftana.kismet.lib.Types
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.*
import hlaaftana.kismet.type.*
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
			println "INNER VALUE CLASS: " + args[0].inner().getClass()
			if (args[0].inner() instanceof Collection) {
				def iter = args[0].inner().iterator()
				if (iter.hasNext()) println "ELEMENT CLASS: " + iter.next().getClass()
			}
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
	}, parameter_type = new TypedTemplate() {
		@Override
		TypedExpression transform(TypedContext context, TypedExpression... args) {
			def t = args.length > 0 ? (Type) args[0].instruction.evaluate(context) : Type.ANY
			def pt = new ParameterType(t)
			new TypedConstantExpression(Types.META_TYPE.generic(pt), pt)
		}
	}, parametrize = new TypedTemplate() {
		static void analyze(Type a, Type b) {
			if (a instanceof ParameterType) a.trySet(b)
			else if (!a.relation(b).assignableFrom)
				throw new UnexpectedTypeException('types ' + a + ' and ' + b + ' do not match')
			else if (a instanceof GenericType) {
				if (b instanceof SingleType || (b instanceof GenericType && b.arguments == null)) {
					// do nothing
				} else if (b instanceof GenericType) {
					for (int i = 0; i < a.arguments.length; ++i) {
						analyze(a.arguments[i], b.arguments[i])
					}
				} else {
					throw new UnexpectedTypeException('dont know where i am')
				}
			} else if (b instanceof GenericType) {
				for (int i = 0; i < b.arguments.length; ++i) {
					analyze(Type.ANY, b.arguments[i])
				}
			}
		}

		@Override
		TypedExpression transform(TypedContext context, TypedExpression... args) {
			analyze((Type) args[0].instruction.evaluate(context), (Type) args[1].instruction.evaluate(context))
			TypedNoExpression.INSTANCE
		}
	}, unparam = new TypedTemplate() {
		@Override
		TypedExpression transform(TypedContext context, TypedExpression... args) {
			def t = ((ParameterType) args[0].instruction.evaluate(context)).inner
			new TypedConstantExpression(Types.META_TYPE.generic(t), t)
		}
	}

	static NativeModule tests = new NativeModule("tests")
	static {
		tests.define('echo', Functions.func(Type.NONE, Type.ANY), echo)
		tests.define('analyze', Functions.func(Type.NONE, Type.ANY), analyze)
		tests.define('type_relation', Functions.typedTmpl(Strings.STRING_TYPE, Types.META_TYPE, Types.META_TYPE), type_relation)
		tests.define('explain_typed', Functions.typedTmpl(Type.NONE, Type.ANY), explain_typed)
		tests.define('parameter_type',
			new UnionType(Functions.typedTmpl(Types.META_TYPE), Functions.typedTmpl(Types.META_TYPE, Types.META_TYPE)), parameter_type)
		tests.define('parametrize',
			Functions.typedTmpl(Type.NONE, Types.META_TYPE, Types.META_TYPE), parametrize)
		tests.define('unparam',
			Functions.typedTmpl(Types.META_TYPE, Types.META_TYPE), unparam)
	}

	static void run(Parser parser, String text) {
		def p = parser.parse(text)
		def tc = Kismet.PRELUDE.typedContext.child()
		tc.addVariable('echo', echo, Functions.func(Type.NONE, Type.ANY))
		tc.addVariable('analyze', analyze, Functions.func(Type.NONE, Type.ANY))
		tc.addVariable('type_relation', type_relation, Functions.typedTmpl(Strings.STRING_TYPE, Types.META_TYPE, Types.META_TYPE))
		tc.addVariable('explain_typed', explain_typed, Functions.typedTmpl(Type.NONE, Type.ANY))
		tc.addVariable('parameter_type', parameter_type,
			new UnionType(Functions.typedTmpl(Types.META_TYPE), Functions.typedTmpl(Types.META_TYPE, Types.META_TYPE)))
		tc.addVariable('parametrize', parametrize,
			Functions.typedTmpl(Type.NONE, Types.META_TYPE, Types.META_TYPE))
		tc.addVariable('unparam', unparam,
			Functions.typedTmpl(Types.META_TYPE, Types.META_TYPE))
		def t = p.type(tc)
		def i = t.instruction
		def mem = new RuntimeMemory([Kismet.PRELUDE.typedContext] as Memory[], tc.size())
		mem.memory[0] = echo
		mem.memory[1] = analyze
		mem.memory[2] = type_relation
		mem.memory[3] = explain_typed
		mem.memory[4] = parameter_type
		mem.memory[5] = parametrize
		mem.memory[6] = unparam
		i.evaluate(mem)
	}

	static KismetModuleSpace<File> modules = new KismetModuleSpace<>(defaultDependencies: [(Module) Kismet.PRELUDE, tests])

	static void run(Parser parser, File file) {
		def mod = KismetModule.from(modules, file)
		mod.parse(parser)
		mod.type()
		mod.run()
	}

	static main(args) {
		def parser = new Parser()
		parser.memory = new Context(Kismet.DEFAULT_CONTEXT, [echo: (IKismetObject) echo, analyze: (IKismetObject) analyze])
		def passed = [], failed = []
		for (f in new File('examples').list()) {
			println "file: $f"
			final file = new File("examples/" + f)
			try {
				run(parser, file)
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
