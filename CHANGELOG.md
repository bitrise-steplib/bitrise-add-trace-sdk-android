Change Log
==========

## 0.1.0 - 2021-04-16
* fix: **buildscript replace issue:** InjectTraceTask replaced first occurrence of buildscript,
but it should not be replaced if it is a String literal.
* fix: **String literals in build.gradle:** InjectTraceTask removed some String literals, when it contained
commenting chars. This has been fixed, String literals will not
be affected by the comment removal.
* feat!: **Rename input project_path:** Renamed input project_path to project_location.

## 0.0.5 - 2021-02-19
* fix: **Buildscript block order issue:** Buildscript blocks should be declared before any plugins block.
  This has been fixed.

## 0.0.4 - 2021-02-15
* fix: **Path issue:** Fixed path issue for default "project_path" step input.

## 0.0.3 - 2021-02-10
* fix: **Remove SDK repo references:** Removed SDK repo references from README.md and
step.yml, as it is currently private.
* fix: **SDK versioning policy update:** Step will use latest versions by default.

## 0.0.2 - 2021-02-08
* Maintenance release, no fixes or new features

## 0.0.1 - 2021-02-03
* Initial release
