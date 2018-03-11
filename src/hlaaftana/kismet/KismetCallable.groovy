package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.CallExpression
import hlaaftana.kismet.parser.Expression
import hlaaftana.kismet.parser.Token

@CompileStatic
trait KismetCallable {
	boolean isPure() { false }
	int getPrecedence() { 0 }
	abstract KismetObject call(Context c, Expression... args)
}
