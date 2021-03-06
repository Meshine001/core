/**
 * 
 */
package com.dotcms.rendering.velocity.services;

import java.io.File;
import java.util.HashSet;

import java.util.Set;

import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.ResourceCache;
import org.apache.velocity.runtime.resource.ResourceManager;

import com.dotcms.contenttype.model.type.ContentType;
import com.dotcms.rendering.velocity.util.VelocityUtil;
import com.dotcms.repackage.com.google.common.collect.ImmutableSet;
import com.dotmarketing.business.Cachable;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.business.DotCacheAdministrator;
import com.dotmarketing.business.DotCacheException;
import com.dotmarketing.util.Logger;

import com.liferay.util.StringUtil;

/**
 * @author Jason Tesser
 * @author Andres Olarte
 * @since 1.6.5 The DotResourceCache was created to allow velocities cache to be distributed across
 *        nodes in a cluster. It also allows the dotCMS to set velocity to always cache and pull
 *        from cache Our services methods which generate the velocity files will handle the filling
 *        and removing of The cache. If something is not in cache though the DotResourceLoader will
 *        be called.
 */
public class DotResourceCache implements ResourceCache, Cachable {


    private DotCacheAdministrator cache;

    private String primaryGroup = "VelocityCache";
    private String macroCacheGroup = "VelocityMacroCache";
    // region's name for the cache
    private String[] groupNames = {primaryGroup, macroCacheGroup};
    private static final String MACRO_PREFIX = "MACRO_PREFIX";
    private final Set<String> ignoreGlobalVM;



    public DotResourceCache() {
        cache = CacheLocator.getCacheAdministrator();
        String files = System.getProperty(RuntimeConstants.VM_LIBRARY);
        Set<String> holder = new HashSet<>();
        for (String file : files.split(",")) {
            if (file != null) {
                // System.out.println("FILE: " +file);
                holder.add(file.trim());
            }
        }
        ignoreGlobalVM = ImmutableSet.copyOf(holder);
    }

    public String[] getMacro(String name) {


        String[] rw = null;
        try {
            rw = (String[]) cache.get(MACRO_PREFIX + name, macroCacheGroup);
        } catch (DotCacheException e) {
            Logger.debug(this, "Cache Entry not found", e);
        }
        return rw;

    }

    public void putMacro(String name, String content) {
        if (name == null || content == null) {
            Logger.warn(this.getClass(), "Cannot add a null macro to cache:" + name + " / " + content);
            return;
        }
        String[] rw = {name, content};
        cache.put(MACRO_PREFIX + name, rw, macroCacheGroup);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.velocity.runtime.resource.ResourceCache#get(java.lang.Object)
     */
    @Override
    public Resource get(final Object resourceKey) {

        final String key = cleanKey(resourceKey.toString());

        try {
            return (Resource) cache.get(key, primaryGroup);
        } catch (DotCacheException e) {
            Logger.debug(this, "Cache Entry not found", e);
        }
        return null;
    }

    @Override
    public void initialize(RuntimeServices rs) {
        cache = CacheLocator.getCacheAdministrator();
    }

    public void addMiss(Object resourceKey) {
        Logger.info(this.getClass(), "velocityMiss:" + resourceKey);
    }

    public boolean isMiss(Object resourceKey) {
        return false;
    }

    @Override
    public Resource put(final Object resourceKey, final Resource resource) {
        if (resource != null && ignoreGlobalVM.contains(resource.getName())) {
            return resource;
        }

        String key = cleanKey(resourceKey.toString());

        // Add the key to the cache
        cache.put(key, resource, primaryGroup);

        return resource;

    }

    @Override
    public Resource remove(final Object resourceKey) {

        final String key = cleanKey(resourceKey.toString());

        try {
            cache.remove(key, primaryGroup);
        } catch (Exception e) {
            Logger.debug(this, e.getMessage(), e);
        }
        return null;
    }

    public void clearCache() {
        for (String group : groupNames) {
            cache.flushGroup(group);
        }

    }



    public String[] getGroups() {
        return groupNames;
    }

    public String getPrimaryGroup() {
        return primaryGroup;
    }

    private String cleanKey(String key) {
        if (key.startsWith(ResourceManager.RESOURCE_TEMPLATE + ""))
            key = key.substring((ResourceManager.RESOURCE_TEMPLATE + "").length());

        if (key.startsWith(File.separatorChar + "")) {
            key = key.substring(1);
        }
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        key = StringUtil.replace(key, '\\', '/');
        return key;
    }



    public void removeContentTypeFile(ContentType contentType) {
        String folderPath = "working/";
        String filePath = folderPath + contentType.inode() + "." + VelocityType.CONTENT_TYPE.fileExtension;

        String velocityRootPath = VelocityUtil.getVelocityRootPath();
        String absolutPath = velocityRootPath + File.separator + filePath;
        java.io.File f = new java.io.File(absolutPath);
        f.delete();
        remove(ResourceManager.RESOURCE_TEMPLATE + filePath);
    }

}
