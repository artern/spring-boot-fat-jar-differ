# Changelog

All notable changes to this project are documented in this file.

## [0.1.3] - 2026-05-28

### Fixed

- Fixed executable WAR patch output corruption when a launch-script preamble exists.
  - Reworked patch output assembly to write ZIP payload first, then prepend preamble and adjust ZIP offsets.
  - Added ZIP offset rewrite support for central directory and ZIP64 metadata where needed.
- Preserved file permissions during patch apply and backup creation to avoid losing executable bits on deployed archives.
- Added regression coverage for executable WAR patch flow to validate ZIP offset alignment and executable permission preservation.

### Changed

- Updated integration consumers in `nice-admin-projects` to use `spring-boot-fat-jar-differ` version `0.1.3`.
