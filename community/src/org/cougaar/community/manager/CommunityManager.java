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

import javax.naming.directory.ModificationItem;

import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.FindCommunityCallback;

/**
 * Interface for a CommunityManager that is responsible for maintaining
 * state for one or more communities.
 */
public interface CommunityManager {

  /**
   * Defines community to manage.
   * @param community Community
   */
  public void manageCommunity(Community community);

  /**
   * Client request to be handled by manager.
   * @param source String  Name of agent submitting request
   * @param communityName String  Target Community
   * @param reqType int  Request type (Refer to
   *   org.cougaar.core.service.community.CommunityServiceConstants for list of
   *   recognized values)
   * @param entity Entity Affected Entity
   * @param attrMods ModificationItem[] Attribute modifications to be applied
   *    to affected entity
   * @return CommunityResponse  Response callback
   */
  public CommunityResponse processRequest(String             source,
                                          String             communityName,
                                          int                reqType,
                                          Entity             entity,
                                          ModificationItem[] attrMods);

  /**
   * Locate the manager for specified community.
   * @param communityName String  Target community
   * @param fmcb FindCommunityCallback  Callback that is invoked after manager
   *    has been located.
   */
  public void findManager(String                communityName,
                          FindCommunityCallback fmcb);

}
