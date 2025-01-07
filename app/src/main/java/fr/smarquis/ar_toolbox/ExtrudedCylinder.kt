/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.smarquis.ar_toolbox

import com.google.ar.sceneform.math.MathHelper
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.RenderableDefinition
import com.google.ar.sceneform.rendering.RenderableDefinition.Submesh
import com.google.ar.sceneform.rendering.Vertex
import com.google.ar.sceneform.rendering.Vertex.UvCoordinate
import com.google.ar.sceneform.utilities.AndroidPreconditions
import kotlin.math.cos
import kotlin.math.sin

/** Utility class used to dynamically construct [ModelRenderable]s for extruded cylinders.  */
object ExtrudedCylinder {
    private const val NUMBER_OF_SIDES = 8

    private enum class Direction {
        UP,
        DOWN,
    }

    /**
     * Creates a [ModelRenderable] in the shape of a cylinder with the give specifications.
     *
     * @param radius the radius of the constructed cylinder
     * @param points the list of points the extruded cylinder will be constructed around
     * @param material the material to use for rendering the cylinder
     * @return renderable representing a cylinder with the given parameters
     */
    fun makeExtrudedCylinder(radius: Float, points: List<Vector3>, material: Material): RenderableDefinition? {
        AndroidPreconditions.checkMinAndroidApiLevel()

        if (points.size < 2) {
            return null
        }

        val vertices = ArrayList<Vertex>()
        val triangleIndices = ArrayList<Int>()
        val rotations = ArrayList<Quaternion>()
        val desiredUp = Vector3.up()

        for (point in 0 until points.size - 1) {
            generateVerticesFromPoints(
                desiredUp,
                vertices,
                rotations,
                points[point + 1],
                points[point],
                radius,
            )
        }

        updateConnectingPoints(vertices, points, rotations, radius)
        generateTriangleIndices(triangleIndices, points.size)
        updateEndPointUV(vertices)

        // Add start cap
        makeDisk(vertices, triangleIndices, points, 0, Direction.UP)
        // Add end cap
        makeDisk(vertices, triangleIndices, points, points.size - 1, Direction.DOWN)

        val submesh = Submesh.builder().setTriangleIndices(triangleIndices).setMaterial(material).build()

        return RenderableDefinition.builder()
            .setVertices(vertices)
            .setSubmeshes(listOf(submesh))
            .build()
    }

    private fun generateVerticesFromPoints(desiredUp: Vector3, vertices: MutableList<Vertex>, rotations: MutableList<Quaternion>, firstPoint: Vector3, secondPoint: Vector3, radius: Float) {
        val difference = Vector3.subtract(firstPoint, secondPoint)
        var directionFromTopToBottom = difference.normalized()
        var rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, desiredUp)

        // cosTheta0 provides the angle between the rotations
        if (rotations.isNotEmpty()) {
            val cosTheta0 = dot(rotations[rotations.size - 1], rotationFromAToB).toDouble()
            // Flip end rotation to get shortest path if needed
            if (cosTheta0 < 0.0) {
                rotationFromAToB = negated(rotationFromAToB)
            }
        }
        rotations.add(rotationFromAToB)

        directionFromTopToBottom = Quaternion.rotateVector(rotationFromAToB, Vector3.forward()).normalized()
        val rightDirection = Quaternion.rotateVector(rotationFromAToB, Vector3.right()).normalized()
        val upDirection = Quaternion.rotateVector(rotationFromAToB, Vector3.up()).normalized()
        desiredUp.set(upDirection)

        val bottomVertices = ArrayList<Vertex>()

        val halfHeight = difference.length() / 2
        val center = Vector3.add(firstPoint, secondPoint).scaled(.5f)

        val thetaIncrement = (2 * Math.PI).toFloat() / NUMBER_OF_SIDES
        var theta = 0f
        var cosTheta = cos(theta.toDouble()).toFloat()
        var sinTheta = sin(theta.toDouble()).toFloat()
        val uStep = 1.0.toFloat() / NUMBER_OF_SIDES

