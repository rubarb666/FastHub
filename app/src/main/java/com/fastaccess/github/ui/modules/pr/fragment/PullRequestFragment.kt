package com.fastaccess.github.ui.modules.pr.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.fastaccess.data.model.CommentModel
import com.fastaccess.data.model.TimelineModel
import com.fastaccess.data.persistence.models.LoginModel
import com.fastaccess.data.persistence.models.PullRequestModel
import com.fastaccess.data.storage.FastHubSharedPreference
import com.fastaccess.github.R
import com.fastaccess.github.base.BaseViewModel
import com.fastaccess.github.extensions.isTrue
import com.fastaccess.github.extensions.observeNotNull
import com.fastaccess.github.extensions.routeForResult
import com.fastaccess.github.extensions.timeAgo
import com.fastaccess.github.ui.adapter.IssueTimelineAdapter
import com.fastaccess.github.ui.modules.issuesprs.BaseIssuePrTimelineFragment
import com.fastaccess.github.ui.modules.pr.fragment.viewmodel.PullRequestTimelineViewModel
import com.fastaccess.github.utils.EDITOR_DEEPLINK
import com.fastaccess.github.utils.EXTRA
import com.fastaccess.github.utils.EXTRA_THREE
import com.fastaccess.github.utils.EXTRA_TWO
import com.fastaccess.github.utils.extensions.hideKeyboard
import com.fastaccess.github.utils.extensions.theme
import com.fastaccess.markdown.widget.SpannableBuilder
import github.type.CommentAuthorAssociation
import github.type.LockReason
import github.type.PullRequestState
import io.noties.markwon.Markwon
import io.noties.markwon.utils.NoCopySpannableFactory
import kotlinx.android.synthetic.main.comment_box_layout.*
import kotlinx.android.synthetic.main.issue_header_row_item.*
import kotlinx.android.synthetic.main.issue_pr_view_layout.*
import kotlinx.android.synthetic.main.recyclerview_fastscroll_empty_state_layout.*
import kotlinx.android.synthetic.main.title_toolbar_layout.*
import javax.inject.Inject

/**
 * Created by Kosh on 28.01.19.
 */
