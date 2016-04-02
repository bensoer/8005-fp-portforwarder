package gui

import javafx.application.Platform
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.scene.chart.PieChart
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

internal class StatisticsPane:GridPane()
{
    private val COL_INDEX_LABEL:Int = 0
    private val COL_INDEX_CONTENT:Int = 1
    private val COL_INDEX_USAGE_BY_PORT:Int = 2
    private val COL_INDEX_USAGE_BY_CONNECTION:Int = 3

    private val THROUGHPUT_LABEL:String = "Throughput:"
    private val MAX_THROUGHPUT_LABEL:String = "Max Throughput:"
    private val BYTES_FORWARDED_LABEL:String = "Bytes Forwarded:"
    private val CURRENT_CONNECTIONS_LABEL:String = "Current Connections:"
    private val MAX_CONNECTIONS_LABEL:String = "Max Concurrent Connections:"
    private val TOTAL_CONNECTIONS_LABEL:String = "Total Connections:"

    private var nextRow:Int = 0

    private val throughputDisplay = TextDisplay()
    private val maxThroughputDisplay = TextDisplay()
    private val bytesForwardedDisplay = TextDisplay()
    private val currentConnectionsDisplay = TextDisplay()
    private val maxConnectionsDisplay = TextDisplay()
    private val totalConnectionsDisplay = TextDisplay()
    private val usageByPortDisplay = PieChart()
    private val usageByConnectionDisplay = PieChart()

    /**
     * collection of bytes that were sent in the last 1000 milliseconds.
     */
    private val bytesSentLastSecond = DelayQueue<DelayedByteCount>()

    fun bytesForwarded(connection:InetSocketAddress,port:Int,numBytes:Int)
    {
        synchronized(bytesSentLastSecond)
        {
            bytesSentLastSecond.add(DelayedByteCount(connection,port,numBytes,System.currentTimeMillis()+TimeUnit.MILLISECONDS.convert(1,TimeUnit.SECONDS)))
        }
        Platform.runLater {bytesForwarded += numBytes}
    }

    fun connectionOpened()
    {
        Platform.runLater {currentConnections++}
    }

    fun connectionClosed()
    {
        Platform.runLater {currentConnections--}
    }

    private var throughput = 0

        set(value)
        {
            throughputDisplay.value.text = value.toString()
            if (value > maxThroughput) maxThroughput = value
            field = value
        }

    private var maxThroughput = 0

        set(value)
        {
            maxThroughputDisplay.value.text = value.toString()
            field = value
        }

    private var bytesForwarded = 0

        set(value)
        {
            bytesForwardedDisplay.value.text = value.toString()
            field = value
        }

    private var currentConnections = 0

        set(value)
        {
            val delta = value-field
            if (delta > 0) totalConnections += delta
            if (value > maxConnections) maxConnections = value
            currentConnectionsDisplay.value.text = value.toString()
            field = value
        }

    private var maxConnections = 0

        set(value)
        {
            maxConnectionsDisplay.value.text = value.toString()
            field = value
        }

    private var totalConnections = 0

        set(value)
        {
            totalConnectionsDisplay.value.text = value.toString()
            field = value
        }

    init
    {
        // configure aesthetic properties
        padding = Insets(Dimens.KEYLINE_SMALL.toDouble())
        hgap = Dimens.KEYLINE_SMALL.toDouble()
        vgap = Dimens.KEYLINE_SMALL.toDouble()

        // configure grid constraints
        with(ColumnConstraints())
        {
            halignment = HPos.RIGHT
            columnConstraints.add(COL_INDEX_LABEL,this)
        }

        with(ColumnConstraints())
        {
            isFillWidth = true
            halignment = HPos.LEFT
            hgrow = Priority.ALWAYS
            columnConstraints.add(COL_INDEX_CONTENT,this)
        }

        with(ColumnConstraints())
        {
            halignment = HPos.CENTER
            columnConstraints.add(COL_INDEX_USAGE_BY_PORT,this)
        }

        with(ColumnConstraints())
        {
            halignment = HPos.CENTER
            columnConstraints.add(COL_INDEX_USAGE_BY_PORT,this)
        }

        // configure & add child nodes
        throughputDisplay.label.text = THROUGHPUT_LABEL
        maxThroughputDisplay.label.text = MAX_THROUGHPUT_LABEL
        bytesForwardedDisplay.label.text = BYTES_FORWARDED_LABEL
        currentConnectionsDisplay.label.text = CURRENT_CONNECTIONS_LABEL
        maxConnectionsDisplay.label.text = MAX_CONNECTIONS_LABEL
        totalConnectionsDisplay.label.text = TOTAL_CONNECTIONS_LABEL

        usageByPortDisplay.minWidth = 300.0
        usageByPortDisplay.minHeight = 150.0
        usageByPortDisplay.prefWidth = 300.0
        usageByPortDisplay.prefHeight = 150.0
        usageByPortDisplay.animated = false
        usageByPortDisplay.labelsVisible = true

        usageByConnectionDisplay.minWidth = 350.0
        usageByConnectionDisplay.minHeight = 150.0
        usageByConnectionDisplay.prefWidth = 350.0
        usageByConnectionDisplay.prefHeight = 150.0
        usageByConnectionDisplay.animated = false
        usageByConnectionDisplay.labelsVisible = true

        add(throughputDisplay)
        add(maxThroughputDisplay)
        add(bytesForwardedDisplay)
        add(currentConnectionsDisplay)
        add(maxConnectionsDisplay)
        add(totalConnectionsDisplay)

        // add pie charts. each should be in its own column and span all rows.
        // todo: find out way to get the row count some other way??? eh..
        add(usageByPortDisplay,COL_INDEX_USAGE_BY_PORT,0,1,nextRow+1)
        add(usageByConnectionDisplay,COL_INDEX_USAGE_BY_CONNECTION,0,1,nextRow+1)

        // start a timer to update the GUI display
        Timer("statisticsPaneGuiUpdater",true)
            .scheduleAtFixedRate(UpdateGuiTask(),100,100)
    }

