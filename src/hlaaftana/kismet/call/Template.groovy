package hlaaftana.kismet.call

import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.vm.Context
import hlaaftana.kismet.vm.IKismetObject

trait Template implements KismetCallable {
	// doesn't transform arguments if true
	boolean isHungry() { false }
	// doesn't transform result if true
	boolean isOptimized() { false }

	abstract Expression transform(Parser parser, Expression... args)

	IKismetObject call(Context c, Expression... args) {
		transform(null, args).evaluate(c)
	}
}
