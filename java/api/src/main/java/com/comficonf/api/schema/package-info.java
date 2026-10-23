/**
 * Declarative description of editable configuration fields.
 *
 * <p>Start fields with the static factories of {@link com.comficonf.api.schema.ConfigField} and collect them
 * with {@link com.comficonf.api.schema.ConfigSchema#of}. Schemas are plain data: they never contain executable
 * code, and the bridge sends them to the web editor as JSON.
 */
@NullMarked
package com.comficonf.api.schema;

import org.jspecify.annotations.NullMarked;
