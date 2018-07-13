package hlaaftana.kismet.exceptions

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.call.Expression

@InheritConstructors
@CompileStatic
class KismetEvaluationException extends KismetException {
	KismetEvaluationException(Expression expr, Throwable ex) {
		super("Line $expr.ln col $expr.cl in expression $expr", ex)
	}
	KismetEvaluationException(Expression expr, String msg) {
		super("Line $expr.ln col $expr.cl in expression $expr: $msg")
	}
}