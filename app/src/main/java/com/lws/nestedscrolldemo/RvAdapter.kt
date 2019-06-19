package com.lws.nestedscrolldemo

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lws.nestedscrolldemo.R.id
import com.lws.nestedscrolldemo.R.layout


class RvAdapter(context: Context, private val mData: List<InfoBean>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val mInflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            InfoBean.TYPE_TITLE -> TitleHolder(mInflater.inflate(layout.layout_title_layout, viewGroup, false))
            else -> ContentHolder(mInflater.inflate(layout.layout_content_layout, viewGroup, false))
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, i: Int) {
        val (_, title, content) = mData[i]
        when (getItemViewType(i)) {
            InfoBean.TYPE_ITEM -> {
                val contentHolder = viewHolder as ContentHolder
                contentHolder.tvTitle.text = title
                contentHolder.tvContent.text = content
            }
            InfoBean.TYPE_TITLE -> {
                val titleHolder = viewHolder as TitleHolder
                titleHolder.tvTitle.text = title
            }
        }
    }

    override fun getItemCount(): Int {
        return mData.size
    }

    override fun getItemViewType(position: Int): Int {
        return mData[position].type
    }

    class TitleHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvTitle: TextView = itemView.findViewById(id.tv_title)
    }

    class ContentHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvTitle: TextView = itemView.findViewById(id.tv_title)
        var tvContent: TextView = itemView.findViewById(id.tv_content)
    }
}