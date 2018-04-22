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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.mailinglist.MailingListConnector;
import org.xwiki.contrib.mailinglist.MailingListException;

@Component
@Singleton
@Named("ovh")
public class OVHMailingListConnector implements MailingListConnector
{

    private static final Pattern MAIL_REGEX = Pattern.compile("(.*)@(.*)");

    private static final Map<String, String> METHOD = new HashMap<>();

    private static final Map<String, String> PATH = new HashMap<>();

    static {
        METHOD.put("add", "POST");
        METHOD.put("delete", "DELETE");

        PATH.put("add", "/email/domain/{0}/mailingList/{1}/subscriber");
        PATH.put("delete", "/email/domain/{0}/mailingList/{1}/subscriber/{2}");
    }

    @Override
    public void add(Map<String, String> profileConfiguration, String mailingList, String email)
        throws MailingListException
    {
        String body = "{\"email\": \"" + email + "\"}";

        try {
            exec(profileConfiguration, mailingList, email, "add", body);
        } catch (Exception e) {
            throw new MailingListException("Failed add delete member", e);
        }

    }

    @Override
    public void delete(Map<String, String> profileConfiguration, String mailingList, String email)
        throws MailingListException
    {
        try {
            exec(profileConfiguration, mailingList, email, "delete", "");
        } catch (Exception e) {
            throw new MailingListException("Failed to delete member", e);
        }
    }

    private void exec(Map<String, String> profileConfiguration, String mailingList, String email, String method,
        String body) throws NoSuchAlgorithmException, IOException
    {
        // define base vars
        String appKey = profileConfiguration.get("appKey");
        String appSecret = profileConfiguration.get("appSecret");
        String httpMethod = METHOD.get(method);
        String consumerKey = profileConfiguration.get("consumerKey");
        String endpoint = profileConfiguration.get("endpoint");

        Matcher matcher = MAIL_REGEX.matcher(mailingList);
        matcher.find();
        String path = MessageFormat.format(PATH.get(method), URLEncoder.encode(matcher.group(2), "UTF8"),
            URLEncoder.encode(matcher.group(1), "UTF8"), URLEncoder.encode(email, "UTF8"));

        URL url = new URL(new StringBuilder(endpoint).append(path).toString());

        // get timestamp from local system
        long timestamp = System.currentTimeMillis() / 1000;

        // build signature
        String toSign = new StringBuilder(appSecret).append("+").append(consumerKey).append("+").append(httpMethod)
            .append("+").append(url).append("+").append(body).append("+").append(timestamp).toString();

        System.out.println(toSign);

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

        String inputLine;
        BufferedReader in;
        int responseCode = request.getResponseCode();
        if (responseCode == 200) {
            in = new BufferedReader(new InputStreamReader(request.getInputStream()));
        } else {
            in = new BufferedReader(new InputStreamReader(request.getErrorStream()));
        }

        // build response
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        if (responseCode != 200) {
            if (responseCode == 400) {
                throw new IOException(response.toString());
            } else if (responseCode == 403) {
                throw new IOException(response.toString());
            } else if (responseCode == 404) {
                throw new IOException(response.toString());
            } else if (responseCode == 409) {
                throw new IOException(response.toString());
            } else {
                throw new IOException(response.toString());
            }
        }
    }

    private String toHashSHA1(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException
    {
        MessageDigest md;
        md = MessageDigest.getInstance("SHA-1");
        byte[] sha1hash = new byte[40];
        md.update(text.getBytes("iso-8859-1"), 0, text.length());
        sha1hash = md.digest();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < sha1hash.length; i++) {
            sb.append(Integer.toString((sha1hash[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

}
