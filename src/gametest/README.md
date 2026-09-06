Run the tube placement regression tests with Java 21:

```powershell
.\gradlew.bat --no-configuration-cache -I src/gametest/run.gradle runGameTestServer
```

The runner uses an isolated world in `build/tube-gametest-world` and separate
compiled classes/resources in `build/tube-gametest`. Test fixtures do not enter
the normal mod JAR. Check the GameTest summary for the number of passed tests;
the Minecraft launcher can exit successfully even when test startup fails.
