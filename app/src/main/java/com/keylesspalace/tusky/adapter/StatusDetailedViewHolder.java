package com.keylesspalace.tusky.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Card;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CustomURLSpan;
import com.keylesspalace.tusky.util.HtmlUtils;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.viewdata.StatusViewData;
import com.squareup.picasso.Picasso;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;

import androidx.annotation.Nullable;

class StatusDetailedViewHolder extends StatusBaseViewHolder {
    private TextView reblogs;
    private TextView favourites;
    private LinearLayout cardView;
    private LinearLayout cardInfo;
    private ImageView cardImage;
    private TextView cardTitle;
    private TextView cardDescription;
    private TextView cardUrl;
    private View infoDivider;
    private View favReblogInfoContainer;

    StatusDetailedViewHolder(View view) {
        super(view, false);
        reblogs = view.findViewById(R.id.status_reblogs);
        favourites = view.findViewById(R.id.status_favourites);
        cardView = view.findViewById(R.id.card_view);
        cardInfo = view.findViewById(R.id.card_info);
        cardImage = view.findViewById(R.id.card_image);
        cardTitle = view.findViewById(R.id.card_title);
        cardDescription = view.findViewById(R.id.card_description);
        cardUrl = view.findViewById(R.id.card_link);
        infoDivider = view.findViewById(R.id.status_info_divider);
        favReblogInfoContainer = view.findViewById(R.id.status_reblog_fav_info);
    }

    @Override
    protected int getMediaPreviewHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.status_detail_media_preview_height);
    }

    @Override
    protected void setCreatedAt(@Nullable Date createdAt) {
        if (createdAt != null) {
            DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT);
            timestampInfo.setText(dateFormat.format(createdAt));
        } else {
            timestampInfo.setText("");
        }
    }

    private void setReblogAndFavCount(int reblogCount,  int favCount, String statusId) {

        NumberFormat numberFormat = NumberFormat.getNumberInstance();

        if(reblogCount > 0) {
            String reblogCountString = numberFormat.format(reblogCount);
            reblogs.setText(HtmlUtils.fromHtml(reblogs.getResources().getQuantityString(R.plurals.reblogs, reblogCount, reblogCountString)));
            reblogs.setVisibility(View.VISIBLE);
        } else {
            reblogs.setVisibility(View.GONE);
        }
        if(favCount > 0) {
            String favCountString = numberFormat.format(favCount);
            favourites.setText(HtmlUtils.fromHtml(favourites.getResources().getQuantityString(R.plurals.favs, favCount, favCountString)));
            favourites.setVisibility(View.VISIBLE);
        } else {
            favourites.setVisibility(View.GONE);
        }

        if(reblogs.getVisibility() == View.GONE && favourites.getVisibility() == View.GONE) {
            infoDivider.setVisibility(View.GONE);
            favReblogInfoContainer.setVisibility(View.GONE);
        } else {
            infoDivider.setVisibility(View.VISIBLE);
            favReblogInfoContainer.setVisibility(View.VISIBLE);
        }

        reblogs.setOnClickListener( v-> {

        });
        favourites.setOnClickListener( v-> {

        });
    }

    private void setApplication(@Nullable Status.Application app) {
        if (app != null) {

            timestampInfo.append("  •  ");

            if (app.getWebsite() != null) {
                URLSpan span = new CustomURLSpan(app.getWebsite());

                SpannableStringBuilder text = new SpannableStringBuilder(app.getName());
                text.setSpan(span, 0, app.getName().length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                timestampInfo.append(text);
                timestampInfo.setMovementMethod(LinkMovementMethod.getInstance());
            } else {
                timestampInfo.append(app.getName());
            }
        }
    }

    @Override
    void setupWithStatus(final StatusViewData.Concrete status, final StatusActionListener listener,
                         boolean mediaPreviewEnabled) {
        super.setupWithStatus(status, listener, mediaPreviewEnabled);

        setReblogAndFavCount(status.getReblogsCount(), status.getFavouritesCount(), status.getId());

        setApplication(status.getApplication());


        View.OnLongClickListener longClickListener = view -> {
            TextView textView = (TextView)view;
            ClipboardManager clipboard = (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("toot", textView.getText());
            clipboard.setPrimaryClip(clip);

            Toast.makeText(view.getContext(), R.string.copy_to_clipboard_success, Toast.LENGTH_SHORT).show();

            return true;
        };

        content.setOnLongClickListener(longClickListener);
        contentWarningDescription.setOnLongClickListener(longClickListener);

        if(status.getAttachments().size() == 0 && status.getCard() != null && !TextUtils.isEmpty(status.getCard().getUrl())) {
            final Card card = status.getCard();
            cardView.setVisibility(View.VISIBLE);
            cardTitle.setText(card.getTitle());
            cardDescription.setText(card.getDescription());

            cardUrl.setText(card.getUrl());

            if(card.getWidth() > 0 && card.getHeight() > 0 && !TextUtils.isEmpty(card.getImage())) {
                cardImage.setVisibility(View.VISIBLE);

                if(card.getWidth() > card.getHeight()) {
                    cardView.setOrientation(LinearLayout.VERTICAL);
                    cardImage.getLayoutParams().height = cardImage.getContext().getResources()
                            .getDimensionPixelSize(R.dimen.card_image_vertical_height);
                    cardImage.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                    cardInfo.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                    cardInfo.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                } else {
                    cardView.setOrientation(LinearLayout.HORIZONTAL);
                    cardImage.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                    cardImage.getLayoutParams().width = cardImage.getContext().getResources()
                            .getDimensionPixelSize(R.dimen.card_image_horizontal_width);
                    cardInfo.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    cardInfo.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                }

                cardView.setClipToOutline(true);

                Picasso.with(cardImage.getContext())
                        .load(card.getImage())
                        .fit()
                        .centerCrop()
                        .into(cardImage);

            } else {
                cardImage.setVisibility(View.GONE);
            }

            cardView.setOnClickListener(v -> LinkHelper.openLink(card.getUrl(), v.getContext()));

        } else {
            cardView.setVisibility(View.GONE);
        }

        setStatusVisibility(status.getVisibility());
    }

    private void setStatusVisibility(Status.Visibility visibility) {
        if (visibility == null || this.timestampInfo == null) {
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
