/*
 * <copyright>
 *  Copyright 2003 BBNT Solutions, LLC
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.DirContext;

import org.cougaar.core.service.community.Entity;

/**
 * Static Community helper methods.
 */
public class CommunityUtils {

  /**
   * Clone attributes.
   * @param attrs Attributes Original copy
   * @return Attributes      Clone of attributes
   */
  public static Attributes cloneAttributes(Attributes attrs) {
    Attributes clone = (Attributes)attrs.clone();
    NamingEnumeration ne = attrs.getAll();
    try {
      while (ne.hasMore()) {
        Attribute attr = (Attribute)ne.next();
        String id = new String(attr.getID());
        Attribute attrClone = new BasicAttribute(id);
        NamingEnumeration ne1 = attr.getAll();
        while (ne1.hasMore()) {
          String val = new String(ne1.next().toString());
          attrClone.add(val);
        }
        clone.put(attrClone);
      }
    } catch (NamingException ex) {
      ex.printStackTrace();
    }
    return clone;
  }

  /**
   * Clone Entities.
   * @param entities Map Original copy
   * @return Map      Clone of Entities
   */
  public static Map cloneEntities(Map entities) {
    Map clone = Collections.synchronizedMap(new HashMap());
    for (Iterator it = entities.values().iterator(); it.hasNext();) {
      Entity entityClone = (Entity)((EntityImpl)it.next()).clone();
      clone.put(entityClone.getName(), entityClone);
    }
    return clone;
  }

  /**
   * Return a collection of entity names.
   * @param entities Collection  Entities
   * @return Collection          Collection of names
   */
  public static Collection getEntityNames(Collection entities) {
    Collection names = new ArrayList();
    for (Iterator it = entities.iterator(); it.hasNext();) {
      Entity entity = (Entity)it.next();
      names.add(entity.getName());
    }
    return names;
  }

  /**
   * Create an attribute ModificationArray based on the differences between
   * 2 attribute sets.
   * @param oldAttrs Attributes
   * @param newAttrs Attributes
   * @return ModificationItem[]
   */
  public static ModificationItem[] getAttributeModificationItems(Attributes oldAttrs,
                                                                 Attributes newAttrs) {
   List modsList = new ArrayList();
   for(NamingEnumeration enums = oldAttrs.getAll(); enums.hasMoreElements();) {
     Attribute oldAttr = (Attribute) enums.nextElement();
     Attribute newAttr = (Attribute) newAttrs.get(oldAttr.getID());
     if (newAttr == null || !newAttr.equals(oldAttr)) {
       modsList.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE, oldAttr));
     }
   }
   for(NamingEnumeration enums = newAttrs.getAll(); enums.hasMoreElements();) {
     Attribute newAttr = (Attribute) enums.nextElement();
     Attribute oldAttr = (Attribute) oldAttrs.get(newAttr.getID());
     if (oldAttr != null || !newAttr.equals(oldAttr)) {
       modsList.add(new ModificationItem(DirContext.ADD_ATTRIBUTE, newAttr));
     }
   }
   return (ModificationItem[])modsList.toArray(new ModificationItem[0]);
 }

 /**
  * Add new attribute id/value to attribute set,
  * @param attrs Attributes
  * @param id String
  * @param value String
  */
 public static void setAttribute(Attributes attrs, String id, String value) {
    if (attrs != null) {
      Attribute attr = attrs.get(id);
      if (attr == null) {
        attrs.put(new BasicAttribute(id, value));
      } else {
        if (!attr.contains(value)) {
          attr.add(value);
        }
      }
    }
  }

  /**
   * Check for existence of attribute id and value in attribute set.
   * @param attrs Attributes
   * @param id String
   * @param value String
   * @return boolean
   */
  public static boolean hasAttribute(Attributes attrs, String id, String value) {
    boolean result = false;
    if (attrs != null) {
      Attribute attr = attrs.get(id);
      result = attr != null && attr.contains(value);
    }
    return result;
  }

  /**
   * Converts a collection of entities to a compact string representation of names
   */
  public static String entityNames(Collection members) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = members.iterator(); it.hasNext(); ) {
      sb.append(it.next().toString() + (it.hasNext() ? "," : ""));
    }
    return (sb.append("]").toString());
  }

}