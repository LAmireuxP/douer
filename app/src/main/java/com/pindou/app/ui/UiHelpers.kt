package com.pindou.app.ui

import kotlin.math.min

/** 把扁平数组按每段 [size] 切成长度相等（末段可短一截）的列表；多页图纸平铺内存时用 */
internal fun chunkInts(arr: IntArray, size: Int): List<IntArray> =
    List((arr.size + size - 1) / size) { i ->
        arr.copyOfRange(i * size, min((i + 1) * size, arr.size))
    }

/** 布尔版 [chunkInts]：施工进度 / 锁定标记与格数据平铺同一份布局 */
internal fun chunkBooleans(arr: BooleanArray, size: Int): List<BooleanArray> =
    List((arr.size + size - 1) / size) { i ->
        arr.copyOfRange(i * size, min((i + 1) * size, arr.size))
    }
