package com.lws.nestedscrolldemo

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initImmersed()
        initView()
    }

    private fun initImmersed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val decorView = window.decorView
            val option = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            decorView.systemUiVisibility = option
            window.navigationBarColor = Color.TRANSPARENT
            window.statusBarColor = Color.TRANSPARENT
            val actionBar = supportActionBar
            actionBar!!.hide()
        }
    }

    private fun initView() {
        initWebView()
        initRecyclerView()
        initToolBarView()
    }

    private fun initToolBarView() {
        v_tool_bar.setOnClickListener { nested_container.scrollToTarget(rv_list) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        web_container.apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("https://github.com/ReactiveX/RxAndroid")
        }
    }

    private fun initRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        rv_list.layoutManager = layoutManager
        val data = getCommentData()
        val rvAdapter = RvAdapter(this, data)
        rv_list.adapter = rvAdapter
    }

    private fun getCommentData(): List<InfoBean> {
        val commentList = ArrayList<InfoBean>()
        val titleBean = InfoBean()
        titleBean.type = InfoBean.TYPE_TITLE
        titleBean.title = "评论列表"
        commentList.add(titleBean)
        for (i in 0..39) {
            val contentBean = InfoBean()
            contentBean.type = InfoBean.TYPE_ITEM
            contentBean.title = "评论标题$i"
            contentBean.content = "评论内容$i"
            commentList.add(contentBean)
        }
        return commentList
    }
}
