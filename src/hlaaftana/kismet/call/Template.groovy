package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeRelation
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory

@CompileStatic
abstract class Template implements KismetCallable, IKismetObject {
	// doesn't transform arguments if true
	boolean isImmediate() { true }
	// doesn't transform result if true
	boolean isOptimized() { false }

	abstract Expression transform(Parser parser, Expression... args)

	IKismetObject call(Memory c, Expression... args) {
		transform(null, args).evaluate(c)
	}

	Template inner() { this }
}
