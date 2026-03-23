package viz

import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.BlendMode
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.draw.shadeStyle
import org.openrndr.extra.fx.patterns.Checkers
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.shape.Rectangle

fun main() {
    application {

        configure {
            width = 1280
            height = 720
        }
        program {
            val video = loadVideo("data/extracted/eple/eple-jux.mp4")
            video.play()


            val splitImage = renderTarget(video.width/2, video.height) {
                colorBuffer()
            }

            val splitDepth = renderTarget(video.width/2, video.height) {
                colorBuffer()
            }

            val frontImage = renderTarget(video.width/2, video.height) {
                colorBuffer()
            }

            val backImage = renderTarget(video.width/2, video.height) {
                colorBuffer()
            }


            val checkers = Checkers()


            extend {
                video.draw {

                    drawer.isolatedWithTarget(splitDepth) {
                        drawer.ortho(splitDepth)
                        drawer.image(
                            it,
                            Rectangle(
                                splitImage.width.toDouble(),
                                0.0,
                                splitImage.width.toDouble(),
                                splitImage.height.toDouble()
                            ),
                            Rectangle(0.0, 0.0, splitImage.width.toDouble(), splitImage.height.toDouble())
                        )
                    }

                    drawer.isolatedWithTarget(splitImage) {
                        drawer.ortho(splitImage)
                        drawer.image(
                            it, Rectangle(0.0, 0.0, splitImage.width.toDouble(), splitImage.height.toDouble()),
                            Rectangle(0.0, 0.0, splitImage.width.toDouble(), splitImage.height.toDouble())
                        )
                    }

                    drawer.isolatedWithTarget(splitDepth) {
                        drawer.ortho(splitDepth)
                        drawer.fill = ColorRGBa.BLACK.opacify(0.001)
                        drawer.stroke = null
                        //drawer.rectangle(drawer.bounds)

                        drawer.drawStyle.blendMode = BlendMode.MAX

                        drawer.image(splitDepth.colorBuffer(0))
                    }

                    checkers.apply(emptyArray(), arrayOf(backImage.colorBuffer(0)))
                    drawer.isolatedWithTarget(backImage) {
                        drawer.ortho(backImage)
                        drawer.shadeStyle = shadeStyle {
                            fragmentTransform = """
                                float splitDepth = dot(texture(p_splitDepth, va_texCoord0).rgb, vec3(1.0/3.0));
                                float depth = pow(p_depth, 2.2);


                                float o = smoothstep(0.01, 0.0, depth - splitDepth);
                                x_fill.a = o;
                            """.trimIndent()
                            parameter("splitDepth", splitDepth.colorBuffer(0))
                            parameter("depth", mouse.position.y / (this@program.height))
                        }
                        drawer.image(splitImage.colorBuffer(0))
                    }

                    checkers.apply(emptyArray(), arrayOf(frontImage.colorBuffer(0)))
                    drawer.isolatedWithTarget(frontImage) {
                        drawer.ortho(frontImage)
                        drawer.shadeStyle = shadeStyle {
                            fragmentTransform = """
                                float splitDepth = dot(texture(p_splitDepth, va_texCoord0).rgb, vec3(1.0/3.0));
                                float depth = pow(p_depth, 2.2);


                                float o = smoothstep(0.0, 0.01, depth - splitDepth);
                                x_fill.a = o;
                            """.trimIndent()
                            parameter("splitDepth", splitDepth.colorBuffer(0))
                            parameter("depth", mouse.position.y / (this@program.height))
                        }
                        drawer.image(splitImage.colorBuffer(0))
                    }
                }
                drawer.image(backImage.colorBuffer(0))
                                drawer.image(frontImage.colorBuffer(0), frontImage.width.toDouble(), 0.0)
            }



        }
    }
}