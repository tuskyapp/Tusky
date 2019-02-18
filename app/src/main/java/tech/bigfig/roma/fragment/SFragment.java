/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import tech.bigfig.roma.BaseActivity;
import tech.bigfig.roma.BottomSheetActivity;
import tech.bigfig.roma.ComposeActivity;
import tech.bigfig.roma.MainActivity;
import tech.bigfig.roma.R;
import tech.bigfig.roma.ReportActivity;
import tech.bigfig.roma.ViewMediaActivity;
import tech.bigfig.roma.ViewTagActivity;
import tech.bigfig.roma.db.AccountEntity;
import tech.bigfig.roma.db.AccountManager;
import tech.bigfig.roma.entity.Attachment;
import tech.bigfig.roma.entity.Status;
import tech.bigfig.roma.network.MastodonApi;
import tech.bigfig.roma.network.TimelineCases;
import tech.bigfig.roma.util.HtmlUtils;
import tech.bigfig.roma.viewdata.AttachmentViewData;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.view.ViewCompat;

/* Note from Andrew on Jan. 22, 2017: This class is a design problem for me, so I left it with an
 * awkward name. TimelineFragment and NotificationFragment have significant overlap but the nature
 * of that is complicated by how they're coupled with Status and Notification and the corresponding
 * adapters. I feel like the profile pages and thread viewer, which I haven't made yet, will also
 * overlap functionality. So, I'm momentarily leaving it and hopefully working on those will clear
 * up what needs to be where. */
public abstract class SFragment extends BaseFragment {

    protected abstract TimelineCases timelineCases();

    protected abstract void removeItem(int position);

    protected abstract void onReblog(final boolean reblog, final int position);

    private BottomSheetActivity bottomSheetActivity;

