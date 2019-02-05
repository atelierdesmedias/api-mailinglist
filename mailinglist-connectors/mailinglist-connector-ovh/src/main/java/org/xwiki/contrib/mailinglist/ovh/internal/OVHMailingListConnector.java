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

import java.util.Collections;
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
@Named("ovh")
public class OVHMailingListConnector extends AbstractOVHMailingListConnector
{
    /**
     * Default constructor.
     */
    public OVHMailingListConnector()
    {
        super("/email/domain/{0}/mailingList/{1}/subscriber", "/email/domain/{0}/mailingList/{1}/subscriber/{2}");
    }

    @Override
    public void add(Map<String, String> profileConfiguration, String mailingList, String email)
        throws MailingListException
    {
        Map<String, Object> body = Collections.singletonMap("email", email);

        try {
            exec(profileConfiguration, mailingList, email, METHOD_ADD, body);
        } catch (Exception e) {
            throw new MailingListException("Failed add delete member", e);
        }
    }
}
