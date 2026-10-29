## [0.0.7] - 2026-10-29

- Added `AndroidSMSReader.fetchInboxAfter` and `countInboxAfter`: native, checkpoint-based
  incremental inbox reads ordered by (date, _id).
- `AndroidSMSMessage.id` now carries the Android SMS provider `_id` (previously always 0 when
  built via `fromJson`). Missing `id` still defaults to 0.
- `AndroidSMSMessage.fromJson` no longer throws on null `address`/`body`/`type`; they default to ''.
  (Behavior change: consumers that relied on the exception should check for empty values.)

## [0.0.6] - 2025-10-31

- Removed the library name
- Modified the SDK ver
- Upgraded to the latest version

## [0.0.5] - 2025-07-26

- Code Refactored
- gradle updated
- Sample project - updated

- ## [0.0.4] - 2025-07-26
- Bug Fixes

## [0.0.3] - 2025-07-26

- Added a ID to model class

- ## [0.0.2] - 2025-07-26
- Added comments

## [0.0.1] - 2025-07-26

- Initial release of `android_sms_reader`.
- Supports:
    - Fetch all SMS messages
    - Filter by inbox/sent/draft
    - Pagination
    - Search messages by keyword/number
    - Message count by type
    - Real-time incoming SMS stream
    - SMS permission handling
    - Export to JSON
