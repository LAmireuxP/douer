package com.pindou.app.core.pattern

/**
 * 色号标注位置：同一片 4 连通、同色号的区域只在最接近重心的格子标一次。
 *
 * 大色块不再每个格子都印一遍色号；不同区域仍各自保留一个标注，
 * 保证照着拼时每一块都能找到对应色号。
 */
object ColorLabelLayout {

    /**
     * 简化标号：只标同色连通区域的外圈，内部留空。
     *
     * 判据是 4 邻域里只要有一个格子越界或颜色不同，该格就在区域边界上。
     * 单格区域、2 格宽的长条等所有格子都是边界，会全部保留标号。
     */
    fun labelMaskOutline(cells: IntArray, width: Int, height: Int): BooleanArray {
        val total = width.coerceAtLeast(0) * height.coerceAtLeast(0)
        if (width <= 0 || height <= 0 || cells.size < total) {
            return BooleanArray(total) { false }
        }
        val mask = BooleanArray(total)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val color = cells[index]
                if (color < 0) continue
                val leftDiff = x == 0 || cells[index - 1] != color
                val rightDiff = x + 1 == width || cells[index + 1] != color
                val topDiff = y == 0 || cells[index - width] != color
                val bottomDiff = y + 1 == height || cells[index + width] != color
                if (leftDiff || rightDiff || topDiff || bottomDiff) mask[index] = true
            }
        }
        return mask
    }

    fun labelMask(cells: IntArray, width: Int, height: Int): BooleanArray {
        val total = width.coerceAtLeast(0) * height.coerceAtLeast(0)
        if (width <= 0 || height <= 0 || cells.size < total) {
            return BooleanArray(total) { false }
        }

        val mask = BooleanArray(total)
        val visited = BooleanArray(total)
        val queue = IntArray(total)

        for (start in 0 until total) {
            if (visited[start]) continue
            val color = cells[start]
            if (color < 0) {
                visited[start] = true
                continue
            }

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var sumX = 0L
            var sumY = 0L

            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                sumX += x
                sumY += y

                if (x > 0) {
                    val next = index - 1
                    if (!visited[next] && cells[next] == color) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
                if (x + 1 < width) {
                    val next = index + 1
                    if (!visited[next] && cells[next] == color) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
                if (y > 0) {
                    val next = index - width
                    if (!visited[next] && cells[next] == color) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
                if (y + 1 < height) {
                    val next = index + width
                    if (!visited[next] && cells[next] == color) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
            }

            val cx = sumX.toDouble() / tail
            val cy = sumY.toDouble() / tail
            var bestIndex = queue[0]
            var bestDistance = Double.MAX_VALUE
            for (i in 0 until tail) {
                val index = queue[i]
                val dx = (index % width) - cx
                val dy = (index / width) - cy
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
            mask[bestIndex] = true
        }
        return mask
    }
}

/** 色号标注模式：每格都标，或只在同色区域外圈标。 */
enum class CodeLabelMode {
    FULL,
    OUTLINE,
}
