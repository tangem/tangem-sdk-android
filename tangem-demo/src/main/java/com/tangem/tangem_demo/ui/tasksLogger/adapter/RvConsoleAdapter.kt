package com.tangem.tangem_demo.ui.tasksLogger.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tangem.LogMessage
import com.tangem.MessageType
import com.tangem.tangem_demo.R
import com.tangem.tangem_demo.postUi
import com.tangem.tangem_demo.ui.tasksLogger.ConsoleWriter

/**
[REDACTED_AUTHOR]
 */
class RvConsoleAdapter : RecyclerView.Adapter<ConsoleVH>(), ConsoleWriter {
    private val itemList = mutableListOf<LogMessage>()

    var onItemCountChanged: ((Int) -> Unit)? = null

    fun addItem(item: LogMessage) {
        postUi {
            itemList.add(item)
            notifyDataSetChanged()
            onItemCountChanged?.invoke(itemCount)
        }
    }

    fun clear() {
        postUi {
            itemList.clear()
            notifyDataSetChanged()
            onItemCountChanged?.invoke(itemCount)
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConsoleVH {
        val inflater = LayoutInflater.from(parent.context)
        val vhView = inflater.inflate(R.layout.item_console_message_rv, parent, false)
        return ConsoleVH(vhView)
    }

    override fun onBindViewHolder(holder: ConsoleVH, position: Int) {
        holder.bindData(itemList[position])
    }

    override fun getItemCount(): Int = itemList.size

    override fun write(message: LogMessage) {
        addItem(message)
    }
}

class ConsoleVH(view: View) : RecyclerView.ViewHolder(view) {

    private val tvLogMessage = view.findViewById<TextView>(R.id.tvLogMessage)
    private val bottomSeparator = view.findViewById<View>(R.id.bottomSeparator)

    fun bindData(data: LogMessage) {
        val context = tvLogMessage.context
        showSeparator(data.type)
        tvLogMessage.setTextColor(ContextCompat.getColor(context, getColorResource(data.type)))
        tvLogMessage.text = "$data"
    }

    private fun showSeparator(type: MessageType) {
        when (type) {
            MessageType.STOP_SESSION -> bottomSeparator.visibility = View.VISIBLE
            else -> {
                bottomSeparator.visibility = View.GONE
            }
        }
    }

    private fun getColorResource(type: MessageType): Int {
        return when (type) {
            MessageType.ERROR -> R.color.red
            MessageType.SEND_DATA -> R.color.blue
            MessageType.RECEIVE_DATA -> R.color.green
            MessageType.SEND_TLV -> R.color.dark_blue
            MessageType.RECEIVE_TLV -> R.color.dark_green
            MessageType.DELAY, MessageType.SECURITY_DELAY -> R.color.green
            else -> R.color.black
        }
    }
}