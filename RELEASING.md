# Releasing

1. Update the `VERSION_NAME` in `gradle.properties` to the release version.

2. Commit

   ```
   $ git commit -am "Prepare version X.Y.Z"
   ```

3. Tag

   ```
   $ git tag -am "Version X.Y.Z" X.Y.Z
   ```

4. Update the `VERSION_NAME` in `gradle.properties` to the next "SNAPSHOT" version.

5. Commit

   ```
   $ git commit -am "Prepare next development version"
   ```

6. Push!

   ```
   $ git push && git push --tags
   ```

   This will trigger a GitHub Action workflow which will create a GitHub release and upload the
   release artifacts to Maven Central.
