/* Written in 2017 by Andrew Dawson
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and
 * neighboring rights to this software to the public domain worldwide. This software is distributed
 * without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>. */

package com.keylesspalace.tusky.util;

import android.net.Uri;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one link and its parameters from the link header of an HTTP message.
 *
 * @see <a href="https://tools.ietf.org/html/rfc5988">RFC5988</a>
 */
public class HttpHeaderLink {
    private static class Parameter {
        public String name;
        public String value;
    }

    private List<Parameter> parameters;
    public Uri uri;

    private HttpHeaderLink(String uri) {
        this.uri = Uri.parse(uri);
        this.parameters = new ArrayList<>();
    }

    private static int findAny(String s, int fromIndex, char[] set) {
        for (int i = fromIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            for (char member : set) {
                if (c == member) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findEndOfQuotedString(String line, int start) {
        for (int i = start; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                i += 1;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static class ValueResult {
        String value;
        int end;

        ValueResult() {
            end = -1;
        }

        void setValue(String value) {
            value = value.trim();
            if (!value.isEmpty()) {
                this.value = value;
            }
        }
    }

    private static ValueResult parseValue(String line, int start) {
        ValueResult result = new ValueResult();
        int foundIndex = findAny(line, start, new char[] {';', ',', '"'});
        if (foundIndex == -1) {
            result.setValue(line.substring(start));
            return result;
        }
        char c = line.charAt(foundIndex);
        if (c == ';' || c == ',') {
            result.end = foundIndex;
            result.setValue(line.substring(start, foundIndex));
            return result;
        } else {
            int quoteEnd = findEndOfQuotedString(line, foundIndex + 1);
            if (quoteEnd == -1) {
                quoteEnd = line.length();
            }
            result.end = quoteEnd;
            result.setValue(line.substring(foundIndex + 1, quoteEnd));
            return result;
        }
    }

    private static int parseParameters(String line, int start, HttpHeaderLink link) {
        for (int i = start; i < line.length(); i++) {
            int foundIndex = findAny(line, i, new char[] {'=', ','});
            if (foundIndex == -1) {
                return -1;
            } else if (line.charAt(foundIndex) == ',') {
                return foundIndex;
            }
            Parameter parameter = new Parameter();
            parameter.name = line.substring(line.indexOf(';', i) + 1, foundIndex).trim();
            link.parameters.add(parameter);
            ValueResult result = parseValue(line, foundIndex);
            parameter.value = result.value;
            if (result.end == -1) {
                return -1;
            } else {
                i = result.end;
            }
        }
        return -1;
    }

    /**
     * @param line the entire link header, not including the initial "Link:"
     * @return all links found in the header
     */
    public static List<HttpHeaderLink> parse(@Nullable String line) {
        List<HttpHeaderLink> linkList = new ArrayList<>();
        if (line != null) {
            for (int i = 0; i < line.length(); i++) {
                int uriEnd = line.indexOf('>', i);
                String uri = line.substring(line.indexOf('<', i) + 1, uriEnd);
                HttpHeaderLink link = new HttpHeaderLink(uri);
                linkList.add(link);
                int parseEnd = parseParameters(line, uriEnd, link);
                if (parseEnd == -1) {
                    break;
                } else {
                    i = parseEnd;
                }
            }
        }
        return linkList;
    }

    /**
     * @param links intended to be those returned by parse()
     * @param relationType of the parameter "rel", commonly "next" or "prev"
     * @return the link matching the given relation type
     */
    @Nullable
    public static HttpHeaderLink findByRelationType(List<HttpHeaderLink> links,
            String relationType) {
        for (HttpHeaderLink link : links) {
            for (Parameter parameter : link.parameters) {
                if (parameter.name.equals("rel") && parameter.value.equals(relationType)) {
                    return link;
                }
            }
        }
        return null;
    }
}
