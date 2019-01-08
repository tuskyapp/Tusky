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

package tech.bigfig.roma.util;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class IOUtils {

    private static final int DEFAULT_BLOCKSIZE = 16384;

    public static void closeQuietly(@Nullable Closeable stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            // intentionally unhandled
        }
    }

    public static boolean copyToFile(ContentResolver contentResolver, Uri uri, File file) {
        InputStream from;
        FileOutputStream to;
        try {
            from = contentResolver.openInputStream(uri);
            to = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            return false;
        }
        if (from == null) {
            return false;
        }
        byte[] chunk = new byte[DEFAULT_BLOCKSIZE];
        try {
            while (true) {
                int bytes = from.read(chunk, 0, chunk.length);
                if (bytes < 0) {
                    break;
                }
                to.write(chunk, 0, bytes);
            }
        } catch (IOException e) {
            return false;
        }
        closeQuietly(from);
        closeQuietly(to);
        return true;
    }
}
