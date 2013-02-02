package com.silverkeytech.android_rivers

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import com.actionbarsherlock.app.SherlockListFragment
import com.silverkeytech.android_rivers.db.Bookmark
import com.silverkeytech.android_rivers.db.BookmarkCollection
import com.silverkeytech.android_rivers.db.BookmarkKind
import com.silverkeytech.android_rivers.db.DatabaseManager
import com.silverkeytech.android_rivers.db.SortingOrder
import com.silverkeytech.android_rivers.db.getBookmarkCollectionFromDb
import com.silverkeytech.android_rivers.db.getBookmarksFromDb
import com.silverkeytech.android_rivers.db.removeItemByUrlFromBookmarkDb
import com.actionbarsherlock.view.Menu
import com.actionbarsherlock.view.MenuInflater
import com.silverkeytech.android_rivers.db.saveBookmarkToDb
import com.actionbarsherlock.view.MenuItem

public class RssListFragment() : SherlockListFragment() {
    class object {
        public val TAG: String = javaClass<RssListFragment>().getSimpleName()
    }

    var parent : Activity? = null
    var lastEnteredUrl: String? = ""

    public override fun onAttach(activity: Activity?) {
        super<SherlockListFragment>.onAttach(activity)
        parent = activity
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        super<SherlockListFragment>.onCreate(savedInstanceState)
    }

    public override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val vw = inflater!!.inflate(R.layout.rss_list_fragment, container, false)

