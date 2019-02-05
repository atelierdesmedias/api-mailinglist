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
package org.xwiki.contrib.mailinglist.interval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.mailinglist.MailingListConnector;
import org.xwiki.contrib.mailinglist.MailingListException;
import org.xwiki.contrib.mailinglist.MailingListManager;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultMailingListManager implements MailingListManager
{
    @Inject
    private ConfigurationSource configuration;

    @Inject
    private ComponentManager componentManager;

    private Map<String, String> createConfiguration(String profile)
    {
        Map<String, String> profileConfiguration = new HashMap<>();

        String prefix = "mailinglist." + profile + ".";
        for (String key : this.configuration.getKeys()) {
            if (key.startsWith(prefix)) {
                profileConfiguration.put(key.substring(prefix.length()),
                    this.configuration.getProperty(key, String.class));
            }
        }

        return profileConfiguration;
    }

    private MailingListConnector getConnector(Map<String, String> profileConfiguration) throws MailingListException
    {
        String connectorHint = profileConfiguration.get("connector");

        MailingListConnector connector;
        try {
            connector = this.componentManager.getInstance(MailingListConnector.class, connectorHint);
        } catch (ComponentLookupException e) {
            throw new MailingListException("Failed to get connector for hint " + connectorHint, e);
        }

        return connector;
    }

    @Override
    public void add(String profile, String mailingList, String email) throws MailingListException
    {
        Map<String, String> profileConfiguration = createConfiguration(profile);

        MailingListConnector connector = getConnector(profileConfiguration);

        connector.add(profileConfiguration, mailingList, email);
    }

    @Override
    public void delete(String profile, String mailingList, String email) throws MailingListException
    {
        Map<String, String> profileConfiguration = createConfiguration(profile);

        MailingListConnector connector = getConnector(profileConfiguration);

        connector.delete(profileConfiguration, mailingList, email);
    }

    @Override
    public List<String> getMembers(String profile, String mailingList) throws MailingListException
    {
        Map<String, String> profileConfiguration = createConfiguration(profile);

        MailingListConnector connector = getConnector(profileConfiguration);

        return connector.getMembers(profileConfiguration, mailingList);
    }
}
