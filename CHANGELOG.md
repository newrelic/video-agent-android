## [4.5.0](https://github.com/newrelic/video-agent-android/compare/v4.4.0...v4.5.0) (2026-08-31)

### Features

* **sample-app:** show a LIVE badge instead of a countdown for live streams ([ba35923](https://github.com/newrelic/video-agent-android/commit/ba35923105da851ad5402447fbaa4b23210cb1f6))
* **sample-app:** support direct manifest URL playback and mock-server testing ([ce2c475](https://github.com/newrelic/video-agent-android/commit/ce2c4754aed20564ce8798f8b2ccfcdef29f29b6))

### Bug Fixes

* **exoplayer:** report contentIsLive and omit contentDuration for live streams ([67c4fd9](https://github.com/newrelic/video-agent-android/commit/67c4fd931f263def47f31059efc8798ebaa5308d))
* **mediatailor:** append newly-reported ads when a tracking-built avail grows ([fd2b5fd](https://github.com/newrelic/video-agent-android/commit/fd2b5fd3496e503f109ca786a9afc58b9e0ab63e)), closes [#2](https://github.com/newrelic/video-agent-android/issues/2) [#3](https://github.com/newrelic/video-agent-android/issues/3)
* **mediatailor:** centralise trailing-segment sessionId extraction ([f0feb4c](https://github.com/newrelic/video-agent-android/commit/f0feb4cd93241b047c48c6985fa3ad0880adad23))
* **mediatailor:** classify how the tracking endpoint was resolved ([87dcb30](https://github.com/newrelic/video-agent-android/commit/87dcb304ebd983201002929aa2262b272e5d0e13))
* **mediatailor:** correct DASH cue offset and give HLS breaks a stable id ([4ba3b21](https://github.com/newrelic/video-agent-android/commit/4ba3b21b5b8cf4e81ad4d34f964b3799eb0bbca6))
* **mediatailor:** derive tracking URL from implicit-session DASH playlist path ([be33fa0](https://github.com/newrelic/video-agent-android/commit/be33fa0643feba0100f7fa1d455d854fba8f9e27))
* **mediatailor:** derive tracking URL from implicit-session HLS playlist path ([4e31bcb](https://github.com/newrelic/video-agent-android/commit/4e31bcb349118628b597e3e7a2aff1940042fcc3))
* **mediatailor:** describe pending implicit-session tracking URL as deferral ([f0513d4](https://github.com/newrelic/video-agent-android/commit/f0513d49f18290784fdb428ed9200b3ce2617d3d))
* **mediatailor:** emit AD_ERROR when tracking fetch fails silently ([2750947](https://github.com/newrelic/video-agent-android/commit/2750947b965f4fa18beaa783e5266f3c6da5d05f))
* **mediatailor:** expose creativeId-first primary key for per-ad metrics ([c8de5dd](https://github.com/newrelic/video-agent-android/commit/c8de5ddc05bdef18c202d628f1ad8574db1846f2))
* **mediatailor:** harden schedule merge against zombie breaks and pod drift ([83b1eec](https://github.com/newrelic/video-agent-android/commit/83b1eec298304415133b9093f19cb2dbe5f444aa))
* **mediatailor:** keep manifest pod boundaries when tracking ad count disagrees ([3f3ce3f](https://github.com/newrelic/video-agent-android/commit/3f3ce3fe2450bdea46d2e5e1bf7fd821a0f33822))
* **mediatailor:** let EXT-X-DATERANGE override an earlier trackingUrl guess ([f1cceac](https://github.com/newrelic/video-agent-android/commit/f1cceac1c0603e3c04be36aa136927d8fe026cfd))
* **mediatailor:** make playhead poll interval configurable at construction ([cad7625](https://github.com/newrelic/video-agent-android/commit/cad76253aac6ae39509d0206d31b63acbbc5dfaf))
* **mediatailor:** match growing-avail ads by start time when adId is absent ([b92394b](https://github.com/newrelic/video-agent-android/commit/b92394b16c90aca446f3c84274c537f637149a6b))
* **mediatailor:** match manifest breaks by id, gate MISSING_AVAIL_START once ([d846520](https://github.com/newrelic/video-agent-android/commit/d846520406609a7593fc6a2c4369c52985e858bc))
* **mediatailor:** paginate NextToken within one poll instead of across polls ([9d52fb9](https://github.com/newrelic/video-agent-android/commit/9d52fb904ed1e2040369441162694675d0568189))
* **mediatailor:** parse tracking-event beacons with ad-start-relative timing ([1b54387](https://github.com/newrelic/video-agent-android/commit/1b54387b2d834cc0726e0d1101a6e9b6506df20e))
* **mediatailor:** pod endTimeMs must not exceed its enclosing break ([036b17d](https://github.com/newrelic/video-agent-android/commit/036b17d53b05b5ecd605a27886b6e9d10a34f243))
* **mediatailor:** prune viewed avails from the live schedule ([333cc0f](https://github.com/newrelic/video-agent-android/commit/333cc0f565f172ae50bf2f6975aa2e28b149aebd))
* **mediatailor:** read HLS tracking URL from EXT-X-DATERANGE when advertised ([8d5fb50](https://github.com/newrelic/video-agent-android/commit/8d5fb507cbe2363f706f352f2fd22e7f7299f7e7))
* **mediatailor:** rename segmentPrefix to canonical adSegmentPrefix ([7988167](https://github.com/newrelic/video-agent-android/commit/79881679bee7d366e726f005b072a60ad176581e))
* **mediatailor:** require unanimous representation agreement for DASH ad periods ([f872c9d](https://github.com/newrelic/video-agent-android/commit/f872c9de0b8202da1bcc168e3c6b2f97faf6c240))
* **mediatailor:** revert tracking requests from POST back to GET ([8420b02](https://github.com/newrelic/video-agent-android/commit/8420b0250a7f7f5ff24775ede20a09f55024d947))
* **mediatailor:** round-trip NextToken across tracking polls ([9e0dbcf](https://github.com/newrelic/video-agent-android/commit/9e0dbcfc1cd7ece6388609baa50832863e1dd324))
* **mediatailor:** stabilise avail identity across live window rotations ([bd8397b](https://github.com/newrelic/video-agent-android/commit/bd8397bc6d1e0eb1e8ab485a8a1522e1e98155c6))
* **mediatailor:** surface avails missing startTimeInSeconds as an error ([8147f87](https://github.com/newrelic/video-agent-android/commit/8147f87521f0b35a33f0669517ea87c7c31d24ae))
* **mediatailor:** surface pods appended after the playhead left their window ([9044d79](https://github.com/newrelic/video-agent-android/commit/9044d795303dccfdff684f00629d06aca937f2a7))
* **mediatailor:** treat empty avails as no-fill instead of firing AD_START ([d32ac96](https://github.com/newrelic/video-agent-android/commit/d32ac96c72d838eebf9b20e1a9333e222b811736))
## [4.4.0](https://github.com/newrelic/video-agent-android/compare/v4.3.1...v4.4.0) (2026-08-04)

### New features

* add JP region support ([e97c6ca](https://github.com/newrelic/video-agent-android/commit/e97c6caaac5ba1b261f8fa9812c8a262d49f09a4))

### Bug fixes

* Align totalPauseTime with iOS clock model; stop emitting totalTimeSwitchedDown ([924c7b9](https://github.com/newrelic/video-agent-android/commit/924c7b9ffcb5b65b5023bd7b41cf4515f964686b))
* classify startup vs playback error by content-started (iOS parity) ([a6b3288](https://github.com/newrelic/video-agent-android/commit/a6b3288932f0ae60513ff58ca0214ae61b9432f2))
* correct CONTENT_DROPPED_FRAMES event fields and fix ANR root cause ([a5afc73](https://github.com/newrelic/video-agent-android/commit/a5afc731735e5b05ef5f17fa4e2d7b7bd33843cb))
* Dedup consecutive identical download-rate samples ([e9b6d1f](https://github.com/newrelic/video-agent-android/commit/e9b6d1f2c7de121e371c62b904917a92e23bda8b))
* Drive bitrate timer from isPlaying transition ([011ade3](https://github.com/newrelic/video-agent-android/commit/011ade3370a71149376e26f4e29aed70e1d28900))
* Emit fresh totalPlaytime in QOE_AGGREGATE ([af065d6](https://github.com/newrelic/video-agent-android/commit/af065d624bb7e8f6df468423c2b1527603b4c6e8))
* exclude only ad time from startupTime (iOS parity) ([9321ac8](https://github.com/newrelic/video-agent-android/commit/9321ac8279dd2000cedec898a2f9f1996e9e5405))
* Final-QoE via buffer — at CONTENT_END, recordEvent the final QoE into the crash-safe buffer ([e1d3ae1](https://github.com/newrelic/video-agent-android/commit/e1d3ae1469f29b42d04ef47b0f2a7b700046db83))
* Remove unused classpath ([f729768](https://github.com/newrelic/video-agent-android/commit/f729768ae8560edd246c3651cc848db60b977207))
* Remove unused dependency ([0bdd95b](https://github.com/newrelic/video-agent-android/commit/0bdd95bf41e9511d1f60e373ca75e6e36de013f1))
* Remove unused plugin ([fa11838](https://github.com/newrelic/video-agent-android/commit/fa1183822a0f044f475481871b956ad92ac5f7e6))
* Replace the global harvestCycleNumber gate with a per-tracker ([9e19faf](https://github.com/newrelic/video-agent-android/commit/9e19faf605f2fbcf69740132c6a69a0f0e4f6157))
* Update variable name ([72de035](https://github.com/newrelic/video-agent-android/commit/72de0354bd2bc78ee12d1124b25d70b20ffbcbf2))
* whitelist QOE_AGGREGATE context attributes (iOS parity) ([dd1d3ef](https://github.com/newrelic/video-agent-android/commit/dd1d3efeb00b345bdbdc0ee48b6e57e35e8e667f))
## [4.3.1](https://github.com/newrelic/video-agent-android/compare/v4.3.0...v4.3.1) (2026-07-13)

### Bug Fixes

* CSAI preroll no longer drops CONTENT_START. ([948f229](https://github.com/newrelic/video-agent-android/commit/948f229cc59b99e9eba68e565e6504987092d759))
  
## [4.3.0](https://github.com/newrelic/video-agent-android/compare/v4.2.0...v4.3.0) (2026-06-10)

### Features

* Add new QOE attributes ([6442505](https://github.com/newrelic/video-agent-android/commit/64425052089b337989e27f2a8c0acdc2df444f48))
* enable QoE by default with interval multiplier 2 ([8d71a24](https://github.com/newrelic/video-agent-android/commit/8d71a245b0670e46e1f054ab31726437a5e907b8))
* introduce NRAdConfig — unified ad configuration and custom CDN support ([dd5c528](https://github.com/newrelic/video-agent-android/commit/dd5c528f2f99def88f26fdb8d37948db5b7a9624)), closes [#106](https://github.com/newrelic/video-agent-android/issues/106) [#108](https://github.com/newrelic/video-agent-android/issues/108)

### Bug Fixes

* fire BUFFER_END and PAUSE correctly when user pauses during rebuffer ([e3cd1b3](https://github.com/newrelic/video-agent-android/commit/e3cd1b3ae3418b09060559f4ec90c186d59df168))
* Issue fixed related to switch ups and downs ([0b6e8a8](https://github.com/newrelic/video-agent-android/commit/0b6e8a853b7f8c700aa8e3c4e73ef4875635fbc0))
## [4.2.0](https://github.com/newrelic/video-agent-android/compare/v4.1.1...v4.2.0) (2026-05-21)

### Features

* **mediatailor:** add AWS MediaTailor SSAI tracker for DASH + HLS ([caf8f7c](https://github.com/newrelic/video-agent-android/commit/caf8f7cd7c718cd6c480aef50afe377d62419857))

### Bug Fixes

* **sample:** remove trust-all SSL bypass in MediaTailor sample ([92ad555](https://github.com/newrelic/video-agent-android/commit/92ad555c5243c052a5fedf2c1c4cd2d02242c82b))
## [Unreleased]

### Features

* **NRAdConfig** — unified ad configuration object, the single place for all ad options per player session.
  * `NRAdConfig.csai()` — Google IMA / any CSAI framework.
  * `NRAdConfig.mediaTailor()` — AWS MediaTailor SSAI with default AWS domain detection.
  * `NRAdConfig.mediaTailor(segmentPrefix)` — custom CDN with a non-`/tm/` ad-segment path.
  * `NRAdConfig.mediaTailor(segmentPrefix, trackingUrl)` — custom CDN + explicit tracking URL (POST session-init flow).
  * Pass `null` as the `adConfig` argument to `NRVideoPlayerConfiguration` to disable ad tracking.

* **Backward compatibility** — existing integrations built against v4.2.0 compile without changes:
  * `new NRVideoPlayerConfiguration(name, player, true, attrs)` → `NRAdConfig.csai()`
  * `new NRVideoPlayerConfiguration(name, player, false, attrs)` → no ad tracking
  * `AdTrackerType.IMA` → `NRAdConfig.csai()`
  * `AdTrackerType.MEDIA_TAILOR` → `NRAdConfig.mediaTailor()`
  * `AdTrackerType.NONE` → no ad tracking
  * `isAdEnabled()` and `getAdTrackerType()` kept as deprecated methods.
  All deprecated; migrate to `NRAdConfig` factory methods at your own pace.

* **Custom CDN support for MediaTailor** — ad segments served from customer-owned CDN domains are now detected automatically:
  * `/tm/` (AWS-recommended CDN prefix) is checked for all customers without any configuration.
  * `segmentPrefix` in `NRAdConfig` overrides this for CDN paths that differ from `/tm/`.
  * When `NRAdConfig.mediaTailor()` is passed, the tracker activates unconditionally — no `mediatailor` substring required in the manifest URL, enabling fully custom CDN manifest domains.

* **NRMediaTailorTracker** — AWS Elemental MediaTailor SSAI tracker module.
  * Supports **DASH** (multi-period BaseURL detection + single-period SCTE‑35 EventStream) and **HLS** (segment URL + discontinuity-driven pod splitting).
  * Implicit and explicit session-init flows (POST `/v1/session/…`); recovers `sessionId` from DASH `<Location>` when the client-side URI doesn't carry it.
  * VOD and Live — tracking API polled once for VOD, on every manifest refresh for Live.
  * Rich VAST metadata on `VideoAdAction` events emitted by `NRTrackerMediaTailor` (identified by `trackerName = "NRMTracker"`): `adSystem`, `vastAdId`, `creativeSequence`, `skipOffset`, `adProgramDateTime`, `availProgramDateTime`, `isBumper`, `nonLinearAvailsCount`. These attributes are **not** populated by `NRTrackerIMA`.
  * Structured log tags — `[MT][CONFIG]`, `[MT][DETECT]`, `[MT][HLS]`, `[MT][DASH]`, `[MT][TRACK]`, `[MT][EVENT]` — for targeted filtering in Logcat.
  * `notifyAdSkipped()` API for skippable-ad UX integrations.

* **NRTrackerExoPlayer** — new `isLinkedAdBreakActive()` gate suppresses stray `CONTENT_PAUSE` / `CONTENT_RESUME` / `CONTENT_BUFFER_*` / `CONTENT_START` / `CONTENT_RENDITION_CHANGE` / `CONTENT_SEEK_START` events during SSAI ad breaks, and routes error callbacks (`onPlayerError`, `onLoadError`) to the ad tracker as `AD_ERROR` when one is active.

## [4.1.1](https://github.com/newrelic/video-agent-android/compare/v4.1.0...v4.1.1) (2026-04-22)

### Bug Fixes

* Add Obfuscation Rules Feature ([f756c78](https://github.com/newrelic/video-agent-android/commit/f756c78884842e32c553e3a2038a47047b4b6b5e))
* Added qoeHarvestCycle ([aa4940c](https://github.com/newrelic/video-agent-android/commit/aa4940c801a781a2ff7f20a7fa5b78068ad6d67b))
* Added qoeHarvestCycle ([0218d31](https://github.com/newrelic/video-agent-android/commit/0218d317ef4ad5af90a9b03be0e5580eb4b4f03e))
* Added timeSinceRequested, timeSinceStarted to qoe event payload ([1f627cf](https://github.com/newrelic/video-agent-android/commit/1f627cf06a7023fde878c0db5a8a57098f94825c))
* Adding QoeProvider ([ed48dee](https://github.com/newrelic/video-agent-android/commit/ed48deea85e71299e4a8132176571734a6508cf1))
* Addressing review comment ([785a2fd](https://github.com/newrelic/video-agent-android/commit/785a2fd0c5a550e7c89e044a5d398a245a0bd5c9))
* Changed architecture of QOE ([7913826](https://github.com/newrelic/video-agent-android/commit/79138264e77cee91318192184b05421221922282))
* fallback for ad bitrate ([3077faa](https://github.com/newrelic/video-agent-android/commit/3077faad473c135c51a25cda8e11dede2d5724f5))
* Final QOE event at content end ([f151a21](https://github.com/newrelic/video-agent-android/commit/f151a21e0f7cc202bda80be2118c620932f7a011))
* Fixed build issue ([1cb695c](https://github.com/newrelic/video-agent-android/commit/1cb695c5663d8a9e21fac6dd5ebacc5eb6318dc1))
* Thread-safe QOE generation and real-time totalPlaytime calculation ([e53e6f7](https://github.com/newrelic/video-agent-android/commit/e53e6f74755a69c9874054a264c40a617d47f05f))
* Thread-safe QOE generation using attribute caching ([43d21cd](https://github.com/newrelic/video-agent-android/commit/43d21cd6d80148a1c70bb3c23cc06b5a44631dfb))
* Thread-safe QOE with real-time metrics and immediate final QOE ([a506f66](https://github.com/newrelic/video-agent-android/commit/a506f6685f398f5087d5b81481f3546d10f96080))
* Update bitrate name ([928d3af](https://github.com/newrelic/video-agent-android/commit/928d3aff5345ea2278999b605ba2818a3f6412e6))
* Update function name ([42a842e](https://github.com/newrelic/video-agent-android/commit/42a842ee592c2ec03dd3a355dac2c1084a302ba0))
* Update name to contentSegmentDownloadBitrate, contentNetworkDownloadBitrate ([e1a3dc9](https://github.com/newrelic/video-agent-android/commit/e1a3dc9551b9cb5bbea5de13d900462635d8bf2d))
* Update ReadMe ([d02ee40](https://github.com/newrelic/video-agent-android/commit/d02ee40e7ef0cce495712e26b37a24bc5dd73775))
* Updated calculation of Startup time, Average bitrate ([371da00](https://github.com/newrelic/video-agent-android/commit/371da00df9bde031a67864315baf82f895ed3fe2))
* Updated harvestCycle to AtomicInteger ([0d550d2](https://github.com/newrelic/video-agent-android/commit/0d550d2bb3dc0e030276ba1f05543aff694c8708))
* Updated the condition of when the QOE events are sent and updated readme ([f307de9](https://github.com/newrelic/video-agent-android/commit/f307de99bc3abac4185389ebb8f1cc2f26ea5457))
## [4.1.2-beta](https://github.com/newrelic/video-agent-android/compare/v4.0.6...v4.1.2-beta) (2026-03-18)
## [4.1.0](https://github.com/newrelic/video-agent-android/compare/v4.0.6...v4.1.0) (2026-03-31)

### Features

* add beta release workflow for JitPack from stable-beta branch ([6317416](https://github.com/newrelic/video-agent-android/commit/631741631f27bdef223584bfd740ecfec2d2e5c8))
* add beta release workflow for JitPack from stable-beta branch ([94475c6](https://github.com/newrelic/video-agent-android/commit/94475c6da583f9a6bb583077c5372e56c5c79d2b))
* beta release workflow update ([aa77e9e](https://github.com/newrelic/video-agent-android/commit/aa77e9e2840ef27584be957deff96a6bf0a2252c))
* beta release workflow update ([131dd23](https://github.com/newrelic/video-agent-android/commit/131dd232ea3dd293695ecf65abe77709d5ebd2a7))

### Bug Fixes

* Added qoeHarvestCycle ([aa4940c](https://github.com/newrelic/video-agent-android/commit/aa4940c801a781a2ff7f20a7fa5b78068ad6d67b))
* Added qoeHarvestCycle ([0218d31](https://github.com/newrelic/video-agent-android/commit/0218d317ef4ad5af90a9b03be0e5580eb4b4f03e))
* Added timeSinceRequested, timeSinceStarted to qoe event payload ([1f627cf](https://github.com/newrelic/video-agent-android/commit/1f627cf06a7023fde878c0db5a8a57098f94825c))
* Adding QoeProvider ([ed48dee](https://github.com/newrelic/video-agent-android/commit/ed48deea85e71299e4a8132176571734a6508cf1))
* Changed architecture of QOE ([7913826](https://github.com/newrelic/video-agent-android/commit/79138264e77cee91318192184b05421221922282))
* Final QOE event at content end ([f151a21](https://github.com/newrelic/video-agent-android/commit/f151a21e0f7cc202bda80be2118c620932f7a011))
* Added bitrate ([7bb2d07](https://github.com/newrelic/video-agent-android/commit/7bb2d07e8597a7a54271537dfe35d8ec1a9ed2d1))
* lint error in sample app ([77a33a7](https://github.com/newrelic/video-agent-android/commit/77a33a7781b67df8b6dedbf1edb7bf4cd5090dfb))
* Lint issues ([6939e28](https://github.com/newrelic/video-agent-android/commit/6939e28278701c9d2092cc2140acdcf28c16c338))
* pr review comments ([0885a1a](https://github.com/newrelic/video-agent-android/commit/0885a1a620cd2d1d21393319325bbf629fbed219))
* Regional Collector URLs ([ac2811b](https://github.com/newrelic/video-agent-android/commit/ac2811ba8da931a357ab78a0f86fa199e94f21b8))
* Remove comment ([32b39a6](https://github.com/newrelic/video-agent-android/commit/32b39a68a1c42edd3254ffd3c8e3887656c4a1a3))
* Remove extra comment ([7aa1226](https://github.com/newrelic/video-agent-android/commit/7aa12265e6f6c6970a0a7ace8e15886a5cdac08f))
* Rename QOE playback failure attrs to error ([825779c](https://github.com/newrelic/video-agent-android/commit/825779c3c01b5dee6ee749549e292af7f6d53ee8))
* revert use of getOrDefault ([5cb62a6](https://github.com/newrelic/video-agent-android/commit/5cb62a6f1be8f933f5e87a2f43706f4bea9d639a))
* Staging credentials ([7187413](https://github.com/newrelic/video-agent-android/commit/7187413c7313c6e02486e881bcfce53d02a73852))
* Thread-safe QOE generation and real-time totalPlaytime calculation ([e53e6f7](https://github.com/newrelic/video-agent-android/commit/e53e6f74755a69c9874054a264c40a617d47f05f))
* Thread-safe QOE generation using attribute caching ([43d21cd](https://github.com/newrelic/video-agent-android/commit/43d21cd6d80148a1c70bb3c23cc06b5a44631dfb))
* Thread-safe QOE with real-time metrics and immediate final QOE ([a506f66](https://github.com/newrelic/video-agent-android/commit/a506f6685f398f5087d5b81481f3546d10f96080))
* Trigger HEARTBEAT at elapsed time 0 ([fb40db3](https://github.com/newrelic/video-agent-android/commit/fb40db388cddfc78da3a6c8a6ceb7ed0cd9c407b))
* Updated calculation of Startup time, Average bitrate ([371da00](https://github.com/newrelic/video-agent-android/commit/371da00df9bde031a67864315baf82f895ed3fe2))
* Updated harvestCycle to AtomicInteger ([0d550d2](https://github.com/newrelic/video-agent-android/commit/0d550d2bb3dc0e030276ba1f05543aff694c8708))
* Updated the condition of when the QOE events are sent and updated readme ([f307de9](https://github.com/newrelic/video-agent-android/commit/f307de99bc3abac4185389ebb8f1cc2f26ea5457))
* Trigger HEARTBEAT at elapsed time 0 ([fb40db3](https://github.com/newrelic/video-agent-android/commit/fb40db388cddfc78da3a6c8a6ceb7ed0cd9c407b))
* use getActualBitrate function ([ba7b922](https://github.com/newrelic/video-agent-android/commit/ba7b922da46e47f4220101d09cb76733586f75d3))
## [4.1.1-beta](https://github.com/newrelic/video-agent-android/compare/v4.1.0-beta...v4.1.1-beta) (2026-01-30)
## [4.1.0-beta](https://github.com/newrelic/video-agent-android/compare/v4.0.5...v4.1.0-beta) (2026-01-30)

### Features

* add beta release workflow for JitPack from stable-beta branch ([94475c6](https://github.com/newrelic/video-agent-android/commit/94475c6da583f9a6bb583077c5372e56c5c79d2b))
* beta release workflow update ([131dd23](https://github.com/newrelic/video-agent-android/commit/131dd232ea3dd293695ecf65abe77709d5ebd2a7))
## [4.0.5](https://github.com/newrelic/video-agent-android/compare/v4.0.4...v4.0.5) (2026-01-20)

### Bug Fixes

* Replace bitrate calculation to use manifest rendition bit rate instead of download speed ([b676954](https://github.com/newrelic/video-agent-android/commit/b67695487e25d1b3b24ceba660fb0b7aa6d6c0d4))
* Update media3 from 1.1.0 to 1.2.0 for Android 14 compatibility ([320d92b](https://github.com/newrelic/video-agent-android/commit/320d92bdbc9ef5f75eceddede00eaddd5d1b8aad))
## [4.0.4](https://github.com/newrelic/video-agent-android/compare/v4.0.3...v4.0.4) (2025-12-18)

### Bug Fixes

* Build issues codeql ([#70](https://github.com/newrelic/video-agent-android/issues/70)) ([32f4438](https://github.com/newrelic/video-agent-android/commit/32f44389146c95b6d7cd3916701f0aadfb9a1d4c))
* updating sample app's custtom attrribute ([9054208](https://github.com/newrelic/video-agent-android/commit/905420870710dd82032398f14dd0763235b7969b))