        return vw
    }

    public override fun onResume() {
        Log.d(TAG, "OnResume")
        super<SherlockListFragment>.onResume()
    }

    public override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater!!.inflate(R.menu.rss_list_fragment_menu, menu)
        super<SherlockListFragment>.onCreateOptionsMenu(menu, inflater)

    }

    public override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item!!.getItemId()){
            R.id.rss_list_fragment_menu_show_add_dialog -> {
                displayAddNewRssDialog()
                return false
            }
            else -> return false
        }
    }

    public override fun onHiddenChanged(hidden: Boolean) {
        Log.d(TAG, "OnHiddenChanged $hidden")
        if (!hidden){
            displayRssBookmarks()
        }
        super<SherlockListFragment>.onHiddenChanged(hidden)
    }


    public override fun onPause() {
        Log.d(TAG, "OnPause")
        super<SherlockListFragment>.onPause()
    }

    fun displayAddNewRssDialog(){
        val dlg = createSingleInputDialog(parent!!, "Add new RSS", lastEnteredUrl, "Set url here", {
            dlg, url ->
            lastEnteredUrl = url
            Log.d(TAG, "Entered $url")
            if (url.isNullOrEmpty()){
                parent!!.toastee("Please enter url of the river")
            }
            else {
                var currentUrl = url!!
                if (!currentUrl.contains("http://"))
                    currentUrl = "http://" + currentUrl

                val u = safeUrlConvert(currentUrl)
                if (u.isTrue()){
                    DownloadFeed(parent!!, true)
                            .executeOnComplete {
                        res ->
                        if (res.isTrue()){
                            var feed = res.value!!

                            val res2 = saveBookmarkToDb(feed.title, currentUrl, BookmarkKind.RSS, feed.language, null)

                            if (res2.isTrue()){
                                parent!!.toastee("$currentUrl is successfully bookmarked")
                                displayRssBookmarks()
                            }
                            else{
                                parent!!.toastee("Sorry, we cannot add this $currentUrl river", Duration.LONG)
                            }
                        }else{
                            parent!!.toastee("Error ${res.exception?.getMessage()}", Duration.LONG)
                        }
                    }
                            .execute(currentUrl)
                    dlg?.dismiss()
                }else{
                    Log.d(TAG, "RSS $currentUrl conversion generates ${u.exception?.getMessage()}")
                    parent!!.toastee("The url you entered is not valid. Please try again", Duration.LONG)
                }
            }
        })

        dlg.show()
    }

    fun showMessage(msg: String) {
        val txt = getView()!!.findViewById(R.id.rss_list_fragment_message_tv) as TextView
        if (msg.isNullOrEmpty()){
            txt.setVisibility(View.INVISIBLE)
            txt.setText("")
        }
        else{
            txt.setVisibility(View.VISIBLE)
            txt.setText(msg)
        }
    }

    fun handleRssListing(bookmarks: List<Bookmark>) {
        if (bookmarks.count() == 0){
            showMessage(parent!!.getString(R.string.empty_rss_items_list)!!)
        }
        else
            showMessage("")

        val adapter = object : ArrayAdapter<Bookmark>(parent, android.R.layout.simple_list_item_1, android.R.id.text1, bookmarks){
            public override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
                val text = bookmarks[position].toString()
                return currentListItem(text, convertView, parent)
            }
        }

        val list = getView()!!.findViewById(android.R.id.list) as ListView
        list.setAdapter(adapter)
        list.setOnItemClickListener(object : OnItemClickListener{
            public override fun onItemClick(p0: AdapterView<out Adapter?>?, p1: View?, p2: Int, p3: Long) {
                val bookmark = bookmarks.get(p2)
                startFeedActivity(parent!!, bookmark.url, bookmark.title, bookmark.language)
            }
        })

        list.setOnItemLongClickListener(object : AdapterView.OnItemLongClickListener{
            public override fun onItemLongClick(p0: AdapterView<out Adapter?>?, p1: View?, p2: Int, p3: Long): Boolean {
                val currentBookmark = bookmarks.get(p2)
                showRssBookmarkQuickActionPopup(parent!!, currentBookmark, p1!!, list)
                return true
            }
        })
    }

    fun displayRssBookmarks(){
        val bookmarks = getBookmarksFromDb(BookmarkKind.RSS, SortingOrder.ASC)
        handleRssListing(bookmarks)
    }

    fun showRssBookmarkQuickActionPopup(context: Activity, currentBookmark: Bookmark, item: View, list: View) {
        //overlay popup at top of clicked overview position
        val popupWidth = item.getWidth()
        val popupHeight = item.getHeight()

        val x = context.getLayoutInflater()!!.inflate(R.layout.main_feed_quick_actions, null, false)!!
        val pp = PopupWindow(x, popupWidth, popupHeight, true)

        x.setBackgroundColor(android.graphics.Color.LTGRAY)

        x.setOnClickListener {
            pp.dismiss()
        }

        val removeIcon = x.findViewById(R.id.main_feed_quick_action_delete_icon) as ImageView
        removeIcon.setOnClickListener {
            val dlg = createConfirmationDialog(context = context, message = "Are you sure about removing this RSS bookmark?", positive = {
                try{
                    val res = removeItemByUrlFromBookmarkDb(currentBookmark.url)
                    if (res.isFalse())
                        context.toastee("Error in removing this bookmark ${res.exception?.getMessage()}")
                    else {
                        context.toastee("Bookmark removed")
                        displayRssBookmarks()
                    }
                }
                catch(e: Exception){
                    context.toastee("Error in trying to remove this bookmark ${e.getMessage()}")
                }
                pp.dismiss()
            }, negative = {
                pp.dismiss()
            })

            dlg.show()
        }


        fun showCollectionAssignmentPopup(alreadyBelongsToACollection: Boolean) {
            var coll = getBookmarkCollectionFromDb(sortByTitleOrder = SortingOrder.ASC)

            if (coll.size == 0){
                context.toastee("Please create a collection before assigning a bookmark to it")
                pp.dismiss()
            }
            else if (coll.size == 1 && alreadyBelongsToACollection){
                context.toastee("This RSS already belongs to a collection and there is no other collection to reassign it to")
                pp.dismiss()
            }
            else {
                val dialog = AlertDialog.Builder(context)
                if (alreadyBelongsToACollection)
                    dialog.setTitle("Reassign bookmark to collection")
                else
                    dialog.setTitle("Assign bookmark to collection")

                val collectionWithoutCurrent = coll.filter { x -> x.id != currentBookmark.collection?.id }
                var collectionTitles = collectionWithoutCurrent.map { x -> x.title }.toArray(array<String>())

                dialog.setItems(collectionTitles, dlgClickListener {
                    dlg, idx ->
                    val selectedCollection = collectionWithoutCurrent[idx]

                    if (currentBookmark.collection == null){
                        currentBookmark.collection = BookmarkCollection()
                    }

                    currentBookmark.collection!!.id = selectedCollection.id

                    try{
                        DatabaseManager.bookmark!!.update(currentBookmark)
                        if (alreadyBelongsToACollection)
                            context.toastee("This RSS has been successfully reassigned to '${selectedCollection.title}' collection", Duration.LONG)
                        else
                            context.toastee("This RSS has been successfuly assigned to '${selectedCollection.title}' collection", Duration.LONG)


                    } catch(ex: Exception){
                        context.toastee("Sorry, I have problem updating this RSS bookmark record", Duration.LONG)
                    }

                    pp.dismiss()
                })

                var createdDialog = dialog.create()!!
                createdDialog.setCanceledOnTouchOutside(true)
                createdDialog.setCancelable(true)
                createdDialog.show()
            }
        }

        val editIcon = x.findViewById(R.id.main_feed_quick_action_edit_icon) as ImageView
        editIcon.setOnClickListener {
            //check if it already belongs to a collection so there is no need to download.
            val alreadyBelongsToACollection = currentBookmark.collection != null
            //do a verification that this feed can actually be part of a collection
            if (!alreadyBelongsToACollection){
                DownloadFeed(context, true)
                        .executeOnComplete {
                    res ->
                    if (res.isTrue()){
                        var feed = res.value!!
                        if (!feed.isDateParseable){
                            context.toastee("Sorry, this feed cannot belong to a collection because we cannot determine its dates", Duration.LONG)
                        }
                        else{
                            showCollectionAssignmentPopup(alreadyBelongsToACollection)
                        }
                    }else{
                        context.toastee("Error ${res.exception?.getMessage()}", Duration.LONG)
                    }
                }
                        .execute(currentBookmark.url)
            }else
                showCollectionAssignmentPopup(alreadyBelongsToACollection)

        }

        val itemLocation = getLocationOnScreen(item)
        pp.showAtLocation(list, Gravity.TOP or Gravity.LEFT, itemLocation.x, itemLocation.y)
    }


    public data class ViewHolder (var name: TextView)

    fun currentListItem(text: String, convertView: View?, parent: ViewGroup?): View? {
        var holder: ViewHolder?

        var vw: View? = convertView

        if (vw == null){
            vw = inflater().inflate(android.R.layout.simple_list_item_1, parent, false)

            holder = ViewHolder(vw!!.findViewById(android.R.id.text1) as TextView)
            holder!!.name.setText(text)
            vw!!.setTag(holder)
        }else{
            holder = vw!!.getTag() as ViewHolder
            holder!!.name.setText(text)
        }

        return vw
    }

    fun inflater(): LayoutInflater {
        val inflater: LayoutInflater = parent!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        return inflater
    }

}