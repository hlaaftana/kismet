package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Expression

abstract class Macro {
	abstract KismetObject doCall(Block c, KismetObject<Expression>... expressions)
}

@CompileStatic
class KismetMacro extends Macro {
	KismetObject<Block> b

	KismetMacro(KismetObject<Block> b) {
		this.b = b
	}

	KismetObject doCall(Block s, KismetObject<Expression>... args){
		Block c = b.inner().anonymousClone()
		for (int it = 0; it < args.length; ++it) {
			c.context.directSet('$'.concat(String.valueOf(it)), args[it])
		}
		c.context.directSet('$block', Kismet.model(s))
		c.context.directSet('$all', Kismet.model(args.toList()))
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

	KismetObject doCall(Block c, KismetObject<Expression>... expressions){
		Kismet.model(expressions.length != 0 ? x.call(c, convert ? expressions*.inner() as Expression[] : expressions) : x.call(c))
	}
}