        // Generate edge vertices along the sides of the cylinder.
        for (edgeIndex in 0..NUMBER_OF_SIDES) {
            // Create top edge vertex
            var topPosition = Vector3.add(
                directionFromTopToBottom.scaled(-halfHeight),
                Vector3.add(
                    rightDirection.scaled(radius * cosTheta),
                    upDirection.scaled(radius * sinTheta),
                ),
            )
            var normal = Vector3.subtract(topPosition, directionFromTopToBottom.scaled(-halfHeight)).normalized()
            topPosition = Vector3.add(topPosition, center)
            var uvCoordinate = UvCoordinate(uStep * edgeIndex, 0f)

            var vertex = Vertex.builder()
                .setPosition(topPosition)
                .setNormal(normal)
                .setUvCoordinate(uvCoordinate)
                .build()
            vertices.add(vertex)

            // Create bottom edge vertex
            var bottomPosition = Vector3.add(
                directionFromTopToBottom.scaled(halfHeight),
                Vector3.add(
                    rightDirection.scaled(radius * cosTheta),
                    upDirection.scaled(radius * sinTheta),
                ),
            )
            normal = Vector3.subtract(bottomPosition, directionFromTopToBottom.scaled(halfHeight))
                .normalized()
            bottomPosition = Vector3.add(bottomPosition, center)
            val vHeight = halfHeight * 2
            uvCoordinate = UvCoordinate(uStep * edgeIndex, vHeight)

            vertex = Vertex.builder()
                .setPosition(bottomPosition)
                .setNormal(normal)
                .setUvCoordinate(uvCoordinate)
                .build()
            bottomVertices.add(vertex)

            theta += thetaIncrement
            cosTheta = cos(theta.toDouble()).toFloat()
            sinTheta = sin(theta.toDouble()).toFloat()
        }
        vertices.addAll(bottomVertices)
    }

    private fun updateConnectingPoints(vertices: MutableList<Vertex>, points: List<Vector3>, rotations: List<Quaternion>, radius: Float) {
        // Loop over each segment of cylinder, connecting the ends of this segment to start of the next.
        var currentSegmentVertexIndex = NUMBER_OF_SIDES + 1
        var nextSegmentVertexIndex = currentSegmentVertexIndex + NUMBER_OF_SIDES + 1

        for (segmentIndex in 0 until points.size - 2) {
            val influencePoint = points[segmentIndex + 1]

            val averagedRotation = lerp(rotations[segmentIndex], rotations[segmentIndex + 1], .5f)
            val rightDirection = Quaternion.rotateVector(averagedRotation, Vector3.right()).normalized()
            val upDirection = Quaternion.rotateVector(averagedRotation, Vector3.up()).normalized()

            for (edgeIndex in 0..NUMBER_OF_SIDES) {
                // Connect bottom vertex of current edge to the top vertex of the edge on next segment.
                val theta = (2 * Math.PI).toFloat() * edgeIndex / NUMBER_OF_SIDES
                val cosTheta = cos(theta.toDouble()).toFloat()
                val sinTheta = sin(theta.toDouble()).toFloat()

                // Create new position
                val position = Vector3.add(
                    rightDirection.scaled(radius * cosTheta),
                    upDirection.scaled(radius * sinTheta),
                )
                val normal = position.normalized()
                position.set(Vector3.add(position, influencePoint))

                // Update position, UV, and normals of connecting vertices
                val previousSegmentVertexIndex = currentSegmentVertexIndex - NUMBER_OF_SIDES - 1
                val updatedVertex = Vertex.builder()
                    .setPosition(position)
                    .setNormal(normal)
                    .setUvCoordinate(
                        UvCoordinate(
                            vertices[currentSegmentVertexIndex].uvCoordinate!!.x,
                            Vector3.subtract(
                                position,
                                vertices[previousSegmentVertexIndex].position,
                            )
                                .length() + vertices[previousSegmentVertexIndex].uvCoordinate!!.y,
                        ),
                    )
                    .build()

                vertices[currentSegmentVertexIndex] = updatedVertex
                vertices.removeAt(nextSegmentVertexIndex)
                currentSegmentVertexIndex++
            }
            currentSegmentVertexIndex = nextSegmentVertexIndex
            nextSegmentVertexIndex += NUMBER_OF_SIDES + 1
        }
    }

    private fun updateEndPointUV(vertices: List<Vertex>) {
        // Update UV coordinates of ending vertices
        for (edgeIndex in 0..NUMBER_OF_SIDES) {
            val vertexIndex = vertices.size - edgeIndex - 1
            val currentVertex = vertices[vertexIndex]
            currentVertex.uvCoordinate = UvCoordinate(
                currentVertex.uvCoordinate!!.x,
                Vector3.subtract(
                    vertices[vertexIndex].position,
                    vertices[vertexIndex - NUMBER_OF_SIDES - 1].position,
                )
                    .length() + vertices[vertexIndex - NUMBER_OF_SIDES - 1].uvCoordinate!!.y,
            )
        }
    }

    private fun generateTriangleIndices(triangleIndices: MutableList<Int>, numberOfPoints: Int) {
        // Create triangles along the sides of cylinder part
        for (segment in 0 until numberOfPoints - 1) {
            val segmentVertexIndex = segment * (NUMBER_OF_SIDES + 1)
            for (side in 0 until NUMBER_OF_SIDES) {
                val topLeft = side + segmentVertexIndex
                val topRight = side + segmentVertexIndex + 1
                val bottomLeft = side + NUMBER_OF_SIDES + segmentVertexIndex + 1
                val bottomRight = side + NUMBER_OF_SIDES + segmentVertexIndex + 2

                // First triangle of side.
                triangleIndices.add(topLeft)
                triangleIndices.add(bottomRight)
                triangleIndices.add(topRight)

                // Second triangle of side.
                triangleIndices.add(topLeft)
                triangleIndices.add(bottomLeft)
                triangleIndices.add(bottomRight)
            }
        }
    }

    private fun makeDisk(vertices: MutableList<Vertex>, triangleIndices: MutableList<Int>, points: List<Vector3>, centerPointIndex: Int, direction: Direction) {
        val centerPoint = points[centerPointIndex]
        val nextPoint = points[centerPointIndex + (if (direction == Direction.UP) 1 else -1)]
        val normal = Vector3.subtract(centerPoint, nextPoint).normalized()
        val center = Vertex.builder()
            .setPosition(centerPoint)
            .setNormal(normal)
            .setUvCoordinate(UvCoordinate(.5f, .5f))
            .build()
        val centerIndex = vertices.size
        vertices.add(center)

        val vertexPosition = centerPointIndex * (NUMBER_OF_SIDES + 1)
        for (edge in 0..NUMBER_OF_SIDES) {
            val edgeVertex = vertices[vertexPosition + edge]
            val theta = (2.0 * Math.PI * edge.toDouble() / NUMBER_OF_SIDES).toFloat()
            val uvCoordinate = UvCoordinate((cos(theta.toDouble()) + 1f).toFloat() / 2, (sin(theta.toDouble()) + 1f).toFloat() / 2)
            val topVertex = Vertex.builder()
                .setPosition(edgeVertex.position)
                .setNormal(normal)
                .setUvCoordinate(uvCoordinate)
                .build()
            vertices.add(topVertex)

            if (edge != NUMBER_OF_SIDES) {
                // Add disk triangle, using direction to check which side the triangles should face
                if (direction == Direction.UP) {
                    triangleIndices.add(centerIndex)
                    triangleIndices.add(centerIndex + edge + 1)
                    triangleIndices.add(centerIndex + edge + 2)
                } else {
                    triangleIndices.add(centerIndex)
                    triangleIndices.add(centerIndex + edge + 2)
                    triangleIndices.add(centerIndex + edge + 1)
                }
            }
        }
    }

    /** The dot product of two Quaternions.  */
    private fun dot(lhs: Quaternion, rhs: Quaternion): Float = lhs.x * rhs.x + lhs.y * rhs.y + lhs.z * rhs.z + lhs.w * rhs.w

    private fun negated(quat: Quaternion): Quaternion = Quaternion(-quat.x, -quat.y, -quat.z, -quat.w)

    private fun lerp(a: Quaternion, b: Quaternion, ratio: Float): Quaternion = Quaternion(
        MathHelper.lerp(a.x, b.x, ratio),
        MathHelper.lerp(a.y, b.y, ratio),
        MathHelper.lerp(a.z, b.z, ratio),
        MathHelper.lerp(a.w, b.w, ratio),
    )
}
