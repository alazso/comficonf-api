/**
 * ComfiConf developer API.
 *
 * <p>A plugin describes one or more configuration resources with a {@link com.comficonf.api.ConfigProvider}
 * and registers them through {@link com.comficonf.api.ComfiConfApi}. The ComfiConf bridge plugin supplies
 * the runtime implementation of this API; plugins compile against it as a {@code compileOnly} dependency
 * and declare {@code softdepend: [ComfiConf]} in {@code plugin.yml}.
 */
@NullMarked
package com.comficonf.api;

import org.jspecify.annotations.NullMarked;
