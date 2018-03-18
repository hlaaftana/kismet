package hlaaftana.kismet;

import hlaaftana.kismet.parser.CallExpression;
import hlaaftana.kismet.parser.Expression;

public interface Template extends KismetCallable {
	CallExpression transform(Expression... args);

	@Override
	default KismetObject call(Context c, Expression... args) {
		return transform(args).evaluate(c);
	}
}
