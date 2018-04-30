package hlaaftana.rewrite.kismet.call;

import groovy.transform.CompileStatic;
import hlaaftana.rewrite.kismet.vm.Context;
import hlaaftana.rewrite.kismet.vm.IKismetObject;

@CompileStatic
public interface KismetCallable {
	default boolean isPure() {
		return false;
	}

	default int getPrecedence() {
		return 0;
	}

	IKismetObject call(Context c, Expression... args);
}
