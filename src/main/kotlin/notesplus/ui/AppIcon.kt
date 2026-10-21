package notesplus.ui

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** The Notes+ icon (src/main/resources/icon.png, made by packaging/IconGenerator.java). */
object AppIcon {
    val image: BufferedImage by lazy {
        AppIcon::class.java.getResourceAsStream("/icon.png").use { ImageIO.read(it) }
    }

    val painter: BitmapPainter by lazy { BitmapPainter(image.toComposeImageBitmap()) }
}
