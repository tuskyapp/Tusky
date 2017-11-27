package com.keylesspalace.tusky.adapter;

import android.content.Context;
import android.os.Build;
import android.support.annotation.Nullable;
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

import com.keylesspalace.tusky.R;
import com.keylesspalace.tusky.entity.Card;
import com.keylesspalace.tusky.entity.Status;
import com.keylesspalace.tusky.interfaces.StatusActionListener;
import com.keylesspalace.tusky.util.CustomURLSpan;
import com.keylesspalace.tusky.util.LinkHelper;
import com.keylesspalace.tusky.viewdata.StatusViewData;
import com.squareup.picasso.Picasso;

import java.text.DateFormat;
import java.util.Date;

class StatusDetailedViewHolder extends StatusBaseViewHolder {
    private TextView reblogs;
    private TextView favourites;
    private LinearLayout cardView;
    private LinearLayout cardInfo;
    private ImageView cardImage;
    private TextView cardTitle;
    private TextView cardDescription;
    private TextView cardUrl;

    StatusDetailedViewHolder(View view) {
        super(view);
        reblogs = view.findViewById(R.id.status_reblogs);
        favourites = view.findViewById(R.id.status_favourites);
        cardView = view.findViewById(R.id.card_view);
        cardInfo = view.findViewById(R.id.card_info);
        cardImage = view.findViewById(R.id.card_image);
        cardTitle = view.findViewById(R.id.card_title);
        cardDescription = view.findViewById(R.id.card_description);
        cardUrl = view.findViewById(R.id.card_link);
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

    private void setApplication(@Nullable Status.Application app) {
        if (app != null) {

            timestampInfo.append("  •  ");

            if (app.website != null) {
                URLSpan span = new CustomURLSpan(app.website);

                SpannableStringBuilder text = new SpannableStringBuilder(app.name);
                text.setSpan(span, 0, app.name.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                timestampInfo.append(text);
                timestampInfo.setMovementMethod(LinkMovementMethod.getInstance());
            } else {
                timestampInfo.append(app.name);
            }
        }
    }

    @Override
    void setupWithStatus(final StatusViewData.Concrete status, final StatusActionListener listener,
                         boolean mediaPreviewEnabled) {
        super.setupWithStatus(status, listener, mediaPreviewEnabled);
        reblogs.setText(status.getReblogsCount());
        favourites.setText(status.getFavouritesCount());
        setApplication(status.getApplication());

        if(status.getAttachments().length == 0 && status.getCard() != null && !TextUtils.isEmpty(status.getCard().url)) {
            final Card card = status.getCard();
            cardView.setVisibility(View.VISIBLE);
            cardTitle.setText(card.title);
            cardDescription.setText(card.description);

            cardUrl.setText(card.url);

            if(card.width > 0 && card.height > 0 && !TextUtils.isEmpty(card.image)) {
                cardImage.setVisibility(View.VISIBLE);

                if(card.width > card.height) {
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cardView.setClipToOutline(true);
                }

                Picasso.with(cardImage.getContext())
                        .load(card.image)
                        .fit()
                        .centerCrop()
                        .into(cardImage);

            } else {
                cardImage.setVisibility(View.GONE);
            }

            cardView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    LinkHelper.openLink(card.url, v.getContext());

                }

            });

        } else {
            cardView.setVisibility(View.GONE);
        }


    }
}
