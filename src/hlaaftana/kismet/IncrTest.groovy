package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.NumberExpression
import hlaaftana.kismet.parser.Parser

import javax.imageio.ImageIO
import java.awt.*
import java.awt.image.BufferedImage
import java.util.List

import static java.lang.System.currentTimeMillis as now

@CompileStatic
class IncrTest {
	static List<Long> optimizedPlus1 = []
	static List<Long> optimizedSubsPlus1 = []
	static List<Long> plus1 = []
	static List<Long> subsPlus1 = []

	static void main(String[] args) {
		int times = 60, width = 30, height = 1500
		for (int i = 0; i < times; i++) bench()
		BufferedImage image = new BufferedImage(width * (times - 1), height, BufferedImage.TYPE_INT_RGB)
		Graphics2D graphics = image.createGraphics()
		graphics.setColor(Color.WHITE)
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight())
		graphics.setStroke(new BasicStroke(2))
		graphics.color = new Color(0xffff)
		for (int i = 1; i < times; ++i) {
			int f = (int) optimizedPlus1[i - 1], s = (int) optimizedPlus1[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xa09ef1)
		for (int i = 1; i < times; ++i) {
			int f = (int) plus1[i - 1], s = (int) plus1[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0x999aaa)
		for (int i = 1; i < times; ++i) {
			int f = (int) optimizedSubsPlus1[i - 1], s = (int) optimizedSubsPlus1[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0x11338a)
		for (int i = 1; i < times; ++i) {
			int f = (int) subsPlus1[i - 1], s = (int) subsPlus1[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.dispose()
		ImageIO.write(image, "png", new File("incrs benchmark 2.png"))
	}

	static void bench() {
		def p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
don't %'optimize'
:= x 0
= x[1] null
x""")
		def a = now()
		for (int i = 0; i < 20000; ++i) assert p.evaluate(Kismet.DEFAULT_CONTEXT.child()).inner() == 1
		optimizedSubsPlus1 << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
:= x 0
= x[1] null
x""")
		a = now()
		for (int i = 0; i < 20000; ++i) assert p.evaluate(Kismet.DEFAULT_CONTEXT.child()).inner() == 1
		subsPlus1 << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
don't %'optimize'
:= x 0
= x [+ x 1]
x""")
		a = now()
		for (int i = 0; i < 20000; ++i) assert p.evaluate(Kismet.DEFAULT_CONTEXT.child()).inner() == 1
		optimizedPlus1 << now() - a

		p = new Parser(context: Kismet.DEFAULT_CONTEXT.child()).parse("""\
:= x 0
= x [+ x 1]
x""")
		a = now()
		for (int i = 0; i < 20000; ++i) assert p.evaluate(Kismet.DEFAULT_CONTEXT.child()).inner() == 1
		plus1 << now() - a
	}
}
