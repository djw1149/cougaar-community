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
package org.cougaar.community.manager;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.naming.directory.ModificationItem;

import org.cougaar.community.BlackboardClient;
import org.cougaar.community.CommunityDescriptor;
import org.cougaar.community.CommunityUpdateListener;
import org.cougaar.community.RelayAdapter;
import org.cougaar.community.AbstractCommunityService;

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.component.BindingSite;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.service.community.FindCommunityCallback;

import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.core.service.wp.Callback;
import org.cougaar.core.service.wp.Response;
import org.cougaar.core.service.wp.WhitePagesService;
import org.cougaar.util.UnaryPredicate;

/**
 * Concrete implementation of CommunityManager interface that uses Blackboard
 * Relays to perform communication with remote nodes/agents.
 */
public class DefaultCommunityManagerImpl extends AbstractCommunityManager {

  public static final String SEND_INTERVAL_PROPERTY = "org.cougaar.community.updateInterval";
  // Defines how long CommunityDescriptor updates should be aggregated before
  // sending to interested agents.
  private static long SEND_INTERVAL = 30 * 1000;

  // Defines TTL for community manager binding cache entry
  public static final String WPS_CACHE_EXPIRATION_PROPERTY = "org.cougaar.community.wps.cache.expiration";
  private static long CACHE_TTL = 5 * 60 * 1000;

  public static final String WPS_RETRY_INTERVAL_PROPERTY = "org.cougaar.community.wps.retry.interval";
  private static long WPS_RETRY_DELAY = 15 * 1000;

  // Defines frequency of White Pages read to verify that this agent is still
  // manager for community
  public static final String MANAGER_CHECK_INTERVAL_PROPERTY = "org.cougaar.community.manager.check.interval";
  private static long TIMER_INTERVAL = 1 * 60 * 1000;

  // Defines frequency of White Pages read to verify that this agent is still
  // manager for community
  public static final String CACHE_EXPIRATION_PROPERTY = "org.cougaar.community.manager.cache.expiration";
  private static long CACHE_EXPIRATION = 5 * 60 * 1000;


  protected BindingSite bindingSite;
  protected MyBlackboardClient myBlackboardClient;

  protected Set managedCommunities = Collections.synchronizedSet(new HashSet());
  protected Set communitiesToCheck = Collections.synchronizedSet(new HashSet());

  // Helper class for distributing Community updates
  protected CommunityDistributer distributer;

  // Services used
  protected AbstractCommunityService communityService;  //private CommunityService communityService;
  protected WhitePagesService whitePagesService;

  // This agent
  protected MessageAddress agentId;
  protected CommunityUpdateListener updateListener;

  protected String priorManager = null;

  /**
   * Construct CommunityManager component capable of communicating with remote
   * agents via Blackboard Relays.
   * @param bs       BindingSite
   * @param acs      CommunityService reference
   * @param cul      Listener for local updates
   */
  public DefaultCommunityManagerImpl(BindingSite bs,
                                     AbstractCommunityService acs,
                                     CommunityUpdateListener cul) {
    this.bindingSite = bs;
    ServiceBroker sb = getServiceBroker();
    agentId = getAgentId();
    agentName = agentId.toString();
    logger = (LoggingService)sb.getService(this, LoggingService.class, null);
    communityService = acs;
    whitePagesService =
        (WhitePagesService) sb.getService(this, WhitePagesService.class, null);
    myBlackboardClient = new MyBlackboardClient(bs);
    getSystemProperties();
    distributer = new CommunityDistributer(bs,
                                           SEND_INTERVAL,
                                           CACHE_EXPIRATION,
                                           true,
                                           cul,
                                           myBlackboardClient,
                                           communities);
  }

  public void manageCommunity(Community community) {
    super.manageCommunity(community);
  }

