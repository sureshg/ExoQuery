# ExoQuery Changelog

### Version 1.3.1.PL-1.3.1 (May 29, 2025)
- **Bugfix**: Fix pretty-builder for query.buildPrettyFor ([#8](https://github.com/ExoQuery/ExoQuery/pull/8))
- **Bugfix**: `Fix param(kotlinx.datetime.*)`. ([#7](https://github.com/ExoQuery/ExoQuery/pull/7))
- **Bugfix**: Fixing various edge-cases for boolean vendorization ([#6](https://github.com/ExoQuery/ExoQuery/pull/6))

### Version 1.3.0.PL-1.3.0 (May 26, 2025)
- **Enhancement**: Implement window functions, fix impure + union-flattening cases ([#5](https://github.com/ExoQuery/ExoQuery/pull/5))

### Version 1.2.7.PL-1.2.7 (May 22, 2025)
- **Enhancement**: Move to standard JB annotations since it uses KMP ([49ab709](https://github.com/ExoQuery/ExoQuery/commit/49ab709))
- **Enhancement**: Bump controller, make keyword detection case-insensitive ([93bd4ae](https://github.com/ExoQuery/ExoQuery/commit/93bd4ae))
- **Enhancement**: Allow onConflictUpdate/onConflictIgnore to take nullable fields ([4123bb7](https://github.com/ExoQuery/ExoQuery/commit/4123bb7))

### Version 1.2.6.PL-1.2.6 (May 21, 2025)
- **Enhancement**: Allow nullable `.excluding(column)` for actions ([ab2ca52](https://github.com/ExoQuery/ExoQuery/commit/ab2ca52))

### Version 1.2.5.PL-1.2.5 (May 21, 2025)
- **Testing**: Out-of-box handling of value classes ([11c8fb8](https://github.com/ExoQuery/ExoQuery/commit/11c8fb8))

### Version 1.2.4.PL-1.2.4 (May 19, 2025)
- **Bugfix**: Fix setParams with value class members. Bump controller to 3.2.2. ([94029bd](https://github.com/ExoQuery/ExoQuery/commit/94029bd))
- **Enhancement**: Allow value classes for param(...) ([5433a0e](https://github.com/ExoQuery/ExoQuery/commit/5433a0e))
- **Infrastructure**: Beginning to move to toml libs ([eff9914](https://github.com/ExoQuery/ExoQuery/commit/eff9914))
- **Bugfix**: Fix multiple ExprToQuery cases including select-clause query and select 1 ([33db3f6](https://github.com/ExoQuery/ExoQuery/commit/33db3f6))
- **Infrastructure**: Support configuration without ExoOptions i.e. playground env ([5edb48d](https://github.com/ExoQuery/ExoQuery/commit/5edb48d))
- **Bugfix**: Fix SQL synthesis of Kotlin casts ([d982924](https://github.com/ExoQuery/ExoQuery/commit/d982924))
- **Bugfix**: Fix captured functions using extension-position ([993277c](https://github.com/ExoQuery/ExoQuery/commit/993277c))

### Version 1.2.2.PL-1.2.2 (May 9, 2025)
- **Bugfix**: Fixing issues with `toString/Int/Long/...` casting ([d4d3e71](https://github.com/ExoQuery/ExoQuery/commit/d4d3e71))
- **Bugfix**: Fix various type-conversion casting, most notably with `!!` ([5d9fc28](https://github.com/ExoQuery/ExoQuery/commit/5d9fc28))
- **Enhancement**: TypeParser should understand @Contextual on Type/Class as XRType.Value ([1c3bb5b](https://github.com/ExoQuery/ExoQuery/commit/1c3bb5b))
- **Bugfix**: Fixing custom serialization with `@Serializable(with = MyClass::class)` annotations on class members ([13b6510](https://github.com/ExoQuery/ExoQuery/commit/13b6510))

### Version 1.2.0.PL-1.2.0 (May 2, 2025)
- **Enhancement**: Add support for onConflictUpdate and onConflictIgnore in insert queries ([15f978a](https://github.com/ExoQuery/ExoQuery/commit/15f978a))
- **Enhancement**: Set runner-core to be API since it defines things like 'transaction' ([15f978a](https://github.com/ExoQuery/ExoQuery/commit/15f978a))
- **Bugfix**: Fix XRType-parsing errors that occurred in value classes ([15f978a](https://github.com/ExoQuery/ExoQuery/commit/15f978a))
- **Enhancement**: Change onConflict parsing and do not assign default identifier 'x' in the parser ([d906bbc](https://github.com/ExoQuery/ExoQuery/commit/d906bbc))
- **Enhancement**: Add language-injection to free ([d906bbc](https://github.com/ExoQuery/ExoQuery/commit/d906bbc))

### Version 1.1.2.PL-1.1.2 (April 30, 2025)
- **Bugfix**: Changing all source-main names to be alphanumeric due to DEX issues ([fc60a99](https://github.com/ExoQuery/ExoQuery/commit/fc60a99))
- **Bugfix**: Fix null-value encoder summoning errors ([656a50a](https://github.com/ExoQuery/ExoQuery/commit/656a50a))
- **Enhancement**: Change directory-creation failure (of query-record file) to warn ([cc53a70](https://github.com/ExoQuery/ExoQuery/commit/cc53a70))
- **Infrastructure**: Change `exoquery-runtime` module to `exoquery-engine` ([8908b5b](https://github.com/ExoQuery/ExoQuery/commit/8908b5b))
- **Enhancement**: Only write to query-file if contents different ([c192ad3](https://github.com/ExoQuery/ExoQuery/commit/c192ad3))
- **Infrastructure**: Make controller API dependency since upstreams explicitly need to create it ([0b2935d](https://github.com/ExoQuery/ExoQuery/commit/0b2935d))

### Version 1.1.0.PL-1.1.0 (April 30, 2025)
- **Enhancement**: Bump to Kotlin 2.1.20 ([0c2ff3a](https://github.com/ExoQuery/ExoQuery/commit/0c2ff3a))

### Version 1.0.1.PL-1.0.1 (April 29, 2025)
- **Enhancement**: Move runOn(ctr) to common ([934209c](https://github.com/ExoQuery/ExoQuery/commit/934209c))
- **Enhancement**: Refactor SqlQueryApply to use 'of' for correct rendering of top-level free queries ([3ea2319](https://github.com/ExoQuery/ExoQuery/commit/3ea2319))

### Version 1.0.0.PL-1.0.0 (April 29, 2025)
- **Enhancement**: Introduce `TopLevelFree` so top-level `free` queries do not need additional wrapping ([19a83fe](https://github.com/ExoQuery/ExoQuery/commit/19a83fe))

### Version 0.2.0.PL-0.2.0 (April 24, 2025)
- **Enhancement**: Implement naming override annotations ([edeb3a6](https://github.com/ExoQuery/ExoQuery/commit/edeb3a6))

### Version 0.1.0.PL-0.1.0 (April 22, 2025)
- **Infrastructure**: First release version ([f12a849](https://github.com/ExoQuery/ExoQuery/commit/f12a849))
