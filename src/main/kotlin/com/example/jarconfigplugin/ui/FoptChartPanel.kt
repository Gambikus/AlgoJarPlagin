package com.example.jarconfigplugin.ui

import com.example.jarconfigplugin.model.TaskResult
import com.example.jarconfigplugin.model.TaskStatus
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Scatter plot: X = selectable parameter, Y = fopt, color = algorithm, point size = 3rd parameter.
 * Includes axis selector, algorithm filter, and tooltip on hover.
 */
class FoptChartPanel : JPanel(BorderLayout()) {

    private var allTasks: List<TaskResult> = emptyList()

    private val algorithmColors = LinkedHashMap<String, Color>()
    private val palette = listOf(
        Color(0x4472C4), Color(0xED7D31), Color(0x70AD47),
        Color(0xFF4040), Color(0x7030A0), Color(0x00B0F0)
    )

    enum class Param(val label: String) {
        AGENTS("Agents"), ITERATIONS("Iterations"), DIMENSION("Dimension"), RUNTIME("Runtime (ms)")
    }

    private val xAxisCombo: JComboBox<Param> = JComboBox(Param.values()).apply {
        selectedItem = Param.AGENTS
    }
    private val sizeCombo: JComboBox<String> = JComboBox(arrayOf("(none)", "Iterations", "Agents", "Dimension")).apply {
        selectedItem = "Iterations"
    }

