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

import com.google.ar.sceneform.math.Vector3

/** Smooths a given list of points  */
class LineSimplifier {

    val points = ArrayList<Vector3>()
    private val smoothedPoints = ArrayList<Vector3>()

    fun append(point: Vector3) {
        if (points.isNotEmpty() && Vector3.subtract(points.last(), point).length() < MINIMUM_DISTANCE_BETWEEN_POINTS) {
            return
        }
        points.add(point)
        if (points.size - smoothedPoints.size > POINT_SMOOTHING_INTERVAL) {
            smoothPoints()
        }
    }

    private fun smoothPoints() {
        val pointsToSmooth = points.subList(points.size - POINT_SMOOTHING_INTERVAL - 1, points.size - 1)
        val newlySmoothedPoints = smoothPoints(pointsToSmooth)
        points.subList(points.size - POINT_SMOOTHING_INTERVAL - 1, points.size - 1).clear()
        points.addAll(points.size - 1, newlySmoothedPoints)
        smoothedPoints.addAll(newlySmoothedPoints)
    }

    // Line smoothing using the Ramer-Douglas-Peucker algorithm, modified for 3D smoothing.
    private fun smoothPoints(pointsToSmooth: List<Vector3>): ArrayList<Vector3> {
        val results = ArrayList<Vector3>()
        var maxDistance = 0.0f
        var index = 0
        var distance: Float
        val endIndex = pointsToSmooth.size - 1
        for (i in 0 until endIndex - 1) {
            distance = getPerpendicularDistance(points[0], points[endIndex], points[i])
            if (distance > maxDistance) {
                index = i
                maxDistance = distance
            }
        }
        if (maxDistance > MAXIMUM_SMOOTHING_DISTANCE) {
            val result1 = smoothPoints(pointsToSmooth.subList(0, index))
            val result2 = smoothPoints(pointsToSmooth.subList(index + 1, endIndex))
            results.addAll(result1)
            results.addAll(result2)
        } else {
            results.addAll(pointsToSmooth)
        }
        return results
    }

    private fun getPerpendicularDistance(start: Vector3, end: Vector3, point: Vector3): Float {
        val crossProduct = Vector3.cross(Vector3.subtract(point, start), Vector3.subtract(point, end))
        return crossProduct.length() / Vector3.subtract(end, start).length()
    }

    companion object {
        private const val MINIMUM_DISTANCE_BETWEEN_POINTS = 0.001F
        private const val MAXIMUM_SMOOTHING_DISTANCE = 0.005f
        private const val POINT_SMOOTHING_INTERVAL = 5
    }
}
