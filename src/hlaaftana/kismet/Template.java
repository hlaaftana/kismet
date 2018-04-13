package hlaaftana.kismet;

import hlaaftana.kismet.parser.Expression;

public interface Template extends KismetCallable {
	default boolean isConstant() { return true; }
	Expression transform(Expression... args);

	@Override
	default IKismetObject call(Context c, Expression... args) {
		return transform(args).evaluate(c);
	}
}