  protected void getSystemProperties() {
    try {
      SEND_INTERVAL =
          Long.parseLong(System.getProperty(SEND_INTERVAL_PROPERTY, Long.toString(SEND_INTERVAL)));
      CACHE_TTL =
          Long.parseLong(System.getProperty(WPS_CACHE_EXPIRATION_PROPERTY, Long.toString(CACHE_TTL)));
      WPS_RETRY_DELAY =
          Long.parseLong(System.getProperty(WPS_RETRY_INTERVAL_PROPERTY, Long.toString(WPS_RETRY_DELAY)));
      TIMER_INTERVAL =
          Long.parseLong(System.getProperty(MANAGER_CHECK_INTERVAL_PROPERTY, Long.toString(TIMER_INTERVAL)));
      CACHE_EXPIRATION =
          Long.parseLong(System.getProperty(CACHE_EXPIRATION_PROPERTY, Long.toString(CACHE_EXPIRATION)));
    } catch (Exception ex) {
      if (logger.isWarnEnabled()) {
        logger.warn(agentName + ": Exception setting parameter from system property", ex);
      }
    }
  }

  protected MessageAddress getAgentId() {
    AgentIdentificationService ais =
        (AgentIdentificationService)getServiceBroker().getService(this,
        AgentIdentificationService.class, null);
    MessageAddress addr = ais.getMessageAddress();
    getServiceBroker().releaseService(this, AgentIdentificationService.class, ais);
    return addr;
  }

  protected ServiceBroker getServiceBroker() {
    return bindingSite.getServiceBroker();
  }

  /**
   * Processes Requests received via Relay.
   * @param req Request
   */
  protected void processRequest(Request req) {
    if (logger.isDetailEnabled()) {
      logger.detail(agentId + ": processRequest: " + req);
    }
    String source = req.getSource().toString();
    String communityName = req.getCommunityName();
    int reqType = req.getRequestType();
    Entity entity = req.getEntity();
    ModificationItem[] attrMods = req.getAttributeModifications();
    req.setResponse(processRequest(source,
                                   communityName,
                                   reqType,
                                   entity,
                                   attrMods));
    myBlackboardClient.publish(req, BlackboardClient.CHANGE);
  }

  /**
   * Tests whether this agent is the manager for the specified community.
   * @param communityName String
   * @return boolean
   */
  protected boolean isManager(String communityName) {
    return (managedCommunities.contains(communityName) &&
            communities.containsKey(communityName) &&
            distributer.contains(communityName));
  }

  /**
   * Add agents to distribution list for community updates.
   * @param communityName Name of community
   * @param targets Set of agent names (String) to add to distribution
   */
  protected void addTargets(String communityName, Set targets) {
    distributer.addTargets(communityName, targets);
  }

  /**
   * Remove agents from distribution list for community updates.
   * @param communityName Name of community
   * @param targets Set of agent names (String) to remove from distribution
   */
  protected void removeTargets(String communityName, Set targets) {
    distributer.removeTargets(communityName, targets);
  }

  /**
   * Send updated Community info to agents on distribution.
   * @param communityName Name of community
   */
  protected void distributeUpdates(String communityName) {
    distributer.update(communityName);
  }

  /**
   * Get name of community manager.
   * @param communityName String
   * @param fmcb FindManagerCallback
   */
  public void findManager(String                      communityName,
                          final FindCommunityCallback fmcb) {

    communityService.findCommunity(communityName, fmcb, -1);
  }

  /**
   * Asserts community manager role.
   * @param communityName Community to manage
   */
  protected void assertCommunityManagerRole(String communityName) {
    assertCommunityManagerRole(communityName, false);
  }

