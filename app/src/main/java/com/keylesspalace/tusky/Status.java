package com.keylesspalace.tusky;

import android.os.Build;
import android.text.Html;
import android.text.Spanned;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Status {
    enum Visibility {
        PUBLIC,
        UNLISTED,
        PRIVATE,
    }

    private String id;
    private String accountId;
    private String displayName;
    /** the username with the remote domain appended, like @domain.name, if it's a remote account */
    private String username;
    /** the main text of the status, marked up with style for links & mentions, etc */
    private Spanned content;
    /** the fully-qualified url of the avatar image */
    private String avatar;
    private String rebloggedByUsername;
    /** when the status was initially created */
    private Date createdAt;
    /** whether the authenticated user has reblogged this status */
    private boolean reblogged;
    /** whether the authenticated user has favourited this status */
    private boolean favourited;
    private Visibility visibility;

    public Status(String id, String accountId, String displayName, String username, Spanned content,
                  String avatar, Date createdAt, boolean reblogged, boolean favourited,
                  String visibility) {
        this.id = id;
        this.accountId = accountId;
        this.displayName = displayName;
        this.username = username;
        this.content = content;
        this.avatar = avatar;
        this.createdAt = createdAt;
        this.reblogged = reblogged;
        this.favourited = favourited;
        this.visibility = Visibility.valueOf(visibility.toUpperCase());
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUsername() {
        return username;
    }

    public Spanned getContent() {
        return content;
    }

    public String getAvatar() {
        return avatar;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public String getRebloggedByUsername() {
        return rebloggedByUsername;
    }

    public boolean getReblogged() {
        return reblogged;
    }

    public boolean getFavourited() {
        return favourited;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setRebloggedByUsername(String name) {
        rebloggedByUsername = name;
    }

    public void setReblogged(boolean reblogged) {
        this.reblogged = reblogged;
    }

    public void setFavourited(boolean favourited) {
        this.favourited = favourited;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this.id == null) {
            return this == other;
        } else if (!(other instanceof Status)) {
            return false;
        }
        Status status = (Status) other;
        return status.id.equals(this.id);
    }

    private static Date parseDate(String dateTime) {
        Date date;
        String s = dateTime.replace("Z", "+00:00");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        try {
            date = format.parse(s);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        return date;
    }

    private static CharSequence trimTrailingWhitespace(CharSequence s) {
        int i = s.length();
        do {
            i--;
        } while (i >= 0 && Character.isWhitespace(s.charAt(i)));
        return s.subSequence(0, i + 1);
    }

    private static Spanned compatFromHtml(String html) {
        Spanned result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            result = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            result = Html.fromHtml(html);
        }
        /* Html.fromHtml returns trailing whitespace if the html ends in a </p> tag, which
         * all status contents do, so it should be trimmed. */
        return (Spanned) trimTrailingWhitespace(result);
    }

    public static Status parse(JSONObject object, boolean isReblog) throws JSONException {
        String id = object.getString("id");
        String content = object.getString("content");
        Date createdAt = parseDate(object.getString("created_at"));
        boolean reblogged = object.getBoolean("reblogged");
        boolean favourited = object.getBoolean("favourited");
        String visibility = object.getString("visibility");

        JSONObject account = object.getJSONObject("account");
        String accountId = account.getString("id");
        String displayName = account.getString("display_name");
        String username = account.getString("acct");
        String avatar = account.getString("avatar");

        Status reblog = null;
        /* This case shouldn't be hit after the first recursion at all. But if this method is
         * passed unusual data this check will prevent extra recursion */
        if (!isReblog) {
            JSONObject reblogObject = object.optJSONObject("reblog");
            if (reblogObject != null) {
                reblog = parse(reblogObject, true);
            }
        }

        Status status;
        if (reblog != null) {
            status = reblog;
            status.setRebloggedByUsername(username);
        } else {
            Spanned contentPlus = compatFromHtml(content);
            status = new Status(
                    id, accountId, displayName, username, contentPlus, avatar, createdAt,
                    reblogged, favourited, visibility);
        }
        return status;
    }

    public static List<Status> parse(JSONArray array) throws JSONException {
        List<Status> statuses = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            statuses.add(parse(object, false));
        }
        return statuses;
    }
}
