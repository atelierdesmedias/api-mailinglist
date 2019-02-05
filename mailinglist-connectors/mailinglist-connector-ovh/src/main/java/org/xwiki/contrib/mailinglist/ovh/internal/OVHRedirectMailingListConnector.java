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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.mailinglist.MailingListException;

/**
 * OVH mailing list based connector.
 * 
 * @version $Id$
 */
@Component
@Singleton
@Named("ovh-redirect")
public class OVHRedirectMailingListConnector extends AbstractOVHMailingListConnector
{
    public static final String METHOD_GETOBJECT = "getobject";

    /**
     * Default constructor.
     */
    public OVHRedirectMailingListConnector()
    {
        super("/email/domain/{0}/redirection", "/email/domain/{0}/redirection/{2}");

        this.methods.put(METHOD_GETOBJECT, "GET");
        this.paths.put(METHOD_GETOBJECT, "/email/domain/{0}/redirection/{2}");
    }

    @Override
    public void add(Map<String, String> profileConfiguration, String mailingList, String email)
        throws MailingListException
    {
        Map<String, Object> body = new HashMap<>();
        body.put("from", mailingList);
        body.put("localCopy", false);
        body.put("to", email);

        try {
            exec(profileConfiguration, mailingList, email, METHOD_ADD, body);
        } catch (Exception e) {
            throw new MailingListException("Failed add member", e);
        }
    }

    @Override
    protected String getPath(Map<String, String> profileConfiguration, String method, String domain, String name,
        String email) throws NoSuchAlgorithmException, IOException, MailingListException
    {
        if (method.equals(METHOD_DELETE)) {
            // Find the id of the redirect (needed by delete)
            String from = name + '@' + domain;
            Map<String, Object> body = new HashMap<>();
            body.put("from", from);
            body.put("to", email);
            List<String> result = exec(profileConfiguration, domain, name, email, METHOD_GET, body);

            if (result.isEmpty()) {
                throw new MailingListException("No redirection exist from [" + from + "] to [" + email + "]");
            }

            String id = result.get(0);

            // Generate the path
            return super.getPath(profileConfiguration, method, domain, name, id);
        } else {
            return super.getPath(profileConfiguration, method, domain, name, email);
        }
    }

    @Override
    public List<String> getMembers(Map<String, String> profileConfiguration, String mailingList)
        throws MailingListException
    {
        Map<String, Object> body = new HashMap<>();
        body.put("from", mailingList);

        List<String> ids = super.getMembers(profileConfiguration, mailingList, body);

        List<String> members = new ArrayList<>(ids.size());
        for (String id : ids) {
            try {
                Map<String, String> redirectionObject =
                    exec(profileConfiguration, mailingList, id, METHOD_GETOBJECT, body);

                members.add(redirectionObject.get("to"));
            } catch (Exception e) {
                throw new MailingListException("Failed to get member email from id", e);
            }

        }

        return members;
    }
}
