# ComfiConf API

**Powerful plugins. Comfortable configuration.**

ComfiConf lets Minecraft server owners change the settings of their Paper plugins in a web editor at
https://app.comficonf.com instead of hand-editing YAML. Plugins describe their settings with the ComfiConf API; the
editor is generated from that description, the plugin validates and saves every change, and the server owner sees
the real result: applied, saved but waiting for a restart, rejected, or a conflict.

## Documentation

| Site | For |
| --- | --- |
| [developers.comficonf.com](https://developers.comficonf.com) | Plugin developers: the API, schemas, providers, persistence, testing and the demo plugin |
| [docs.comficonf.com](https://docs.comficonf.com) | Server owners: installing and pairing the bridge, the editor, apply results, what leaves the server |

This repository is for plugin developers. It contains:

| Path | Contents |
| --- | --- |
| `java/api` | The developer API, `com.comficonf:comficonf-api` |
| `examples/demo-plugin` | ComfiConfDemo, a voucher plugin showing a complete integration |
| `docs/DEVELOPER_GUIDE.md` | The developer guide, the source text of developers.comficonf.com |

The ComfiConf bridge plugin that server owners install, and the ComfiConf service itself, are not part of this
repository.

## Getting started

Start with the [Quickstart](https://developers.comficonf.com/quickstart). It covers adding the API to your build
(as `compileOnly`, never shaded), the `softdepend` on ComfiConf, the optional hook that keeps your plugin working
without the bridge, and registering a configuration resource. The rest of
[developers.comficonf.com](https://developers.comficonf.com) documents every schema builder, the provider callbacks,
threading, `SafeFileWriter` and `SectionMapper`, with ComfiConfDemo as the worked example. Your plugin keeps full
control: it validates, saves its own files and activates changes, and it keeps working on servers without ComfiConf.

To try an integration, install the ComfiConf bridge from Hangar or Modrinth on a test server and pair it at
https://app.comficonf.com, as described in [Getting started](https://docs.comficonf.com/getting-started/install-the-bridge)
on docs.comficonf.com, then open your plugin's settings there.

## Building

Requires JDK 25.

```bash
cd java && ./gradlew build
```

On Windows, run `gradlew.bat build` in `java`. The build runs all tests and produces:

| File | What it is |
| --- | --- |
| `java/api/build/libs/comficonf-api-<version>.jar` | The developer API, with sources and Javadoc JARs |
| `examples/demo-plugin/build/libs/ComfiConfDemo-<version>.jar` | The demo plugin |

`./gradlew :api:publishToMavenLocal` installs the API into your local Maven repository.

## Security

Report vulnerabilities privately through **Security**, **Report a vulnerability** on this repository, not in a public
issue.

## License

The API and the demo plugin are licensed under the Apache License, Version 2.0; see `LICENSE`. The ComfiConf bridge
and the ComfiConf service are proprietary.
