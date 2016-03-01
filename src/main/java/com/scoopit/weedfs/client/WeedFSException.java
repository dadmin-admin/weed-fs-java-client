/*
 * (C) Copyright 2013 Scoop IT SAS (http://scoop.it/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Contributors:
 *     Philippe GASSMANN
 *     Jean-Baptiste BELLET
 */
package com.scoopit.weedfs.client;

import java.io.IOException;

public class WeedFSException extends IOException {

    private static final long serialVersionUID = 1L;

    private String body;

    public WeedFSException(String reason) {
        this(reason, (String) null);
    }

    public WeedFSException(String reason, String body) {
        super(reason);

        this.body = body;
    }

    public WeedFSException(Exception cause) {
        super(cause);
    }

    public WeedFSException(String reason, Exception cause) {
        super(reason, cause);
    }

    public String getBody() {
        return body;
    }
}
