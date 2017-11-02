package hlaaftana.kismet

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.util.regex.Pattern

@CompileStatic
class Path {
	static final Pattern REGEX = ~/(?:\[\d+])|(?:(?:\.|^)[^.\[]+)/
	String raw
	List<PathExpression> expressions = []

	Path(List<PathExpression> exprs) { expressions = exprs }

	Path(String aaa){
		raw = aaa
		expressions = REGEX.matcher(raw).iterator().withIndex().collect { String it, int i ->
			it.startsWith('[') ? new SubscriptPathExpression(it[1 .. -2]) :
								 new PropertyPathExpression(it[(i == 0 ? 0 : 1) .. -1])
		}
	}

	static Path parse(String aaa){ new Path(aaa) }

	String toString() { raw }

	def apply(c){
		for (it in expressions) c = it.act(c)
		c
	}

	Tuple2<PathExpression, Path> dropLastAndLast() {
		List x = new ArrayList(expressions)
		[x.pop(), new Path(x)] as Tuple2<PathExpression, Path>
	}

	static abstract class PathExpression {
		String raw

		PathExpression(String raw) {
			this.raw = raw
		}

		abstract act(r)
	}

	@InheritConstructors
	static class PropertyPathExpression extends PathExpression {
		@CompileDynamic
		def act(r) {
			raw ? (raw.startsWith('*') ?
				r*."${raw.substring(1)}" :
				r."$raw") :
				r
		}
	}

	static class SubscriptPathExpression extends PathExpression {
		int val
		SubscriptPathExpression(String r) { super(r); val = r as int }

		def act(r) {
			r.invokeMethod('getAt', val)
		}
	}
}

