# ComfiConf developer guide

This guide shows how to make the settings of your Paper plugin editable on the ComfiConf website. It walks
through the reference plugin in `examples/demo-plugin` (ComfiConfDemo, a voucher plugin) and documents every
part of the developer API you need.

The normative definition is the Javadoc of `java/api`, published at
https://javadoc.io/doc/com.comficonf/comficonf-api. When this guide and the Javadoc disagree, the Javadoc wins.

## Contents

1. [How ComfiConf works](#1-how-comficonf-works)
2. [Requirements](#2-requirements)
3. [Adding the dependency](#3-adding-the-dependency)
4. [Declaring the soft dependency](#4-declaring-the-soft-dependency)
5. [The optional hook pattern](#5-the-optional-hook-pattern)
6. [Registering a resource](#6-registering-a-resource)
7. [Writing a ConfigProvider](#7-writing-a-configprovider)
8. [Bukkit YAML with SectionMapper](#8-bukkit-yaml-with-sectionmapper)
9. [Schema reference](#9-schema-reference)
10. [Values and paths](#10-values-and-paths)
11. [Validation: what the bridge checks for you](#11-validation-what-the-bridge-checks-for-you)
12. [Revisions, backups and SafeFileWriter](#12-revisions-backups-and-safefilewriter)
13. [Threading rules and callSync](#13-threading-rules-and-callsync)
14. [Reporting the apply outcome truthfully](#14-reporting-the-apply-outcome-truthfully)
15. [Sensitive fields](#15-sensitive-fields)
16. [notifyChanged and unregistering](#16-notifychanged-and-unregistering)
17. [Testing your integration](#17-testing-your-integration)
18. [Troubleshooting](#18-troubleshooting)
19. [Checklist](#19-checklist)

## 1. How ComfiConf works

ComfiConf gives server owners a web editor for plugin settings. Your plugin defines what can be configured and
how a change is activated; ComfiConf provides the interface.

```
web editor  --HTTPS-->  backend  <--WebSocket (outbound from the server)--  ComfiConf bridge plugin
                                                                                   |
                                                                       your plugin (ConfigProvider)
```

1. The server owner installs the **ComfiConf bridge plugin** (plugin name `ComfiConf`). It opens an outbound
   WebSocket to the backend, so no port is opened on the Minecraft server, and is paired with the owner's account
   once with `/comficonf pair`.
2. Your plugin registers a **configuration resource** with the bridge: a stable id, a display name and a
   `ConfigProvider` with four callbacks.
3. When the owner opens your resource, the bridge calls `describe()` (the schema) and `read()` (current values).
   The editor is generated from the schema; there is no plugin-specific web code.
4. When the owner applies a change, the bridge checks the submitted values against your schema, computes the
   changes, calls your `validate()` for rules the schema cannot express, and finally `apply()`, where your plugin
   persists the values and activates them. The bridge reports your truthful outcome back to the editor.

ComfiConf is **not** a remote file editor. The bridge never reads or writes your files and exposes no file,
console or command access to the backend. Everything goes through your callbacks, so your plugin keeps full
control over storage, validation and activation.

## 2. Requirements

| Item | Version |
| --- | --- |
| Server | Paper 26.1.2, 26.2 or 26.3 (and Purpur builds based on them). Folia is not supported yet. |
| Java | 25 |
| API artifact | `com.comficonf:comficonf-api:0.1.0`, API version 1 (`ComfiConfApi.API_VERSION`) |
| Runtime | `ComfiConf-Bridge-0.1.0.jar` installed on the server. Without it your plugin must still work. |

## 3. Adding the dependency

The API JAR has no runtime dependencies of its own. At runtime its classes come from the bridge JAR, which
contains exactly one copy of `com.comficonf.api`. Your plugin compiles against the API and **never shades it**.
A shaded copy creates a second set of API classes that the bridge cannot see; see
[Troubleshooting](#18-troubleshooting).

The API is on Maven Central as `com.comficonf:comficonf-api`, with its sources and Javadoc, so no extra repository
is needed for it.

### Gradle (Kotlin DSL)

```kotlin
plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    // Provided by the ComfiConf bridge at runtime. compileOnly keeps it out of your JAR.
    compileOnly("com.comficonf:comficonf-api:0.1.0")

    // The API is needed on the test classpath to unit test your provider.
    testImplementation("com.comficonf:comficonf-api:0.1.0")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.74-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
```

If you use the Shadow plugin for other libraries, keep ComfiConf out of it: a `compileOnly` dependency is never
part of the shadow JAR. Do not move the API to `implementation` "to make it work"; that is the shading mistake.

### Maven

```xml
<repositories>
  <repository>
    <id>papermc</id>
    <url>https://repo.papermc.io/repository/maven-public/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>26.1.2.build.74-stable</version>
    <scope>provided</scope>
  </dependency>
  <dependency>
    <groupId>com.comficonf</groupId>
    <artifactId>comficonf-api</artifactId>
    <version>0.1.0</version>
    <!-- Provided by the ComfiConf bridge at runtime; never shade it. -->
    <scope>provided</scope>
  </dependency>
</dependencies>
```

Maven resolves the API from Maven Central, its default repository; only Paper needs the `papermc` repository. If
you use the `maven-shade-plugin`, `provided` dependencies are not included.

## 4. Declaring the soft dependency

The bridge must be enabled before your plugin so that its classes and its `ComfiConfApi` service exist when your
`onEnable` runs.

### plugin.yml

```yaml
name: ComfiConfDemo
main: com.comficonf.demo.ComfiConfDemoPlugin
version: '0.1.0'
api-version: '26.1'
softdepend: [ComfiConf]
```

Use `depend: [ComfiConf]` instead only if your plugin cannot run without ComfiConf. For almost every plugin,
ComfiConf is a convenience, so `softdepend` is right.

### paper-plugin.yml

Paper plugins declare the dependency with an explicit load order and must join the bridge's classpath to see
the API classes:

```yaml
name: ComfiConfDemo
main: com.comficonf.demo.ComfiConfDemoPlugin
version: '0.1.0'
api-version: '26.1'
dependencies:
  server:
    ComfiConf:
      load: BEFORE
      required: false
      join-classpath: true
```

Paper plugins cannot declare commands in YAML; register them with `registerCommand` or the lifecycle API. The
ComfiConf integration itself is the same for both formats.

## 5. The optional hook pattern

Your plugin has to start normally when the bridge is not installed. Java loads a class when it is first used,
and loading a class that refers to a missing class fails with `NoClassDefFoundError`. So:

1. The **main class must not reference any `com.comficonf.api` type**: no fields, parameters, return types,
   local variables, casts or `catch` clauses of API types.
2. Check `getServer().getPluginManager().isPluginEnabled("ComfiConf")` first.
3. Only then call a separate class (here `ComfiConfIntegration`) that uses the API.

The demo keeps the integration behind a small interface declared in an API-free package, so the main class can
hold it in a field:

```java
package com.comficonf.demo;

/** The plugin's view of its optional ComfiConf integration. */
public interface DemoIntegration {
    String resourceId();
    boolean isRegistered();
    void notifyChanged();
    void close();
}
```

```java
// ComfiConfDemoPlugin.onEnable(), after loading config.yml into the runtime holder
if (getServer().getPluginManager().isPluginEnabled("ComfiConf")) {
    try {
        integration = ComfiConfIntegration.register(this, runtime, configFile(), backupDirectory(),
                this::notifyAdmins);
        getLogger().info("Registered " + integration.resourceId() + " with ComfiConf");
    } catch (LinkageError e) {
        // ComfiConf is installed, but its classes do not match the API this plugin was compiled against.
        Plugin bridge = getServer().getPluginManager().getPlugin("ComfiConf");
        getLogger().warning(incompatibleBridgeMessage(
                bridge == null ? "?" : bridge.getPluginMeta().getVersion(), e));
    } catch (RuntimeException e) {
        getLogger().log(Level.WARNING, "Could not register with ComfiConf; continuing without it", e);
    }
} else {
    getLogger().info("ComfiConf is not installed; vouchers are configured through config.yml only");
}

// In the same class
static String incompatibleBridgeMessage(String bridgeVersion, LinkageError error) {
    String missing = error.getMessage() == null
            ? error.getClass().getSimpleName()
            : "missing " + error.getMessage().replace('/', '.');
    return "The installed ComfiConf " + bridgeVersion + " does not include the ComfiConf API this plugin needs ("
            + missing + "). Update ComfiConf to the version this plugin was built for; vouchers are configured"
            + " through config.yml only until then.";
}
```

```java
@Override
public void onDisable() {
    if (integration != null) {
        integration.close();
        integration = null;
    }
}
```

`ComfiConfIntegration.register` has only plugin and JDK types in its signature, so referring to it does not load
any API class until the call actually runs. The demo's test `NoApiReferenceTest` scans the compiled class files
to make sure no class outside `com.comficonf.demo.integration` mentions `com/comficonf/api`; copy that test into
your project (see [Testing](#17-testing-your-integration)).

## 6. Registering a resource

A resource is one editable unit, usually one configuration file. Its id has the form `namespace:name`, each
part 1 to 64 characters of `[a-z0-9._-]` starting with a letter or digit. **Keep the id stable across plugin
versions**: the website and the audit history refer to it.

```java
public static DemoIntegration register(JavaPlugin plugin, VoucherRuntime runtime, Path configFile,
        Path backupDirectory, Consumer<String> adminNotifier) {
    ConfigResource resource = ConfigResource.builder("comficonf-demo:vouchers")
            .displayName("Vouchers")
            .description("Voucher rewards, cooldowns and announcements of ComfiConfDemo.")
            .provider(new VoucherConfigProvider(configFile, backupDirectory, runtime, adminNotifier,
                    plugin.getLogger()))
            .callbackThread(CallbackThread.WORKER)
            .build();
    return new ComfiConfIntegration(ComfiConfApi.get().register(plugin, resource));
}
```

| Builder method | Meaning |
| --- | --- |
| `displayName(String)` | Name in the editor, at most 80 characters. Defaults to the name part of the id. |
| `description(String)` | Short description, at most 500 characters. |
| `provider(ConfigProvider)` | Required. The callbacks. |
| `callbackThread(CallbackThread)` | `WORKER` (default) or `MAIN`; see [Threading](#13-threading-rules-and-callsync). |

`ComfiConfApi` lookup and registration:

| Call | Behavior |
| --- | --- |
| `ComfiConfApi.get()` | Returns the bridge's implementation from the Bukkit `ServicesManager`; throws `IllegalStateException` when the bridge is not enabled. |
| `ComfiConfApi.find()` | Same, as an `Optional`. |
| `register(plugin, resource)` | Registers the resource. Throws `IllegalArgumentException` if the id is already registered (by any plugin) and `IllegalStateException` if your plugin is not enabled, so call it in `onEnable`, not `onLoad`. |
| `registrations(plugin)` / `unregisterAll(plugin)` | Your active registrations; remove all of them. |
| `connectionState()` | `DISCONNECTED`, `CONNECTING`, `UNPAIRED` or `CONNECTED`. Informational; you never need it to register. |
| `apiVersion()` / `bridgeVersion()` | Version of the running bridge. A plugin compiled against a newer API compares `apiVersion()` with `ComfiConfApi.API_VERSION` before calling newer methods. |

You can register several resources, for example one per configuration file.

## 7. Writing a ConfigProvider

`ConfigProvider` has four callbacks. The bridge never runs two callbacks of the same resource at the same time,
and one validate or apply operation runs its callbacks back to back.

```java
public interface ConfigProvider {
    ConfigSchema describe();
    ConfigSnapshot read() throws Exception;
    default ValidationResult validate(ConfigValues proposed) throws Exception { return ValidationResult.ok(); }
    ApplyResult apply(ApplyContext context) throws Exception;
}
```

### describe()

Returns the fields the editor may show and change. Called whenever the editor opens the resource and before every
validate and apply, so a schema can depend on runtime data (for example choices built from loaded content). The
demo builds its schema once, together with the `SectionMapper` that maps it to `config.yml`
([section 8](#8-bukkit-yaml-with-sectionmapper)):

```java
public final class VoucherConfigProvider implements ConfigProvider {
    private final SectionMapper mapper = VoucherSchema.mapper();

    @Override
    public ConfigSchema describe() {
        return mapper.schema();
    }
}

// VoucherSchema
public static SectionMapper mapper() {
    return SectionMapper.builder(create()).keyedSection("vouchers").build();
}
```

An excerpt of `VoucherSchema.create()`:

```java
ConfigSchema.of(
        ConfigField.object("settings").label("General").fields(
                ConfigField.string("prefix").label("Message prefix")
                        .description("MiniMessage text shown before every voucher message.")
                        .maxLength(64),
                ConfigField.bool("announce-redemptions"),
                ConfigField.stringList("announcement-channels")
                        .choice("chat", "Chat").choice("actionbar", "Action bar").choice("title", "Title")
                        .uniqueItems().minItems(1).ordered(false)
                        .visibleWhen("announce-redemptions", true),
                ConfigField.integer("max-redemptions-per-day").range(0, 100).slider(),
                ConfigField.decimal("bonus-multiplier").range(0.0, 10.0).step(0.1).unit("x"),
                ConfigField.enumeration("storage-mode")
                        .choice("yaml", "YAML file").choice("sqlite", "SQLite database")
                        .restartRequired(),
                ConfigField.stringList("disabled-worlds")
                        .itemPattern("[A-Za-z0-9_.-]{1,64}", "World names use letters, digits, '_', '-' and '.'."),
                ConfigField.text("welcome-message").maxLength(500),
                ConfigField.string("webhook-url").label("Webhook URL").optional().writeOnly()
                        .pattern("https://.+", "Use an https:// URL.")),
        ConfigField.objectList("vouchers").itemKey("id").itemTitle("display-name").itemLabel("voucher")
                .minItems(1)
                .fields(
                        ConfigField.string("id").pattern("[a-z0-9_-]{1,32}"),
                        ConfigField.string("display-name").length(1, 48),
                        ConfigField.bool("enabled"),
                        ConfigField.material("material").itemsOnly(),
                        ConfigField.integer("cooldown-seconds").label("Cooldown").range(0, 604_800)
                                .unit("seconds"),
                        ConfigField.string("permission").pattern("[a-z0-9_.-]+").maxLength(64),
                        ConfigField.integer("weight").range(1, 1000),
                        ConfigField.enumeration("rarity")
                                .choice("common", "Common").choice("rare", "Rare")
                                .choice("epic", "Epic").choice("legendary", "Legendary")));
```

Only declared keys are ever sent to the editor. Everything else in your file (`config-version`, a `metrics`
section, voucher `lore`) stays private and must be preserved by your `apply`.

### read()

Returns the current values and a **revision** identifying that state. The revision must change whenever the stored
values change, including manual edits, so the bridge can detect conflicts. For files, read the bytes **once** and
derive both the values and the revision from the same bytes:

```java
@Override
public ConfigSnapshot read() throws IOException, InvalidConfigException {
    byte[] content = Files.readAllBytes(configFile);
    ConfigValues values = mapper.read(VoucherConfigLoader.parse(content));
    return ConfigSnapshot.of(values, Revisions.of(content));
}
```

`mapper.read` maps the YAML onto the schema: the `vouchers` map becomes a list in file order with the map key as
the `id` field, and the empty `webhook-url` of the optional field becomes "no value" (`null`). Without
`SectionMapper`, return values with the types of [section 10](#10-values-and-paths); keys you return that the schema
does not declare are dropped by the bridge.

### validate(proposed)

Called with the complete proposed values **after** the bridge has checked them against the schema (types, ranges,
patterns, choices, required values, list sizes, duplicate keys and so on). Only check rules the schema cannot
express, typically cross-field rules. Errors block the change; warnings are shown but never block.

```java
@Override
public ValidationResult validate(ConfigValues proposed) {
    ValidationResult.Builder result = ValidationResult.builder();
    if (proposed.getString("settings.prefix", "").indexOf('§') >= 0) {
        result.error("settings.prefix", "Use MiniMessage tags such as <gold> instead of § color codes.");
    }
    List<ConfigValues> vouchers = proposed.getObjectList("vouchers");
    boolean anyEnabled = false;
    for (int i = 0; i < vouchers.size(); i++) {
        ConfigValues voucher = vouchers.get(i);
        boolean enabled = voucher.getBoolean("enabled", false);
        anyEnabled |= enabled;
        if (enabled && voucher.getLong("cooldown-seconds", 0) < 60) {
            result.warning("vouchers[" + i + "].cooldown-seconds",
                    "A cooldown under 60 seconds lets players redeem this voucher almost continuously.");
        }
        if (!voucher.getString("permission", "").startsWith("vouchers.")) {
            result.warning("vouchers[" + i + "].permission",
                    "Permissions usually start with 'vouchers.' so they can be granted together.");
        }
    }
    if (!anyEnabled) {
        result.error("vouchers", "At least one voucher must be enabled.");
    }
    // Text that config.yml cannot store first, see "Text that YAML cannot store" below.
    return mapper.validate(proposed).merge(result.build());
}
```

Issue paths use the syntax of [section 10](#10-values-and-paths); `""` addresses the whole resource. Messages are
shown to the server owner (at most 500 characters). `ValidationIssue` also accepts your own machine-readable code
(`[a-z0-9_]{1,64}`); the builder methods use the code `plugin`.

#### Text that YAML cannot store

Bukkit's `YamlConfiguration` (SnakeYAML) saves a string that contains a control character, such as an escape
code pasted from a terminal (U+001B), a bell (U+0007), DEL (U+007F), a C1 control (U+0080 to U+009F other than
U+0085, for example U+0092 from mis-decoded text), U+FFFE or U+FFFF, as `!!binary` data instead of text. Reading
the file back then returns a `byte[]`, not the string, so your plugin can no longer load the value. U+0085 (next
line) is read back as a line feed. Tab, line feed, carriage return and all other characters are stored
unchanged.

`SectionMapper.validate(values)` reports such text as an error at its path, for text fields and string list
items. It accepts tab, line feed, carriage return, U+0020 to U+007E, U+00A0 to U+D7FF, U+E000 to U+FFFD and
U+10000 and above. The demo merges it with its own rules, as shown above, and also checks it in `apply`.

If you read YAML yourself, never turn an unexpected value into text with `String.valueOf` in `read()`: a `byte[]`
becomes `[B@2dafd777`, and the next apply writes that back as the real value. `SectionMapper.read` throws for
`byte[]` and section values instead, so the editor reports that the file cannot be read and the server log names
the path.

### apply(context)

Persists and activates the validated values. The demo:

1. takes the runtime's update lock, so that its own `/vouchers reload` cannot swap in an older file at the same
   time,
2. reads the **current** file bytes and compares their revision with `context.baseRevision()` (another edit may
   have happened since the editor loaded the values),
3. refuses text that YAML cannot store as text (see [validate](#text-that-yaml-cannot-store)),
4. writes the proposed values into the parsed document with `SectionMapper.write`, keeping every key and comment
   the schema does not cover,
5. parses the new bytes again and checks that they read back to the proposed values; otherwise it returns
   `REJECTED` and writes nothing,
6. writes the new bytes with `SafeFileWriter`, which re-checks the revision under a lock, backs up the old file
   and replaces it atomically,
7. builds a new immutable runtime configuration **from the parsed new bytes**, so the active configuration is
   exactly what is on disk, swaps it in atomically and releases the update lock,
8. tells online administrators on the main thread through `context.runSync`,
9. reports the truthful result.

```java
@Override
public ApplyResult apply(ApplyContext context) throws IOException, InvalidConfigException {
    String newRevision;
    runtime.updateLock().lock();
    try {
        byte[] current = Files.readAllBytes(configFile);
        String currentRevision = Revisions.of(current);
        if (!currentRevision.equals(context.baseRevision())) {
            return ApplyResult.conflict(currentRevision);
        }
        ValidationResult storable = mapper.validate(context.proposed());
        if (!storable.isValid()) {
            return ApplyResult.rejected(storable);
        }
        YamlConfiguration yaml = VoucherConfigLoader.parse(current);
        mapper.write(context.proposed(), yaml);
        byte[] updated = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        YamlConfiguration saved = VoucherConfigLoader.parse(updated);
        List<String> altered;
        try {
            // Paths whose value in the new bytes differs from the proposal; null and missing count as equal.
            altered = alteredPaths(context.proposed().asMap(), mapper.read(saved).asMap(), "");
        } catch (IllegalStateException e) {
            logger.log(Level.WARNING, "config.yml would not read back the values from ComfiConf", e);
            altered = List.of("");
        }
        if (!altered.isEmpty()) {
            ValidationResult.Builder rejection = ValidationResult.builder();
            for (String path : altered) {
                rejection.error(path, "config.yml cannot store this value unchanged, so nothing was saved.");
            }
            return ApplyResult.rejected(rejection.build());
        }
        try {
            newRevision = SafeFileWriter.write(configFile, updated, context.baseRevision(), backups)
                    .newRevision();
        } catch (RevisionConflictException e) {
            return ApplyResult.conflict(e.currentRevision());
        }

        VoucherConfig next;
        try {
            next = runtimeLoader.load(saved);
        } catch (InvalidConfigException | RuntimeException e) {
            logger.log(Level.WARNING, "Saved config.yml from ComfiConf, but could not activate it", e);
            return ApplyResult.savedRestartRequired(newRevision, "The values were saved, but the plugin could"
                    + " not load them (" + e.getMessage() + "). They take effect after a restart or a"
                    + " successful /vouchers reload.");
        }
        runtime.swap(next);
    } finally {
        runtime.updateLock().unlock();
    }
    try {
        context.runSync(() -> adminNotifier.accept("Voucher settings were updated through ComfiConf by "
                + context.actor().label() + "."));
    } catch (Exception e) {
        logger.log(Level.FINE, "Could not notify online administrators", e);
    }

    String proposedMode = context.proposed().getString("settings.storage-mode");
    boolean storageChanged = !proposedMode.equals(context.current().get("settings.storage-mode"));
    if (storageChanged && !proposedMode.equals(runtime.activeStorageMode())) {
        return ApplyResult.savedRestartRequired(newRevision, "Saved. The storage mode change to '"
                + proposedMode + "' takes effect after a restart; all other changes are active.",
                List.of("settings.storage-mode"));
    }
    return ApplyResult.applied(newRevision);
}
```

What `ApplyContext` gives you:

| Method | Content |
| --- | --- |
| `resourceId()`, `operationId()` | The resource and a unique id of this operation. A retried submission carries the same id; the bridge never applies it twice. |
| `baseRevision()` | Revision the editor started from. It matched the stored revision when the operation started. |
| `current()` | Values read at the start of the operation, including sensitive values and keys the schema does not declare. Numbers of declared `integer` and `decimal` fields are converted to the declared type the same way as for the editor (a YAML `2` in a `decimal` field becomes `2.0`), so an unchanged number equals its value in `proposed()`. Everything else is what your `read()` returned and is not checked against the schema, so compare with `get(path)` instead of typed getters such as `getString`, which throw on an unexpected type (after your file was already written). To find out which fields changed, `changes()` is the most direct source. |
| `proposed()` | Complete validated values to persist, including unchanged and sensitive values. |
| `changes()` | The computed differences (`FieldChange`: path, kind, apply mode, sensitive flag, item key, before, after). Never empty. |
| `requiresRestart()` | Whether a changed field is declared `restartRequired()`. |
| `actor()` | The account that requested the change (`id`, `label` such as an email address, meant for logs). |
| `callSync(task)` / `runSync(task)` | Run code on the server main thread and wait for it; see [Threading](#13-threading-rules-and-callsync). |

#### Preserving what the schema does not cover

The editor only knows declared fields, so `apply` must merge, not overwrite. `SectionMapper.write` does: it updates
the declared `settings` keys in place (comments and unknown keys survive, and unchanged values keep their exact
YAML form), and rebuilds the `vouchers` map in the proposed order by **reusing each existing voucher section** (so
its `lore` and comments survive), dropping removed vouchers and creating sections for added ones.

Numbers stay numbers: `Long` values are written back as YAML integers and `Double` values as YAML floats. Bukkit's
`YamlConfiguration` keeps comments of keys that still exist when `parseComments` is enabled (the default). If you
write your own mapping instead, it must do the same.

## 8. Bukkit YAML with SectionMapper

`com.comficonf.api.bukkit.SectionMapper` maps a Bukkit `ConfigurationSection`, such as a `YamlConfiguration` loaded
from your `config.yml`, to the values of your schema and back, so you do not write a mapper. It is part of the API
JAR; there is no extra dependency. The schema mirrors the file: each field is stored under its key, an `object`
field is a nested section, and an `objectList` field is a list of sections, or a section keyed by item id when you
declare it with `keyedSection`.

A complete provider for a file like this:

```yaml
prefix: "<gold>[Homes]</gold> "
max-homes: 5
kits:
  starter:          # kept: comments, and keys the schema does not declare
    icon: WOODEN_SWORD
    cooldown: 3600
```

```java
public final class HomesConfigProvider implements ConfigProvider {

    private final Path file;
    private final BackupPolicy backups;
    private final HomesPlugin plugin;
    private final SectionMapper mapper = SectionMapper.builder(ConfigSchema.of(
                    ConfigField.string("prefix").maxLength(64),
                    ConfigField.integer("max-homes").range(1, 100),
                    ConfigField.objectList("kits").itemKey("id").fields(
                            ConfigField.string("id").pattern("[a-z0-9_-]{1,32}"),
                            ConfigField.material("icon").itemsOnly(),
                            ConfigField.integer("cooldown").min(0).unit("seconds"))))
            .keyedSection("kits")   // kits.<id> sections instead of a YAML list
            .build();

    public HomesConfigProvider(HomesPlugin plugin, Path file, Path backupDirectory) {
        this.plugin = plugin;
        this.file = file;
        this.backups = BackupPolicy.in(backupDirectory);
    }

    @Override
    public ConfigSchema describe() {
        return mapper.schema();
    }

    @Override
    public ConfigSnapshot read() throws IOException, InvalidConfigurationException {
        byte[] content = Files.readAllBytes(file);
        return ConfigSnapshot.of(mapper.read(parse(content)), Revisions.of(content));
    }

    @Override
    public ValidationResult validate(ConfigValues proposed) {
        return mapper.validate(proposed);   // add your own rules with .merge(...)
    }

    @Override
    public ApplyResult apply(ApplyContext context) throws Exception {
        byte[] current = Files.readAllBytes(file);
        if (!Revisions.of(current).equals(context.baseRevision())) {
            return ApplyResult.conflict(Revisions.of(current));
        }
        YamlConfiguration yaml = parse(current);
        mapper.write(context.proposed(), yaml);
        String newRevision;
        try {
            newRevision = SafeFileWriter.write(file, yaml.saveToString().getBytes(StandardCharsets.UTF_8),
                    context.baseRevision(), backups).newRevision();
        } catch (RevisionConflictException e) {
            return ApplyResult.conflict(e.currentRevision());
        }
        try {
            context.runSync(() -> plugin.loadSettings(yaml));   // your code: activate what was written
        } catch (Exception e) {
            return ApplyResult.savedRestartRequired(newRevision, "Saved, but the plugin could not load the new"
                    + " settings. They take effect after a restart.");
        }
        return ApplyResult.applied(newRevision);
    }

    private static YamlConfiguration parse(byte[] content) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(new String(content, StandardCharsets.UTF_8));
        return yaml;
    }
}
```

What the mapper does (the Javadoc of `SectionMapper` has every rule):

- **Reads** exactly the declared keys. YAML integers become `Long`, any number in a `decimal` field becomes
  `Double`, and numbers or booleans in text fields become text, so an unquoted numeric secret arrives as a
  `String`. A value of the wrong kind that can still be shown, such as `'yes'` in a boolean field, is passed on
  unchanged, and the editor reports it at its path. Values that cannot be shown without losing data (a section where
  a single value belongs, `!!binary` data, a date) make `read` throw `IllegalStateException` with the path, so the
  editor reports that the file cannot be read.
- **Writes** only declared keys. Undeclared keys and the comments of kept keys stay, unchanged values keep their YAML
  form (a decimal stored as `2` stays `2`), and list items keep their undeclared keys by matching the stored item
  with the same `itemKey`. Items of a `keyedSection` list reuse the stored section of the same id.
- **Missing values**: a missing key reads as `null`, and a missing required list as an empty list. For optional
  `string`, `text`, `enum` and `material` fields, `""` in the file reads as `null` and `null` is written as `""`, so
  `webhook-url: ""` keeps its line and comment. Clearing any other optional field removes its key.
- **Validates** what YAML cannot store unchanged: text with control characters (see
  [Text that YAML cannot store](#text-that-yaml-cannot-store)) and `keyedSection` ids containing `.`.

`SectionMapper` is immutable and thread-safe; a `YamlConfiguration` is not, so parse a new one in each callback as
above. The demo additionally parses the new bytes again and checks that they read back to the proposed values
before it writes them ([apply(context)](#applycontext)).

## 9. Schema reference

Create fields with the static factories of `ConfigField` and collect them with `ConfigSchema.of(...)` or
`ConfigSchema.builder()`. Builders validate eagerly and throw `SchemaException` (an `IllegalArgumentException`)
for inconsistent definitions, so a broken schema fails at `describe()` time in your own tests.

### Common attributes (all field types)

| Builder method | Default | Effect in the editor and bridge |
| --- | --- | --- |
| `label(String)` | derived from the key (`cooldown-seconds` becomes "Cooldown seconds") | Field label, at most 80 characters. Labels also build change labels such as `Vouchers › Daily Reward › Cooldown`. |
| `description(String)` | none | Help text, at most 1000 characters. |
| `optional()` / `required(boolean)` | required | A required value must be present and not `null`; required `string`, `text`, `enum` and `material` values must also be non-empty. Required lists may be empty unless `minItems` says otherwise. Objects are always present and cannot be optional. |
| `readOnly()` | false | Shown but not editable. A submitted value that differs from the current value is rejected (`read_only`). Applies to the whole subtree of objects and lists. Fields of an object list item that was added in the editor have no current value and are not compared; check them in `validate` if they matter. |
| `restartRequired()` / `applyMode(ApplyMode)` | `LIVE` | Declares that changes only take effect after a restart. The editor highlights it and the bridge lists such changes in `restartRequired` when you return `savedRestartRequired` without paths. |
| `visibleWhen(field, value)` / `visibleWhen(VisibilityCondition)` | always visible | Shows the field only while a sibling scalar, non-sensitive field matches (`whenEquals`, `whenNotEquals`, `whenIn`, `whenNotIn`). Hidden fields keep their values and are still validated. The compared values must have the sibling's value type: a `double` such as `1.0` for a `decimal` sibling (`1` throws `SchemaException`), and a `long` within ±9007199254740991 for an `integer` sibling. |

Keys are 1 to 64 characters of `[A-Za-z0-9_-]` and unique among siblings. A schema has at most 2000 fields in
total and at most 8 levels of nesting.

### Field types

| Factory | Value type (`ConfigValues`) | JSON | Editor control |
| --- | --- | --- | --- |
| `ConfigField.string(key)` | `String` | string | text input |
| `ConfigField.text(key)` | `String` | string | text area |
| `ConfigField.bool(key)` | `Boolean` | boolean | toggle |
| `ConfigField.integer(key)` | `Long`, within ±9007199254740991 | integer | number input, slider with `slider()` |
| `ConfigField.decimal(key)` | `Double` | number | decimal input, slider with `slider()` |
| `ConfigField.enumeration(key)` / `enumeration(key, MyEnum.class)` | `String` (a choice value) | string | dropdown |
| `ConfigField.stringList(key)` | `List<String>` | array of strings | multi-select with choices, tag input without |
| `ConfigField.material(key)` | `String`, a `Material.name()` such as `DIAMOND_SWORD` | string | searchable material picker |
| `ConfigField.object(key)` | `Map` / nested `ConfigValues` | object | grouped section (top-level objects are editor sections) |
| `ConfigField.objectList(key)` | `List` of objects | array of objects | repeatable cards: add, remove, duplicate, reorder, bulk edit |

### Type-specific attributes

**string, text** (`StringFieldBuilder`)

| Method | Effect |
| --- | --- |
| `minLength(n)`, `maxLength(n)`, `length(min, max)` | Length in Unicode code points. |
| `pattern(regex)`, `pattern(regex, message)` | The whole value must match (`java.util.regex` with default flags, `Matcher.matches()`). With default flags `\s` and POSIX classes such as `\p{Alpha}` are ASCII only, and `.` matches one code point except line terminators. The editor repeats the check while typing only when the pattern has an exact JavaScript translation; constructs without one (inline flags, class intersections, possessive or atomic quantifiers, `\Z`, `\G`, `\R`) are checked by the bridge only, when the owner reviews. The message is shown on mismatch. A pattern has at most 1000 characters and its message at most 300. |
| `placeholder(text)` | Placeholder of the empty input. |
| `defaultValue(text)` | Suggested value for new list items and empty optional values. |
| `sensitive()` | Secret value, never sent to the editor and not editable; see [section 15](#15-sensitive-fields). |
| `writeOnly()` | Secret value that the editor can replace without seeing it. Implies `sensitive()`. |

**boolean**: `defaultValue(boolean)`.

**integer** (`IntegerFieldBuilder`) and **decimal** (`DecimalFieldBuilder`)

| Method | Effect |
| --- | --- |
| `min(n)`, `max(n)`, `range(min, max)` | Inclusive bounds. Integer bounds, steps and defaults must be within ±9007199254740991. |
| `step(n)` | Increment of the editor's controls; a hint, values are not forced to be multiples. |
| `unit(text)` | Unit shown next to the input, such as `seconds` or `x` (at most 24 characters). |
| `slider()` | Adds a slider; needs both bounds. |
| `defaultValue(n)` | Default within the bounds. |

**enum** (`EnumFieldBuilder`): `choice(value)`, `choice(value, label)`, `choice(value, label, description)`,
`choice(Choice)`, `defaultValue(value)`. At least one and at most 500 unique choices. `enumeration(key, MyEnum.class)`
adds one choice per enum constant with a derived label.

**stringList** (`StringListFieldBuilder`)

| Method | Effect |
| --- | --- |
| `choice(...)` | Restricts items to the choices (multi-select). Without choices the list is free-form. |
| `minItems(n)`, `maxItems(n)` | List size bounds. |
| `uniqueItems()` | Items must be distinct; repeats are reported at the repeated item. |
| `ordered(boolean)` | Whether order matters and can be changed (default `true`). |
| `itemMinLength(n)`, `itemMaxLength(n)`, `itemPattern(regex, message)` | Constraints of each item, with the same limits as for strings. |
| `placeholder(text)`, `defaultValue(list)` | As for strings. |

**material** (`MaterialFieldBuilder`): `itemsOnly()` (`Material.isItem()`), `blocksOnly()` (`Material.isBlock()`),
`filter(MaterialFilter)`, `defaultValue(name)`. The bridge accepts only non-legacy materials of the running server
that are blocks or items (not `AIR`, `CAVE_AIR`, `VOID_AIR`).

**object** (`ObjectFieldBuilder`): `fields(...)`, `field(ConfigField)`. At least one child.

**objectList** (`ObjectListFieldBuilder`)

| Method | Effect |
| --- | --- |
| `fields(...)` | Item fields; at least one. Sensitive fields are not allowed inside lists. |
| `itemKey(fieldKey)` | A required `string` item field whose value identifies an item; values must be unique (`duplicate_key`). Change summaries match items by this key, so editing, reordering, adding and removing items is reported precisely. Strongly recommended. |
| `itemTitle(fieldKey)` | Scalar item field used as each card's title and in change labels. Defaults to the item key. |
| `itemLabel(noun)` | Singular noun such as `voucher`, used for "Add voucher" (at most 40 characters). |
| `minItems(n)`, `maxItems(n)` | List size bounds. |
| `ordered(boolean)` | Whether order matters (default `true`). Reordering an ordered keyed list produces a `reordered` change. |

## 10. Values and paths

`ConfigValues` is an immutable tree. Values are normalized when you create it:

| Java value | Stored as | JSON |
| --- | --- | --- |
| `String` | `String` | string |
| `Boolean` | `Boolean` | boolean |
| `byte`, `short`, `int`, `long`, `BigInteger` (fitting a long) | `Long` | integer |
| `float`, `double`, `BigDecimal` | `Double` (finite only) | number |
| `List` | unmodifiable `List` without `null` items | array |
| `Map` with `String` keys, or `ConfigValues` | unmodifiable `Map`, insertion order kept | object |
| `null` | `null` (no value) | `null` or omitted |

For Bukkit YAML, `SectionMapper` ([section 8](#8-bukkit-yaml-with-sectionmapper)) does all conversions in both
directions. If you build values yourself: scalar `YamlConfiguration` values can usually be passed through directly,
since an `Integer` from YAML becomes a `Long`. For the editor, the bridge also converts integers to `Double` for
`decimal` fields and whole decimals to `Long` for `integer` fields. Convert other values whose YAML type differs from
the field type yourself, and convert on write as well (YAML reads `2` in a decimal field as an integer). A
`ConfigurationSection` is not a `Map`, a `!!binary` value is a `byte[]` (see
[Text that YAML cannot store](#text-that-yaml-cannot-store)), and every map key must be a valid field key
(`[A-Za-z0-9_-]{1,64}`); otherwise `ConfigValues.of` throws `IllegalArgumentException`. So
`ConfigValues.of(yaml.getValues(false))` does not work. Build plain maps of the declared keys instead.

Paths join keys with `.` and address list items with `[index]`: `settings.prefix`, `vouchers[2].cooldown-seconds`,
`settings.disabled-worlds[0]`. The empty path is the root. Useful accessors:

```java
values.getString("settings.prefix");                     // throws if missing
values.getString("settings.prefix", "");                 // with fallback
values.getLong("vouchers[0].cooldown-seconds");
values.getInt("settings.max-redemptions-per-day");       // throws if it does not fit an int
values.getDouble("settings.bonus-multiplier");           // also accepts integers
values.getStringList("settings.disabled-worlds");        // empty when missing
values.getObject("settings");                            // nested ConfigValues
values.getObjectList("vouchers");                        // List<ConfigValues>
values.contains("settings.webhook-url");
values.with("settings.prefix", "<aqua>[V]</aqua> ");     // returns a modified copy
values.without("settings.legacy");                       // returns a copy without the key
ConfigValues.builder().put("settings.prefix", "x").build();
```

`ConfigPath.child`, `ConfigPath.index` and `ConfigPath.parse` build and split paths.

## 11. Validation: what the bridge checks for you

Before your `validate` runs, the bridge converts the submitted JSON and checks it against your schema. All
problems are collected and reported as errors with these codes:

| Code | Rule |
| --- | --- |
| `unknown_field` | A key the schema does not declare, at any depth. |
| `invalid_type` | Wrong JSON type. Integers accept numbers without a fractional part (`5.0` becomes `5`) within ±9007199254740991; decimals accept any finite number. Text containing an unpaired UTF-16 surrogate (for example the JSON escape `"\ud800"`) is rejected too, because it cannot be encoded as UTF-8. |
| `required` | Missing or `null` required value; empty required `string`, `text`, `enum` or `material`. |
| `min`, `max` | Number outside the inclusive range. |
| `min_length`, `max_length` | Length in code points outside the range (per item for string lists). |
| `pattern` | No full match; the message is your `patternMessage` when set. |
| `invalid_choice` | Enum value or string list item that is not a choice. |
| `min_items`, `max_items` | List size outside the range. |
| `duplicate_item` | Repeated item in a `uniqueItems` string list, reported at the repeat. |
| `duplicate_key` | Repeated `itemKey` value, reported at `list[i].<itemKey>` of the repeat. |
| `unknown_material`, `material_filter` | Material missing on the server, or not matching `itemsOnly()` / `blocksOnly()`. |
| `read_only` | A read-only value differs from the current value (items added to an object list have none to compare with). |
| `not_writable` | A value was submitted for a `sensitive` field that is not `writeOnly`. |
| `no_changes` | Apply only: the proposal equals the current values. |

Consequently your `validate` and `apply` receive values that contain exactly your declared fields, with the
declared types (`Long`, `Double`, `String`, `Boolean`, lists and maps), satisfying every declared constraint.
Optional fields without a value are present as `null`. You never need to re-check types or ranges. The only
exception: stored values of sensitive fields that the submission left out are passed through as `read()` returned
them (see [section 15](#15-sensitive-fields)).

## 12. Revisions, backups and SafeFileWriter

A revision is an opaque string of 1 to 128 printable ASCII characters without spaces. It stays on the server: the
editor only sees a keyed token of it, while `ApplyContext.baseRevision()` and the revisions you return in
`ApplyResult` are your own. For file-backed configuration, use the content hash:

| Helper | Use |
| --- | --- |
| `Revisions.of(bytes)` | Lowercase hex SHA-256 of the bytes. Use it with the bytes you just parsed. |
| `Revisions.ofFile(path)` | Same for a file; `Revisions.ABSENT` when it does not exist. |
| `SafeFileWriter.write(target, bytes, expectedRevision, policy)` | Takes a per-path lock, compares the file's revision with `expectedRevision` (pass `null` to skip), copies the old file to the backup directory (unless the newest backup already has the same content), writes a temporary file in the same directory, `fsync`s it and moves it over the target atomically where the file system supports it. Older backups beyond the retention are deleted only after the move succeeded; when any step fails, the target is unchanged and the backup taken by that call is deleted again. Throws `RevisionConflictException` (with `currentRevision()`) when the file changed; nothing is written then. Returns the new revision and the backup path. |
| `BackupPolicy.in(dir)` / `new BackupPolicy(dir, retain)` | Backup directory and number of backups kept per file (default 10). Backups are named `<file>.<path id>.<yyyyMMdd-HHmmss-SSS>.bak`, where the path id is the first 12 hex characters of the SHA-256 of the file's absolute real path, so same-named files in different directories (`arenas/a/settings.yml`, `arenas/b/settings.yml`) can share one backup directory. |
| `SafeFileWriter.listBackups(target, policy)` | Backups of the file, newest first. |
| `SafeFileWriter.restoreLatestBackup(target, policy)` | Copies the newest backup over the file (backing up the replaced content first). |
| `SafeFileWriter.lockFor(target)` | The lock used by `write`, for your own writes to the same file. It is keyed by the path as given, without resolving links. |

`SafeFileWriter` puts a new file under the target's name. If the target is a symbolic link, the link is resolved
first, so the file it points to is replaced and the link stays. The new file gets the POSIX permissions of the
replaced file, and its owner and group where the server process may set them. A **hard link** of the target is
not updated: its other names keep the old content.

Rules that keep conflict detection reliable:

- Compute the revision from the **same bytes** you parsed in `read()`. Hashing a re-serialized document or
  reading the file twice opens a window where an edit goes unnoticed.
- Never put timestamps or counters into the revision of unchanged content, or every apply ends in `CONFLICT`.
- In `apply`, check `baseRevision` against the current content before you merge, and pass `baseRevision` as
  `expectedRevision` to `SafeFileWriter` so the check is repeated immediately before the write.
- Values that do not live in a file (a database, an in-memory object) need their own revision, for example a
  version counter that you increment on every write.

## 13. Threading rules and callSync

| Rule | Detail |
| --- | --- |
| Network I/O never runs on the main thread. | The bridge's WebSocket, JSON work and request queue use their own daemon threads. |
| `CallbackThread.WORKER` (default) | Callbacks run on a bridge worker thread, never on the main thread. File and database access belong here. |
| `CallbackThread.MAIN` | Callbacks run on the server main thread through the Bukkit scheduler. For providers whose state is not thread-safe; callbacks must be fast. A callback that the main thread has not started within 10 seconds is cancelled and the request fails. |
| One resource, one callback at a time | The bridge never overlaps two callbacks of the same resource, in either mode, and runs the callbacks of one operation back to back. |
| Global limits | At most 4 callbacks run at once across all resources and at most 64 requests wait; beyond that the backend gets `BUSY`. Requests that waited longer than their time to live are dropped without calling you. |
| `ApplyContext.callSync(task)` / `runSync(task)` | Runs `task` on the main thread and waits up to 10 seconds, then throws `TimeoutException` (a task that has not started by then is cancelled). On the main thread it runs inline. Use it for Bukkit API calls that require the main thread, such as messaging players, changing worlds or entities. |

Keep work on the main thread short: do the disk I/O and parsing on the worker thread, and only hop to the main
thread for the final Bukkit calls. Never call `callSync` while holding a lock that main-thread code might need.

Your plugin's own code (commands, `/reload` handlers) may run at the same time as a callback on a worker thread,
so share state the way the demo does: an immutable runtime configuration in an `AtomicReference` that is swapped
in one step. Swapping atomically is not enough on its own: a reload that read the file just before your `apply`
wrote it could swap in the older content right after `apply` swapped in the new one. The demo's `VoucherRuntime`
therefore holds one lock (`updateLock()`) around "read or write the file, then swap" in both `apply` and
`reload`. Keep such a lock short and never call `callSync` while holding it: the reload command takes it on the
main thread.

## 14. Reporting the apply outcome truthfully

Server owners rely on the result to know whether their change is live. Use the `ApplyResult` factories; they
enforce the invariants:

| Factory | Status | Persisted | Activated | Use when |
| --- | --- | --- | --- | --- |
| `ApplyResult.applied(newRevision)` | `APPLIED` | yes | yes | The values are stored **and active**. Saving a file alone is never enough. |
| `ApplyResult.savedRestartRequired(newRevision, message)` | `SAVED_RESTART_REQUIRED` | yes | no | Stored, but not everything is active: restart-only fields changed, or the live reload failed. The bridge fills `restartRequired` with the changed `restartRequired()` fields. |
| `ApplyResult.savedRestartRequired(newRevision, message, paths)` | `SAVED_RESTART_REQUIRED` | yes | no | Same, naming the paths that wait for a restart. |
| `ApplyResult.rejected(validationResult)` / `rejected(message)` | `REJECTED` | no | no | Your storage refuses the values. Needs at least one error. |
| `ApplyResult.conflict(currentRevision)` | `CONFLICT` | no | no | The stored state no longer matches `baseRevision`. The editor offers to reload. |
| `ApplyResult.failed(message)` | `FAILED` | no | no | Nothing was stored and the operation failed. |

What the bridge does around your `apply`:

- If `apply` throws (any exception, also `NoClassDefFoundError` and other linkage errors), the bridge logs the
  stack trace and calls `read()` again. Unchanged revision: `FAILED`, not persisted. Changed revision: `FAILED`,
  persisted, with a message that the values were saved but may not be active. A `null` result counts as an
  exception.
- Exception messages never leave the server. When a callback throws, the editor only sees the callback and the
  exception class, for example `read() threw InvalidConfigurationException (details in the server log)`; the
  message and stack trace go to the server log. Parser exceptions embed file content (SnakeYAML quotes the
  offending line, which can be a password), so this applies to `describe`, `read`, `validate` and `apply`
  alike. To show the owner an explanation, return it: `ValidationResult` issues, `ApplyResult.rejected(message)`
  or `ApplyResult.failed(message)`, and keep secrets and configuration values out of those messages. As a
  backstop, the bridge replaces with `[secret]` every stored or submitted sensitive value of 4 or more characters
  and every recognized secret (Discord and Slack webhook URLs, a password inside a connection URL, a `password=`
  parameter) in issue messages, apply messages, change labels and item keys before they leave the server.
- Every outcome is cached for 15 minutes under the operation id, so a retried submission returns the cached result
  instead of applying twice.
- After `APPLIED` or `SAVED_RESTART_REQUIRED`, open editors are told that the resource changed.

## 15. Sensitive fields

Use them for webhooks, tokens, passwords and similar secrets.

| Declaration | Editor | Submitting a value |
| --- | --- | --- |
| `.sensitive()` | Shows "Set" or "Not set", never the value. | Refused with `not_writable`. |
| `.writeOnly()` | Shows "Set" or "Not set" and offers **Replace value**. | Replaces the stored value. |

- Only `string` and `text` fields outside object lists can be sensitive.
- `read()` returns the real value; the bridge removes it before anything leaves the server and reports only
  whether a value exists (`null` and `""` count as "no value").
- When a submission omits a sensitive field (the normal case), the bridge merges the stored value from `read()`
  into `proposed`, so your `apply` always receives the complete values. Merged values are passed through as
  stored, whatever their type, without re-validation.
- Return sensitive values from `read()` as `String`. YAML reads unquoted numbers and `yes`/`no` as numbers and
  booleans, so a numeric password, PIN or channel id arrives as an `Integer` or `Long`. `SectionMapper.read`
  converts it to text; without it, convert it with `String.valueOf` in `read()`. The bridge keeps such a value
  unchanged when the field is omitted and logs a warning, but a replacement from the editor is always a `String`.
- Changes of sensitive fields are reported without before and after values. A submitted replacement is always a
  change, also when it equals the stored value, so reviewing changes never confirms a guessed value; your `apply`
  then receives a change for the field with an unchanged value. The bridge never logs values; do not log them in
  your plugin either.
- A secret that is "unset" in your file (the demo stores `webhook-url: ""`) should be returned as `null` from
  `read()` and written back from `null` in `apply`. `SectionMapper` does this for optional text fields.

## 16. notifyChanged and unregistering

Call `registration.notifyChanged()` whenever your values change outside ComfiConf, typically after your own reload
command or after a manual edit you detected. Open editors then show that newer values exist. The demo's reload:

```java
@Nullable String reload() {
    try {
        runtime().reload(configFile());   // reads and swaps under VoucherRuntime.updateLock()
    } catch (IOException | InvalidConfigException e) {
        return e.getMessage();   // keep the previous configuration
    }
    if (integration != null) {
        integration.notifyChanged();   // DemoIntegration delegates to ResourceRegistration.notifyChanged()
    }
    return null;
}
```

Calls within about 250 ms are combined into one notification, so calling it generously is fine. It does nothing
after the registration was removed.

Unregistering:

- Call `registration.unregister()` (or `close()`, it is `AutoCloseable`) in `onDisable`. Calling it again has no
  effect.
- The bridge also unregisters every resource of a plugin when it receives that plugin's `PluginDisableEvent`, and
  everything when the bridge itself is disabled. Explicit unregistering is still good practice, and required if
  you remove a resource while your plugin keeps running.
- `ComfiConfApi.get().unregisterAll(plugin)` removes all your resources at once.

## 17. Testing your integration

The API is plain Java, so providers can be unit tested without a server. The demo's tests in
`examples/demo-plugin/src/test` show the patterns:

- **Use a temporary directory** (`@TempDir`) with a copy of your default `config.yml`, and construct your provider
  directly. Bukkit's `YamlConfiguration` works in plain unit tests.
- **Implement `ApplyContext` in a few lines** for tests, with `callSync` running the task inline:

  ```java
  @Override
  public <T> T callSync(Callable<T> task) throws Exception {
      return task.call();
  }
  ```

- **Test the contract, not just the happy path**: `read()` returns the SHA-256 of the file bytes and a different
  revision after an external edit; a stale `baseRevision` returns `CONFLICT` and leaves the file and backups
  untouched; `apply` keeps unknown keys, comments and per-item data; numbers stay numbers; item order follows the
  proposal; restart-only changes return `SAVED_RESTART_REQUIRED` and do not change the active value; a failing
  live reload never returns `APPLIED`.
- **Round trip**: `apply(read())` with unchanged values must produce a file that reads back to the same values.
- **Guard the optional hook** with a bytecode scan of your compiled classes:

  ```java
  Path classes = Path.of(MyPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
  try (Stream<Path> walk = Files.walk(classes)) {
      for (Path file : walk.filter(p -> p.toString().endsWith(".class")).toList()) {
          String relative = classes.relativize(file).toString().replace('\\', '/');
          if (!relative.startsWith("com/example/myplugin/comficonf/")) {
              assertThat(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1))
                      .as(relative).doesNotContain("com/comficonf/api");
          }
      }
  }
  ```

- **Build your schema in a test** (`describe()`), so `SchemaException`s show up at build time.
- On a test server, run the bridge with `debug: true` in `plugins/ComfiConf/config.yml` to log every message type
  and request id (never values), and use `/comficonf status` and `/comficonf plugins`.

## 18. Troubleshooting

**`NoClassDefFoundError: com/comficonf/api/...` when ComfiConf is not installed**

Your main class (or another class loaded at startup, such as a listener or command class) refers to an API type.
Move every API use into the integration class and call it only after
`isPluginEnabled("ComfiConf")`; see [section 5](#5-the-optional-hook-pattern). Remember that method signatures,
fields and `catch` clauses count as references.

**`NoClassDefFoundError` or `ClassNotFoundException` for API classes although ComfiConf is installed**

The bridge was not loaded before your plugin, or your Paper plugin does not share its classpath. Add
`softdepend: [ComfiConf]` to `plugin.yml`, or `load: BEFORE` and `join-classpath: true` under
`dependencies.server.ComfiConf` in `paper-plugin.yml`.

**`ComfiConfApi.get()` throws "not installed or not enabled" although the bridge runs, or a `ClassCastException`
mentions `ComfiConfApi`**

You shaded the API into your JAR. Your copy of `ComfiConfApi` is a different class than the bridge's, so the
service lookup cannot find it. Use `compileOnly` (Gradle) or `provided` (Maven) and check that your JAR contains no
`com/comficonf/api` entries (`jar tf MyPlugin.jar | grep comficonf`).

**`IllegalStateException: Plugin ... is not enabled` from `register`**

You registered in `onLoad` or from a constructor. Register in `onEnable`.

**`IllegalArgumentException: Resource ... is already registered`**

Two plugins (or two registrations of yours) use the same id, or you registered again after a reload without
unregistering. Unregister in `onDisable` and keep ids unique, for example prefixed with your plugin name.

**Every apply returns `CONFLICT`**

Your revision is not stable: it is computed from something other than the stored bytes, includes a timestamp, or
`read()` and `apply()` compute it differently. See [section 12](#12-revisions-backups-and-safefilewriter).

**Apply returns `REJECTED` with `no_changes`**

The submitted values equal the current ones as `read()` returned them. If the owner did change something, `read()`
returns values that differ from what you store, for example a default your mapper fills in for a missing key, so
the edit only restored what `read()` already reported.

**A setting is missing in the editor**

Only declared fields are shown, and values under undeclared keys are dropped. Check the key spelling and nesting
in your schema against what `read()` returns.

**The editor shows a type error for a value that looks right in the file**

YAML typed the value differently: `"5"` is a string and `5` an integer, and `yes`, `no`, `on` and `off` are
booleans. The bridge already converts integer YAML values for `decimal` fields and whole decimals for `integer`
fields, so numbers are rarely the cause; a quoted number in a number field, or an unquoted number or boolean in a
`string` field, is. Convert in `read()`, and write the declared type back in `apply()`. `SectionMapper` converts
numbers and booleans in text fields to text; a quoted number in a number field or a quoted `'true'` in a boolean
field stays a type error, which the owner fixes by entering the value again in the editor.

**`read()` fails with "... is a section, not a list; to read a section keyed by ..."**

The file stores an object list as a section keyed by id (`kits: {starter: {...}}`), but the `SectionMapper` expects
a YAML list. Declare the list with `SectionMapper.builder(schema).keyedSection("kits")`.

**`read()` fails with "stored as binary data (!!binary)", or a text value shows as `[B@...`**

The file holds a string that YAML could only save as binary data, because it contained a control character; see
[Text that YAML cannot store](#text-that-yaml-cannot-store). Replace the value with text in the file, and reject
such text in `validate()` so it cannot be saved again.

**Apply returns `REJECTED` with "config.yml cannot store this value unchanged"**

The new file content did not read back to the proposed values, so nothing was saved. In the demo this means the
proposal holds a value that `SectionMapper.write` does not store, such as a key the schema does not declare; check
the reported path.

**Callbacks time out or the backend reports `BUSY`**

A `MAIN` callback waited more than 10 seconds for the main thread (server lag or a long main-thread task), or
callbacks block for a long time. Use `WORKER`, keep main-thread work short, and never wait for a worker from the
main thread.

**`callSync` throws `TimeoutException`**

The main thread did not finish the task within 10 seconds, or the server is stopping. Treat it as a failed
side effect, not as a failed apply, if the values are already active (the demo logs it and still reports
`APPLIED`).

**`NoClassDefFoundError` for a `com.comficonf.api` class when your plugin enables**

The installed ComfiConf plugin does not contain the API classes your plugin was compiled against, usually because it
is older than the `comficonf-api` version in your build. Install a ComfiConf version at least as new as that API.
Catching `LinkageError` around the registration, as in section 5, keeps your plugin running until then.

**The bridge does not connect**

Run `/comficonf status`. The bridge connects to ComfiConf at `wss://app.comficonf.com/api/v1/bridge`. After
editing `plugins/ComfiConf/config.yml`, run `/comficonf reconnect`. While the backend is unreachable the bridge
keeps retrying with backoff (up to one minute) and logs a warning only when the error changes or every ten minutes.

## 19. Checklist

- [ ] `compileOnly` (Gradle) or `provided` (Maven) dependency on `com.comficonf:comficonf-api`; nothing shaded.
- [ ] `softdepend: [ComfiConf]` in `plugin.yml`, or the `paper-plugin.yml` dependency with `load: BEFORE`,
      `required: false`, `join-classpath: true`.
- [ ] The main class references no API type; the integration is called only after
      `isPluginEnabled("ComfiConf")`; a test scans the class files.
- [ ] A stable resource id `namespace:name`.
- [ ] `describe()` declares every editable field with labels, descriptions and constraints; object lists have an
      `itemKey`.
- [ ] Bukkit YAML is mapped with `SectionMapper`, with `keyedSection` for lists stored as sections keyed by id.
- [ ] `read()` derives values and revision from the same bytes, returns secrets as `String`, and never turns
      unexpected values (such as `byte[]` from `!!binary`) into text.
- [ ] `validate()` checks only cross-field and storage rules, including text the storage cannot hold unchanged
      (for YAML: control characters, reported by `SectionMapper.validate`).
- [ ] `apply()` re-checks `baseRevision`, preserves undeclared data, checks that the new bytes read back to the
      proposed values, writes with `SafeFileWriter`, activates the values from the written bytes and returns
      `APPLIED` only when they are active.
- [ ] Restart-only fields use `restartRequired()` and lead to `SAVED_RESTART_REQUIRED`.
- [ ] Secrets are `sensitive()` or `writeOnly()` and never logged.
- [ ] Files shared between servers are symbolic links, not hard links: `SafeFileWriter` replaces the file, so
      other hard-linked names keep the old content.
- [ ] Your reload command calls `notifyChanged()`; `onDisable` unregisters.
- [ ] Unit tests cover read, validate, apply, conflict and the round trip.
