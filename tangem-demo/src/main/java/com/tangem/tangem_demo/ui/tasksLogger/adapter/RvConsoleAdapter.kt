package com.tangem.tangem_demo.ui.tasksLogger.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tangem.tangem_demo.R
import com.tangem.tangem_demo.postUi
import com.tangem.tangem_demo.ui.tasksLogger.ConsoleMessage
import com.tangem.tangem_demo.ui.tasksLogger.ConsoleWriter
import com.tangem.tangem_demo.ui.tasksLogger.MessageType

/**
[REDACTED_AUTHOR]
 */
class RvConsoleAdapter : RecyclerView.Adapter<ConsoleVH>(), ConsoleWriter {
    private val itemList = mutableListOf<ConsoleMessage>()

    var onItemCountChanged: ((Int) -> Unit)? = null

    fun addItem(item: ConsoleMessage) {
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

    override fun addLogMessage(message: ConsoleMessage) {
        addItem(message)
    }
}

class ConsoleVH(view: View) : RecyclerView.ViewHolder(view) {

    private val tvLogMessage = view.findViewById<TextView>(R.id.tvLogMessage)

    fun bindData(data: ConsoleMessage) {
        val context = tvLogMessage.context
        when (data.type) {
            MessageType.VERBOSE -> {
                tvLogMessage.setTextColor(ContextCompat.getColor(context, R.color.grey))
            }
            MessageType.INFO -> {
                tvLogMessage.setTextColor(ContextCompat.getColor(context, R.color.green))
            }
            MessageType.ERROR -> {
                tvLogMessage.setTextColor(ContextCompat.getColor(context, R.color.red))
            }
            MessageType.VIEW_DELEGATE -> {
                tvLogMessage.setTextColor(ContextCompat.getColor(context, R.color.black))
            }
        }
        tvLogMessage.text = data.message
    }
}