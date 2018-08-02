package hlaaftana.kismet.call;

import hlaaftana.kismet.parser.Parser;
import hlaaftana.kismet.scope.Context;
import hlaaftana.kismet.vm.IKismetObject;

public interface Template extends KismetCallable {
	// doesn't transform arguments if true
	default boolean isHungry() { return false; }
	// doesn't transform result if true
	default boolean isOptimized() { return false; }

	Expression transform(Parser parser, Expression... args);

	default IKismetObject call(Context c, Expression... args) {
		return transform(null, args).evaluate(c);
	}
}
