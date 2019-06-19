package com.lws.nestedscrolldemo

data class InfoBean(var type: Int = 0, var title: String = "", var content: String = "") {
    companion object {
        const val TYPE_TITLE = 1
        const val TYPE_ITEM = 2
    }

    override fun toString(): String {
        return "InfoBean{type=$type, title='$title', content='$content'}"
    }
}