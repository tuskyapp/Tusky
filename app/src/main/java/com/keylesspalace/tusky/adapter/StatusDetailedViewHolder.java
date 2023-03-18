package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CardViewMode;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.NoUnderlineURLSpan;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.text.DateFormat;
import java.util.Date;

public class StatusDetailedViewHolder extends StatusBaseViewHolder {
    private final TextView reblogs;
    private final TextView favourites;
    private final View infoDivider;

    private static final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT);

    public StatusDetailedViewHolder(View view) {
        super(view);
        reblogs = view.findViewById(R.id.status_reblogs);
        favourites = view.findViewById(R.id.status_favourites);
        infoDivider = view.findViewById(R.id.status_info_divider);
    }

    @Override
    protected void setMetaData(StatusViewData.Concrete statusViewData, StatusDisplayOptions statusDisplayOptions, StatusActionListener listener) {

        Status status = statusViewData.getActionable();

        Status.Visibility visibility = status.getVisibility();
        Context context = metaInfo.getContext();

        Drawable visibilityIcon = getVisibilityIcon(visibility);
        CharSequence visibilityString = getVisibilityDescription(context, visibility);

        SpannableStringBuilder sb = new SpannableStringBuilder(visibilityString);

        if (visibilityIcon != null) {
            ImageSpan visibilityIconSpan = new ImageSpan(
                    visibilityIcon,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? DynamicDrawableSpan.ALIGN_CENTER : DynamicDrawableSpan.ALIGN_BASELINE
            );
            sb.setSpan(visibilityIconSpan, 0, visibilityString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        String metadataJoiner = context.getString(R.string.metadata_joiner);

        Date createdAt = status.getCreatedAt();
        if (createdAt != null) {

            sb.append(" ");
            sb.append(dateFormat.format(createdAt));
        }

        Date editedAt = status.getEditedAt();

        if (editedAt != null) {
            String editedAtString = context.getString(R.string.post_edited, dateFormat.format(editedAt));

            sb.append(metadataJoiner);
            int spanStart = sb.length();
            int spanEnd = spanStart + editedAtString.length();

            sb.append(editedAtString);

            if (statusViewData.getStatus().getEditedAt() != null) {
                NoUnderlineURLSpan editedClickSpan = new NoUnderlineURLSpan("") {
                    @Override
                    public void onClick(@NonNull View view) {
                        listener.onShowEdits(getBindingAdapterPosition());
                    }
                };

                sb.setSpan(editedClickSpan, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        Status.Application app = status.getApplication();

        if (app != null) {

            sb.append(metadataJoiner);

            if (app.getWebsite() != null) {
                CharSequence text = LinkHelper.createClickableText(app.getName(), app.getWebsite());
                sb.append(text);
            } else {
                sb.append(app.getName());
            }
        }

        metaInfo.setMovementMethod(LinkMovementMethod.getInstance());
        metaInfo.setText(sb);
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
        setupCard(uncollapsedStatus, status.isExpanded(), CardViewMode.FULL_WIDTH, statusDisplayOptions, listener); // Always show card for detailed status
        if (payloads == null) {
            Status actionable = uncollapsedStatus.getActionable();

            if (!statusDisplayOptions.hideStats()) {
                setReblogAndFavCount(actionable.getReblogsCount(),
                        actionable.getFavouritesCount(), listener);
            } else {
                hideQuantitativeStats();
            }
        }
    }

    private @Nullable Drawable getVisibilityIcon(@Nullable Status.Visibility visibility) {

        if (visibility == null) {
            return null;
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
                return null;
        }

        final Drawable visibilityDrawable = AppCompatResources.getDrawable(
                this.metaInfo.getContext(), visibilityIcon
        );
        if (visibilityDrawable == null) {
            return null;
        }

        final int size = (int) this.metaInfo.getTextSize();
        visibilityDrawable.setBounds(
                0,
                0,
                size,
                size
        );
        visibilityDrawable.setTint(this.metaInfo.getCurrentTextColor());

        return visibilityDrawable;
    }

    private void hideQuantitativeStats() {
        reblogs.setVisibility(View.GONE);
        favourites.setVisibility(View.GONE);
        infoDivider.setVisibility(View.GONE);
    }
}
