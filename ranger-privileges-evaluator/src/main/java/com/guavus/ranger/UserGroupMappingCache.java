package com.guavus.ranger;

import com.amazon.dlic.auth.ldap.util.LdapHelper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.elasticsearch.common.settings.Settings;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Divyansh Jain
 */

public class UserGroupMappingCache {

    private Settings settings;
    private LoadingCache<String, Set<String>> userGroupCache = null;
    private int DEFAULT_SIZE = 100;
    final ExecutorService executor = Executors.newFixedThreadPool(1);

    public UserGroupMappingCache() {
    }

    public UserGroupMappingCache(Settings settings) {
        this.settings = settings;
    }

    public void init() {
        init(DEFAULT_SIZE);
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public void init(int size) {
        this.userGroupCache = CacheBuilder.newBuilder()
                .maximumSize(size)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(
                        new CacheLoader<String, Set<String>>() {
                            public Set<String> load(String key) throws Exception {
                                return LdapHelper.findUserGroup(settings, key);
                            }

                        });

        // ToDo: Add code for loading entries from local FS cache for recovery from reboots
    }

    public Set<String> getUserGroups(String user) throws ExecutionException {
        return userGroupCache.get(user);
    }
}
