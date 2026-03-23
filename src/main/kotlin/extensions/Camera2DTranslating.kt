package extensions

import org.openrndr.*
import org.openrndr.draw.Drawer
import org.openrndr.draw.RenderTarget
import org.openrndr.events.Event
import org.openrndr.extra.camera.ChangeEvents
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector4
import org.openrndr.math.mix
import org.openrndr.math.transforms.buildTransform
import org.openrndr.math.transforms.ortho
import org.openrndr.shape.Rectangle
import kotlin.math.max

/**
 * The [Camera2DTranslating] extension enables panning, rotating and zooming the view
 * with the mouse:
 * - left click and drag to **pan**
 * - use the mouse wheel to **zoom** in and out
 *
 * Usage: `extend(Camera2D())`
 */
class Camera2DTranslating : Extension, ChangeEvents {
    override var enabled = true

    var view = Matrix44.IDENTITY

    override val changed = Event<Unit>()
    val beforeDraw = Event<Unit>("before-draw")


    private var dirty = true
        set(value) {
            if (value && !field) {
                changed.trigger(Unit)
            }
            field = value
        }
    override val hasChanged: Boolean
        get() = dirty

    val scale: Vector2
        get() {
            return (view * Vector4(1.0, 1.0, 0.0, 0.0)).xy
        }

    fun fitTo(target: Rectangle, translationBlend:Double, scaleBlend:Double = translationBlend) {
        val b = bounds
        val bs = max(b.width, b.height)
        val ts = max(target.width, target.height)

        view = buildTransform {
            translate(b.center)
            scale( mix(1.0, (bs/ts), scaleBlend))
            translate(-b.center)
        } * view

        val b2 = bounds
        val translate = target.center - b2.center

        view = buildTransform {
            translate(-translate * translationBlend)
        } * view
    }

    fun translate(input: Vector2): Vector2 {
        return (view.inversed * input.xy01).xy
    }

    fun project(input: Vector2): Vector2 {
        val width = RenderTarget.active.width
        val height = RenderTarget.active.height

        return org.openrndr.math.transforms.project(
            input.xy0,
            ortho(0.0, width.toDouble(), 0.0, height.toDouble(), -1.0, 1.0),
            view,
            width,
            height
        ).xy
    }

    val bounds: Rectangle
        get() {
            val vi = view.inversed
            val tl = vi * Vector4(0.0, 0.0, 0.0, 1.0)
            val br = vi * Vector4(RenderTarget.active.width.toDouble(), RenderTarget.active.height.toDouble(), 0.0, 1.0)
            return Rectangle(tl.xy, br.x - tl.x, br.y - tl.y)
        }

    /**
     * Used to prevent event feedback loops
     */
    private val echoBuster = mutableSetOf<MouseEvent>()


    fun setupMouseEvents(mouse: MouseEvents) {

        mouse.buttonDown.listen {
            if (it !in echoBuster && !it.propagationCancelled) {
                it.cancelPropagation()
                mouse.buttonDown.trigger(it.copy(position = translate(it.position)).apply { echoBuster.add(this) })
            } else {
                echoBuster.remove(it)
            }
        }
        mouse.buttonUp.listen {
            if (it !in echoBuster && !it.propagationCancelled) {
                it.cancelPropagation()
                mouse.buttonUp.trigger(it.copy(position = translate(it.position)).apply { echoBuster.add(this) })
            } else {
                echoBuster.remove(it)
            }
        }


        mouse.moved.listen {
            if (it !in echoBuster && !it.propagationCancelled) {
                it.cancelPropagation()
                mouse.moved.trigger(it.copy(position = translate(it.position)).apply { echoBuster.add(this) })
            } else {
                echoBuster.remove(it)
            }
        }

        mouse.dragged.listen {
            if (!it.propagationCancelled && it !in echoBuster) {
                if (KeyModifier.ALT in it.modifiers) {
                    it.cancelPropagation()
                    when (it.button) {
                        MouseButton.LEFT -> view = buildTransform {
                            translate(it.dragDisplacement)
                        } * view

                        else -> Unit
                    }
                    dirty = true

                } else {
                    it.cancelPropagation()
                    mouse.dragged.trigger(it.copy(position = translate(it.position)).apply { echoBuster.add(this) })

                }
            } else {
                echoBuster.remove(it)
            }
        }
        mouse.scrolled.listen {
            if (!it.propagationCancelled) {
                val scaleFactor = 1.0 - it.rotation.y * 0.03

                val scaleIncrement =
                    when {
                        KeyModifier.SHIFT in it.modifiers && it.modifiers.size == 1 -> Vector2(scaleFactor, 1.0)
                        KeyModifier.CTRL in it.modifiers  && it.modifiers.size == 1 -> Vector2(1.0, scaleFactor)
                        else -> Vector2(scaleFactor, scaleFactor)
                    }

                view = buildTransform {
                    translate(it.position)
                    scale(scaleIncrement.x, scaleIncrement.y, 1.0)
                    translate(-it.position)
                } * view

//                val check = scale
//                if (check.x < 0.1) {
//                    view = buildTransform {
//                        translate(it.position)
//                        scale(0.1 / check.x, 1.0)
//                        translate(-it.position)
//                    } * view
//
//                }
//
//                if (check.y < 0.1) {
//                    view = buildTransform {
//                        translate(it.position)
//                        scale(1.0, 0.1 / check.y)
//                        translate(-it.position)
//                    } * view
//                }
                dirty = true
            }
        }
    }

    override fun setup(program: Program) {
        setupMouseEvents(program.mouse)
        program.keyboard.keyDown.listen {
            if (it.key == KEY_ESCAPE) {
                view = Matrix44.IDENTITY
            }
        }
    }

    override fun beforeDraw(drawer: Drawer, program: Program) {
        beforeDraw.trigger(Unit)
        drawer.pushTransforms()
        drawer.ortho(RenderTarget.active)
        drawer.view = view
    }

    override fun afterDraw(drawer: Drawer, program: Program) {
        dirty = false
        drawer.popTransforms()
    }
}