    private fun updatePieChart(pieChart:PieChart, pieData:Map<String,Double>)
    {
        with(pieChart.data)
        {
            // remove zeroed data
            (this as MutableIterable<PieChart.Data>).removeAll {it.name !in pieData.keys}
            // update existing data
            forEach {it.pieValue = pieData[it.name]!!}
            // add new data
            val existingPieData = map {it.name}.toSet()
            val newPieData = pieData.filter {it.key !in existingPieData}
            addAll(newPieData.map {PieChart.Data(it.key,it.value)})
        }
    }

    private fun add(newTextDisplay:TextDisplay)
    {
        add(newTextDisplay.label,COL_INDEX_LABEL,nextRow)
        add(newTextDisplay.value,COL_INDEX_CONTENT,nextRow)
        nextRow++
    }

    private inner class UpdateGuiTask:TimerTask()
    {
        private val isRunnablePendingExecutionLock = ReentrantLock()

        private var isRunnablePendingExecution = false

            set(value)
            {
                assert(isRunnablePendingExecutionLock.isHeldByCurrentThread)
            }

        override fun run()
        {
            isRunnablePendingExecutionLock.withLock()
            {
                if (!isRunnablePendingExecution)
                {
                    isRunnablePendingExecution = true
                    enqueueRunnable()
                }
            }
        }

        private fun enqueueRunnable()
        {
            Platform.runLater()
            {
                val bytesSentLastSecond = synchronized(bytesSentLastSecond)
                {
                    // remove expired bytes sent last second
                    while (bytesSentLastSecond.poll() != null);

                    // return a snapshot of the array's contents
                    bytesSentLastSecond.toTypedArray()
                }

                // update throughput
                throughput = bytesSentLastSecond.sumBy {it.byteCount}

                // update usageByPort
                run()
                {
                    val pieData = bytesSentLastSecond
                        .groupBy {it.port.toString()}
                        .mapValues {it.value.sumBy {it.byteCount}.toDouble()}
                        .plus("unused" to (maxThroughput-throughput).toDouble())
                    updatePieChart(usageByPortDisplay,pieData)
                }

                // update usageByConnection
                run()
                {
                    val pieData = bytesSentLastSecond
                        .groupBy {"${it.connection.hostString}:${it.connection.port}"}
                        .mapValues {it.value.sumBy {it.byteCount}.toDouble()}
                        .plus("unused" to (maxThroughput-throughput).toDouble())
                    updatePieChart(usageByConnectionDisplay,pieData)
                }

                isRunnablePendingExecutionLock.withLock()
                {
                    isRunnablePendingExecution = false
                }
            }
        }
    }
}

private class TextDisplay(
    val label:Label = Label(),
    val value:Label = Label())

private class DelayedByteCount(
    val connection:InetSocketAddress,
    val port:Int,
    val byteCount:Int,
    val dequeueTime:Long)
:Delayed
{
    override fun getDelay(unit:TimeUnit):Long
    {
        val delay = dequeueTime-System.currentTimeMillis()
        return unit.convert(delay,TimeUnit.MILLISECONDS)
    }

    override fun compareTo(other:Delayed):Int
    {
        return (getDelay(TimeUnit.MILLISECONDS)-other.getDelay(TimeUnit.MILLISECONDS))
            .coerceIn(-1L..1L).toInt()
    }
}