  /**
   * Asserts community manager role by binding address to community name in
   * White Pages
   * @param communityName Community to manage
   * @param override      If true any existing binding will be removed
   *                      and replaced with new
   */
  protected void assertCommunityManagerRole(String communityName,
                                            boolean override) {
    if (logger.isDetailEnabled()) {
      logger.detail(agentName + ": assertCommunityManagerRole: agent=" +
                    agentId.toString() +
                    " community=" + communityName);
    }
    try {
      bindCommunityManager(communityName, override);
      communitiesToCheck.add(communityName);
      myBlackboardClient.startVerifyManagerCheck();
    } catch (Throwable ex) {
      if (logger.isWarnEnabled()) {
        logger.warn(agentName +
                    ": Unable to (re)bind agent as community manager:" +
                    " error=" + ex +
                    " agent=" + agentId +
                    " community=" + communityName);
      }
    }
  }

  /**
   * Return current time as a long.
   * @return  Current time
   */
  private long now() {
    return System.currentTimeMillis();
  }

  /** Create a wp entry for white pages binding
   * @param communityName Name of community to bind
   * @return AddressEntry for new manager binding.
   * @exception Exception Unable to create AddressEntry
   */
  private AddressEntry createManagerEntry(String communityName) throws Exception {
    URI uri = URI.create("agent:///"+agentId);
    AddressEntry entry =
      AddressEntry.getAddressEntry(communityName+".comm", "community", uri);
    return entry;
  }

  /**
   * Bind this agent to community name in White Pages.
   * @param communityName String  Name of community to bind
   * @param override boolean  Override existing binding
   * @throws Exception
   */
  private void bindCommunityManager(final String communityName,
                                    final boolean override) throws Exception {
    final AddressEntry communityAE = createManagerEntry(communityName);
    Callback cb = new Callback() {
      public void execute(Response resp) {
        Response.Bind bindResp = (Response.Bind)resp;
        if (resp.isAvailable()) {
          if (logger.isDebugEnabled())
            logger.debug(agentName + ": bind: " +
                          " success=" + resp.isSuccess() +
                          " didBind=" + bindResp.didBind());
          if (bindResp.didBind()) {
            distributer.add(communityName,
                            Collections.singleton(agentId.toString()));
            if (logger.isDebugEnabled()) {
              logger.debug(agentName + ": Managing community " +
                           communityName);
            }
            managedCommunities.add(communityName);
          } else {
            if (logger.isDetailEnabled())
              logger.detail(
                  agentName + ": Unable to bind agent as community manager:" +
                  " agent=" + agentId +
                  " community=" + communityName +
                  " entry=" + communityAE +
                  " attemptingRebind=" + override);
            if (override) {
              rebindCommunityManager(communityAE, communityName);
            }
          }
          resp.removeCallback(this);
        }
      }
    };
    whitePagesService.bind(communityAE, cb);
  }

  private void rebindCommunityManager(AddressEntry ae,
                                      final String communityName) {
    Callback cb = new Callback() {
      public void execute(Response resp) {
        Response.Bind bindResp = (Response.Bind) resp;
        if (resp.isAvailable()) {
          if (logger.isDebugEnabled())
            logger.debug(agentName+": rebind: " +
                        " success=" + resp.isSuccess() +
                        " didBind=" + bindResp.didBind());
          if (bindResp.didBind()) {
            logger.debug(agentName+": Managing community (rebind)" + communityName);
            managedCommunities.add(communityName);
          } else {
            if (logger.isDebugEnabled())
              logger.debug(agentName+": Unable to rebind agent as community manager:" +
                           " agent=" + agentId +
                           " community=" + communityName);
          }
          resp.removeCallback(this);
        }
      }
    };
    whitePagesService.rebind(ae, cb);
  }

  private UnaryPredicate communityDescriptorPredicate = new UnaryPredicate() {
    public boolean execute(Object o) {
      return (o instanceof RelayAdapter &&
              ((RelayAdapter)o).getContent() instanceof CommunityDescriptor);
    }
  };

