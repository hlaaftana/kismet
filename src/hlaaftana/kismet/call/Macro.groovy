package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.vm.Context
import hlaaftana.kismet.vm.IKismetObject

abstract class Macro implements KismetCallable {
	boolean pure
	int precedence
}

@CompileStatic
class KismetMacro extends Macro {
	Block b

	KismetMacro(Block b) {
		this.b = b
	}

	IKismetObject call(Context s, Expression... args){
		Block c = b.child()
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
	Closure x

	GroovyMacro(Closure x) {
		this.x = x
	}

	IKismetObject call(Context c, Expression... expressions){
		Kismet.model(expressions.length != 0 ? x.call(c, expressions) : x.call(c))
	}
}