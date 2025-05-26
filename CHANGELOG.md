# ExoQuery Changelog

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
- **Bugfix**: Fixing issues with casting ([d4d3e71](https://github.com/ExoQuery/ExoQuery/commit/d4d3e71))
- **Bugfix**: Fix type-conversion casting ([5d9fc28](https://github.com/ExoQuery/ExoQuery/commit/5d9fc28))
- **Enhancement**: TypeParser should understand @Contextual on Type/Class as XRType.Value ([1c3bb5b](https://github.com/ExoQuery/ExoQuery/commit/1c3bb5b))
- **Bugfix**: Fixing custom serialization issues and testing ([13b6510](https://github.com/ExoQuery/ExoQuery/commit/13b6510))
