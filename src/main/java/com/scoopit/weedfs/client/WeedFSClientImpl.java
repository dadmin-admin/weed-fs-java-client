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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoopit.weedfs.client.caching.LookupCache;
import com.scoopit.weedfs.client.net.AssignResult;
import com.scoopit.weedfs.client.net.LookupResult;
import com.scoopit.weedfs.client.net.WriteResult;
import com.scoopit.weedfs.client.status.MasterStatus;
import com.scoopit.weedfs.client.status.VolumeStatus;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class WeedFSClientImpl implements WeedFSClient {

    final URL masterURL;
    final HttpClient httpClient;
    final LookupCache lookupCache;

    WeedFSClientImpl(URL masterURL, HttpClient httpClient, LookupCache lookupCache) {
        this.masterURL = masterURL;
        this.httpClient = httpClient;
        this.lookupCache = lookupCache;
    }

    @Override
    public Assignation assign(AssignParams params) throws IOException, WeedFSException {
        boolean hasUrlParam = false;
        StringBuilder url = new StringBuilder(new URL(masterURL, "/dir/assign").toExternalForm());
        Map<String, Object> urlParams = new HashMap<>();

        if (params.versionCount > 0) {
            urlParams.put("count", params.versionCount);
        }

        if (params.replicationStrategy != null) {
            urlParams.put("replication", params.replicationStrategy.parameterValue);
        }

        if (params.collection != null) {
            urlParams.put("collection", params.collection);
        }

        for (Map.Entry<String, Object> param : urlParams.entrySet()) {
            url.append((hasUrlParam) ? '&' : '?');
            url.append(param.getKey());
            url.append('=');
            url.append(param.getValue());

            hasUrlParam = true;
        }

        HttpGet get = new HttpGet(url.toString());
        try {
            HttpResponse response = httpClient.execute(get);

            ObjectMapper mapper = new ObjectMapper();
            String content = getContentOrNull(response);
            
            try {
                AssignResult result = mapper.readValue(content, AssignResult.class);

                if (result.error != null) {
                    throw new WeedFSException(result.error);
                }

                return new Assignation(result);
            } catch (JsonMappingException | JsonParseException e) {
                throw new WeedFSException("Unable to parse JSON from weed-fs from: " + content, e);
            }
        } finally {
            get.abort();
        }
    }

    @Override
    public void delete(WeedFSFile file, Location location) throws IOException, WeedFSException {
        StringBuilder url = new StringBuilder();
        if (!location.publicUrl.contains("http")) {
            url.append("http://");
        }
        url.append(location.publicUrl);
        url.append("/");
        url.append(file.fid);

        if (file.version > 0) {
            url.append("_");
            url.append(file.version);
        }

        HttpDelete delete = new HttpDelete(url.toString());
        try {
            HttpResponse response = httpClient.execute(delete);

            StatusLine line = response.getStatusLine();
            if (line.getStatusCode() < 200 || line.getStatusCode() > 299) {
                throw new WeedFSException("Error deleting file " + file.fid + " on " + location.publicUrl + ": " + line.getStatusCode() + " "
                        + line.getReasonPhrase(), EntityUtils.toString(response.getEntity(), "UTF-8"));
            }
        } finally {
            delete.abort();
        }
    }

    @Override
    public List<Location> lookup(long volumeId) throws IOException, WeedFSException {
        if (lookupCache != null) {
            List<Location> ret = lookupCache.lookup(volumeId);
            if (ret != null) {
                return ret;
            }
        }

        StringBuilder url = new StringBuilder(new URL(masterURL, "/dir/lookup").toExternalForm());
        url.append("?volumeId=");
        url.append(volumeId);

        HttpGet get = new HttpGet(url.toString());
        try {
            HttpResponse response = httpClient.execute(get);

            String content = getContentOrNull(response);
            ObjectMapper mapper = new ObjectMapper();
            try {
                LookupResult result = mapper.readValue(content, LookupResult.class);

                if (result.error != null) {
                    throw new WeedFSException(result.error);
                }

                if (lookupCache != null) {
                    lookupCache.setLocation(volumeId, result.locations);
                }

                return result.locations;
            } catch (JsonMappingException | JsonParseException e) {
                throw new WeedFSException("Unable to parse JSON from weed-fs from: " + content, e);
            }
        } finally {
            get.abort();
        }

    }

    @Override
    public int write(WeedFSFile file, Location location, File fileToUpload) throws IOException, WeedFSException {
        if (fileToUpload.length() == 0) {
            throw new WeedFSException("Cannot write a 0-length file");
        }
        return write(file, location, fileToUpload, null, null, null);
    }

    @Override
    public int write(WeedFSFile file, Location location, byte[] dataToUpload, String fileName) throws IOException, WeedFSException {
        if (dataToUpload.length == 0) {
            throw new WeedFSException("Cannot write a 0-length data");
        }
        return write(file, location, null, dataToUpload, null, fileName);
    }

    @Override
    public int write(WeedFSFile file, Location location, InputStream inputToUpload, String fileName) throws IOException, WeedFSException {
        return write(file, location, null, null, inputToUpload, fileName);
    }

    private String sanitizeFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "file";
        } else if (fileName.length() > 256) {
            return fileName.substring(0, 255);
        }
        return fileName;

    }

    private int write(WeedFSFile file, Location location, File fileToUpload, byte[] dataToUpload, InputStream inputToUpload, String fileName)
            throws IOException, WeedFSException {
        StringBuilder url = new StringBuilder();
        if (!location.publicUrl.contains("http")) {
            url.append("http://");
        }
        url.append(location.publicUrl);
        url.append('/');
        url.append(file.fid);

        if (file.version > 0) {
            url.append('_');
            url.append(file.version);
        }

        HttpPost post = new HttpPost(url.toString());

        MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
        if (fileToUpload != null) {
            if (fileName == null) {
                fileName = fileToUpload.getName();
            }
            multipartEntityBuilder.addBinaryBody("file", fileToUpload, ContentType.APPLICATION_OCTET_STREAM, sanitizeFileName(fileName));
        } else if (dataToUpload != null) {
            multipartEntityBuilder.addBinaryBody("file", dataToUpload, ContentType.APPLICATION_OCTET_STREAM, sanitizeFileName(fileName));
        } else {
            multipartEntityBuilder.addBinaryBody("file", inputToUpload, ContentType.APPLICATION_OCTET_STREAM, sanitizeFileName(fileName));
        }
        post.setEntity(multipartEntityBuilder.build());

        try {
            HttpResponse response = httpClient.execute(post);

            String content = getContentOrNull(response);
            ObjectMapper mapper = new ObjectMapper();
            try {
                WriteResult result = mapper.readValue(content, WriteResult.class);

                if (result.error != null) {
                    throw new WeedFSException(result.error);
                }

                return result.size;
            } catch (JsonMappingException | JsonParseException e) {
                throw new WeedFSException("Unable to parse JSON from weed-fs from: " + content, e);
            }
        } finally {
            post.abort();
        }
    }

    @Override
    public InputStream read(WeedFSFile file, Location location) throws IOException, WeedFSException, WeedFSFileNotFoundException {
        StringBuilder url = new StringBuilder();
        if (!location.publicUrl.contains("http")) {
            url.append("http://");
        }
        url.append(location.publicUrl);
        url.append('/');
        url.append(file.fid);

        if (file.version > 0) {
            url.append('_');
            url.append(file.version);
        }
        HttpGet get = new HttpGet(url.toString());
        HttpResponse response = httpClient.execute(get);
        StatusLine line = response.getStatusLine();
        if (line.getStatusCode() == 404) {
            get.abort();
            throw new WeedFSFileNotFoundException(file, location);
        }
        if (line.getStatusCode() != 200) {
            get.abort();
            throw new WeedFSException("Error reading file " + file.fid + " on " + location.publicUrl + ": " + line.getStatusCode() + " "
                    + line.getReasonPhrase());
        }
        return response.getEntity().getContent();
    }

    @Override
    public MasterStatus getMasterStatus() throws IOException {
        URL url = new URL(masterURL, "/dir/status");

        HttpGet get = new HttpGet(url.toString());

        try {
            HttpResponse response = httpClient.execute(get);
            StatusLine line = response.getStatusLine();

            if (line.getStatusCode() != 200) {
                throw new IOException("Not 200 status recieved for master status url: " + url.toExternalForm());
            }

            String content = getContentOrNull(response);
            ObjectMapper mapper = new ObjectMapper();
            try {
                return mapper.readValue(content, MasterStatus.class);

            } catch (JsonMappingException | JsonParseException e) {
                throw new WeedFSException("Unable to parse JSON from weed-fs from: " + content, e);
            }
        } finally {
            get.abort();
        }
    }

    @Override
    public VolumeStatus getVolumeStatus(Location location) throws IOException {
        StringBuilder url = new StringBuilder();
        if (!location.publicUrl.contains("http")) {
            url.append("http://");
        }
        url.append(location.publicUrl);
        url.append("/status");

        HttpGet get = new HttpGet(url.toString());

        try {
            HttpResponse response = httpClient.execute(get);
            StatusLine line = response.getStatusLine();

            if (line.getStatusCode() != 200) {
                throw new IOException("Not 200 status recieved for master status url: " + url.toString());
            }

            String content = getContentOrNull(response);
            ObjectMapper mapper = new ObjectMapper();
            try {
                return mapper.readValue(content, VolumeStatus.class);

            } catch (JsonMappingException | JsonParseException e) {
                throw new WeedFSException("Unable to parse JSON from weed-fs from: " + content, e);
            }
        } finally {
            get.abort();
        }
    }

    private String getContentOrNull(HttpResponse response) throws IOException {
        return convertStreamToString(response.getEntity().getContent());
    }

    private static String convertStreamToString(java.io.InputStream is) {
        try (java.util.Scanner s = new java.util.Scanner(is, "UTF-8")) {
            return s.useDelimiter("\\A").hasNext() ? s.next() : "";
        }
    }
}
