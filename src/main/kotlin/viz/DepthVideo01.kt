package viz

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.BlendMode
import org.openrndr.draw.ColorType
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.parameter
import org.openrndr.draw.renderTarget
import org.openrndr.draw.shadeStyle
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.shape.Rectangle

fun main() {
    application {
        program {
            val video = loadVideo("data/extracted/eple/eple-jux.mp4")
            video.play()
            println(video.width)
            println(video.height)

            val image = renderTarget(video.width/2, video.height) {
                colorBuffer()
            }

            val depth = renderTarget(video.width/2, video.height) {
                colorBuffer(type = ColorType.FLOAT32)
            }


            val splitImage = renderTarget(video.width/2, video.height) {
                colorBuffer()
            }

            val splitDepth = renderTarget(video.width/2, video.height) {
                colorBuffer()
            }



            keyboard.keyDown.listen {
                if (it.name == "r"){
                    depth.clearColor(0, ColorRGBa.BLACK)
                }
            }

            depth.clearColor(0, ColorRGBa.BLACK)

            extend {
                video.draw {

                    drawer.isolatedWithTarget(splitDepth) {
                        drawer.ortho(splitDepth)
                        drawer.image(it, Rectangle(image.width.toDouble(), 0.0, image.width.toDouble(), image.height.toDouble()),
                            Rectangle(0.0, 0.0, image.width.toDouble(), image.height.toDouble())
                        )
                    }

                    drawer.isolatedWithTarget(splitImage) {
                        drawer.ortho(splitImage)
                        drawer.image(it, Rectangle(0.0, 0.0, image.width.toDouble(), image.height.toDouble()),
                            Rectangle(0.0, 0.0, image.width.toDouble(), image.height.toDouble())
                        )
                    }

                    drawer.isolatedWithTarget(depth) {
                        drawer.ortho(depth)
                        drawer.fill = ColorRGBa.BLACK.opacify(0.001)
                        drawer.stroke = null
                        //drawer.rectangle(drawer.bounds)

                        drawer.drawStyle.blendMode = BlendMode.MAX

                        drawer.image(splitDepth.colorBuffer(0))
                    }

                    drawer.isolatedWithTarget(image) {
                        drawer.ortho(depth)
                        drawer.shadeStyle = shadeStyle {
                            fragmentTransform = """
                                float depth = dot(texture(p_depth, va_texCoord0).rgb, vec3(1.0/3.0));
                                float splitDepth = dot(texture(p_splitDepth, va_texCoord0).rgb, vec3(1.0/3.0));
                                
                                float o = smoothstep(0.01, 0.0, depth - splitDepth);
                                x_fill.a = o;
                            """.trimIndent()
                            parameter("depth", depth.colorBuffer(0))
                            parameter("splitDepth", splitDepth.colorBuffer(0))
                        }
                        drawer.image(splitImage.colorBuffer(0))
                    }
                }
                drawer.image(image.colorBuffer(0))
            }
        }
    }
}