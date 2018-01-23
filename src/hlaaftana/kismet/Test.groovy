package hlaaftana.kismet

class Test {
	static main(args) {
		println Kismet.eval(new File('test.ksmt').text)
	}
}
