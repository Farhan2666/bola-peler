package com.bolapeler.assistive.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bolapeler.assistive.R
import com.bolapeler.assistive.model.PanelAction

class PanelActionAdapter(
    private val context: Context,
    private var items: List<PanelAction>
) : BaseAdapter() {

    fun updateItems(newItems: List<PanelAction>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): PanelAction = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_panel_action, parent, false)
        val item = items[position]
        val icon = view.findViewById<ImageView>(R.id.ivActionIcon)
        val label = view.findViewById<TextView>(R.id.tvActionLabel)

        if (item.appDrawable != null) {
            icon.setImageDrawable(item.appDrawable)
        } else if (item.iconRes != 0) {
            icon.setImageResource(item.iconRes)
        }
        label.text = item.label
        return view
    }
}
