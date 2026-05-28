# Changelog

All notable changes to this project are documented in this file.

## [0.1.3] - 2026-05-28

### Fixed

- Fixed executable WAR patch output corruption when a launch-script preamble exists.
  - Reworked patch output assembly to write ZIP payload first, then prepend preamble and adjust ZIP offsets.
  - Added ZIP offset rewrite support for central directory and ZIP64 metadata where needed.
- Preserved target archive entry order when rebuilding patched archives so executable WARs keep classpath-sensitive resources in the same order as the target artifact.
- Preserved file permissions during patch apply and backup creation to avoid losing executable bits on deployed archives.
- Added regression coverage for executable WAR patch flow to validate ZIP offset alignment, executable permission preservation, and classpath-sensitive entry ordering.
