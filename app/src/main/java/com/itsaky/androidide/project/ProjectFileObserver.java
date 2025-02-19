/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.project;

import android.os.Build;
import android.os.FileObserver;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.util.List;

/**
 * A file observer that observer files in project directories.
 * Sub classes need to provide the file extensions that they want to get
 * notified about.
 *
 * @author Akash Yadav
 */
public abstract class ProjectFileObserver extends FileObserver {
    // Cannot use constructors that accept java.io.File
    // They were added in API 29
    public ProjectFileObserver (String path) {
        this (path, ALL_EVENTS);
    }
    
    public ProjectFileObserver (String path, int mask) {
        super (path, mask);
    }
}
