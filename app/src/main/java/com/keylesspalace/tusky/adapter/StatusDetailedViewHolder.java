package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CardViewMode;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StatusDetailedViewHolder extends StatusBaseViewHolder {
    private final TextView reblogs;
    private final TextView favourites;
    private final View infoDivider;

    public StatusDetailedViewHolder(View view) {
        super(view);
        reblogs = view.findViewById(R.id.status_reblogs);
        favourites = view.findViewById(R.id.status_favourites);
        infoDivider = view.findViewById(R.id.status_info_divider);
    }

    @Override
    protected void setCreatedAt(Date createdAt, Date editedAt, StatusDisplayOptions statusDisplayOptions) {
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT);
        Context context = timestampInfo.getContext();
        List<String> list = new ArrayList<>();
        if (createdAt != null) {
            list.add(dateFormat.format(createdAt));
        }
        if (editedAt != null) {
            list.add(context.getString(R.string.post_edited, dateFormat.format(editedAt)));
        }
        timestampInfo.setText(TextUtils.join(context.getString(R.string.timestamp_joiner), list));
    }

    private void setReblogAndFavCount(int reblogCount, int favCount, StatusActionListener listener) {

        if (reblogCount > 0) {
            reblogs.setText(getReblogsText(reblogs.getContext(), reblogCount));
            reblogs.setVisibility(View.VISIBLE);
        } else {
            reblogs.setVisibility(View.GONE);
        }
        if (favCount > 0) {
            favourites.setText(getFavsText(favourites.getContext(), favCount));
            favourites.setVisibility(View.VISIBLE);
        } else {
            favourites.setVisibility(View.GONE);
        }

        if (reblogs.getVisibility() == View.GONE && favourites.getVisibility() == View.GONE) {
            infoDivider.setVisibility(View.GONE);
        } else {
            infoDivider.setVisibility(View.VISIBLE);
        }

        reblogs.setOnClickListener(v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onShowReblogs(position);
            }
        });
        favourites.setOnClickListener(v -> {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onShowFavs(position);
            }
        });
    }

    private void setApplication(@Nullable Status.Application app) {
        if (app != null) {

            timestampInfo.append("  •  ");

            if (app.getWebsite() != null) {
                CharSequence text = LinkHelper.createClickableText(app.getName(), app.getWebsite());
                timestampInfo.append(text);
                timestampInfo.setMovementMethod(LinkMovementMethod.getInstance());
            } else {
                timestampInfo.append(app.getName());
            }
        }
    }

    @Override
    public void setupWithStatus(@NonNull final StatusViewData.Concrete status,
                                @NonNull final StatusActionListener listener,
                                @NonNull StatusDisplayOptions statusDisplayOptions,
                                @Nullable Object payloads) {
        // We never collapse statuses in the detail view
        StatusViewData.Concrete uncollapsedStatus = (status.isCollapsible() && status.isCollapsed()) ?
            status.copyWithCollapsed(false) :
            status;

        super.setupWithStatus(uncollapsedStatus, listener, statusDisplayOptions, payloads);
        setupCard(uncollapsedStatus, CardViewMode.FULL_WIDTH, statusDisplayOptions, listener); // Always show card for detailed status
        if (payloads == null) {
            Status actionable = uncollapsedStatus.getActionable();

            if (!statusDisplayOptions.hideStats()) {
                setReblogAndFavCount(actionable.getReblogsCount(),
                        actionable.getFavouritesCount(), listener);
            } else {
                hideQuantitativeStats();
            }

            setApplication(actionable.getApplication());

            setStatusVisibility(actionable.getVisibility());
        }
    }

    private void setStatusVisibility(Status.Visibility visibility) {

        if (visibility == null) {
            return;
        }

        int visibilityIcon;
        switch (visibility) {
            case PUBLIC:
                visibilityIcon = R.drawable.ic_public_24dp;
                break;
            case UNLISTED:
                visibilityIcon = R.drawable.ic_lock_open_24dp;
                break;
            case PRIVATE:
                visibilityIcon = R.drawable.ic_lock_outline_24dp;
                break;
            case DIRECT:
                visibilityIcon = R.drawable.ic_email_24dp;
                break;
            default:
                return;
        }

        final Drawable visibilityDrawable = this.timestampInfo.getContext()
                .getDrawable(visibilityIcon);
        if (visibilityDrawable == null) {
            return;
        }

        final int size = (int) this.timestampInfo.getTextSize();
        visibilityDrawable.setBounds(
                0,
                0,
                size,
                size
        );
        visibilityDrawable.setTint(this.timestampInfo.getCurrentTextColor());
        this.timestampInfo.setCompoundDrawables(
                visibilityDrawable,
                null,
                null,
                null
        );
    }

    private void hideQuantitativeStats() {
        reblogs.setVisibility(View.GONE);
        favourites.setVisibility(View.GONE);
        infoDivider.setVisibility(View.GONE);
    }
}
