Focus chat sheet scroll regression: Android 1.2.1
Source commit: e9e5e3d8039461546c78a5514a2a3feb65e40e4b

Device: Android 15 / API 35 software emulator.
Offline fixture: 50 synthetic messages; no live chat messages sent.
Fixed implementation: both instrumentation gesture tests passed (retry after startup timeout).
Bottom edge stability: 3 upward gestures, 135 animation frames, maximum allowed movement 1 pixel.
Reverse scrolling and downward dismissal were also checked.

The baseline transcript intentionally contains a FAILED test. It runs the original
ModalBottomSheet implementation and catches a 159-pixel jump (814 -> 655).
The baseline exists only as a temporary test fixture, not in the final source.

GitHub CI compiles these instrumentation tests; device execution was local.
