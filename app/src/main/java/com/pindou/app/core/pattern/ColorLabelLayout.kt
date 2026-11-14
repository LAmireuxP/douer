package com.pindou.app.core.pattern

/**
 * 色号标注位置：同一片 4 连通、同色号的区域只在最接近重心的格子标一次。
 *
 * 大色块不再每个格子都印一遍色号；不同区域仍各自保留一个标注，
 * 保证照着拼时每一块都能找到对应色号。
 */
object ColorLabelLayout {

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
