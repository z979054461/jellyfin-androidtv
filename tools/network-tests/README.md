# Player transfer-rate checks

Run `sh tools/network-tests/run.sh` with JDK 21 available on PATH. This standalone
suite needs neither an Android SDK nor network access. It covers warmup, measured
intervals, concurrent transfers, thread safety, idle decay, local-read exclusion,
media replacement/closed-session callbacks, and long-running bounded history.

The meter adds actual Media3 `TransferListener.onBytesTransferred` payload counts
from the current player's media sources (including manifests and external subtitles).
It does not measure link capacity, headers, TLS overhead, posters, danmaku, or other
app requests. No probe requests, URLs, credentials, or transfer objects are retained.

History consists of forty 50 ms buckets, giving a two-second rolling rate with up
to 50 ms boundary quantization. During the first two seconds the denominator is
elapsed time since the first network transfer; the first 50 ms is marked unready.
No events in the retained window means zero, even if playback is still buffered or
paused. Network and local observations are distinct; a local subtitle alone cannot
mark an HTTP main source local. The existing pipeline has no disk cache and does
not make claims about cached playback. Media replacement creates a fresh immutable
listener/factory binding and permanently closes the prior meter.

Full Android compilation and on-device player/UI tests remain separate checks.
