/*
 * <copyright>
 *  Copyright 2001-2003 Mobile Intelligence Corp
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA).
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 *
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
 * </copyright>
 */

package org.cougaar.community;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Agent;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.service.community.CommunityChangeListener;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;
import org.cougaar.core.service.community.FindCommunityCallback;

import org.cougaar.util.log.LoggerFactory;
import org.cougaar.util.log.Logger;

/**
 * This class listens for community change events and updates local
 * CommunityMembership object to reflect current state.  The
 * CommunityMembership is used to rejoin communities on a restart and to
 * periodically verify that this agents view of the world is in sync with
 * that of applicable community managers.
 */
public class MembershipWatcher {

  protected String thisAgent;
  protected CommunityMemberships myCommunities;
  protected CommunityService communityService;
  protected Logger logger;
  protected List managedCommunities = Collections.synchronizedList(new ArrayList());;
  protected List pendingOperations = Collections.synchronizedList(new ArrayList());

  public MembershipWatcher(String agentName,
                           CommunityService commSvc,
                           CommunityMemberships memberships) {
    this.thisAgent = agentName;
    this.myCommunities = memberships;
    this.communityService = commSvc;
    this.logger =
        LoggerFactory.getInstance().createLogger(MembershipWatcher.class);
  }

  public synchronized void validate() {
    if (logger.isDebugEnabled()) {
      logger.debug(thisAgent + ": validate community memberships: " + thisAgent +
                   " myCommunities=" + myCommunities);
    }
    for (Iterator it = myCommunities.listCommunities().iterator(); it.hasNext(); ) {
      final String communityName = (String)it.next();
      Community community = communityService.getCommunity(communityName, null);
      Collection entities = myCommunities.getEntities(communityName);
      for (Iterator it1 = entities.iterator(); it1.hasNext(); ) {
        Entity entity = (Entity)it1.next();
        if (community == null || !community.hasEntity(entity.getName()) &&
            !pendingOperations.contains(communityName)) {
          checkCommunity(communityName, entity, true);
        }
      }
    }
    Collection parents =
        communityService.listParentCommunities(null, (CommunityResponseListener)null);
    for (Iterator it1 = parents.iterator(); it1.hasNext(); ) {
      String parentName = (String)it1.next();
      Community parentCommunity = communityService.getCommunity(parentName, null);
      if (parentCommunity != null &&
          parentCommunity.hasEntity(thisAgent) &&
          !pendingOperations.contains(parentName)) {
        checkCommunity(parentName, new AgentImpl(thisAgent), myCommunities.contains(parentName, thisAgent));
      }
    }

  }

  protected void checkCommunity(final String  communityName,
                                final Entity  entity,
                                final boolean isMember) {
    if (logger.isDebugEnabled()) {
      logger.debug(thisAgent+": checkCommunityMembership:" +
                  " community=" + communityName +
                  " entity=" + entity +
                  " isMember=" + isMember);
    }
    FindCommunityCallback fccb = new FindCommunityCallback() {
      public void execute(String managerName) {
        if (managerName != null) { // Community exists
          Community community = communityService.getCommunity(communityName,
            new CommunityResponseListener() {
              public void getResponse(CommunityResponse resp) {
                Object obj = resp.getContent();
                if (obj != null && !(obj instanceof Community)) {
                  logger.warn(thisAgent+": Invalid response object, type=" +
                              obj.getClass().getName());
                } else {
                  Community community = (Community)obj;
                  if (isMember &&
                      (community == null || !community.hasEntity(entity.getName()))) {
                    rejoin(communityName, entity);
                  } else if (!isMember &&
                             community != null &&
                             community.hasEntity(entity.getName())) {
                    leave(communityName, entity.getName());
                  }
                }
              }
            });
            if (community != null) {
              if (isMember && !community.hasEntity(entity.getName())) {
                rejoin(communityName, entity);
              } else if (!isMember && community.hasEntity(entity.getName())) {
                leave(communityName, entity.getName());
              }
            }
        } else { // Community doesn't exist
          rejoin(communityName, entity);
        }
      }
    };
    communityService.findCommunity(communityName, fccb, 10000);
  }

  protected void rejoin(final String communityName, Entity entity) {
    if (logger.isDebugEnabled()) {
      logger.debug(thisAgent+": Re-joining community:" +
                  " community=" + communityName +
                  " entity=" + entity.getName());
    }
    pendingOperations.add(communityName);
    int type = entity instanceof Agent
                  ? CommunityService.AGENT
                  : CommunityService.COMMUNITY;
    communityService.joinCommunity(communityName,
                                   entity.getName(),
                                   type,
                                   entity.getAttributes(),
                                   false,
                                   null,
      new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          pendingOperations.remove(communityName);
          if (logger.isDetailEnabled()) {
            logger.detail(thisAgent + ": Join status=" + resp);
          }
        }
    });
  }

  protected void leave(final String communityName, String entityName) {
    if (logger.isDebugEnabled()) {
      logger.debug(thisAgent+": Leaving community:" +
                  " community=" + communityName +
                  " entity=" + entityName);
    }
    pendingOperations.add(communityName);
    communityService.leaveCommunity(communityName,
                                   entityName,
      new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          if (logger.isDetailEnabled()) {
            logger.detail("Leave status=" + resp);
          }
          pendingOperations.remove(communityName);
        }
    });
  }
}
