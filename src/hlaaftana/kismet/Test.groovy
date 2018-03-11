package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.ParserW

@CompileStatic
class Test {
	static main(args) {
		final text = new File('test.ksmt').text
		def parser = new ParserW()
		parser.optimizePrelude = true
		parser.context = new Context(Kismet.DEFAULT_CONTEXT, [echo: Kismet.model(KismetInner.funcc(System.out.&println))])
		println parser.parse(new File('test.ksmt').text).repr()
		println parser.parse(new File('test.ksmt').text).evaluate(parser.context.child())
	}
}
