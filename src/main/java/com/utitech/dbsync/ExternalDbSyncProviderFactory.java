package com.utitech.dbsync;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalDbSyncProviderFactory implements EventListenerProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger(ExternalDbSyncProviderFactory.class);
    private static final String PROVIDER_ID = "external-db-sync";

    @Override
    public EventListenerProvider create(KeycloakSession keycloakSession) {
        // print statement for debug, remove after testing
        logger.info("New ExternalDbSyncProvider created!");
        return new ExternalDbSyncProvider();
    }

    @Override
    public void init(Config.Scope scope) {
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