    private val algoCheckboxes = mutableMapOf<String, JCheckBox>()
    private val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))

    private val chartView = object : JPanel() {

        // Tooltip state
        private var hoverTask: TaskResult? = null
        private var hoverPt = Point(0, 0)

        init {
            background = Color(0x1E1E1E)
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val prev = hoverTask
                    hoverTask = findNearestTask(e.x, e.y)
                    hoverPt = e.point
                    if (hoverTask != prev) repaint()
                }
            })
            addMouseListener(object : MouseAdapter() {
                override fun mouseExited(e: MouseEvent) {
                    hoverTask = null
                    repaint()
                }
            })
        }

        // Layout constants
        private val padLeft   = 65
        private val padRight  = 20
        private val padTop    = 16
        private val padBottom = 46

        // Cached dot positions for hit-test
        private val dotPositions = mutableListOf<Triple<Int, Int, TaskResult>>() // x, y, task

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val tasks = visibleTasks()

            if (tasks.isEmpty()) {
                g2.color = Color(0x808080)
                g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
                val msg = "No data — load results first"
                val fm = g2.fontMetrics
                g2.drawString(msg, (width - fm.stringWidth(msg)) / 2, height / 2)
                return
            }

            val xParam = (xAxisCombo.selectedItem as? Param) ?: Param.AGENTS
            val sizeParam = (sizeCombo.selectedItem as? String) ?: "(none)"

            val xValues = tasks.map { xValue(it, xParam) }
            val yValues = tasks.mapNotNull { it.fopt }

            val xMin = xValues.minOrNull()!!
            val xMax = xValues.maxOrNull()!!
            val yMin = min(0.0, yValues.minOrNull()!!)
            val yMax = yValues.maxOrNull()!!
            val xRange = if (xMax == xMin) 1.0 else xMax - xMin
            val yRange = if (yMax == yMin) 1.0 else yMax - yMin

            val legendW = legendWidth(g2)
            val plotX = padLeft
            val plotY = padTop
            val plotW = width - padLeft - padRight - legendW
            val plotH = height - padTop - padBottom

            if (plotW < 50 || plotH < 50) return

            // Plot background
            g2.color = Color(0x1E1E1E)
            g2.fillRect(plotX, plotY, plotW, plotH)
            g2.color = Color(0x3A3A3A)
            g2.drawRect(plotX, plotY, plotW, plotH)

            // Grid
            val gridCount = 5
            val gridStroke = BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, floatArrayOf(4f, 4f), 0f)
            g2.stroke = gridStroke
            g2.color = Color(0x333333)
            for (i in 1 until gridCount) {
                val gy = plotY + i * plotH / gridCount
                g2.drawLine(plotX, gy, plotX + plotW, gy)
                val gx = plotX + i * plotW / gridCount
                g2.drawLine(gx, plotY, gx, plotY + plotH)
            }
            g2.stroke = BasicStroke(1f)

            // Y tick labels
            g2.color = Color(0xAAAAAA)
            g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
            var fm = g2.fontMetrics
            for (i in 0..gridCount) {
                val value = yMin + i * yRange / gridCount
                val gy = plotY + plotH - (i * plotH / gridCount)
                val label = formatValue(value)
                g2.drawString(label, plotX - fm.stringWidth(label) - 4, gy + fm.ascent / 2)
                // X ticks
                val xv = xMin + i * xRange / gridCount
                val gx = plotX + i * plotW / gridCount
                val xl = if (xParam == Param.RUNTIME) xv.toLong().toString() else xv.toInt().toString()
                g2.drawString(xl, gx - fm.stringWidth(xl) / 2, plotY + plotH + fm.height + 2)
            }

            // Size range for encoding
            val sizeValues = if (sizeParam != "(none)") tasks.map { sizeValue(it, sizeParam) } else emptyList()
            val sMin = sizeValues.minOrNull() ?: 1.0
            val sMax = sizeValues.maxOrNull() ?: 1.0
            val sRange = if (sMax == sMin) 1.0 else sMax - sMin
            val minDot = 5
            val maxDot = 18

            // Dots
            dotPositions.clear()
            for (task in tasks) {
                val fopt = task.fopt ?: continue
                val color = algorithmColors[task.algorithm] ?: Color.WHITE
                val xv = xValue(task, xParam)
                val px = plotX + ((xv - xMin) / xRange * plotW).roundToInt()
                val py = plotY + plotH - ((fopt - yMin) / yRange * plotH).roundToInt()

                val r = if (sizeParam != "(none)") {
                    val sv = sizeValue(task, sizeParam)
                    (minDot + ((sv - sMin) / sRange * (maxDot - minDot))).roundToInt()
                } else 6

                dotPositions.add(Triple(px, py, task))

                g2.color = Color(color.red, color.green, color.blue, 160)
                g2.fillOval(px - r, py - r, r * 2, r * 2)
                g2.color = color
                g2.stroke = BasicStroke(1.2f)
                g2.drawOval(px - r, py - r, r * 2, r * 2)
                g2.stroke = BasicStroke(1f)
            }

            // Axis labels
            g2.font = Font(Font.SANS_SERIF, Font.BOLD, 11)
            g2.color = Color(0xCCCCCC)
            fm = g2.fontMetrics
            val xLabel = xParam.label
            g2.drawString(xLabel, plotX + (plotW - fm.stringWidth(xLabel)) / 2, height - 4)

            val yLabel = "fopt (lower = better)"
            val oldTx = g2.transform
            g2.rotate(-Math.PI / 2, 11.0, (plotY + plotH / 2).toDouble())
            g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
            g2.drawString(yLabel, 11 - g2.fontMetrics.stringWidth(yLabel) / 2, plotY + plotH / 2)
            g2.transform = oldTx

            // Size legend
            if (sizeParam != "(none)" && sRange > 0) {
                drawSizeLegend(g2, plotX + plotW + 10, plotY + 90, sizeParam, sMin, sMax, minDot, maxDot)
            }

            // Algorithm legend
            drawAlgoLegend(g2, plotX + plotW + 10, plotY)

            // Tooltip
            hoverTask?.let { drawTooltip(g2, it, hoverPt) }
        }

        private fun drawAlgoLegend(g2: Graphics2D, x: Int, startY: Int) {
            g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            val fm = g2.fontMetrics
            val lineH = fm.height + 4
            var y = startY + lineH
            for ((algo, color) in algorithmColors) {
                if (algoCheckboxes[algo]?.isSelected != false) {
                    g2.color = color
                    g2.fillRect(x, y - fm.ascent + 2, 12, 10)
                }
                g2.color = if (algoCheckboxes[algo]?.isSelected != false) Color(0xCCCCCC) else Color(0x555555)
                g2.drawString(algo, x + 16, y)
                y += lineH
            }
        }

        private fun drawSizeLegend(g2: Graphics2D, x: Int, y: Int, param: String, sMin: Double, sMax: Double, minDot: Int, maxDot: Int) {
            g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 9)
            g2.color = Color(0x888888)
            g2.drawString(param, x, y)
            val steps = listOf(sMin, (sMin + sMax) / 2, sMax)
            steps.forEachIndexed { i, sv ->
                val r = (minDot + (sv - sMin) / (if (sMax == sMin) 1.0 else sMax - sMin) * (maxDot - minDot)).roundToInt()
                val cy = y + 16 + i * 28
                g2.color = Color(0x555555)
                g2.fillOval(x + maxDot - r, cy - r, r * 2, r * 2)
                g2.color = Color(0x888888)
                g2.drawOval(x + maxDot - r, cy - r, r * 2, r * 2)
                val lbl = if (param == "Runtime (ms)") sv.toLong().toString() else sv.toInt().toString()
                g2.drawString(lbl, x + maxDot * 2 + 4, cy + 4)
            }
        }

        private fun drawTooltip(g2: Graphics2D, task: TaskResult, pt: Point) {
            val lines = listOf(
                "Task: ${task.taskId}",
                "Algorithm: ${task.algorithm ?: "—"}",
                "Agents: ${task.agents ?: "—"}  Iter: ${task.iterations ?: "—"}  Dim: ${task.dimension ?: "—"}",
                "fopt: ${"%.6f".format(task.fopt)}",
                "Runtime: ${task.runtimeMs ?: "—"} ms",
                "Node: ${task.assignedTo ?: "—"}"
            )
            g2.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            val fm = g2.fontMetrics
            val tw = lines.maxOf { fm.stringWidth(it) } + 12
            val th = lines.size * fm.height + 8
            var tx = pt.x + 12
            var ty = pt.y - th / 2
            if (tx + tw > width - 4) tx = pt.x - tw - 8
            if (ty < 4) ty = 4
            if (ty + th > height - 4) ty = height - th - 4

            g2.color = Color(0x2A2A2A)
            g2.fillRoundRect(tx, ty, tw, th, 6, 6)
            g2.color = Color(0x555555)
            g2.drawRoundRect(tx, ty, tw, th, 6, 6)
            g2.color = Color(0xDDDDDD)
            lines.forEachIndexed { i, line ->
                g2.drawString(line, tx + 6, ty + fm.height * (i + 1))
            }
        }

        private fun findNearestTask(mx: Int, my: Int): TaskResult? {
            var best: TaskResult? = null
            var bestDist = 20.0 * 20.0
            for ((px, py, task) in dotPositions) {
                val d = ((px - mx).toLong() * (px - mx) + (py - my).toLong() * (py - my)).toDouble()
                if (d < bestDist) { bestDist = d; best = task }
            }
            return best
        }

        private fun legendWidth(g2: Graphics2D): Int {
            if (algorithmColors.isEmpty()) return 20
            g2.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            val maxText = algorithmColors.keys.maxOfOrNull { g2.fontMetrics.stringWidth(it) } ?: 0
            return maxText + 30
        }
    }

    init {
        xAxisCombo.addActionListener { chartView.repaint() }
        sizeCombo.addActionListener { chartView.repaint() }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
            border = JBUI.Borders.emptyBottom(2)
            add(JBLabel("X axis:"))
            add(xAxisCombo)
            add(JBLabel("  Point size:"))
            add(sizeCombo)
            add(JBLabel("  Algorithms:"))
            add(filterPanel)
        }
        add(toolbar, BorderLayout.NORTH)
        add(chartView, BorderLayout.CENTER)
        preferredSize = Dimension(600, 340)
    }

    fun update(newTasks: List<TaskResult>) {
        allTasks = newTasks.filter { it.status == TaskStatus.DONE && it.fopt != null }

        val algos = allTasks.mapNotNull { it.algorithm }.distinct()
        algos.forEachIndexed { i, algo ->
            algorithmColors.getOrPut(algo) { palette[i % palette.size] }
        }

        // Rebuild filter checkboxes for new algorithms
        filterPanel.removeAll()
        algoCheckboxes.clear()
        for (algo in algos) {
            val cb = JCheckBox(algo, true).apply {
                isOpaque = false
                addActionListener { chartView.repaint() }
            }
            algoCheckboxes[algo] = cb
            filterPanel.add(cb)
        }
        filterPanel.revalidate()
        chartView.repaint()
    }

    private fun visibleTasks(): List<TaskResult> =
        allTasks.filter { algoCheckboxes[it.algorithm]?.isSelected != false }

    private fun xValue(task: TaskResult, param: Param): Double = when (param) {
        Param.AGENTS     -> task.agents?.toDouble() ?: 0.0
        Param.ITERATIONS -> task.iterations?.toDouble() ?: 0.0
        Param.DIMENSION  -> task.dimension?.toDouble() ?: 0.0
        Param.RUNTIME    -> task.runtimeMs?.toDouble() ?: 0.0
    }

    private fun sizeValue(task: TaskResult, param: String): Double = when (param) {
        "Iterations" -> task.iterations?.toDouble() ?: 1.0
        "Agents"     -> task.agents?.toDouble() ?: 1.0
        "Dimension"  -> task.dimension?.toDouble() ?: 1.0
        else         -> 1.0
    }

    private fun formatValue(v: Double): String =
        if (v == 0.0) "0" else if (kotlin.math.abs(v) < 0.001) "%.2e".format(v) else "%.4f".format(v)
}
