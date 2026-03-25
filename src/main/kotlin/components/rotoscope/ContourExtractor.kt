package components.rotoscope

import org.openrndr.math.Vector2

data class Contour(val points: List<Vector2>)

class ContourExtractor(
    private val minPoints: Int = RotoscopeConfig.CONTOUR_MIN_POINTS
) {
    fun extract(mask: BinaryMask): List<Contour> {
        val adjacency = linkedMapOf<GridPoint, MutableSet<GridPoint>>()

        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                if (!mask.isForeground(x, y)) {
                    continue
                }

                if (!mask.isForeground(x, y - 1)) {
                    addEdge(adjacency, GridPoint(x, y), GridPoint(x + 1, y))
                }
                if (!mask.isForeground(x + 1, y)) {
                    addEdge(adjacency, GridPoint(x + 1, y), GridPoint(x + 1, y + 1))
                }
                if (!mask.isForeground(x, y + 1)) {
                    addEdge(adjacency, GridPoint(x + 1, y + 1), GridPoint(x, y + 1))
                }
                if (!mask.isForeground(x - 1, y)) {
                    addEdge(adjacency, GridPoint(x, y + 1), GridPoint(x, y))
                }
            }
        }

        val usedEdges = mutableSetOf<Edge>()
        val contours = mutableListOf<Contour>()

        adjacency.forEach { (start, neighbours) ->
            neighbours.forEach { next ->
                val edge = Edge.canonical(start, next)
                if (edge in usedEdges) {
                    return@forEach
                }

                val path = tracePath(start, next, adjacency, usedEdges)
                val simplified = compressCollinear(path)
                if (simplified.size >= minPoints) {
                    contours += Contour(
                        simplified.map { point ->
                            Vector2(
                                point.x.toDouble() * mask.cellSize,
                                point.y.toDouble() * mask.cellSize
                            )
                        }
                    )
                }
            }
        }

        return contours
    }

    private fun tracePath(
        start: GridPoint,
        next: GridPoint,
        adjacency: Map<GridPoint, Set<GridPoint>>,
        usedEdges: MutableSet<Edge>
    ): List<GridPoint> {
        val path = mutableListOf(start, next)
        usedEdges += Edge.canonical(start, next)

        var previous = start
        var current = next

        while (true) {
            val candidates = adjacency[current]
                .orEmpty()
                .filter { Edge.canonical(current, it) !in usedEdges }

            if (candidates.isEmpty()) {
                break
            }

            val following = candidates.firstOrNull { it != previous } ?: candidates.first()
            val edge = Edge.canonical(current, following)
            if (!usedEdges.add(edge)) {
                break
            }

            path += following
            previous = current
            current = following

            if (current == start) {
                break
            }
        }

        return path
    }

    private fun compressCollinear(points: List<GridPoint>): List<GridPoint> {
        if (points.size <= 2) {
            return points
        }

        val compressed = mutableListOf(points.first())
        for (index in 1 until points.lastIndex) {
            val previous = compressed.last()
            val current = points[index]
            val next = points[index + 1]
            val dx1 = current.x - previous.x
            val dy1 = current.y - previous.y
            val dx2 = next.x - current.x
            val dy2 = next.y - current.y

            if (dx1 == dx2 && dy1 == dy2) {
                continue
            }

            compressed += current
        }
        compressed += points.last()
        return compressed
    }

    private fun addEdge(
        adjacency: MutableMap<GridPoint, MutableSet<GridPoint>>,
        start: GridPoint,
        end: GridPoint
    ) {
        adjacency.getOrPut(start) { linkedSetOf() } += end
        adjacency.getOrPut(end) { linkedSetOf() } += start
    }

    private data class GridPoint(val x: Int, val y: Int)

    private data class Edge(val a: GridPoint, val b: GridPoint) {
        companion object {
            fun canonical(a: GridPoint, b: GridPoint): Edge {
                return if (a.y < b.y || (a.y == b.y && a.x <= b.x)) {
                    Edge(a, b)
                } else {
                    Edge(b, a)
                }
            }
        }
    }
}
