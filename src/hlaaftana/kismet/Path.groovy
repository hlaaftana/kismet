package hlaaftana.kismet

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.util.regex.Pattern

@CompileStatic
class Path {
	static final Pattern REGEX = ~/(?:\[\d+])|(?:(?:\.|^)[^.\[]+)/
	String raw
	List<PathStep> expressions = []

	Path(List<PathStep> exprs) { expressions = exprs }

	Path(PathStep... exprs) { expressions = exprs.toList() }

	Path(String aaa){
		raw = aaa
		expressions = REGEX.matcher(raw).iterator().withIndex().collect { String it, int i ->
			it.startsWith('[') ? new SubscriptPathStep(it[1 .. -2]) :
								 new PropertyPathStep(it[(i == 0 ? 0 : 1) .. -1])
		}
	}

	static Path parse(String aaa){ new Path(aaa) }

	String toString() { raw }

	def apply(c){
		for (it in expressions) c = it.act(c)
		c
	}

	Tuple2<PathStep, Path> dropLastAndLast() {
		List x = new ArrayList(expressions)
		new Tuple2<>(x.pop(), new Path(x))
	}

	static abstract class PathStep {
		String raw

		PathStep(String raw) {
			this.raw = raw
		}

		abstract act(r)
	}

	@InheritConstructors
	@CompileStatic
	static class PropertyPathStep extends PathStep {
		def act(r) {
			null != raw ? r[raw] : r
		}
	}

	@CompileStatic
	static class SubscriptPathStep extends PathStep {
		int val
		SubscriptPathStep(String r) { super(r); val = Integer.parseInt(r) }
		SubscriptPathStep(int r) { super(null); val = r }

		def act(r) {
			r.invokeMethod('getAt', val)
		}
	}
}

