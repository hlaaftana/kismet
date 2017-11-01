package hlaaftana.kismet

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import hlaaftana.kismet.parser.StringExpression

@CompileStatic
class Path {
	String raw
	List<PathExpression> expressions = []

	Path(List<PathExpression> exprs) { expressions = exprs }

	Path(String aaa){
		raw = aaa
		StringBuilder latest = new StringBuilder()
		boolean escaped = false
		boolean type = true
		for (c in aaa.chars) {
			int len = latest.length()
			if (!escaped && (c == '.' || c == '[')) {
				String x = latest.toString()
				latest = new StringBuilder()
				expressions.add(type ? new PropertyPathExpression(x) : new SubscriptPathExpression(x))
				type = c == '.'
			} else {
				if (escaped) latest.append('\\')
				if (0 != len || c != ']' || c != '\\') latest.append(c)
			}
			escaped = c == '\\'
		}
		String x = latest.toString()
		expressions.add(type ? new PropertyPathExpression(x) : new SubscriptPathExpression(x))
	}

	static Path parse(String aaa){ new Path(aaa) }

	String toString() { raw }

	def apply(thing){
		for (it in expressions) thing = it.act(thing)
		thing
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

		abstract act(thing)
	}

	@InheritConstructors
	static class PropertyPathExpression extends PathExpression {
		@CompileDynamic
		def act(thing) {
			raw ? (raw.startsWith('*') ?
				thing*."${raw.substring(1)}" :
				thing."$raw") :
				thing
		}
	}

	static class SubscriptPathExpression extends PathExpression {
		def value

		SubscriptPathExpression(String r) {
			super(r)
			value = r.isInteger() ? r as int : new StringExpression(r).value
		}

		def act(thing) {
			thing.invokeMethod('getAt', value)
		}
	}
}

