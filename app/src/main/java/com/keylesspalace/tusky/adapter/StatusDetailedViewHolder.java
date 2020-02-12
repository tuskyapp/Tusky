package com.keylesspalace.tusky.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.util.StatusDisplayOptions;
import com.keylesspalace.tusky.viewdata.StatusViewData;

import java.text.DateFormat;
import java.util.Date;

class StatusDetailedViewHolder extends StatusBaseViewHolder {
    private TextView reblogs;
    private TextView favourites;
    private View infoDivider;

    StatusDetailedViewHolder(View view) {
        super(view);
        reblogs = view.findViewById(R.id.status_reblogs);
        favourites = view.findViewById(R.id.status_favourites);
        infoDivider = view.findViewById(R.id.status_info_divider);
    }

    @Override
    protected int getMediaPreviewHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.status_detail_media_preview_height);
    }

    @Override
    protected void setCreatedAt(Date createdAt, StatusDisplayOptions statusDisplayOptions) {
        if (createdAt == null) {
            timestampInfo.setText("");
        } else {
            DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT);
            timestampInfo.setText(dateFormat.format(createdAt));
        }
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
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                listener.onShowReblogs(position);
            }
        });
        favourites.setOnClickListener(v -> {
            int position = getAdapterPosition();
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
    protected void setupWithStatus(final StatusViewData.Concrete status,
                                   final StatusActionListener listener,
                                   StatusDisplayOptions statusDisplayOptions,
                                   @Nullable Object payloads) {
        super.setupWithStatus(status, listener, statusDisplayOptions, payloads);
        if (payloads == null) {
            setReblogAndFavCount(status.getReblogsCount(), status.getFavouritesCount(), listener);

            setApplication(status.getApplication());

            View.OnLongClickListener longClickListener = view -> {
                TextView textView = (TextView) view;
                ClipboardManager clipboard = (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("toot", textView.getText());
                clipboard.setPrimaryClip(clip);

                Toast.makeText(view.getContext(), R.string.copy_to_clipboard_success, Toast.LENGTH_SHORT).show();

                return true;
            };

            content.setOnLongClickListener(longClickListener);
            contentWarningDescription.setOnLongClickListener(longClickListener);
            setStatusVisibility(status.getVisibility());
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
}
