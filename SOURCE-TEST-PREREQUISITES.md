# Source test prerequisites

The source archive matches this shipped candidate. Build uses Java8, Forge1.12.2,
Scala2.11.1 and the versioned Gradle/dependency declarations. Private offline tests
used existing task-local caches and toolchain; those caches are not in this ZIP.

Preservation regression fixtures also require the prior dev.9, dev.10 and dev.11 source
ZIPs and the dev.12 JAR in sibling deliverables/onejar-dev.N folders, as named in the
source tests. Those frozen originals remain in this task. Obtain the matching
prior bundles to repeat those comparisons elsewhere; do not replace them with
current output. Current source alone does not recreate historical artifacts.

Optional visual-preview main entry points reference the owner-local screenshots;
these images are not runtime assets and are not required for the normal JUnit
pixel/safety tests. No game, external service or host VM runs in these tests.
Dev.13 headless regression reuses the already-declared provided mod APIs on the
test task classpath so untransformed optional interfaces can load. This is a
test fixture prerequisite; it adds no required runtime mod or bundled archive.

Dev.15 preservation fixtures additionally use the frozen dev.14 JAR/source ZIP
for six-contact/half-bevel masks and retained preview/audio/source comparisons.
Keep those sibling deliverables when repeating the complete offline suite.
