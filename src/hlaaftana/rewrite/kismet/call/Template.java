package hlaaftana.rewrite.kismet.call;

import hlaaftana.rewrite.kismet.vm.Context;
import hlaaftana.rewrite.kismet.vm.IKismetObject;

public interface Template extends KismetCallable {
	default boolean isConstant() {
		return true;
	}

	Expression transform(Expression... args);

	@Override
	default IKismetObject call(Context c, Expression... args) {
		return transform(args).evaluate(c);
	}
}