  /**
   * Check WPS binding to verify that the state of this community manager
   * is in sync with the WPS bindings.
   */
  private void verifyManagerRole() {
    Collection l = new HashSet();
    synchronized (managedCommunities) {
      l.addAll(communitiesToCheck);
    }
    for (Iterator it = l.iterator(); it.hasNext(); ) {
      final String communityName = (String)it.next();
      // See if WP binding lists this agent as manager for each name
      // in communityNames collection
      FindCommunityCallback fmcb = new FindCommunityCallback() {
        public void execute(String mgrName) {
          if (logger.isDetailEnabled()) {
            logger.detail(agentName + ": verifyWpsBinding:" +
                          " community=" + communityName +
                          " current=" + mgrName +
                          " prior=" + priorManager);
          }
          if (isManager(communityName) && mgrName == null) {
            assertCommunityManagerRole(communityName, true); // reassert mgr role
          } else if (!isManager(communityName) && agentName.equals(mgrName)) {
            if (logger.isDebugEnabled()) {
              logger.debug(agentName + ": New WP binding:" +
                          " community=" + communityName +
                          " prior=" + priorManager +
                          " new=" + mgrName);
            }
            managedCommunities.add(communityName);
            distributer.add(communityName,
                            Collections.singleton(agentId.toString()));
            myBlackboardClient.startVerifyManagerCheck();
          } else if (isManager(communityName) && !agentName.equals(mgrName)) {
            if (logger.isDebugEnabled()) {
              logger.debug(agentName + ": No longer bound in WP:" +
                           " community=" + communityName +
                           " prior=" + agentName +
                           " new=" + mgrName);
            }
            managedCommunities.remove(communityName);
            distributer.remove(communityName);
          }
          priorManager = mgrName;
        }
      };
      findManager(communityName, fmcb);
    }
  }

  /**
   * Predicate used to select community manager Requests sent by remote
   * agents.
   */
  private IncrementalSubscription requestSub;
  private UnaryPredicate RequestPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof Request);
  }};

  class MyBlackboardClient extends BlackboardClient {

    private BBWakeAlarm verifyMgrAlarm;

    public MyBlackboardClient(BindingSite bs) {
      super(bs);
    }

    protected void startVerifyManagerCheck() {
      if (verifyMgrAlarm == null) {
        verifyMgrAlarm = new BBWakeAlarm(now() + TIMER_INTERVAL);
        alarmService.addRealTimeAlarm(verifyMgrAlarm);
      }
    }

    public void setupSubscriptions() {
      // Subscribe to CommunityManagerRequests
      requestSub =
          (IncrementalSubscription)blackboard.subscribe(RequestPredicate);

      // Re-publish any CommunityDescriptor Relays found on BB
      if (blackboard.didRehydrate()) {
        Collection cds = blackboard.query(communityDescriptorPredicate);
        for (Iterator it = cds.iterator(); it.hasNext(); ) {
          RelayAdapter ra = (RelayAdapter)it.next();
          CommunityDescriptor cd = (CommunityDescriptor)ra.getContent();
          if (logger.isDebugEnabled()) {
            logger.info(agentName +
                        ": Found CommunityDescriptor Relay: community=" +
                        cd.getName());
          }
          communities.put(cd.getName(), cd.getCommunity());
          distributer.add(ra);
          assertCommunityManagerRole(cd.getName());
        }
      }

    }

    public void execute() {

      super.execute();

      // On verifyMgrAlarm expiration check WPS binding to verify that
      // manager roles for this agent
      if (verifyMgrAlarm != null && verifyMgrAlarm.hasExpired()) {
        verifyManagerRole();
        verifyMgrAlarm = new BBWakeAlarm(now() + TIMER_INTERVAL);
        alarmService.addRealTimeAlarm(verifyMgrAlarm);
      }

      // Get CommunityManagerRequests sent by remote agents
      Collection communityManagerRequests = requestSub.getAddedCollection();
      for (Iterator it = communityManagerRequests.iterator(); it.hasNext(); ) {
        Request req = (Request)it.next();
        // Process requests sent from remote agents only
        if (!agentName.equals(req.getSource().toString())) {
          processRequest(req);
        }
      }
    }

  }

}
