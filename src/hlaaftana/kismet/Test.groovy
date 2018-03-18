package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Parser

@CompileStatic
class Test {
	static main(args) {
		//final text = new File('test.ksmt').text
		def parser = new Parser()
		parser.context = new Context(Kismet.DEFAULT_CONTEXT, [echo: Kismet.model(KismetInner.funcc(System.out.&println))])
		println parser.parse(new File('old/soda.ksmt').text).repr()
	}
}
