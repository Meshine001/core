package com.dotcms.system;

/**
 * It is intended to be a global context
 */
public interface AppContext {

    <T> T getAttribute(String attributeName);

    <T> void setAttribute(String attributeName, T attributeValue);
}
