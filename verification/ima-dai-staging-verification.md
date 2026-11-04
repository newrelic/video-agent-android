# IMA DAI staging verification (Android)

Run the sample app (`app`, "IMA DAI (SSAI)" switch) against staging. Put the app token in the
ignored `local.properties` (`nr.applicationToken=...`); never commit it. The sample tags events
with `testRun=dai-sample`; change it per run (for example `dai-2026-11-05`) in `VideoPlayerDAI` to
isolate a run. Replace `T` below with that tag. The staging collector is set in
`NRVideoApplication`.

## Test scenarios

| # | Scenario | How | Expected |
|---|----------|-----|----------|
| 1 | Preroll + midroll VOD, play to end | "Tears" button | Per ad: AD_REQUEST, AD_START, 3x AD_QUARTILE, AD_END; AD_BREAK_START/END around each pod; `adPosition` pre then mid |
| 2 | Skip a skippable ad | Tap skip on the IMA UI | AD_END with `adSkipped=1`; the next ad has `adSkipped=0` on AD_START |
| 3 | Seek over a midroll | Seek past a midroll, IMA snaps back | Abandoned ad closed with AD_END before the next AD_START; no stuck break |
| 4 | Background mid-ad | Home button during an ad, then return | Ad state is not lost; ad completes with AD_END |
| 5 | Bad stream key | "Kite" button (invalid asset key) | `AD_ERROR` with `errorCode`, `errorMessage`, `adErrorType`, `adStreamType=DAI`; no AD_START |
| 6 | Network drop during ad | Airplane mode 10 s mid-ad, then restore | AD_BUFFER_START/END or AD_ERROR; session recovers, `adStreamType` still DAI |
| 7 | Live ad break | "Live" button, wait for a break | AD_BREAK_START, ads, AD_BREAK_END; check `adPosition` (live podIndex unverified) |

## NRQL checks (append `SINCE 1 hour ago`)

Action sequence for one run:
```
FROM VideoAdAction SELECT actionName, adPosition, adQuartile, adSkipped, adPlayhead
WHERE testRun = 'T' AND adStreamType = 'DAI' LIMIT MAX
```
Counts per action (expect REQUEST = START = END; QUARTILE = 3 x START unless skipped):
```
FROM VideoAdAction SELECT count(*) WHERE testRun = 'T' AND adStreamType = 'DAI' FACET actionName
```
Break pairing (starts = ends per viewId):
```
FROM VideoAdAction SELECT filter(count(*), WHERE actionName='AD_BREAK_START') AS starts,
  filter(count(*), WHERE actionName='AD_BREAK_END') AS ends
WHERE testRun = 'T' AND adStreamType = 'DAI' FACET viewId
```
Required attributes on AD_START (bitrate and rendition may be absent on DAI):
```
FROM VideoAdAction SELECT count(*) AS total, filter(count(adTitle), WHERE adTitle IS NOT NULL) AS title,
  filter(count(adDuration), WHERE adDuration IS NOT NULL) AS duration,
  filter(count(adCreativeId), WHERE adCreativeId IS NOT NULL) AS creative,
  filter(count(adPosition), WHERE adPosition IS NOT NULL) AS position
WHERE testRun = 'T' AND adStreamType = 'DAI' AND actionName = 'AD_START'
```
Skipped (scenario 2):
```
FROM VideoAdAction SELECT count(*) WHERE testRun = 'T' AND actionName = 'AD_END' FACET adSkipped
```
Errors (scenarios 5 and 6):
```
FROM VideoErrorAction SELECT errorCode, errorMessage, adErrorType, adStreamType, viewId
WHERE testRun = 'T' AND actionName = 'AD_ERROR' LIMIT MAX
```
Ad position (VOD and live):
```
FROM VideoAdAction SELECT count(*) WHERE testRun = 'T' AND actionName = 'AD_START' FACET adPosition
```
CSAI regression (run the existing "Ads" sample; CSAI events must have no DAI attributes):
```
FROM VideoAdAction SELECT count(*) WHERE trackerName = 'IMATracker' AND adStreamType IS NULL AND adSkipped IS NULL
```
Content events still flow during DAI:
```
FROM VideoAction SELECT count(*) WHERE testRun = 'T' FACET actionName
```

## Dashboard

`ima-dai-dashboard.json` is a NerdGraph `dashboardCreate` input. Replace `ACCOUNT_ID_PLACEHOLDER` in
every `accountIds` with the number, and import with:

```
mutation($accountId: Int!, $dashboard: DashboardInput!) {
  dashboardCreate(accountId: $accountId, dashboard: $dashboard) {
    entityResult { guid name } errors { description type }
  }
}
```
Variables: `{"accountId": <ACCOUNT_ID>, "dashboard": <contents of the JSON file>}`. Not imported or
rendered yet; it needs a staging account ID and a New Relic connection.

## Open items to confirm on device

- Live DAI `AdPodInfo.getPodIndex()` mapping to pre/mid/post.
- Whether Media3 forwards `AD_BREAK_STARTED/ENDED` to the builder listener (the tracker needs them for breaks).
- `adPlayhead` comes from the `StreamManager`, so it is only populated when the app uses
  `registerStreamManager` or the two-argument `handleStreamAdEvent`. Media3 hides the StreamManager,
  so it is null in the sample.
