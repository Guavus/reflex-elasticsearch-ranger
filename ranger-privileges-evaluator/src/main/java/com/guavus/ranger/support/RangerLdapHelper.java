package com.guavus.ranger.support;

import com.amazon.dlic.auth.ldap.util.ConfigConstants;
import joptsimple.internal.Strings;
import org.elasticsearch.common.settings.Settings;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 * @author Divyansh Jain
 */

public class RangerLdapHelper {

    public static Set<String> findUserGroup(Settings settings, String key) {
        String ldapServer = settings.getAsList(ConfigConstants.LDAP_HOSTS).get(0);
        if (Strings.isNullOrEmpty(ldapServer)) {
            return null;
        }

        String bindUserDn = settings.get(ConfigConstants.LDAP_BIND_DN);
        String passwd = settings.get(ConfigConstants.LDAP_PASSWORD);

        String userBase = settings.get(ConfigConstants.LDAP_AUTHC_USERBASE);
        String userSearchFilter = settings.get(ConfigConstants.LDAP_AUTHC_USERSEARCH);
        String userGroupAttribute = settings.get(ConfigConstants.LDAP_USER_GROUP_ATTR);

        String groupBase = settings.get(ConfigConstants.LDAP_GROUP_BASE);
        String groupSearchFilter = settings.get(ConfigConstants.LDAP_GROUP_SEARCH);
        String groupNameAttribute = settings.get(ConfigConstants.LDAP_GROUP_NAME_ATTR);

        String userToBeSearched = key;

        String userSearchDn = userBase.replaceAll("\\{0\\}", userToBeSearched);
        String userSearchFilterDn = userSearchFilter.replaceAll("\\{0\\}", userToBeSearched);

        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        props.put(Context.PROVIDER_URL, ldapServer);
        props.put(Context.SECURITY_AUTHENTICATION, "simple");
        props.put(Context.SECURITY_PRINCIPAL, bindUserDn);
        props.put(Context.SECURITY_CREDENTIALS, passwd);

        DirContext ctx = null;
        NamingEnumeration<javax.naming.directory.SearchResult> results = null;
        Set<String> res = new HashSet<String>();
        Set<String> userGrpRes = new HashSet<String>();

        try {

            //ctx = new InitialDirContext(props);
            ctx = new InitialDirContext(props);
            String grpId = null;
            boolean gidFlag = false;

            if (!Strings.isNullOrEmpty(userGroupAttribute)) {
                SearchControls controls = new SearchControls();
                String[] attrIDs = { userGroupAttribute };
                controls.setReturningAttributes(attrIDs);
                controls.setSearchScope(SearchControls.SUBTREE_SCOPE);

                results = ctx.search(userSearchDn, userSearchFilterDn, controls);
                Attribute gid = null;
                while (results.hasMore()) {
                    javax.naming.directory.SearchResult searchResult = (javax.naming.directory.SearchResult) results.next();
                    Attributes attributes = searchResult.getAttributes();
                    gid = attributes.get(userGroupAttribute);
                    break;
                }
                if (gid != null) {
                    if (userGroupAttribute.equalsIgnoreCase("gidNumber")) {
                        grpId = gid.get().toString();
                        userGrpRes.add(grpId);
                    } else {
                        for (int i = 0; i < gid.size(); i++) {
                            String grpDnName = gid.get(i).toString();
                            userGrpRes.add(grpDnName);
                        }
                    }
                }
                gidFlag = true;
            }
            if (!Strings.isNullOrEmpty(groupBase)) {
                String grpSearchDn1 = groupBase.replaceAll("\\{0\\}", userToBeSearched);
                String grpSearchFilterDn1 = groupSearchFilter.replaceAll("\\{0\\}", userToBeSearched);
                SearchControls grpCtrls = new SearchControls();
                String[] attrID1 = { userGroupAttribute, groupNameAttribute };
                grpCtrls.setReturningAttributes(attrID1);
                grpCtrls.setSearchScope(SearchControls.SUBTREE_SCOPE);

                if (gidFlag) {
                    Iterator<String> iter = userGrpRes.iterator();
                    while (iter.hasNext()) {
                        String usrGrpVal = iter.next();
                        String grpSearchDn = grpSearchDn1.replaceAll("\\{1\\}", usrGrpVal);
                        String grpSearchFilterDn = grpSearchFilterDn1.replaceAll("\\{1\\}", usrGrpVal);

                        results = ctx.search(grpSearchDn, grpSearchFilterDn, grpCtrls);
                        while (results.hasMore()) {
                            javax.naming.directory.SearchResult searchResult = (javax.naming.directory.SearchResult) results.next();
                            Attributes attributes = searchResult.getAttributes();
                            Attribute grpName = attributes.get(groupNameAttribute);
                            if (grpName != null) {
                                System.out.println("Found group name = " + grpName.get().toString());
                                res.add(grpName.get().toString());
                            }
                        }
                    }
                } else {
                    results = ctx.search(grpSearchDn1, grpSearchFilterDn1, grpCtrls);
                    while (results.hasMore()) {
                        javax.naming.directory.SearchResult searchResult = (javax.naming.directory.SearchResult) results.next();
                        Attributes attributes = searchResult.getAttributes();
                        Attribute grpName = attributes.get(groupNameAttribute);
                        if (grpName != null) {
                            System.out.println("Found group name = " + grpName.get().toString());
                            res.add(grpName.get().toString());
                        }
                    }
                }
            } else {
                res = userGrpRes;
            }
        } catch (Exception e) {
            System.out.println("Error in querying ldap : " + e.getMessage());
        }
        finally {
            try {
                //results.close();
                ctx.close(); } catch(Exception ex) { }
        }
        return res;
    }
}
