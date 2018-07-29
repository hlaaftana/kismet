package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Prelude
import hlaaftana.kismet.scope.Context

@CompileStatic
class Test {
	static main(args) {
		def parser = new Parser()
		parser.context = new Context(Kismet.DEFAULT_CONTEXT, [echo: Kismet.model(Prelude.funcc(System.out.&println))])
		/*final text = new File('test.ksmt').text
		def p = parser.parse(text)
		println p.repr()
		println p.evaluate(parser.context)*/
		for (f in ['binarysearch', 'compareignorecase', 'factorial', 'fibonacci', 'fizzbuzz', 'memoize']) {
			println "file: $f"
			final file = new File("Kismet/examples/${f}.ksmt")
			def p = parser.parse(file.text)
			println p
			p.evaluate(parser.context.child())
		}
	}
}
