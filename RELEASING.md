Releasing
=========

 1. Change the version in `gradle.properties` to a non-SNAPSHOT verson.
 2. `git commit -am "Prepare for release X.Y.Z."` (where X.Y.Z is the new version)
 3. `./gradlew clean publish --no-parallel`.
 4. Visit [Sonatype Nexus](https://oss.sonatype.org/) and promote the artifact.
 5. `git tag -a X.Y.Z -m "Version X.Y.Z"` (where X.Y.Z is the new version)
 6. Update the `gradle.properties` to the next SNAPSHOT version.
 7. `git commit -am "Prepare next development version."`
 8. `git push && git push --tags`

If step 4 fails, drop the Sonatype repo, fix the problem, commit, and start again at step 3.