    @Inject
    public MastodonApi mastodonApi;
    @Inject
    public AccountManager accountManager;

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        getActivity().overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof BottomSheetActivity) {
            bottomSheetActivity = (BottomSheetActivity) context;
        } else {
            throw new IllegalStateException("Fragment must be attached to a BottomSheetActivity!");
        }
    }

    protected void openReblog(@Nullable final Status status) {
        if (status == null) return;
        bottomSheetActivity.viewAccount(status.getAccount().getId());
    }

    protected void viewThread(Status status) {
        Status actionableStatus = status.getActionableStatus();
        bottomSheetActivity.viewThread(actionableStatus.getId(), actionableStatus.getUrl());
    }

    protected void viewAccount(String accountId) {
        bottomSheetActivity.viewAccount(accountId);
    }

    public void onViewUrl(String url) {
        bottomSheetActivity.viewUrl(url);
    }

    protected void reply(Status status) {
        String inReplyToId = status.getActionableId();
        Status actionableStatus = status.getActionableStatus();
        Status.Visibility replyVisibility = actionableStatus.getVisibility();
        String contentWarning = actionableStatus.getSpoilerText();
        Status.Mention[] mentions = actionableStatus.getMentions();
        Set<String> mentionedUsernames = new LinkedHashSet<>();
        mentionedUsernames.add(actionableStatus.getAccount().getUsername());
        String loggedInUsername = null;
        AccountEntity activeAccount = accountManager.getActiveAccount();
        if(activeAccount != null) {
            loggedInUsername = activeAccount.getUsername();
        }
        for (Status.Mention mention : mentions) {
            mentionedUsernames.add(mention.getUsername());
        }
        mentionedUsernames.remove(loggedInUsername);
        Intent intent = new ComposeActivity.IntentBuilder()
                .inReplyToId(inReplyToId)
                .replyVisibility(replyVisibility)
                .contentWarning(contentWarning)
                .mentionedUsernames(mentionedUsernames)
                .replyingStatusAuthor(actionableStatus.getAccount().getLocalUsername())
                .replyingStatusContent(actionableStatus.getContent().toString())
                .build(getContext());
        getActivity().startActivity(intent);
    }

    protected void more(@NonNull final Status status, View view, final int position) {
        final String id = status.getActionableId();
        final String accountId = status.getActionableStatus().getAccount().getId();
        final String accountUsername = status.getActionableStatus().getAccount().getUsername();
        final Spanned content = status.getActionableStatus().getContent();
        final String statusUrl = status.getActionableStatus().getUrl();
        List<AccountEntity> accounts = accountManager.getAllAccountsOrderedByActive();
        String openAsTitle = null;

        String loggedInAccountId = null;
        AccountEntity activeAccount = accountManager.getActiveAccount();
        if(activeAccount != null) {
            loggedInAccountId = activeAccount.getAccountId();
        }

        PopupMenu popup = new PopupMenu(getContext(), view);
        // Give a different menu depending on whether this is the user's own toot or not.
        if (loggedInAccountId == null || !loggedInAccountId.equals(accountId)) {
            popup.inflate(R.menu.status_more);
        } else {
            popup.inflate(R.menu.status_more_for_user);
            Menu menu = popup.getMenu();
            switch (status.getVisibility()) {
                case PUBLIC:
                case UNLISTED: {
                    final String textId =
                            getString(status.isPinned() ? R.string.unpin_action : R.string.pin_action);
                    menu.add(0, R.id.pin, 1, textId);
                    break;
                }
                case PRIVATE: {
                    boolean reblogged = status.getReblogged();
                    if (status.getReblog() != null) reblogged = status.getReblog().getReblogged();
                    menu.findItem(R.id.status_reblog_private).setVisible(!reblogged);
                    menu.findItem(R.id.status_unreblog_private).setVisible(reblogged);
                    break;
                }
            }
        }

        Menu menu = popup.getMenu();
        MenuItem openAsItem = menu.findItem(R.id.status_open_as);
        switch(accounts.size()) {
            case 0:
            case 1:
                openAsItem.setVisible(false);
                break;
            case 2:
                for (AccountEntity account : accounts) {
                    if (account != activeAccount) {
                        openAsTitle = String.format(getString(R.string.action_open_as), account.getFullName());
                        break;
                    }
                }
                break;
            default:
                openAsTitle = String.format(getString(R.string.action_open_as), "…");
                break;
        }
        openAsItem.setTitle(openAsTitle);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.status_share_content: {
                    Status statusToShare = status;
                    if(statusToShare.getReblog() != null) statusToShare = statusToShare.getReblog();

                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);

                    String stringToShare = statusToShare.getAccount().getUsername() +
                            " - " +
                            statusToShare.getContent().toString();
                    sendIntent.putExtra(Intent.EXTRA_TEXT, stringToShare);
                    sendIntent.setType("text/plain");
                    startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_status_content_to)));
                    return true;
                }
                case R.id.status_share_link: {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, statusUrl);
                    sendIntent.setType("text/plain");
                    startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_status_link_to)));
                    return true;
                }
                case R.id.status_copy_link: {
                    ClipboardManager clipboard = (ClipboardManager)
                            getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText(null, statusUrl);
                    clipboard.setPrimaryClip(clip);
                    return true;
                }
                case R.id.status_open_as: {
                    showOpenAsDialog(statusUrl, item.getTitle());
                    return true;
                }
                case R.id.status_mute: {
                    timelineCases().mute(accountId);
                    return true;
                }
                case R.id.status_block: {
                    timelineCases().block(accountId);
                    return true;
                }
                case R.id.status_report: {
                    openReportPage(accountId, accountUsername, id, content);
                    return true;
                }
                case R.id.status_unreblog_private: {
                    onReblog(false, position);
                    return true;
                }
                case R.id.status_reblog_private: {
                    onReblog(true, position);
                    return true;
                }
                case R.id.status_delete: {
                    showConfirmDeleteDialog(id, position);
                    return true;
                }
                case R.id.pin: {
                    timelineCases().pin(status, !status.isPinned());
                    return true;
                }
            }
            return false;
        });
        popup.show();
    }

    protected void viewMedia(int urlIndex, Status status, @Nullable View view) {
        final Status actionable = status.getActionableStatus();
        final Attachment active = actionable.getAttachments().get(urlIndex);
        Attachment.Type type = active.getType();
        switch (type) {
            case GIFV:
            case VIDEO:
            case IMAGE: {
                final List<AttachmentViewData> attachments = AttachmentViewData.list(actionable);
                final Intent intent = ViewMediaActivity.newIntent(getContext(), attachments,
                        urlIndex);
                if (view != null) {
                    String url = active.getUrl();
                    ViewCompat.setTransitionName(view, url);
                    ActivityOptionsCompat options =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                                    view, url);
                    startActivity(intent, options.toBundle());
                } else {
                    startActivity(intent);
                }
                break;
            }
            case UNKNOWN: {
                /* Intentionally do nothing. This case is here is to handle when new attachment
                 * types are added to the API before code is added here to handle them. So, the
                 * best fallback is to just show the preview and ignore requests to view them. */
                break;
            }
        }
    }

    protected void viewTag(String tag) {
        Intent intent = new Intent(getContext(), ViewTagActivity.class);
        intent.putExtra("hashtag", tag);
        startActivity(intent);
    }

    protected void openReportPage(String accountId, String accountUsername, String statusId,
                                  Spanned statusContent) {
        Intent intent = new Intent(getContext(), ReportActivity.class);
        intent.putExtra("account_id", accountId);
        intent.putExtra("account_username", accountUsername);
        intent.putExtra("status_id", statusId);
        intent.putExtra("status_content", HtmlUtils.toHtml(statusContent));
        startActivity(intent);
    }

    protected void showConfirmDeleteDialog(final String id, final int position) {
        new AlertDialog.Builder(getActivity())
                .setMessage(R.string.dialog_delete_toot_warning)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    timelineCases().delete(id);
                    removeItem(position);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openAsAccount(String statusUrl, AccountEntity account) {
        accountManager.setActiveAccount(account);
        Intent intent = new Intent(getContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(MainActivity.STATUS_URL, statusUrl);
        startActivity(intent);
        ((BaseActivity)getActivity()).finishWithoutSlideOutAnimation();
    }

    private void showOpenAsDialog(String statusUrl, CharSequence dialogTitle) {
        BaseActivity activity = (BaseActivity)getActivity();
        activity.showAccountChooserDialog(dialogTitle, false, account -> openAsAccount(statusUrl, account));
    }
}
