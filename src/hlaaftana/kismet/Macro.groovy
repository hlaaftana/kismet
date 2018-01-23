package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Expression

abstract class Macro implements KismetCallable {
	int precedence
}

@CompileStatic
class KismetMacro extends Macro {
	KismetObject<Block> b

	KismetMacro(KismetObject<Block> b) {
		this.b = b
	}

	KismetObject call(Context s, Expression... args){
		Block c = b.inner().child()
		for (int it = 0; it < args.length; ++it) {
			c.context.set('$'.concat(String.valueOf(it)), Kismet.model(args[it]))
		}
		c.context.set('$context', Kismet.model(s))
		c.context.set('$all', Kismet.model(args.toList()))
		c()
	}
}

@CompileStatic
class GroovyMacro extends Macro {
	boolean convert = true
	Closure x

	GroovyMacro(boolean convert = true, Closure x) {
		this.convert = convert
		this.x = x
	}

	KismetObject call(Context c, Expression... expressions){
		Kismet.model(expressions.length != 0 ? x.call(c, expressions) : x.call(c))
	}
}