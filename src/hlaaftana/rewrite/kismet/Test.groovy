package hlaaftana.rewrite.kismet

import groovy.transform.CompileStatic
import hlaaftana.rewrite.kismet.parser.Parser
import hlaaftana.rewrite.kismet.scope.Prelude
import hlaaftana.rewrite.kismet.vm.Context

@CompileStatic
class Test {
	static main(args) {
		/*final text = new File('test.ksmt').text
		def p = parser.parse(text)
		println p.repr()
		println p.evaluate(parser.context)*/
		def parser = new Parser()
		parser.context = new Context(Kismet.DEFAULT_CONTEXT, [echo: Kismet.model(Prelude.funcc(System.out.&println))])
		for (f in ['binarysearch', 'compareignorecase', 'factorial', 'fibonacci', 'fizzbuzz', 'memoize']) {
			println "file: $f"
			final file = new File("Kismet/examples/${f}.ksmt")
			parser.parse(file.text).evaluate(parser.context.child())
		}
	}
}
