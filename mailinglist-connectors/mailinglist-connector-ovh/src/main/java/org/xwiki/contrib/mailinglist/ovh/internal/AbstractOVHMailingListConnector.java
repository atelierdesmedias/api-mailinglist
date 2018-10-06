/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.mailinglist.ovh.internal;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xwiki.contrib.mailinglist.MailingListConnector;
import org.xwiki.contrib.mailinglist.MailingListException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base class for all OVH API based connectors.
 * 
 * @version $Id$
 * @since 1.1
 */
public abstract class AbstractOVHMailingListConnector implements MailingListConnector
{
    public static final String METHOD_GET = "get";

    public static final String METHOD_ADD = "add";

    public static final String METHOD_DELETE = "delete";

    private static final Pattern MAIL_REGEX = Pattern.compile("(.*)@(.*)");

    private static final Map<String, String> METHODS = new HashMap<>();

    protected final Map<String, String> paths = new HashMap<>();

    static {
        METHODS.put(METHOD_GET, "GET");
        METHODS.put(METHOD_ADD, "POST");
        METHODS.put(METHOD_DELETE, "DELETE");
    }

    public AbstractOVHMailingListConnector(String addPath, String deletePath)
    {
        this.paths.put(METHOD_ADD, addPath);
        this.paths.put(METHOD_DELETE, deletePath);
    }

    @Override
    public void delete(Map<String, String> profileConfiguration, String mailingList, String email)
        throws MailingListException
    {
        try {
            exec(profileConfiguration, mailingList, email, METHOD_DELETE, null);
        } catch (Exception e) {
            throw new MailingListException("Failed to delete member", e);
        }
    }

    protected String getPath(Map<String, String> profileConfiguration, String method, String domain, String name,
        String email) throws UnsupportedEncodingException, NoSuchAlgorithmException, IOException
    {
        return MessageFormat.format(this.paths.get(method), URLEncoder.encode(domain, "UTF8"),
            URLEncoder.encode(name, "UTF8"), URLEncoder.encode(email, "UTF8"));
    }

    protected <T> T exec(Map<String, String> profileConfiguration, String mailingList, String email, String method,
        Map<String, Object> body) throws NoSuchAlgorithmException, IOException
    {
        // Extract mailing list domain and name
        Matcher matcher = MAIL_REGEX.matcher(mailingList);
        matcher.find();

        return exec(profileConfiguration, matcher.group(2), matcher.group(1), email, method, body);
    }

    protected <T> T exec(Map<String, String> profileConfiguration, String listDomain, String listName, String email,
        String method, Map<String, Object> bodyMap) throws NoSuchAlgorithmException, IOException
    {
        // define base vars
        String appKey = profileConfiguration.get("appKey");
        String appSecret = profileConfiguration.get("appSecret");
        String httpMethod = METHODS.get(method);
        String consumerKey = profileConfiguration.get("consumerKey");
        String endpoint = profileConfiguration.get("endpoint");

        String path = getPath(profileConfiguration, method, listDomain, listName, email);

        String body = "";
        StringBuilder urlBuilder = new StringBuilder(endpoint);
        urlBuilder.append(path);
        if (httpMethod.equals("GET")) {
            // Put parameters in the URL
            if (bodyMap != null && !bodyMap.isEmpty()) {
                urlBuilder.append('?');
                for (Map.Entry<String, Object> entry : bodyMap.entrySet()) {
                    urlBuilder.append(entry.getKey());
                    urlBuilder.append('=');
                    urlBuilder.append(URLEncoder.encode(entry.getValue().toString(), "UTF8"));
                    urlBuilder.append('&');
                }
            }
        } else {
            // Put parameters in the body
            if (bodyMap != null && !bodyMap.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                body = mapper.writeValueAsString(bodyMap);
            }
        }

        URL url = new URL(urlBuilder.toString());

        // get timestamp from local system
        long timestamp = System.currentTimeMillis() / 1000;

        // build signature
        String toSign = new StringBuilder(appSecret).append("+").append(consumerKey).append("+").append(httpMethod)
            .append("+").append(url).append("+").append(body).append("+").append(timestamp).toString();

        String signature = new StringBuilder("$1$").append(toHashSHA1(toSign)).toString();

        // set HTTP headers for authentication

        HttpURLConnection request = (HttpURLConnection) url.openConnection();
        request.setRequestMethod(httpMethod);
        request.setReadTimeout(30000);
        request.setConnectTimeout(30000);
        request.setRequestProperty("Content-Type", "application/json");
        request.setRequestProperty("X-Ovh-Application", appKey);

        request.setRequestProperty("X-Ovh-Consumer", consumerKey);
        request.setRequestProperty("X-Ovh-Signature", signature);
        request.setRequestProperty("X-Ovh-Timestamp", Long.toString(timestamp));

        // Send body
        if (body != null && !body.isEmpty()) {
            request.setDoOutput(true);
            DataOutputStream out = new DataOutputStream(request.getOutputStream());
            out.writeBytes(body);
            out.flush();
            out.close();
        }

        BufferedReader in;
        int responseCode = request.getResponseCode();
        if (responseCode == 200) {
            in = new BufferedReader(new InputStreamReader(request.getInputStream()));
        } else {
            in = new BufferedReader(new InputStreamReader(request.getErrorStream()));
        }

        String inputLine;
        // build response
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        String responseBody = response.toString();

        if (responseCode != 200) {
            throw new IOException(responseBody);
        }

        // Parse response body
        ObjectMapper objectMapper = new ObjectMapper();
        return (T) objectMapper.readValue(responseBody, Object.class);
    }

    private String toHashSHA1(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException
    {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(text.getBytes("iso-8859-1"), 0, text.length());
        byte[] sha1hash = md.digest();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sha1hash.length; i++) {
            sb.append(Integer.toString((sha1hash[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

}
