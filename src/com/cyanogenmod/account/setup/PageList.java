/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.account.setup;

import java.util.ArrayList;

public class PageList extends ArrayList<Page> implements PageNode {

    public PageList(Page... pages) {
        for (Page page : pages) {
            add(page);
        }
    }

    @Override
    public Page findPage(String key) {
        for (Page childPage : this) {
            Page found = childPage.findPage(key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Override
    public Page findPage(int id) {
        for (Page childPage : this) {
            Page found = childPage.findPage(id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

}
