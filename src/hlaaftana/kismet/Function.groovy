package hlaaftana.kismet

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

abstract class Function implements KismetCallable {}

@CompileStatic
class KismetFunction extends Function {
	KismetObject<Block> b

	KismetFunction(KismetObject<Block> b) {
		this.b = b
	}

	KismetObject call(KismetObject... args){
		Block c = b.inner().anonymousClone()
		for (int it = 0; it < args.length; ++it) {
			c.context.directSet('$'.concat(String.valueOf(it)), args[it])
		}
		c.context.directSet('$all', Kismet.model(args.toList()))
		c()
	}
}

@CompileStatic
class GroovyFunction extends Function {
	boolean convert = true
	Closure x

	GroovyFunction(boolean convert = true, Closure x) {
		this.convert = convert
		this.x = x
	}

	KismetObject call(KismetObject... args){
		Kismet.model(cc(convert ? args*.inner() as Object[] : args))
	}

	@CompileDynamic
	def cc(...args) {
		x(*args)
	}
}
