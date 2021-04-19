package com.tangem.tangem_demo.ui.tasksLogger.adapter

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.tangem.tangem_demo.R
import com.tangem.tangem_demo.inflate
import com.tangem.tangem_demo.splitCamelCase
import com.tangem.tangem_demo.ui.tasksLogger.CommandType

/**
[REDACTED_AUTHOR]
 */
class CommandSpinnerAdapter(private val itemList: List<CommandType>) : BaseAdapter() {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: TextView = convertView as? TextView ?: parent.inflate(android.R.layout.simple_spinner_item)

        return view.apply { text = toText(itemList[position]) }
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = convertView as? View
            ?: parent.inflate(R.layout.item_command_drop_down_spinner)

        return view.apply {
            findViewById<TextView>(R.id.textView).text = toText(itemList[position])
        }
    }

    override fun getCount(): Int = itemList.size

    override fun getItem(position: Int): Any = itemList[position]

    override fun getItemId(position: Int): Long = itemList.size.toLong()

    private fun toText(type: CommandType): String = type.name.splitCamelCase().toLowerCase().capitalize()
}