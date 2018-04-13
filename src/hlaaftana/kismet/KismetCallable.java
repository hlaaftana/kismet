package hlaaftana.kismet;

import groovy.transform.CompileStatic;
import hlaaftana.kismet.parser.Expression;

@CompileStatic
public interface KismetCallable {
	default boolean isPure() { return false; }

	default int getPrecedence() { return 0; }

	IKismetObject call(Context c, Expression... args);
}
