package com.pindou.app.core.build

import org.junit.Assert.assertEquals
import org.junit.Test

/** BOARD 施工模式：29×29 物理底板分块、跳板、整板完成的纯逻辑边界。 */
class ConstructionBoardTest {

    private fun state(w: Int, h: Int) = ConstructionState(
        width = w,
        height = h,
        done = BooleanArray(w * h),
    )

    @Test
    fun `小板就是一整块底板`() {
        val s = state(20, 20)
        assertEquals(1, s.boardCount)
        assertEquals(400, s.boardCells(0).size)
        assertEquals(0, s.boardOf(0))
        assertEquals(0, s.boardOf(399))
        assertEquals(-1, s.boardOf(400))
    }

    @Test
    fun `30 格会拆成两块横向底板`() {
        val s = state(30, 20)
        assertEquals(2, s.boardCount)
        assertEquals(20 * 29, s.boardCells(0).size)
        assertEquals(20, s.boardCells(1).size)
        assertEquals(1, s.boardOf(29))
    }

    @Test
    fun `下一块未完成底板会跳过已完成板`() {
        val s = state(58, 29)
        assertEquals(2, s.boardCount)
        assertEquals(58 * 29, s.boardCells(0).size + s.boardCells(1).size)
        s.setBoardDone(0, true)
        // 板 0 完成后，第一块未完成的是板 1；从板 1 起找仍返回 1
        assertEquals(1, s.nextIncompleteBoard(0))
        assertEquals(1, s.nextIncompleteBoard(1))
    }

    @Test
    fun `整板完成返回改动数且游标推进`() {
        val s = state(29, 29)
        assertEquals(841, s.setBoardDone(0, true))
        assertEquals(-1, s.nextIncompleteBoard())
        assertEquals(0, s.setBoardDone(0, true))
    }

    @Test
    fun `完成后面的板后回绕到前面未完成板`() {
        val s = state(58, 29)
        // 板 0 保持未完成，完成板 1 后从板 2 起找，应回绕到板 0
        assertEquals(841, s.setBoardDone(1, true))
        assertEquals(0, s.nextIncompleteBoardCircular(2))
        assertEquals(0, s.nextIncompleteBoardCircular(1))
        assertEquals(0, s.nextIncompleteBoardCircular(0))
    }

    @Test
    fun `BOARD 游标由界面处理且不进行列跳转分支`() {
        val s = state(29, 29)
        s.updateOrientation(ConstructionState.Orientation.BOARD)
        assertEquals(ConstructionState.Orientation.BOARD, s.orientation)
        assertEquals(false, s.jumpToNextIncomplete())
    }
}
