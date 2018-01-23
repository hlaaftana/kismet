package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Expression

@CompileStatic
interface KismetCallable {
	int getPrecedence()
	KismetObject call(Context c, Expression... args)
}
