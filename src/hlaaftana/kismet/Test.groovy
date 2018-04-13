package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Parser

@CompileStatic
class Test {
	static main(args) {
		/*final text = new File('test.ksmt').text
		def p = parser.parse(text)
		println p.repr()
		println p.evaluate(parser.context)*/
		def parser = new Parser()
		parser.context = new Context(Kismet.DEFAULT_CONTEXT, [echo: Kismet.model(KismetInner.funcc(System.out.&println))])
		for (f in ['binarysearch', 'compareignorecase', 'factorial', 'fibonacci', 'fizzbuzz', 'memoize']) {
			println "file: $f"
			final file = new File("Kismet/examples/${f}.ksmt")
			parser.parse(file.text).evaluate(parser.context.child())
		}
	}
}
