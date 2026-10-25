package com.example.domain.pathfinding

import com.example.domain.model.ExtinguisherItem
import com.example.domain.model.MapEdgeItem
import com.example.domain.model.MapNodeItem
import com.example.domain.model.MapObjectType
import com.example.domain.model.Point2D
import java.util.PriorityQueue
import kotlin.math.hypot

data class NavigationRoute(
    val pathPoints: List<Point2D>,
    val totalDistanceMeters: Float,
    val targetName: String,
    val targetType: String, // EXTINGUISHER, EXIT
    val crossesHazardRoom: Boolean,
    val isEuclideanOnly: Boolean = false,
    val notes: String = ""
)

object IndoorPathfinder {

    fun euclideanDistance(p1: Point2D, p2: Point2D, scaleMeters: Float = 12.0f): Float {
        val dx = (p1.x - p2.x) * scaleMeters
        val dy = (p1.y - p2.y) * scaleMeters
        return hypot(dx, dy)
    }

    /**
     * Finds shortest path to the nearest extinguisher on the current floor.
     */
    fun findPathToNearestExtinguisher(
        startPoint: Point2D,
        extinguishers: List<ExtinguisherItem>,
        nodes: List<MapNodeItem>,
        edges: List<MapEdgeItem>,
        hazardRoomIds: Set<String>,
        scaleMeters: Float = 12.0f
    ): NavigationRoute? {
        if (extinguishers.isEmpty()) return null

        // If no topological graph is available, use Euclidean fallback
        if (nodes.isEmpty() || edges.isEmpty()) {
            val nearest = extinguishers.minByOrNull { euclideanDistance(startPoint, it.position, scaleMeters) } ?: return null
            val dist = euclideanDistance(startPoint, nearest.position, scaleMeters)
            val isHazard = hazardRoomIds.contains(nearest.roomId)
            return NavigationRoute(
                pathPoints = listOf(startPoint, nearest.position),
                totalDistanceMeters = dist,
                targetName = "${nearest.name} (${nearest.type.name})",
                targetType = "EXTINGUISHER",
                crossesHazardRoom = isHazard,
                isEuclideanOnly = true,
                notes = "Khoảng cách chim bay (Chưa khai báo đồ thị cửa/hành lang trên tầng)"
            )
        }

        // Connect start to nearest map node
        val nearestStartNode = nodes.minByOrNull { euclideanDistance(startPoint, it.position, scaleMeters) } ?: return null

        var bestRoute: NavigationRoute? = null
        var minTotalDist = Float.MAX_VALUE

        for (ext in extinguishers) {
            val nearestTargetNode = nodes.minByOrNull { euclideanDistance(ext.position, it.position, scaleMeters) } ?: continue
            val pathNodes = dijkstra(nearestStartNode.id, nearestTargetNode.id, nodes, edges, hazardRoomIds)
            if (pathNodes != null) {
                val fullPoints = mutableListOf<Point2D>()
                fullPoints.add(startPoint)
                pathNodes.forEach { fullPoints.add(it.position) }
                fullPoints.add(ext.position)

                var dist = euclideanDistance(startPoint, nearestStartNode.position, scaleMeters) +
                        euclideanDistance(nearestTargetNode.position, ext.position, scaleMeters)
                for (i in 0 until pathNodes.size - 1) {
                    dist += euclideanDistance(pathNodes[i].position, pathNodes[i + 1].position, scaleMeters)
                }

                val hasHazard = hazardRoomIds.contains(ext.roomId)
                if (dist < minTotalDist) {
                    minTotalDist = dist
                    bestRoute = NavigationRoute(
                        pathPoints = fullPoints,
                        totalDistanceMeters = dist,
                        targetName = "${ext.name} (${ext.type.name})",
                        targetType = "EXTINGUISHER",
                        crossesHazardRoom = hasHazard,
                        isEuclideanOnly = false,
                        notes = if (hasHazard) "CẢNH BÁO: Bình nằm trong hoặc cạnh phòng có cảnh báo cháy!" else "Đường di chuyển qua hành lang & cửa an toàn"
                    )
                }
            }
        }

        // Fallback to Euclidean if graph had no reachable component
        if (bestRoute == null) {
            val nearest = extinguishers.minByOrNull { euclideanDistance(startPoint, it.position, scaleMeters) } ?: return null
            val dist = euclideanDistance(startPoint, nearest.position, scaleMeters)
            return NavigationRoute(
                pathPoints = listOf(startPoint, nearest.position),
                totalDistanceMeters = dist,
                targetName = nearest.name,
                targetType = "EXTINGUISHER",
                crossesHazardRoom = hazardRoomIds.contains(nearest.roomId),
                isEuclideanOnly = true,
                notes = "Không tìm thấy đường nối đồ thị — Sử dụng đường chim bay"
            )
        }

        return bestRoute
    }

