/*
 * Copyright 2018 Keval Patel.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kevalpatel2106.ci.greenbuild.repoList

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.showIcons
import android.view.*
import com.kevalpatel2106.ci.greenbuild.R
import com.kevalpatel2106.ci.greenbuild.base.application.BaseApplication
import com.kevalpatel2106.ci.greenbuild.base.ciInterface.ServerInterface
import com.kevalpatel2106.grrenbuild.entities.Repo
import com.kevalpatel2106.ci.greenbuild.base.ciInterface.RepoSortBy
import com.kevalpatel2106.ci.greenbuild.base.view.DividerItemDecoration
import com.kevalpatel2106.ci.greenbuild.base.view.PageRecyclerViewAdapter
import com.kevalpatel2106.ci.greenbuild.di.DaggerDiComponent
import kotlinx.android.synthetic.main.fragment_repo_list.*
import javax.inject.Inject


/**
 * An [AppCompatActivity] to display the list of list of user repo.
 */
class RepoListFragment : Fragment(), PageRecyclerViewAdapter.RecyclerViewListener<Repo>, PopupMenu.OnMenuItemClickListener {

    @Inject
    internal lateinit var viewModelProvider: ViewModelProvider.Factory

    private lateinit var model: RepoListViewModel

    private lateinit var activity: Activity

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Activity) {
            activity = context
        } else {
            throw IllegalStateException("This fragment should be linked to the activity.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerDiComponent.builder()
                .applicationComponent(BaseApplication.get(activity).getApplicationComponent())
                .build()
                .inject(this@RepoListFragment)

        model = ViewModelProviders
                .of(this@RepoListFragment, viewModelProvider)
                .get(RepoListViewModel::class.java)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_repo_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Set the adapter
        val adapter = RepoListAdapter(activity, model.repoList.value!!, this)
        repo_list_rv.layoutManager = LinearLayoutManager(activity)
        repo_list_rv.adapter = adapter
        repo_list_rv.itemAnimator = DefaultItemAnimator()
        repo_list_rv.addItemDecoration(DividerItemDecoration(activity))

        model.repoList.observe(this@RepoListFragment, Observer {
            it?.let {
                if (it.isNotEmpty()) {
                    repo_list_view_flipper.displayedChild = 0
                    (repo_list_rv.adapter as RepoListAdapter).notifyDataSetChanged()
                } else {
                    repo_list_view_flipper.displayedChild = 2
                    repository_error_tv.text = getString(R.string.error_no_build_started)
                }
                activity.invalidateOptionsMenu()
            }
        })

        model.errorLoadingList.observe(this@RepoListFragment, Observer {
            it?.let {
                repo_list_view_flipper.displayedChild = 2
                repository_error_tv.text = it
            }
        })

        model.isLoadingFirstTime.observe(this@RepoListFragment, Observer {
            it?.let {
                repo_list_view_flipper.displayedChild = if (it) 1 else 0
                activity.invalidateOptionsMenu()
            }
        })

        model.isLoadingList.observe(this@RepoListFragment, Observer {
            it?.let {
                if (!it) {
                    repo_list_refresher.isRefreshing = false
                    adapter.onPageLoadComplete()
                }
            }
        })
        model.hasNextPage.observe(this@RepoListFragment, Observer {
            it?.let { adapter.hasNextPage = it }
        })

        repo_list_refresher.setOnRefreshListener {
            repo_list_refresher.isRefreshing = true
            model.loadRepoList(1)
        }
    }

    override fun onPageComplete(pos: Int) {
        model.loadRepoList((pos / ServerInterface.PAGE_SIZE) + 1)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (!model.repoList.value!!.isEmpty() && !model.isLoadingFirstTime.value!!) {
            //Display only of the list is not empty
            inflater.inflate(R.menu.menu_repo_list, menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.repo_list_sort -> {
                val sortPopUpMenu = PopupMenu(context!!, activity.findViewById(R.id.repo_list_sort))
                sortPopUpMenu.inflate(R.menu.pop_up_repo_list_sort)
                sortPopUpMenu.showIcons()
                sortPopUpMenu.setOnMenuItemClickListener(this)
                sortPopUpMenu.show()
            }
        }
        return false
    }

    /**
     * This method will be invoked when a any popup menu item is clicked if the item
     * itself did not already handle the event.
     *
     * @param item the menu item that was clicked
     * @return `true` if the event was handled, `false` otherwise
     */
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.repo_list_sort_by_name_asc -> {
                repo_list_refresher.isRefreshing = true
                repo_list_rv.smoothScrollToPosition(0)

                // Reload the list
                model.sortOrder = RepoSortBy.NAME_ASC
            }
            R.id.repo_list_sort_by_name_desc -> {
                repo_list_refresher.isRefreshing = true
                repo_list_rv.smoothScrollToPosition(0)

                // Reload the list
                model.sortOrder = RepoSortBy.NAME_DESC
            }
            R.id.repo_list_sort_by_time -> {
                repo_list_refresher.isRefreshing = true
                repo_list_rv.smoothScrollToPosition(0)

                // Reload the list
                model.sortOrder = RepoSortBy.LAST_BUILD_TIME_DESC
            }
        }
        return false
    }

    companion object {

        internal fun getInstance(): RepoListFragment {
            return RepoListFragment().apply {
                retainInstance = true
                setHasOptionsMenu(true)
            }
        }
    }
}