class PullRequestFragment : BaseIssuePrTimelineFragment() {

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    @Inject lateinit var markwon: Markwon
    @Inject lateinit var preference: FastHubSharedPreference

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory).get(PullRequestTimelineViewModel::class.java) }

    override val adapter by lazy {
        IssueTimelineAdapter(markwon, preference.theme, onCommentClicked(), onDeleteCommentClicked(), onEditCommentClicked())
    }

    override fun layoutRes(): Int = R.layout.issue_pr_fragment_layout
    override fun viewModel(): BaseViewModel? = viewModel
    override fun isPr(): Boolean = true
    override fun lockIssuePr(lockReason: LockReason?) = viewModel.lockUnlockIssue(login, repo, number, lockReason, true)
    override fun onMilestoneAdd(timeline: TimelineModel) = viewModel.addTimeline(timeline)
    override fun reload(refresh: Boolean) = viewModel.loadData(login, repo, number, refresh)
    override fun sendComment(comment: String) = viewModel.createComment(login, repo, number, comment)

    override fun onFragmentCreatedWithUser(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onFragmentCreatedWithUser(view, savedInstanceState)
        observeChanges()
    }

    override fun onDestroyView() {
        mentionsPresenter.onDispose()
        super.onDestroyView()
    }

    override fun editIssuerPr(
        title: String?,
        description: String?
    ) = viewModel.editIssue(login, repo, number, title, description)

    override fun lockUnlockIssuePr() = viewModel.lockUnlockIssue(login, repo, number)
    override fun closeOpenIssuePr() = viewModel.closeOpenIssue(login, repo, number)

    private fun observeChanges() {
        viewModel.getPullRequest(login, repo, number).observeNotNull(this) {
            initIssue(it.first, it.second)
        }
        viewModel.timeline.observeNotNull(this) { timeline ->
            adapter.submitList(timeline)
        }
        viewModel.userNamesLiveData.observeNotNull(this) {
            mentionsPresenter.setUsers(it)
        }
        viewModel.commentProgress.observeNotNull(this) {
            commentProgress.isVisible = it
            sendComment.isVisible = !it
            if (!it) {
                commentText.setText("")
                commentText.hideKeyboard()
                recyclerView.scrollToPosition(adapter.itemCount)
            }
        }
        viewModel.forceAdapterUpdate.observeNotNull(this) {
            it.isTrue { adapter.notifyDataSetChanged() }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun initIssue(
        model: PullRequestModel,
        me: LoginModel?
    ) {
        issueHeaderWrapper.isVisible = true
        val theme = preference.theme
        title.text = model.title
        toolbar.title = SpannableBuilder.builder()
            .append(getString(R.string.pull_request))
            .bold("#${model.number}")

        opener.text = if (model.merged == true) {
            SpannableBuilder.builder()
                .bold(model.mergedBy?.login)
                .space()
                .append(getString(R.string.merged).toLowerCase())
                .space()
                .bold(model.headRefName)
                .space()
                .append(getString(R.string.to))
                .space()
                .bold(model.baseRefName)
                .space()
                .append(model.mergedAt?.timeAgo())
        } else {
            SpannableBuilder.builder()
                .bold(model.author?.login)
                .space()
                .append(getString(R.string.want_to_merge))
                .space()
                .bold(model.headRefName)
                .space()
                .append(getString(R.string.to))
                .space()
                .bold(model.baseRefName)
                .space()
                .append(model.createdAt?.timeAgo())
        }

        userIcon.loadAvatar(model.author?.avatarUrl, model.author?.url ?: "")
        author.text = model.author?.login
        association.text = if (CommentAuthorAssociation.NONE.rawValue() == model.authorAssociation) {
            model.updatedAt?.timeAgo()
        } else {
            "${model.authorAssociation?.toLowerCase()?.replace("_", "")} ${model.updatedAt?.timeAgo()}"
        }

        description.post {
            val bodyMd = model.body
//            description.setMovementMethod(LinkMovementMethod.getInstance())
            description.setSpannableFactory(NoCopySpannableFactory.getInstance())
            markwon.setMarkdown(
                description, if (!bodyMd.isNullOrEmpty()) bodyMd else "**${getString(R.string.no_description_provided)}**"
            )
        }

        state.text = model.state?.toLowerCase()
        state.setChipBackgroundColorResource(
            if (PullRequestState.OPEN.rawValue().equals(model.state, true)) {
                R.color.material_green_500
            } else if (PullRequestState.CLOSED.rawValue().equals(model.state, true)) {
                R.color.material_red_500
            } else {
                R.color.material_blue_700
            }
        )

        adaptiveEmoticon.init(requireNotNull(model.id), model.reactionGroups) {
            adaptiveEmoticon.initReactions(model.reactionGroups)
        }
        val isAuthor = login == me?.login || model.authorAssociation?.equals(CommentAuthorAssociation.OWNER.rawValue(), true) == true ||
            model.authorAssociation?.equals(CommentAuthorAssociation.COLLABORATOR.rawValue(), true) == true
        menuClick(model.url, model.labels, model.assignees, model.title, model.body, isAuthor)
        initLabels(model.labels)
        initAssignees(model.assignees)
        initMilestone(model.milestone)
        initToolbarMenu(isAuthor, model.viewerCanUpdate == true, model.viewerDidAuthor, model.locked, state = model.state)
        recyclerView.removeEmptyView()
    }

    override fun onEditCommentClicked(): (position: Int, comment: CommentModel) -> Unit = { position, comment ->
        routeForResult(
            EDITOR_DEEPLINK, EDIT_COMMENT_REQUEST_CODE, bundleOf(
                EXTRA to comment.body,
                EXTRA_TWO to comment.databaseId
            )
        )
    }

    override fun onDeleteCommentClicked(): (position: Int, comment: CommentModel) -> Unit = { position, comment ->
        viewModel.deleteComment(login, repo, comment.databaseId?.toLong() ?: 0L)
    }

    override fun onEditComment(comment: String?, commentId: Int?) {
        viewModel.editComment(login, repo, comment, commentId?.toLong())
    }

    companion object {
        const val TAG = "IssueFragment"
        fun newInstance(
            login: String,
            repo: String,
            number: Int
        ) = PullRequestFragment().apply {
            arguments = bundleOf(EXTRA to login, EXTRA_TWO to repo, EXTRA_THREE to number)
        }
    }
}