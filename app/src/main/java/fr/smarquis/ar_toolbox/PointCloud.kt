package fr.smarquis.ar_toolbox

import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.RenderableDefinition
import com.google.ar.sceneform.rendering.Vertex

object PointCloud {

    private val SIZE = Vector3.one().scaled(0.005F)
    private val EXTENT = SIZE.scaled(0.5F)

    private val UP = Vector3.up()
    private val DOWN = Vector3.down()
    private val FRONT = Vector3.forward()
    private val BACK = Vector3.back()
    private val LEFT = Vector3.left()
    private val RIGHT = Vector3.right()

    private val UV_00 = Vertex.UvCoordinate(0.0f, 0.0f)
    private val UV_10 = Vertex.UvCoordinate(1.0f, 0.0f)
    private val UV_01 = Vertex.UvCoordinate(0.0f, 1.0f)
    private val UV_11 = Vertex.UvCoordinate(1.0f, 1.0f)

    fun makePointCloud(pointCloud: com.google.ar.core.PointCloud, material: Material): RenderableDefinition? {
        val buffer = pointCloud.points
        val points = buffer.limit() / 4
        if (points == 0) {
            return null
        }

        val vertices = mutableListOf<Vertex>()
        val triangleIndices = mutableListOf<Int>()

        for (i in 0 until points) {
            /* {x, y, z, confidence} */
            val x = buffer[i * 4]
            val y = buffer[i * 4 + 1]
            val z = buffer[i * 4 + 2]
            val center = Vector3(x, y, z)

            val p0 = Vector3(center.x + -EXTENT.x, center.y + -EXTENT.y, center.z + EXTENT.z)
            val p1 = Vector3(center.x + EXTENT.x, center.y + -EXTENT.y, center.z + EXTENT.z)
            val p2 = Vector3(center.x + EXTENT.x, center.y + -EXTENT.y, center.z + -EXTENT.z)
            val p3 = Vector3(center.x + -EXTENT.x, center.y + -EXTENT.y, center.z + -EXTENT.z)
            val p4 = Vector3(center.x + -EXTENT.x, center.y + EXTENT.y, center.z + EXTENT.z)
            val p5 = Vector3(center.x + EXTENT.x, center.y + EXTENT.y, center.z + EXTENT.z)
            val p6 = Vector3(center.x + EXTENT.x, center.y + EXTENT.y, center.z + -EXTENT.z)
            val p7 = Vector3(center.x + -EXTENT.x, center.y + EXTENT.y, center.z + -EXTENT.z)

            vertices.add(vertex(p0, DOWN, UV_01))
            vertices.add(vertex(p1, DOWN, UV_11))
            vertices.add(vertex(p2, DOWN, UV_10))
            vertices.add(vertex(p3, DOWN, UV_00))
            vertices.add(vertex(p7, LEFT, UV_01))
            vertices.add(vertex(p4, LEFT, UV_11))
            vertices.add(vertex(p0, LEFT, UV_10))
            vertices.add(vertex(p3, LEFT, UV_00))
            vertices.add(vertex(p4, FRONT, UV_01))
            vertices.add(vertex(p5, FRONT, UV_11))
            vertices.add(vertex(p1, FRONT, UV_10))
            vertices.add(vertex(p0, FRONT, UV_00))
            vertices.add(vertex(p6, BACK, UV_01))
            vertices.add(vertex(p7, BACK, UV_11))
            vertices.add(vertex(p3, BACK, UV_10))
            vertices.add(vertex(p2, BACK, UV_00))
            vertices.add(vertex(p5, RIGHT, UV_01))
            vertices.add(vertex(p6, RIGHT, UV_11))
            vertices.add(vertex(p2, RIGHT, UV_10))
            vertices.add(vertex(p1, RIGHT, UV_00))
            vertices.add(vertex(p7, UP, UV_01))
            vertices.add(vertex(p6, UP, UV_11))
            vertices.add(vertex(p5, UP, UV_10))
            vertices.add(vertex(p4, UP, UV_00))

            val offset = i * 24
            for (j in 0..5) {
                triangleIndices.add(offset + 3 + 4 * j)
                triangleIndices.add(offset + 1 + 4 * j)
                triangleIndices.add(offset + 0 + 4 * j)
                triangleIndices.add(offset + 3 + 4 * j)
                triangleIndices.add(offset + 2 + 4 * j)
                triangleIndices.add(offset + 1 + 4 * j)
            }
        }

        val submesh = RenderableDefinition.Submesh.builder().setMaterial(material).setTriangleIndices(triangleIndices).build()
        return RenderableDefinition.builder().setVertices(vertices).setSubmeshes(listOf(submesh)).build()
    }

    private fun vertex(position: Vector3, normal: Vector3, uv: Vertex.UvCoordinate): Vertex = Vertex.builder().setPosition(position).setNormal(normal).setUvCoordinate(uv).build()
}
