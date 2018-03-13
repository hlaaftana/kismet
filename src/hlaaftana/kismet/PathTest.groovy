package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.Parser

import javax.imageio.ImageIO
import java.awt.*
import java.awt.image.BufferedImage
import java.util.List

import static java.lang.System.currentTimeMillis as now

@CompileStatic
class PathTest {
	static List<Long> path = []
	static List<Long> name = []

	static void main(String[] args) {
		int times = 120, width = 30, height = 1500
		for (int i = 0; i < times / 2; i++) bench()
		BufferedImage image = new BufferedImage(width * (times - 1), height, BufferedImage.TYPE_INT_RGB)
		Graphics2D graphics = image.createGraphics()
		graphics.setColor(Color.WHITE)
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight())
		graphics.setStroke(new BasicStroke(2))
		graphics.color = new Color(0xff)
		for (int i = 1; i < times; ++i) {
			int f = (int) path[i - 1], s = (int) path[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.color = new Color(0xff0000)
		for (int i = 1; i < times; ++i) {
			int f = (int) name[i - 1], s = (int) name[i]
			graphics.drawLine((i - 1) * width, f, i * width, s)
		}
		graphics.dispose()
		ImageIO.write(image, "png", new File("paths benchmark.png"))
	}

	static void bench() {
		def c = new Context(Kismet.DEFAULT_CONTEXT, [x: Kismet.model("abcd")])
		def p = new Parser(context: Kismet.DEFAULT_CONTEXT.child(), path2: true)

		def a = now()
		for (int i = 0; i < 5000; ++i) p.parse("""\
* x[0] [size x.chars]""").evaluate(c.child())
		name << now() - a

		a = now()
		for (int i = 0; i < 5000; ++i) Kismet.parse("""\
* x[0] [size x.chars]""", c.child())()
		path << now() - a

		a = now()
		for (int i = 0; i < 5000; ++i) Kismet.parse("""\
* x[0] [size x.chars]""", c.child())()
		path << now() - a

		a = now()
		for (int i = 0; i < 5000; ++i) p.parse("""\
* x[0] [size x.chars]""").evaluate(c.child())
		name << now() - a
	}
}
