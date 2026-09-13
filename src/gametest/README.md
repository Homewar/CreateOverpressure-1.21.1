Run the tube placement regression tests with Java 21:

```powershell
.\gradlew.bat --no-configuration-cache -I src/gametest/run.gradle runGameTestServer
```

The runner uses an isolated world in `build/tube-gametest-world` and separate
compiled classes/resources in `build/tube-gametest`. Test fixtures do not enter
the normal mod JAR. Check the GameTest summary for the number of passed tests;
the Minecraft launcher can exit successfully even when test startup fails.

`TubeSectionGameTests` covers standalone sections without support blocks:
persistence, appearance, collision and picking, surface interactions, legacy
migration with cargo, and transport through sections before and after a pump.
It also checks straight/curved joints and delivery after a straight tube is
added to the end of a curve while cargo is already travelling, including reload.
Animation tests cover uneven snapshot arrival, stopped cargo, delayed removal
and interpolation by distance along curved geometry.
`TubePlacementGameTests` checks that construction matches the preview and that
curved free ends do not create hidden or terminal blocks.