    /**
     * Finds shortest evacuation path to the nearest EXIT.
     */
    fun findPathToNearestExit(
        startPoint: Point2D,
        nodes: List<MapNodeItem>,
        edges: List<MapEdgeItem>,
        hazardRoomIds: Set<String>,
        scaleMeters: Float = 12.0f
    ): NavigationRoute? {
        val exitNodes = nodes.filter { it.type == MapObjectType.EXIT }
        if (exitNodes.isEmpty()) return null

        val nearestStartNode = nodes.minByOrNull { euclideanDistance(startPoint, it.position, scaleMeters) } ?: return null

        var bestRoute: NavigationRoute? = null
        var minTotalDist = Float.MAX_VALUE

        for (exit in exitNodes) {
            val pathNodes = dijkstra(nearestStartNode.id, exit.id, nodes, edges, hazardRoomIds)
            if (pathNodes != null) {
                val fullPoints = mutableListOf<Point2D>()
                fullPoints.add(startPoint)
                pathNodes.forEach { fullPoints.add(it.position) }

                var dist = euclideanDistance(startPoint, nearestStartNode.position, scaleMeters)
                for (i in 0 until pathNodes.size - 1) {
                    dist += euclideanDistance(pathNodes[i].position, pathNodes[i + 1].position, scaleMeters)
                }

                if (dist < minTotalDist) {
                    minTotalDist = dist
                    bestRoute = NavigationRoute(
                        pathPoints = fullPoints,
                        totalDistanceMeters = dist,
                        targetName = exit.name.ifBlank { "Lối thoát hiểm" },
                        targetType = "EXIT",
                        crossesHazardRoom = false,
                        isEuclideanOnly = false,
                        notes = "Lộ trình thoát nạn khẩn cấp"
                    )
                }
            }
        }

        return bestRoute
    }

    private fun dijkstra(
        startId: String,
        targetId: String,
        nodes: List<MapNodeItem>,
        edges: List<MapEdgeItem>,
        hazardRoomIds: Set<String>
    ): List<MapNodeItem>? {
        val nodeMap = nodes.associateBy { it.id }
        val adj = mutableMapOf<String, MutableList<Pair<String, Float>>>()
        edges.filter { it.enabled }.forEach { edge ->
            adj.getOrPut(edge.fromNodeId) { mutableListOf() }.add(edge.toNodeId to edge.distanceMeters)
            adj.getOrPut(edge.toNodeId) { mutableListOf() }.add(edge.fromNodeId to edge.distanceMeters)
        }

        val distances = mutableMapOf<String, Float>().withDefault { Float.MAX_VALUE }
        val previous = mutableMapOf<String, String?>()
        val pq = PriorityQueue<Pair<String, Float>>(compareBy { it.second })

        distances[startId] = 0.0f
        pq.add(startId to 0.0f)

        while (pq.isNotEmpty()) {
            val (u, currentDist) = pq.poll() ?: break
            if (u == targetId) break
            if (currentDist > distances.getValue(u)) continue

            adj[u]?.forEach { (neighbor, weight) ->
                val alt = currentDist + weight
                if (alt < distances.getValue(neighbor)) {
                    distances[neighbor] = alt
                    previous[neighbor] = u
                    pq.add(neighbor to alt)
                }
            }
        }

        if (!distances.containsKey(targetId) || distances.getValue(targetId) == Float.MAX_VALUE) {
            return null
        }

        val path = mutableListOf<MapNodeItem>()
        var curr: String? = targetId
        while (curr != null) {
            nodeMap[curr]?.let { path.add(0, it) }
            curr = previous[curr]
        }
        return path
    }
}
