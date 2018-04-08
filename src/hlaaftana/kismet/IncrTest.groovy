package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Parser

import javax.imageio.ImageIO
import java.awt.*
import java.awt.image.BufferedImage
import java.util.List

import static java.lang.System.currentTimeMillis as now

@CompileStatic
class IncrTest {
	static List<Long> optimizedIncr = []
	static List<Long> optimizedPipePlus1 = []
	static List<Long> optimizedPipeNext = []
	static List<Long> optimizedPlus1 = []
	static List<Long> optimizedNext = []
	static List<Long> incr = []
	static List<Long> pipePlus1 = []
	static List<Long> pipeNext = []
	static List<Long> plus1 = []
	static List<Long> next = []

	static void main(String[] args) {
		int times = 60, width = 30, height = 1500
		for (int i = 0; i < times; i++) bench()
		BufferedImage image = new BufferedImage(width * (times - 1), height, BufferedImage.TYPE_INT_RGB)
		Graphics2D graphics = image.createGraphics()
		graphics.setColor(Color.WHITE)
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight())
		graphics.setStroke(new BasicStroke(2))
		graphics.color = new Color(0xff)
		for (int i = 1; i < times; ++i) {
			int f = (int) optimizedIncr[i - 1], s = (int) optimizedIncr[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xff00)
		for (int i = 1; i < times; ++i) {
			int f = (int) optimizedPipePlus1[i - 1], s = (int) optimizedPipePlus1[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xff0000)
		for (int i = 1; i < times; ++i) {
			int f = (int) optimizedPipeNext[i - 1], s = (int) optimizedPipeNext[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xffff)
		for (int i = 1; i < times; ++i) {
			int f = (int) optimizedPlus1[i - 1], s = (int) optimizedPlus1[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xddff00)
		for (int i = 1; i < times; ++i) {
			int f = (int) optimizedNext[i - 1], s = (int) optimizedNext[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xabcd00)
		for (int i = 1; i < times; ++i) {
			int f = (int) incr[i - 1], s = (int) incr[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xddffdd)
		for (int i = 1; i < times; ++i) {
			int f = (int) pipePlus1[i - 1], s = (int) pipePlus1[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xddaaff)
		for (int i = 1; i < times; ++i) {
			int f = (int) pipeNext[i - 1], s = (int) pipeNext[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xa09ef1)
		for (int i = 1; i < times; ++i) {
			int f = (int) plus1[i - 1], s = (int) plus1[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xaa7b1c)
		for (int i = 1; i < times; ++i) {
			int f = (int) next[i - 1], s = (int) next[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.dispose()
		ImageIO.write(image, "png", new File("incrs benchmark.png"))
	}

	/*
	current run order: optimized pipe plus 1,
	  optimized pipe next, optimized plus 1, optimized next,
	  pipe plus 1, pipe next, plus 1, next, incr, optimized incr

	without counting parsing time:
	1/2. optimized plus 1/optimized next (~22 ms)
	3. optimized incr
	4. plus 1
	5. next
	6. optimized pipe plus 1  /
	7. optimized pipe next   /
	8. incr                 / wonky order but close enough
	9. pipe next           /
	10. pipe plus 1       /

	optimized incr is slightly slower because of = being called normally
	instead of AssignExpression

	with parsing time:
	1. incr (~140 ms)
	2. next
	3. pipe next
	4. plus 1
	(10 ms gap)
	5. pipe plus 1
	(40 ms gap)
	6. optimized next
	7. optimized plus 1
	8. optimized incr
	9. optimized pipe next
	(10 ms gap)
	10. optimized pipe plus 1
	 */
	static void bench() {
		def p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
don't %'optimize !optimize_pure'
:= x 0
|>= x [+ 1]
x""")
		def a = now()
		for (int i = 0; i < 5000; ++i) p.evaluate(Kismet.DEFAULT_CONTEXT.child())
		optimizedPipePlus1 << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
don't %'optimize !optimize_pure'
:= x 0
|>= x next
x""")
		a = now()
		for (int i = 0; i < 5000; ++i) p.evaluate(Kismet.DEFAULT_CONTEXT.child())
		optimizedPipeNext << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
don't %'optimize'
:= x 0
= x [+ x 1]
x""")
		a = now()
		for (int i = 0; i < 5000; ++i) p.evaluate(Kismet.DEFAULT_CONTEXT.child())
		optimizedPlus1 << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
don't %'optimize'
:= x 0
= x [next x]
x""")
		a = now()
		for (int i = 0; i < 5000; ++i) p.evaluate(Kismet.DEFAULT_CONTEXT.child())
		optimizedNext << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
:= x 0
|>= x [+ 1]
x""")
		a = now()
		for (int i = 0; i < 5000; ++i) p.evaluate(Kismet.DEFAULT_CONTEXT.child())
		pipePlus1 << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
:= x 0
|>= x next
x""")
		a = now()
		for (int i = 0; i < 5000; ++i) p.evaluate(Kismet.DEFAULT_CONTEXT.child())
		pipeNext << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
:= x 0
= x [+ x 1]
x""")
		a = now()
		for (int i = 0; i < 5000; ++i) p.evaluate(Kismet.DEFAULT_CONTEXT.child())
		plus1 << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
:= x 0
= x [next x]
x""")
		a = now()
		for (int i = 0; i < 5000; ++i) p.evaluate(Kismet.DEFAULT_CONTEXT.child())
		next << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
:= x 0
incr x
x""")
		a = now()
		for (int i = 0; i < 5000; ++i) p.evaluate(Kismet.DEFAULT_CONTEXT.child())
		incr << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
don't %'optimize'
:= x 0
incr x
x""")
		a = now()
		for (def i = 0; i < 5000; ++i) p.evaluate(Kismet.DEFAULT_CONTEXT.child())
		optimizedIncr << now() - a
	}
}
