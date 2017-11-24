package hlaaftana.kismet

import javax.imageio.ImageIO
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage

class Benchmark {
	static main(args) {
		def kismet = Kismet.eval(new File('test.ksmt').text).inner() as List
		def times = kismet.size()
		def max = kismet.max()
		def (width, height) = [30, 2500]
		BufferedImage image = new BufferedImage(width * times, height, BufferedImage.TYPE_INT_RGB)
		image.createGraphics().with {
			color = Color.WHITE
			fillRect(0, 0, image.width, image.height)
			stroke = new BasicStroke(5)
			color = Color.BLACK
			for (i in 1..<times) {
				int f = (height * (kismet[i - 1] / max)) as int,
					s = (height * (kismet[i] / max)) as int
				drawLine((i - 1) * width, height - f, i * width, height - s)
			}
			color = Color.BLUE
			drawString("Max: $max", 20, 20)
			drawString("Min: ${kismet.min()}", 20, 40)
			drawString("Avg: ${kismet.collect { it as BigInteger }.sum() / times}", 120, 20)
			drawString("Med: ${(kismet[times.intdiv(2) as int] + kismet[(times.intdiv(2) as int) - 1]) / 2}", 120, 40)
			dispose()
		}
		ImageIO.write(image, "png", new File("kismet_nothing.png"))
	}
}
