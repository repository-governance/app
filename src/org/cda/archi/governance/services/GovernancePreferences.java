package org.cda.archi.governance.services;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

/** Stores only non-secret search preferences in the current Archi/Eclipse instance. */
public final class GovernancePreferences {
    private static final String NODE = "org.cda.archi.governance";
    private static final String ORGANIZATION = "github.organization";
    private static final String TOPIC = "github.repositoryTopic";
    public static final String DEFAULT_TOPIC = "archimate";

    private final IEclipsePreferences preferences;

    public GovernancePreferences() {
        this(InstanceScope.INSTANCE.getNode(NODE));
    }

    GovernancePreferences(IEclipsePreferences preferences) {
        this.preferences = preferences;
    }

    public String getOrganization() { return preferences.get(ORGANIZATION, ""); }
    public String getTopic() { return preferences.get(TOPIC, DEFAULT_TOPIC); }

    public void saveSearch(String organization, String topic) throws org.osgi.service.prefs.BackingStoreException {
        preferences.put(ORGANIZATION, organization);
        preferences.put(TOPIC, topic);
        preferences.flush();
    }
}
