package org.openmrs.module.lime;

import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.UserSessionListener;
import org.openmrs.api.context.Context;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of UserSessionListener hence adding roles to the corresponding
 * roles
 */
@Component
@Qualifier("customMosulMetadataUserListener")
public class MosulMetadataUserListener implements UserSessionListener {

	private static final Logger log = LoggerFactory.getLogger(MosulMetadataUserListener.class);

    @Override
    public void loggedInOrOut(User user, Event event, Status status) {
        try {
            if (event == Event.LOGIN) {
                String mosulUsername = user.getUsername();
                String mosulRoleName = MosulMetadataConstants.USER_ROLES.get(mosulUsername);

                if (mosulRoleName != null) {
                    Role mosulRole = Context.getUserService().getRole(mosulRoleName);
                    verifyUserRole(user, mosulRole);
                } else {
                    log.debug("user: '{}' is not a Mosul user hence skipping adding Mosul role", user.getUsername());
                }
            }
        } catch (Exception e) {
            log.error("Unable to assign Mosul user Corresponding Role. ", e);
        }
    }

    private void verifyUserRole(User user, Role role) {
        if (!user.hasRole(role.getRole())) {
            user.addRole(role);
            Context.getUserService().saveUser(user);
            log.debug("Granted Mosul user: '{}' Mosul role: '{}'", user.getUsername(), role.getRole());
        } else {
            log.debug("Mosul user: '{}' already has Mosul role: '{}''. Skipping...", user.getUsername(), role.getRole());
        }
    }
}
